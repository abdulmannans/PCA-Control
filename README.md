# PCA Control — Parental / Guard Android App

Personal parental-control APK: one install, choose **Parental** or **Guard**, link with a code/QR, then lock or lock+block the Guard phone over **Firebase** and **SMS**.

## Prerequisites

1. **JDK 17**
2. **Android SDK** (API 35, build-tools, platform-tools)
3. **Firebase project** (Spark is fine)
   - Create an Android app with package `com.pca.control`
   - Enable **Cloud Firestore** and **Cloud Messaging**
   - Download `google-services.json` and replace [`app/google-services.json`](app/google-services.json)
4. Set environment (example for Homebrew installs):

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
```

## Build APK

```bash
cd /Users/abdulmannansiddiquei/Sites/PCA
./gradlew assembleDebug
```

APK path:

`app/build/outputs/apk/debug/app-debug.apk`

Install on both phones:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## First-run flow

1. Install the same APK on parent and child (Guard) phones.
2. **Guard phone:** open app → **Guard** → enable Device Admin → **Generate pairing code** → leave QR/code on screen.
3. **Parental phone:** open app → **Parental** → enter code (and optional Guard phone number for SMS) → **Link**.
4. After link, Guard launcher icon is **hidden**. Re-open via **Settings → Apps → PCA Control** if needed.

## Device Owner (uninstall protection)

On the **Guard** phone (USB debugging, preferably a clean user with no accounts):

```bash
adb shell dpm set-device-owner com.pca.control/.devicepolicy.PcaDeviceAdminReceiver
```

Without Device Owner, lock still works with Device Admin; **lock+block apps** and hard uninstall protection need Device Owner.

## Parental actions

| Action | Behavior |
|--------|----------|
| **Lock device** | Immediate screen lock (`lockNow`) — recommended |
| **Lock + block apps** | Lock + suspend user apps until unlock (Device Owner) |
| **Unlock / clear app block** | Clears package suspensions |
| **Power off** | Disabled — tagged **not workable / risky** (Android blocks third-party shutdown) |

Commands are written to Firestore (`devices/{guardId}/commands`) and, if a Guard number is saved, also sent as SMS.

## SMS keywords (text the Guard phone)

- `PCA LOCK`
- `PCA LOCKBLOCK`
- `PCA UNLOCK`

Guard must grant SMS permission.

## Firestore collections

- `pairings/{CODE}` — short-lived pairing session
- `devices/{deviceId}` — device registry
- `devices/{guardId}/commands/{id}` — remote commands (`action`, `status`)

Suggested rules for personal use (tighten for production):

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if true; // personal project only
    }
  }
}
```

## Limitations

- Real **power off** is not possible without root/system privileges.
- Some OEMs kill background listeners; Device Owner + disabling battery optimization helps.
- Placeholder `google-services.json` builds the APK but pairing/FCM will fail until you add your Firebase file.
- Hiding the icon removes it from the app drawer; the package remains visible under Settings → Apps unless further restricted as Device Owner.

## Package

`com.pca.control`
