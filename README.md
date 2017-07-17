Encdroid
========

Encdroid is an application for Android devices to access data in
[EncFS](http://www.arg0.net/encfs) volumes stored locally on the device OR on
cloud storage services like Dropbox and Google Drive.

Encdroid uses [encfs-java](https://github.com/mrpdaemon/encfs-java) in order
to fully support all types of EncFS volumes and all possible operations on
these volumes including ability to write/rename/move files and directories.

## Installing

The easiest way to install Encdroid is through the
[Google Play store](https://play.google.com/store/apps/details?id=org.mrpdaemon.android.encdroid).

## Building

The recommended way to build Encdroid is to either import the project using
Android Studio, or using gradle directly through the command line. For example
to build the debug APK from the command line use the following:

`ANDROID_HOME=/path/to/your/android/sdk/dir` ./gradlew assembleDebug

Once the build completes the resulting APK will be under `encdroid/build/outputs/apk/endroid-debug.apk`

For a fully functional build see the instructions below on "Dropbox API keys"
and "Google Play Services".

### Target API Level

The recommended API level to build Encdroid against is currently: 21

### Dropbox API Keys

Since Dropbox API keys should be kept as a secret by each developer, this
repository doesn't contain valid Dropbox API keys. In order to get Dropbox
functionality working one must obtain their own Dropbox API keys through
the [Dropbox developer website] (https://www.dropbox.com/developers) and then
replace the stubs for APP_KEY and APP_SECRET in DropboxAccount.java with their
own keys. If you are planning to contribute patches or pull requests to the
project please make sure to NOT disclose your own API keys.

### Android NDK

Since version 1.3 Encdroid also includes native code for improving PBKDF2
performance. The encdroid/src/main/jni/ directory contains prebuilt versions of
the openssl libcrypto shared library for armeabi, armeabi-v7a, mips and x86. A
small JNI glue library in jni/pbkdf2.c provides PBKDF2 functionality using
openssl. The gradle build requires the NDK to be installed to build the native
components of the project.

### Required Libraries

Libraries required to build Encdroid are committed to this repository under
the libs/ folder. The following notable libraries are used:

* encfs-java
* dropbox-android-sdk
* httpmime
* json_simple
* Google API client
* Google http client
* Google API service for drive
* Google OAuth client
* Jackson

Note that [openssl-android](https://github.com/guardianproject/openssl-android)
is also a requirement, however this repository contains prebuilt versions of the
library under jni/ so the project can be built without compiling openssl-android
from scratch.

## Licensing

Encdroid is free software released under the GNU General Public License. For
more information, please see the LICENSE file and the Free Software Foundation
[website](http://www.gnu.org/licenses/gpl.html).
