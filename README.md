# PCA Control — Parental / Guard Android App

Personal parental-control APK: one install, choose **Parental** or **Guard**, link via SMS pairing code, then lock/unlock the Guard phone over **Firebase** and **SMS**.

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
./gradlew assembleRelease
```

APK path (also copied to repo root):

`app/build/outputs/apk/release/app-release.apk` → [`PCA-Control.apk`](PCA-Control.apk)

Install on both phones:

```bash
adb install -r PCA-Control.apk
```

## First-run flow (v1.1+)

1. Install the same APK on parent and child (Guard) phones.
2. **Guard phone:** open app → **Guard** → enable Device Admin → enter **Parent phone** (prefer country code, e.g. `+919004875711`) → **Send pairing code**.
   - Guard does **not** show the code or QR; Parent receives `PCA PAIR XXXXXX` by SMS.
3. **Parental phone:** open app → **Parental** → enter the SMS code → **Link**.
4. After link, Guard starts a **foreground service** (persistent notification) so lock works while other apps are open. Allow notifications + **unrestricted battery**.
5. Guard launcher icon is **hidden**. Re-open via **Settings → Apps → PCA Control** if needed.
6. On Parent home, optionally save the Guard phone number for SMS command fallback.

## Background lock (v1.1.1)

Remote lock used to fail when PCA was not on screen (Android blocks background activity starts). Guard now:

- Runs `GuardCommandService` while linked
- Posts a **full-screen lock notification** when Parent locks
- Re-shows the lock UI via a watchdog if gestures dismiss it

On first lock without Device Owner, Android may ask to **pin the screen** — accept that for stronger Home/Recents blocking.

## Device Owner (hard lock / uninstall protection)

On the **Guard** phone (USB debugging, preferably a clean user with no accounts):

```bash
adb shell dpm set-device-owner com.pca.control/.devicepolicy.PcaDeviceAdminReceiver
```

With Device Owner, lock task fully blocks Home/Recents while locked. Without it, screen pinning + sticky re-launch is the soft path.

## Parental actions

| Action | Behavior |
|--------|----------|
| **Lock device** | Custom lock screen; 6-digit unlock PIN SMS’d to Parent and shown in Parent app |
| **Unlock** | Clears the custom lock (remote). Typing the PIN on the Guard lock screen also unlocks |

Commands are written to Firestore (`devices/{guardId}/commands`) and, if a Guard number is saved, also sent as SMS.

## SMS keywords (text the Guard phone from the **Parent** number only)

- `PCA LOCK`
- `PCA UNLOCK`

Guard only accepts SMS commands when the sender matches the saved Parent phone (formats like `+91…` vs local 10-digit are treated as the same number).

## Firestore collections

- `pairings/{CODE}` — short-lived pairing session
- `devices/{deviceId}` — device registry (`activeLockPin`, `lockActive`, …)
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

- Soft lock without Device Owner / screen pinning is a **deterrent** — Settings / force-stop can still escape.
- Prefer saving parent numbers with country code (`+91…`) so SMS sender matching is reliable.
- Real **power off** is not possible without root/system privileges.
- Aggressive OEM battery savers can still delay lock — use “Allow unrestricted battery use” on Guard.
- Placeholder `google-services.json` builds the APK but pairing/FCM will fail until you add your Firebase file.

## Package

`com.pca.control` — version **1.1.1**
