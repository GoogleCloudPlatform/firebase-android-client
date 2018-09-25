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

import android.content.Context;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import androidx.test.filters.LargeTest;
import androidx.test.filters.Suppress;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@LargeTest
public class SpeechTranslationHelperAndroidTest {

    @Suppress
    @Test
    public void translateAudioMessage_Success() throws IOException, InterruptedException {
        final Object waiter = new Object();

        String file = "assets/speech-recording-16khz.b64";
        InputStream in = this.getClass().getClassLoader().getResourceAsStream(file);
        InputStreamReader inputStreamReader = new InputStreamReader(in);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

        StringBuilder base64EncodedAudioMessage = new StringBuilder();

        String line;
        while((line = bufferedReader.readLine()) != null) {
            base64EncodedAudioMessage.append(line);
        }

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        synchronized (waiter) {
            SpeechTranslationHelper.getInstance()
                    .translateAudioMessage(context, base64EncodedAudioMessage.toString(),
                    16000, new SpeechTranslationHelper.SpeechTranslationListener() {
                        @Override
                        public void onTranslationSucceeded(String responseBody) {
                            assertTrue(true);
                            synchronized (waiter) {
                                waiter.notify();
                            }
                        }

                        @Override
                        public void onTranslationFailed(Exception e) {
                            fail();
                            synchronized (waiter) {
                                waiter.notify();
                            }
                        }
                    });

            synchronized (waiter) {
                waiter.wait();
            }
        }
    }
}