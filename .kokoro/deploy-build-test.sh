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
export adb_command="$ANDROID_HOME"'/platform-tools/adb'
# Install Android SDK, tools, and build tools API 29, system image, and emulator
echo "y" | ${ANDROID_HOME}/tools/bin/sdkmanager \
    "platforms;android-29" "tools" "platform-tools" "build-tools;29.0.3" \
    "system-images;android-29;google_apis;x86_64" "emulator"

export PATH=${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools:${PATH}

echo "Use the headless emulator build..."
cp "$ANDROID_HOME"'/emulator/qemu/linux-x86_64/qemu-system-x86_64-headless' \
   "$ANDROID_HOME"'/emulator/qemu/linux-x86_64/qemu-system-x86_64'

echo "Move to the tools/bin directory…"
cd ${ANDROID_HOME}/tools/bin
echo "no" | ./avdmanager create avd -n test -k "system-images;android-29;google_apis;x86_64"
echo ""
echo "Start the emulator…"
cd ${ANDROID_HOME}/emulator
emulator -avd test -no-audio -no-window -screen no-touch &
$adb_command wait-for-device

echo "Setting up speech translation microservice…"

git clone https://github.com/GoogleCloudPlatform/nodejs-docs-samples.git github/nodejs-docs-samples
export QT_DEBUG_PLUGINS=1
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
gcloud functions deploy speechTranslate --runtime nodejs10 --trigger-http --allow-unauthenticated \
    --update-env-vars ^:^OUTPUT_BUCKET=playchat-c5cc70f6-61ed-4640-91be-996721838560:SUPPORTED_LANGUAGE_CODES=en,es,fr

echo "Move to the root directory of the repo…"
cd ${cwd}/github/firebase-android-client

# Copy the Google services configuration file and test values
cp ${KOKORO_GFILE_DIR}/google-services.json app/google-services.json
cp ${KOKORO_GFILE_DIR}/speech_translation_test.xml app/src/main/res/values/speech_translation.xml

echo "Run tests and build APK file…"
$adb_command logcat --clear
$adb_command logcat v long > logcat_sponge_log &
./gradlew clean check build connectedAndroidTest
$adb_command logcat --clear

echo "Delete the Cloud Function…"
gcloud functions delete speechTranslate
