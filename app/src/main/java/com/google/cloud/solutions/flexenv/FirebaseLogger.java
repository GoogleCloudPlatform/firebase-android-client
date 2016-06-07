/**
 # Copyright Google Inc. 2016
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

// [START FirebaseLogger]
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.cloud.solutions.flexenv.common.LogEntry;

/*
 * FirebaseLogger pushes user event logs to a specified path.
 * A backend Servlet instance listens to
 * the same key and keeps track of event logs.
 */
public class FirebaseLogger {
    private DatabaseReference logRef;
    private String tag;

    public FirebaseLogger(DatabaseReference firebase, String path) {
        logRef = firebase.child(path);
    }

    public void log(String tag, String message) {
        LogEntry entry = new LogEntry(tag, message);
        logRef.push().setValue(entry);
    }

}
// [END FirebaseLogger]
