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
Note that due to U.S. export regulations around applications with encryption
functionality it is only available to download from the U.S. However since it is
open source software, anyone can download and build it for using it on their own
phone.

## Building

The recommended way of building Encdroid is using Eclipse and the Android
Developer Tools (ADT) plugin. Once installed simply "Import Existing Android
Code into Workspace" against the cloned repository.

### Required Libraries

Encdroid depends on the following libraries which must be placed in the libs/
directory before building:

* encfs-java (latest commit on git is recommended although it should build
  fine against the last release most of the the time).
* dropbox-android-sdk-1.5.1
* httpmime-4.0.3
* json_simple-1.1

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
