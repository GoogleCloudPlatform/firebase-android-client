# Build a mobile app using  Firebase and App Engine Flexible Environment
This repository contains Android client sample code for "[Build a mobile app using  Firebase and App Engine Flexible Environment backend](https://cloud.google.com/solutions/mobile/mobile-app-backend-on-cloud-platform#firebase-managed-vms)" paper.

## Build Requirements
Following Google APIs are needed to be enabled from Google Developers Console.
- Google App Engine
- Google Compute Engine
- Sign up with [Firebase](https://www.firebase.com/) and obtain Firebase URL.

Firebase is a Google product, independent from Google Cloud Platform.

Build and test environment(verified)
- Android Studio 1.5.1
- Marshmallow API Level 23, x86_64, Android 6.0 (with Google APIs)
- UI layout is optimal for Nexus 5.


## Configuration

- Make sure to complete [ALL steps](https://developers.google.com/identity/sign-in/android/start-integrating) to generate the necessary credentials.
  - App name : play
  - Android package name : com.google.cloud.solutions.managedvm
  - service : Google Sign-in

- Configure "app/res/values/strings.xml" file.
```xml
...
    <string name="firebase_endpoint">"Firebase URL"</string>
...
```


## Launch and test
- Start a virtual device and run the app.
- Sign in with a Google ID.
- Select a channel from top-left menu and enter messages.

![Nexus 5](./nexus5.png)


## License
 Copyright 2016 Google Inc. All Rights Reserved.

 Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
      http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS-IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the specific language governing permissions and limitations under the License.

This is not an official Google product.
