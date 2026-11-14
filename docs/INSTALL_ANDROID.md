# Installing on Android

## Get the APK

Either build it:

```bash
./gradlew :androidApp:assembleRelease
```

The file is `androidApp/build/outputs/apk/release/androidApp-release.apk` (about 10 MB).
A debug build (`assembleDebug`, about 14 MB) works too.

Or download the `knit-studio-android` artifact from the repository's **Actions** tab or the APK
attached to a GitHub release.

## Install

**From the phone**

1. Copy the APK to the phone (USB, cloud drive, or send it to yourself).
2. Open it from the Files app.
3. Android asks to allow installs from that app ("Install unknown apps") — allow it once.
4. Tap **Install**.

**From a computer with adb**

```bash
adb install -r androidApp/build/outputs/apk/release/androidApp-release.apk
```

## Good to know

- Requires Android 8.0 (API 26) or newer. Phones and tablets, portrait and landscape.
- The app requests **no permissions** and never uses the network.
- Both APKs are signed with the Android *debug* key so they can be installed directly. Debug keys
  differ per machine: an APK built on another computer cannot update an installed one — make a
  backup in the app (Settings › Back up everything), uninstall, install the new APK and restore.
  To avoid that, sign with your own keystore:
  1. `keytool -genkeypair -v -keystore knit.jks -keyalg RSA -keysize 2048 -validity 10000 -alias knit`
  2. Add a `signingConfigs` entry in `androidApp/build.gradle.kts` that reads the keystore path and
     passwords from `keystore.properties` or environment variables. Both `*.jks` and
     `keystore.properties` are git-ignored; never commit them.
- Play Protect may show a notice for apps from outside the Play Store. That is expected for a
  personal build.
- Your data lives in the app's private storage. Uninstalling deletes it; back up first.
