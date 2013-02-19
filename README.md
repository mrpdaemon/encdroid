Encdroid
========

Encdroid is an application for Android devices to access data in
[EncFS](http://www.arg0.net/encfs) volumes stored locally on the device OR on
cloud storage services like Dropbox, which is currently the only service
supported although more cloud services will be supported in future releases.

Encdroid uses [encfs-java](https://github.com/mrpdaemon/encfs-java) in order
to fully support all types of EncFS volumes and all possible operations on
these volumes including ability to write/rename/move files and directories.

## Installing

The easiest way to install Encdroid is through the
[Google Play store](https://play.google.com/store/apps/details?id=org.mrpdaemon.android.encdroid).

## Building

The recommended way of building Encdroid is using Eclipse and the Android
Developer Tools (ADT) plugin. Once installed simply "Import Existing Android
Code into Workspace" against the cloned repository.

### Android NDK

Since version 1.3 Encdroid also includes native code for improving PBKDF2
performance. The jni/ directory contains prebuilt versions of the openssl
libcrypto shared library for armeabi, armeabi-v7a, mips and x86. A small JNI
glue library in jni/pbkdf2.c provides PBKDF2 functionality using openssl. In
order to properly build the native components of the project please refer to the
[Android NDK documentation](http://developer.android.com/tools/sdk/ndk/index.html).
Once you have the NDK installed running the following on the toplevel project
directory should result in proper building and copying of the libraries to the
libs/ folder:

    ${NDK_ROOT}/ndk-build "APP_ABI := armeabi armeabi-v7a mips x86"

### Required Libraries

Libraries required to build Encdroid are committed to this repository under
the libs/ folder. The following notable libraries are used:

* encfs-java
* dropbox-android-sdk
* httpmime
* json_simple

Note that [openssl-android](https://github.com/guardianproject/openssl-android)
is also a requirement, however this repository contains prebuilt versions of the
library under jni/ so the project can be built without compiling openssl-android
from scratch.

### Target API Level

The recommended API level to build Encdroid against is currently: 13

### Dropbox API Keys

Since Dropbox API keys should be kept as a secret by each developer, this
repository doesn't contain valid Dropbox API keys. In order to get Dropbox
functionality working one must obtain their own Dropbox API keys through
the [Dropbox developer website] (https://www.dropbox.com/developers) and then
replace the stubs for APP_KEY and APP_SECRET in EDDropbox.java with their
own keys. If you are planning to contribute patches or pull requests to the
project please make sure to NOT disclose your own API keys.

## Licensing

Encdroid is free software released under the GNU General Public License. For
more information, please see the LICENSE file and the Free Software Foundation
[website](http://www.gnu.org/licenses/gpl.html).
