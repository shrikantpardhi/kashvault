# KashVault

KashVault is an **Android** personal finance app built with Kotlin and Compose Multiplatform. Financial data is stored in a standard SQLite database in the app's private storage; KashVault does not encrypt that database.

The repository includes some early iOS source, but it does not currently configure or provide a buildable iOS app. Android is the supported platform.

## Architecture

| Module | Purpose |
|--------|---------|
| `shared/` | Kotlin + Compose UI, domain models, SQLDelight, Gemini client, Google Sign-In |
| `app/` | Android APK entry (`MainActivity`) |

## Features

- Dashboard (liquidity, net worth, 30-day horizon, budget pulse, recent transactions)
- Add transaction (manual + Magic Log AI parsing, bank or credit-card source)
- Banks & credit cards as payment sources for expense and income
- Wealth hub (SIP/investments, loans/EMI, savings goals, emergency fund)
- Lending (borrow/lend with per-person netting)
- History with daily/weekly/monthly filters
- Monthly budgets
- Category Studio (emoji, color, SMS/UPI tags)
- SMS / UPI inbox (per bank or card; accept into Magic Log)
- Liquidity forecast (local runway + optional Gemini)
- Google Sign-In
- Standard SQLite vault backup and restore (Google Drive appDataFolder + on-device `.db` export/import; backups are not encrypted by KashVault)
- AI Financial Advisor
- Offline-first SQLite (`kashvault.db`)

## Prerequisites

- JDK 21+
- Android Studio or Android SDK command-line tools

## Android APK and Play Store

Debug APK (local install):

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk` (package `com.kashvault.debug`, so it can coexist with the release app).

Signed release APK (sideload) and Play bundle:

```bash
cp keystore.properties.example keystore.properties   # then fill in passwords
./gradlew :app:assembleRelease                 # APK
./gradlew :app:bundleRelease                   # AAB for Play Console
```

Outputs:

- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/bundle/release/app-release.aab`

Play Console needs the **AAB**. Release tasks require `keystore.properties` (or all `KASHVAULT_*` env vars) and fail instead of falling back to a debug-signed APK.

Full keystore, version bump, listing, policy, and rollout steps: **[docs/play-store.md](docs/play-store.md)**.

The app `compileSdk` / `targetSdk` is **36** (Android 16), matching the Google Play target API requirement for updates after 31 August 2026. Edge-to-edge drawing is required at this target; KashVault pads scaffolds and the login screen with safe drawing insets.

## iOS status

iOS is not currently a supported build target. The Gradle module does not declare
iOS targets, and this repository does not include an Xcode project or workspace.

## Google Sign-In

KashVault has **no backend server**. The phone talks only to Google (Sign-In, token refresh, Drive) and, if you add a key, Gemini. Do **not** put a client secret in the app.

1. Create an OAuth **Web client ID** in [Google Cloud Console](https://console.cloud.google.com/apis/credentials). Android Credential Manager uses it as the ID-token audience.
2. Create an **Android client ID** with package `com.kashvault` and every signing SHA-1 you use: debug (local), the upload keystore, and the Play App Signing cert after the first AAB. Play Services uses this on the device for Sign-In and Drive (`drive.appdata`). Enable the Drive API on the same Cloud project. See [docs/play-store.md](docs/play-store.md).
3. Set the Web client ID in Gradle (do not put it on the login screen):

```properties
# Copy gradle.properties.example to gradle.properties, then set:
GOOGLE_WEB_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
```

`gradle.properties` is local-only and ignored by Git. Do not commit it.

**Continue without Google** keeps a fully offline vault. Gemini uses an API key stored in Profile on the device.

When you use Gemini, your request goes directly from the app to Google's Gemini
API. Magic Log sends the text you enter and the available category/account
labels; the advisor sends your question and a summary of vault totals; the
liquidity forecast sends cash-flow totals. Do not include information you do
not want Google to process. See [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

Access tokens last about an hour. On Android, Play Services
`AuthorizationClient` silently mints a new access token after the first Drive
grant. A stored refresh token is tried first; if Google rejects it, Play
Services still runs on the phone. Sign-out revokes the stored refresh/access
tokens with Google.

## Backup and restore

KashVault backs up the SQLite database file, not JSON. These backups contain the
database contents in ordinary SQLite form and are **not encrypted by KashVault**.
A file copy keeps schema, indexes, WAL-checkpointed pages, and every table
identical. JSON dumps cannot do that reliably.

1. **Consistent snapshot.** Backup runs `PRAGMA wal_checkpoint(TRUNCATE)` then copies `kashvault.db`.
2. **Validation.** Restore accepts only files with the SQLite header (`SQLite format 3`). JSON is rejected. After replace, `PRAGMA integrity_check` must return `ok`; otherwise the previous file is rolled back.
3. **Session stays on this device.** Google tokens in a backup are stale, so restore keeps the current Google session (including refresh token), Web client ID, and auth choice.
4. **Google Drive.** Signed-in backups go to the private `appDataFolder` as `kashvault-YYYYMMDDHHmmss.db`. They are not encrypted by KashVault. The app uses the `drive.appdata` scope. Enable the Drive API on the OAuth client.
5. **Local file.** Export writes a `.db` via the Android system file picker. Import picks a `.db` and replaces the on-device vault.

Android Auto Backup stays off (`android:allowBackup="false"`). Android 12+ device-to-device transfer is also excluded via `dataExtractionRules`, so `kashvault.db` is not copied off-device except through the in-app SQLite backup actions.

## SMS / UPI inbox

KashVault can turn bank SMS and UPI notifications into pending Magic Log items, tagged to a bank or credit card.

1. **Tags you manage.** Category Studio keywords (for example `swiggy`, `uber`) identify the spend category. Each bank or card has SMS/UPI tags plus optional last-4 digits so a message like `XX4321` maps to that account.
2. **Inbox, then confirm.** Captured items stay pending until you Accept (creates an expense/income on that source) or Dismiss. Filter the list by account. A message that was already parsed, accepted, or dismissed is never parsed again — SMS scan, UPI banners, and paste share the same seen list.
3. **Android capture.** Turn on **Notification access** for live UPI banners (GPay, PhonePe, banks). The listener is protected by `BIND_NOTIFICATION_LISTENER_SERVICE`. **Scan SMS** reads the last 14 days of inbox messages only when you request a scan and grant `READ_SMS`. KashVault does not register an SMS receiver or capture SMS in the background. Google Play treats `READ_SMS` as restricted; Play distribution must meet Google's current permission policy.
4. **On this phone only.** Inbox parsing and matching stay on-device. Nothing is uploaded for inbox matching.

Open the inbox from Home, Add transaction, or Profile.

## Development

```bash
./gradlew :shared:testDebugUnitTest :app:assembleDebug
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution and validation guidance.

## License

KashVault is licensed under the [Apache License 2.0](LICENSE).
