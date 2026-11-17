# Signed APK and Play Store release

Package name: `com.kashvault`  
Current release: `versionName 1.1.1` / `versionCode 4` in `app/build.gradle.kts`

Play Store accepts an **Android App Bundle (`.aab`)**, not a raw APK. Use a signed APK only for sideloading or for a device that cannot install from Play.

Never commit `keystore.properties`, `*.jks`, or `*.keystore`. Keep one copy of the upload keystore offline. If that file is lost and Play App Signing is already on, you can still ship updates. If it is lost before the first upload, you cannot update the same package name.

## 1. Create the upload keystore (once)

From the repo root:

```bash
mkdir -p keystore
keytool -genkeypair -v \
  -keystore keystore/kashvault-upload.jks \
  -storetype JKS \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias kashvault
```

Use a strong store password and key password. Record:

- file path: `keystore/kashvault-upload.jks`
- alias: `kashvault`
- both passwords

Print the **upload-key SHA-1** (needed for Google Sign-In):

```bash
keytool -list -v -keystore keystore/kashvault-upload.jks -alias kashvault
```

Copy to `keystore.properties`:

```bash
cp keystore.properties.example keystore.properties
```

Fill in `storePassword`, `keyAlias`, and `keyPassword`. CI can use the same values as environment variables instead:

| `keystore.properties` | Environment variable |
|-----------------------|----------------------|
| `storeFile` | `KASHVAULT_STORE_FILE` |
| `storePassword` | `KASHVAULT_STORE_PASSWORD` |
| `keyAlias` | `KASHVAULT_KEY_ALIAS` |
| `keyPassword` | `KASHVAULT_KEY_PASSWORD` |

Without this file (or those env vars), release tasks fail before producing an APK or AAB. A release artifact must always use the stable upload key.

## 2. Bump the version

Edit `app/build.gradle.kts`:

```kotlin
versionCode = 5        // must increase on every Play upload
versionName = "1.1.2"  // user-visible
```

`applicationId` (`com.kashvault`) must not change after the first Play upload.

## 3. Generate a signed APK (sideload)

```bash
./gradlew :app:assembleRelease
```

Output:

`app/build/outputs/apk/release/app-release.apk`

Confirm it is signed with the upload key, not debug:

```bash
keytool -printcert -jarfile app/build/outputs/apk/release/app-release.apk
```

The SHA-1 must match the upload keystore, not Android Studio’s debug key (`Android Debug`).

Install on a device:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

If Android reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, an installed `com.kashvault` APK was signed with a different key and cannot be updated in place. Install a release signed by the original key, or back up its data and uninstall it before installing the new release:

```bash
adb uninstall com.kashvault
```

Android 11+ enforces this signer check; changing `minSdk`, `targetSdk`, or the manifest cannot bypass it.

For development builds, use the distinct `com.kashvault.debug` package. It can coexist with the release app:

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 4. Generate a Play Store bundle

```bash
./gradlew :app:bundleRelease
```

Output:

`app/build/outputs/bundle/release/app-release.aab`

Keep the ProGuard mapping from the same build for crash deobfuscation:

`app/build/outputs/mapping/release/mapping.txt`

## 5. First-time Play Console setup

1. Open [Google Play Console](https://play.google.com/console) with the publisher account.
2. **Create app** → name **KashVault**, default language, app or game = app, free or paid.
3. Accept the declarations and complete **All apps** / **App content** tasks as Play shows them.
4. Leave **Play App Signing** enabled (default). Google holds the app-signing key; you upload with the upload keystore from step 1.

### Store listing

Use the Play Console **Main store listing**:

- **App name:** KashVault
- **Short description** (80 characters or fewer): On-device money tracker for banks, cards, dues, and UPI inbox.
- **Full description:** see the Features list in `README.md`.
- **App icon:** 512 × 512 PNG (export from the launcher mark).
- **Feature graphic:** 1024 × 500 PNG.
- **Phone screenshots:** at least 2 (Play currently expects 2–8). Capture Home, Add, Wealth, Inbox, and Settings.
- **Contact email** and, if you collect account data, a **privacy policy URL**.

KashVault stores finances, Google account tokens, and optional SMS/UPI text on device. Host a privacy policy before production.

### App content and policy

Complete every item Play marks required, including:

- **Privacy policy** URL
- **Data safety:** on-device finance data; Google Sign-In / Drive `appDataFolder` if those APIs are used; no sale of data
- **Content rating** questionnaire
- **Target audience**
- **News app** / **COVID-19** / **Data safety** declarations as shown
- **Ads:** this app does not show ads
- **Government / finance** questions: personal finance tracker, not a bank or payments app

`READ_SMS` is a **restricted** Play permission. Play listings should rely on **notification access** plus paste. Sideloaded APKs can still use Scan SMS. Do not declare SMS as a core Play feature unless Google has approved the exception.

`compileSdk` / `targetSdk` is **36**, which meets the Play target API requirement for updates after 31 August 2026.

### Google Sign-In and Drive

In [Google Cloud Console](https://console.cloud.google.com/apis/credentials), configure the Android OAuth client for the release package `com.kashvault` with **both** SHA-1 fingerprints:

1. **Upload-key SHA-1** from `keytool -list` on `kashvault-upload.jks`
2. **App-signing SHA-1** from Play Console → **Test and release** → **App integrity** → **App signing** (shown after the first AAB upload)

Debug SHA-1 is only for local `assembleDebug` builds. Because debug uses `com.kashvault.debug`, register a separate Android OAuth client for that package and the debug SHA-1 if you test Google Sign-In there. Enable the Drive API and `drive.appdata` on the same Cloud project. Set `GOOGLE_WEB_CLIENT_ID` in `gradle.properties` or paste it in Profile.

## 6. Upload and roll out

1. Play Console → **Test and release** → **Testing** → **Internal testing**.
2. **Create new release** → upload `androidApp-release.aab`.
3. Paste release notes (what changed in this `versionCode`).
4. Review and start the internal rollout. Add testers by email or Google Group.
5. Install from the internal testing link and verify sign-in, backup, and a debug-vs-release Google SHA-1 if Sign-In fails.
6. Promote the same release to **Closed testing**, **Open testing**, or **Production** when it looks right.
7. Production: choose countries, review the summary, and roll out (can start at a percentage).

Subsequent updates: bump `versionCode`, run `bundleRelease`, upload a new AAB to a release track. Mapping files can be uploaded on the release’s App bundle explorer page if Play did not pick them up from the AAB.

## 7. Checklist

- [ ] Upload keystore exists only on disk / password manager, not in git
- [ ] `keystore.properties` filled, or CI env vars set
- [ ] `versionCode` increased
- [ ] `./gradlew :app:bundleRelease` produced `app-release.aab`
- [ ] APK/AAB SHA-1 is the upload key, not debug
- [ ] Play listing, privacy policy, data safety, and content rating are complete
- [ ] Android OAuth client has upload SHA-1 and Play app-signing SHA-1
- [ ] Internal test install works before production
