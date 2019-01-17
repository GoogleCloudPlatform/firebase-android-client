/*
 * Copyright 2016 Google LLC.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.solutions.flexenv.common;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.cloud.solutions.flexenv.R;

import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Singleton class that makes authenticated requests to a Google Cloud Storage bucket to get the
 * files that contain translated speech messages.
 */
public class GcsDownloadHelper {
    private static final String GCS_DOWNLOAD_HTTP_METHOD = "GET";
    private static final String TAG = "GcsDownloadHelper";

    private static GcsDownloadHelper _instance;

    private GcsDownloadHelper() { }

    public static GcsDownloadHelper getInstance() {
        if(_instance == null) {
            _instance = new GcsDownloadHelper();
        }
        return _instance;
    }

    /**
     * Performs an authenticated request to a Google Cloud Storage bucket to download the object in
     * the specified path. Returns a pointer to the downloaded file in the local storage.
     * @param context The application context.
     * @param gcsBucket The Google Cloud Storage bucket that contains the object to download.
     * @param gcsPath The path of the object in the bucket.
     * @param downloadListener The callback to deliver the results to.
     */
    public void downloadGcsFile(Context context, CronetEngine cronetEngine, String gcsBucket,
                                String gcsPath, GcsDownloadListener downloadListener) {
        getGcsAccessToken(context, new GcsTokenListener() {
            @Override
            public void onAccessTokenRequestSucceeded(String token) {
                UrlRequest request = buildGcsRequest(context, cronetEngine, gcsBucket, gcsPath,
                        token, downloadListener);
                request.start();
            }
            @Override
            public void onAccessTokenRequestFailed(Exception e) {
                downloadListener.onDownloadFailed(e);
            }
        });
    }

    private void getGcsAccessToken(Context context, GcsTokenListener tokenListener) {
        Runnable runnable = () -> {
            try {
                // [START auth-token]
                Account currentAccount = AccountManager.get(context).getAccounts()[0];
                String scope = "oauth2:" + context.getString(R.string.speechToSpeechOAuth2Scope);
                String token = GoogleAuthUtil.getToken(
                        context, currentAccount, scope, new Bundle());
                // [END auth-token]
                tokenListener.onAccessTokenRequestSucceeded(token);
            } catch (GoogleAuthException | IOException e) {
                Log.e(TAG, e.getLocalizedMessage());
                tokenListener.onAccessTokenRequestFailed(e);
            }
        };
        AsyncTask.execute(runnable);
    }

    private UrlRequest buildGcsRequest(Context context, CronetEngine cronetEngine, String gcsBucket,
                                       String gcsPath, String accessToken,
                                       GcsDownloadListener downloadListener) {
        Executor executor = Executors.newSingleThreadExecutor();

        String gcsPathEndpoint = context.getString(R.string.gcsBaseEndpoint) + gcsBucket + "/" + gcsPath;

        UrlRequest.Builder requestBuilder = cronetEngine.newUrlRequestBuilder(
                gcsPathEndpoint, new UrlRequest.Callback(){
                    FileChannel outputChannel;
                    String localPath;
                    File downloadedFile;
                    @Override
                    public void onRedirectReceived(UrlRequest request, UrlResponseInfo info, String newLocationUrl) {
                        Log.i(TAG, "onRedirectReceived method called.");
                        request.followRedirect();
                    }
                    @Override
                    public void onResponseStarted(UrlRequest request, UrlResponseInfo info) {
                        Log.i(TAG, "onResponseStarted method called.");
                        int httpStatusCode = info.getHttpStatusCode();
                        switch (httpStatusCode) {
                            case 200:
                                localPath = context.getFilesDir() + "/" + gcsBucket + "/" + gcsPath;
                                downloadedFile = new File(localPath);
                                try {
                                    // Check if parent folders exists
                                    File languageFolder = downloadedFile.getParentFile();
                                    if(!languageFolder.exists()) {
                                        if(!languageFolder.mkdirs()) {
                                            String message = "Failed to create directory "
                                                    + languageFolder.getAbsolutePath();
                                            downloadListener.onDownloadFailed(new IOException(message));
                                        }
                                    }
                                    outputChannel = new FileOutputStream(localPath).getChannel();
                                    request.read(ByteBuffer.allocateDirect((int) info.getReceivedByteCount()));
                                } catch (IOException e) {
                                    downloadListener.onDownloadFailed(e);
                                }
                                break;
                            case 403:
                                String errorMessage = "HTTP status code: " + httpStatusCode +
                                        ". Does the user have read access to the GCS bucket?";
                                downloadListener.onDownloadFailed(new SpeechTranslationException(errorMessage));
                                break;
                            default:
                                errorMessage = "Unexpected HTTP status code: " + httpStatusCode;
                                downloadListener.onDownloadFailed(new SpeechTranslationException(errorMessage));
                                break;
                        }
                    }
                    @Override
                    public void onReadCompleted(UrlRequest request, UrlResponseInfo info, ByteBuffer byteBuffer) {
                        Log.i(TAG, "onReadCompleted method called.");
                        request.read(ByteBuffer.allocateDirect((int)info.getReceivedByteCount()));
                        byteBuffer.flip();
                        try {
                            outputChannel.write(byteBuffer);
                        } catch (IOException e) {
                            downloadListener.onDownloadFailed(e);
                        }
                    }
                    @Override
                    public void onSucceeded(UrlRequest request, UrlResponseInfo info) {
                        Log.i(TAG, "onSucceeded method called.");
                        int httpStatusCode = info.getHttpStatusCode();
                        if(httpStatusCode == 200) {
                            try {
                                outputChannel.close();
                                File downloadedFile = new File(localPath);
                                downloadListener.onDownloadSucceeded(downloadedFile);
                            } catch (IOException e) {
                                downloadListener.onDownloadFailed(e);
                            }
                        }
                    }
                    @Override
                    public void onFailed(UrlRequest request, UrlResponseInfo responseInfo, CronetException error) {
                        Log.e(TAG, "The request failed.", error);
                        downloadListener.onDownloadFailed(error);
                    }
                } , executor)
                .setHttpMethod(GCS_DOWNLOAD_HTTP_METHOD)
                .addHeader("Authorization", "Bearer " + accessToken);

        return requestBuilder.build();
    }

    public interface GcsDownloadListener {
        void onDownloadSucceeded(File file);
        void onDownloadFailed(Exception e);
    }
    private interface GcsTokenListener {
        void onAccessTokenRequestSucceeded(String token);
        void onAccessTokenRequestFailed(Exception e);
    }
}
