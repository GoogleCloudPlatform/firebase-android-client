#!/bin/bash
# Copyright 2018 Google LLC.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Fail on any error.
set -e

if [ ! -d ${HOME}/android-sdk ]; then
    mkdir -p ${HOME}/android-sdk
    pushd "${HOME}/android-sdk"
    wget https://dl.google.com/android/repository/sdk-tools-linux-4333796.zip
    unzip sdk-tools-linux-4333796.zip
    popd
fi

export ANDROID_HOME="${HOME}/android-sdk"
# Install Android SDK, tools, and build tools API 27, system image, and emulator
echo "y" | ${ANDROID_HOME}/tools/bin/sdkmanager \
    "platforms;android-27" "tools" "platform-tools" "build-tools;27.0.3" \
    "system-images;android-27;default;x86_64" "emulator"

export PATH=${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools:${PATH}

# Save starting directory. The script needs it later.
cwd=$(pwd)

env

echo "Move to the tools/bin directory…"
cd ${ANDROID_HOME}/tools/bin
echo "no" | ./avdmanager create avd -n test -k "system-images;android-27;default;x86_64"
echo ""
echo "Start the emulator…"
cd ..
emulator -avd test -no-audio -no-window &
adb wait-for-device

echo "Move to the root directory of the repo…"
cd ${cwd}/github/firebase-android-client

# Copy the Google services configuration file
cp ${KOKORO_GFILE_DIR}/google-services.json app/google-services.json

echo "Run tests and build APK file…"
./gradlew clean check build connectedAndroidTest
