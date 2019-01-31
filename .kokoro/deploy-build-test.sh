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

# Save starting directory. The script needs it later.
cwd=$(pwd)

echo "Setting up speech translation microservice…"

git clone https://github.com/GoogleCloudPlatform/nodejs-docs-samples.git github/nodejs-docs-samples
export GOOGLE_APPLICATION_CREDENTIALS=${KOKORO_GFILE_DIR}/secrets-key.json
export GCLOUD_PROJECT=nodejs-docs-samples-tests
export GCF_REGION=us-central1
export NODE_ENV=development
export FUNCTIONS_TOPIC=integration-tests-instance
export FUNCTIONS_BUCKET=$GCLOUD_PROJECT
gcloud auth activate-service-account --key-file "$GOOGLE_APPLICATION_CREDENTIALS"
gcloud config set project $GCLOUD_PROJECT
cd ./github/nodejs-docs-samples/functions/speech-to-speech/functions
env
gcloud components update
gcloud --version
gcloud beta functions deploy speechTranslate --runtime nodejs6 --trigger-http \
    --update-env-vars ^:^OUTPUT_BUCKET=playchat-c5cc70f6-61ed-4640-91be-996721838560:SUPPORTED_LANGUAGE_CODES=en,es,fr

echo "Setting up Android environment…"
cd ${cwd}
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

# Copy the Google services configuration file and test values
cp ${KOKORO_GFILE_DIR}/google-services.json app/google-services.json
cp ${KOKORO_GFILE_DIR}/speech_translation_test.xml app/src/main/res/values/speech_translation.xml

echo "Run tests and build APK file…"
adb logcat --clear
adb logcat v long > logcat_sponge_log &
./gradlew clean check build connectedAndroidTest
adb logcat --clear

echo "Delete the Cloud Function…"
gcloud beta functions delete speechTranslate
