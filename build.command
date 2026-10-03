#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -d '/Applications/Android Studio.app/Contents/jbr/Contents/Home' ]]; then
  export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
fi
if [[ -z "${ANDROID_HOME:-}" ]]; then
  export ANDROID_HOME="$HOME/Library/Android/sdk"
fi
if [[ ! -f "$ANDROID_HOME/platforms/android-35/android.jar" ]]; then
  echo 'Install Android Studio and Android SDK Platform 35 first (Tools > SDK Manager).'
  exit 1
fi
if ! java -version 2>&1 | head -1 | grep -Eq '"(17|18|19|2[0-9])'; then
  echo 'Java 17 or newer is required. Android Studio includes a suitable Java runtime.'
  exit 1
fi
task_gradle="$PWD/.tools/gradle-8.11.1/bin/gradle"
if [[ ! -x "$task_gradle" ]]; then
  mkdir -p .tools
  curl --fail --location --retry 2 https://services.gradle.org/distributions/gradle-8.11.1-bin.zip -o .tools/gradle.zip
  curl --fail --location https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256 -o .tools/gradle.sha256
  task_expected="$(cat .tools/gradle.sha256)"
  task_actual="$(shasum -a 256 .tools/gradle.zip | awk '{print $1}')"
  if [[ "$task_expected" != "$task_actual" ]]; then echo 'Gradle checksum failed.'; exit 1; fi
  unzip -q -o .tools/gradle.zip -d .tools
fi
"$task_gradle" :app:assembleDebug :app:lintDebug --no-daemon
mkdir -p ../outputs
cp app/build/outputs/apk/debug/app-debug.apk ../outputs/SS-Downloader.apk
echo 'Built APK: ../outputs/SS-Downloader.apk'
