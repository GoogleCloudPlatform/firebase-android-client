
/**
 # Copyright 2016 Google LLC.
 # Licensed under the Apache License, Version 2.0 (the "License");
 # you may not use this file except in compliance with the License.
 # You may obtain a copy of the License at
 #
 # http://www.apache.org/licenses/LICENSE-2.0
 #
 # Unless required by applicable law or agreed to in writing, software
 # distributed under the License is distributed on an "AS IS" BASIS,
 # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 # See the License for the specific language governing permissions and
 # limitations under the License.
 **/

package com.google.cloud.solutions.flexenv;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.cloud.solutions.flexenv.common.Message;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/*
 * Main activity to select a channel and exchange messages with other users
 * The app expects users to authenticate with Google ID. It also sends user
 * activity logs to a servlet instance through Firebase.
 */
public class PlayActivity
        extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        GoogleApiClient.OnConnectionFailedListener,
        View.OnKeyListener,
        View.OnClickListener {

    // Firebase keys commonly used with backend servlet instances
    private static final String IBX = "inbox";
    private static final String CHS = "channels";
    private static final String REQLOG = "requestLogger";

    private static final int RC_SIGN_IN = 9001;

    private static final String TAG = "PlayActivity";
    private static final String CURRENT_CHANNEL_KEY = "CURRENT_CHANNEL_KEY";
    private static final String INBOX_KEY = "INBOX_KEY";
    private static final String FIREBASE_LOGGER_PATH_KEY = "FIREBASE_LOGGER_PATH_KEY";
    private static FirebaseLogger fbLog;

    private GoogleApiClient mGoogleApiClient;
    private FirebaseUser currentUser;
    private DatabaseReference databaseReference;
    private String firebaseLoggerPath;
    private FirebaseAuth.AuthStateListener authListener;
    private String inbox;
    private String currentChannel;
    private ChildEventListener channelListener;
    private SimpleDateFormat fmt;

    private Menu channelMenu;
    private TextView channelLabel;
    private List<Map<String, String>> messages;
    private SimpleAdapter messageAdapter;
    private EditText messageText;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ListView messageHistory;
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_play);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        channelMenu = navigationView.getMenu();
        navigationView.setNavigationItemSelectedListener(this);
        initChannels();

        GoogleSignInOptions gso =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();
        authListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                currentUser = firebaseAuth.getCurrentUser();
                if (currentUser != null) {
                    inbox = "client-" + Integer.toString(Math.abs(currentUser.getUid().hashCode()));
                    requestLogger(new LoggerListener() {
                        @Override
                        public void onLoggerAssigned() {
                            Log.d(TAG, "onAuthStateChanged:signed_in:" + inbox);
                            status.setText(String.format(getResources().getString(R.string.signed_in_label),
                                    currentUser.getDisplayName())
                            );
                            fbLog.log(inbox, "Signed in");
                            updateUI(true);
                        }
                    });
                } else {
                    Log.d(TAG, "onAuthStateChanged:signed_out");
                    updateUI(false);
                }
            }
        };

        SignInButton signInButton = findViewById(R.id.sign_in_button);
        signInButton.setSize(SignInButton.SIZE_STANDARD);
        signInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
                // Start authenticating with Google ID first.
                startActivityForResult(signInIntent, RC_SIGN_IN);
            }
        });
        channelLabel = findViewById(R.id.channelLabel);
        Button signOutButton = findViewById(R.id.sign_out_button);
        signOutButton.setOnClickListener(this);

        messages = new ArrayList<>();
        messageAdapter = new SimpleAdapter(this, messages, android.R.layout.simple_list_item_2,
                new String[]{"message", "meta"}, new int[]{android.R.id.text1, android.R.id.text2});
        messageHistory = findViewById(R.id.messageHistory);
        messageHistory.setAdapter(messageAdapter);
        messageText = findViewById(R.id.messageText);
        messageText.setOnKeyListener(this);
        fmt = new SimpleDateFormat("yy.MM.dd HH:mm z", Locale.US);

        status = findViewById(R.id.status);
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseAuth.getInstance().addAuthStateListener(authListener);
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (authListener != null) {
            FirebaseAuth.getInstance().removeAuthStateListener(authListener);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            Log.d(TAG, "SignInResult : " + result.isSuccess());
            // If Google ID authentication is successful, obtain a token for Firebase authentication.
            if (result.isSuccess() && result.getSignInAccount() != null) {
                currentUser = FirebaseAuth.getInstance().getCurrentUser();
                status.setText(getResources().getString(R.string.authenticating_label));
                AuthCredential credential = GoogleAuthProvider.getCredential(
                        result.getSignInAccount().getIdToken(), null);
                FirebaseAuth.getInstance().signInWithCredential(credential)
                        .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                Log.d(TAG, "signInWithCredential:onComplete:" + task.isSuccessful());
                                if (!task.isSuccessful()) {
                                    Log.w(TAG, "signInWithCredential", task.getException());
                                    status.setText(String.format(
                                            getResources().getString(R.string.authentication_failed),
                                            task.getException())
                                    );
                                }
                            }
                        });
            } else {
                updateUI(false);
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.sign_out_button) {
            signOut();
        }
    }

    private void signOut() {
        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        FirebaseAuth.getInstance().signOut();
                        databaseReference.removeEventListener(channelListener);
                        databaseReference.onDisconnect();
                        inbox = null;
                        currentUser = null;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateUI(false);
                            }
                        });
                        fbLog.log(inbox, "Signed out");
                    }
                });
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
            databaseReference.child(CHS + "/" + currentChannel)
                    .push()
                    .setValue(new Message(messageText.getText().toString(), currentUser.getDisplayName()));
            return true;
        }
        return false;
    }

    private void addMessage(String msgString, String meta) {
        Map<String, String> message = new HashMap<>();
        message.put("message", msgString);
        message.put("meta", meta);
        messages.add(message);

        messageAdapter.notifyDataSetChanged();
        messageText.setText("");
    }

    private void updateUI(boolean signedIn) {
        if (signedIn) {
            findViewById(R.id.sign_in_button).setVisibility(View.GONE);
            findViewById(R.id.sign_out_button).setVisibility(View.VISIBLE);
            findViewById(R.id.channelLabel).setVisibility(View.VISIBLE);
            findViewById(R.id.messageText).setVisibility(View.VISIBLE);
            findViewById(R.id.messageHistory).setVisibility(View.VISIBLE);
            findViewById(R.id.status).setVisibility(View.VISIBLE);

            // Select the first channel in the array if there's no channel selected
            switchChannel(currentChannel != null ? currentChannel :
                    getResources().getStringArray(R.array.channels)[0]);
        }
        else {
            findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
            findViewById(R.id.sign_out_button).setVisibility(View.GONE);
            findViewById(R.id.channelLabel).setVisibility(View.GONE);
            findViewById(R.id.messageText).setVisibility(View.GONE);
            findViewById(R.id.messageHistory).setVisibility(View.GONE);
            findViewById(R.id.status).setVisibility(View.GONE);
            ((TextView)findViewById(R.id.status)).setText("");
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);

        switchChannel(item.toString());

        return true;
    }

    private void switchChannel(String channel) {
        messages.clear();

        String msg = "Switching channel to '" + channel + "'";
        fbLog.log(inbox, msg);

        // Switching a listener to the selected channel.
        databaseReference.child(CHS + "/" + currentChannel).removeEventListener(channelListener);
        currentChannel = channel;
        databaseReference.child(CHS + "/" + currentChannel).addChildEventListener(channelListener);

        channelLabel.setText(currentChannel);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(CURRENT_CHANNEL_KEY, currentChannel);
        outState.putString(INBOX_KEY, inbox);
        outState.putString(FIREBASE_LOGGER_PATH_KEY, firebaseLoggerPath);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        currentChannel = savedInstanceState.getString(CURRENT_CHANNEL_KEY);
        inbox = savedInstanceState.getString(INBOX_KEY);
        firebaseLoggerPath = savedInstanceState.getString(FIREBASE_LOGGER_PATH_KEY);
        if (currentUser != null) {
            databaseReference = FirebaseDatabase.getInstance().getReference();
            fbLog = new FirebaseLogger(databaseReference, firebaseLoggerPath);
            updateUI(true);
        }
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {}

// [START requestLogger]
    /*
     * Request that a servlet instance be assigned.
     */
    private void requestLogger(final LoggerListener loggerListener) {
        databaseReference = FirebaseDatabase.getInstance().getReference();
        databaseReference.child(IBX + "/" + inbox).removeValue();
        databaseReference.child(IBX + "/" + inbox).addValueEventListener(new ValueEventListener() {
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getValue(String.class) != null) {
                    firebaseLoggerPath = IBX + "/" + snapshot.getValue(String.class) + "/logs";
                    fbLog = new FirebaseLogger(databaseReference, firebaseLoggerPath);
                    databaseReference.child(IBX + "/" + inbox).removeEventListener(this);
                    loggerListener.onLoggerAssigned();
                }
            }

            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, error.getDetails());
            }
        });

        databaseReference.child(REQLOG).push().setValue(inbox);
    }
// [END requestLogger]

    /*
     * Initialize predefined channels as activity menu.
     * Once a channel is selected, ChildEventListener is attached and
     * waits for messages.
     */
    private void initChannels() {
        String[] channelArray = getResources().getStringArray(R.array.channels);
        Log.d(TAG, "Channels : " + Arrays.toString(channelArray));
        for (String topic : channelArray) {
            channelMenu.add(topic);
        }

        channelListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String prevKey) {
                Message message = snapshot.getValue(Message.class);
                // Extract attributes from Message object to display on the screen.
                if(message != null && !message.getText().isEmpty()) {
                    addMessage(message.getText(), fmt.format(new Date(message.getTimeLong())) + " "
                            + message.getDisplayName());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, error.getDetails());
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String prevKey) {}

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {}

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, String prevKey) {}
        };
    }

    /**
     * A listener to get notifications about server-side loggers.
     */
    private interface LoggerListener {
        /**
         * Called when a logger has been assigned to this client.
         */
        void onLoggerAssigned();
    }
}
