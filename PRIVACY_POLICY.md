# Privacy Policy for KashVault

**Effective Date:** November 17, 2026  
**Developer:** Shripad Pardhi  
**Contact Email:** shripardhi92@gmail.com  
**Application:** KashVault for Android

## 1. Overview

KashVault is an offline-first personal finance tracker. It has no advertising
or analytics SDKs and no developer-operated backend. Optional Google and
Gemini features communicate directly with Google when you choose to use them.

## 2. Information stored and processed

### Financial records and app settings

Accounts, balances, transactions, budgets, loans, goals, profile settings,
Google sign-in tokens, and a Gemini API key (if configured) are stored in the
app's local SQLite database. KashVault does **not** encrypt this database.
Android's app-private storage provides the platform's normal app isolation;
the optional biometric app lock only gates access through the app and does not
encrypt the database or protect exported database files.

### Backups

When you export a backup or use Google Drive backup, KashVault copies the
SQLite database as-is. Backups are **not encrypted by KashVault** and may
contain your financial records, app settings, Google tokens, and Gemini API
key. Google Drive backups are placed in the signed-in account's private
`appDataFolder`; Google processes and stores those files under its own terms.
The developer does not operate a service that receives these backups. Anyone
who obtains an exported backup file may be able to read its contents.

### SMS and notifications

If you grant notification access, KashVault reads notification text for
matching bank and UPI alerts and stores matched items locally in the inbox.
If you grant `READ_SMS` and request a scan, KashVault reads available inbox SMS
history to find candidate transactions. Hourly background scanning is optional,
off by default, and uses Android WorkManager, whose runs may be deferred by the
operating system. KashVault does not register an SMS receiver. Pasted message
text is also processed locally. Inbox text is not sent to KashVault servers.
Scheduled-scan notifications contain only a generic item count, not SMS text.

### Gemini AI features

Gemini use is optional and requires a user-provided API key. Requests are sent
directly over HTTPS to Google's Gemini API; Google processes those requests
under its own terms and policies.

- **Magic Log:** sends the transaction text you submit plus available category
  and payment-account labels so Gemini can parse it.
- **AI Advisor:** sends your question and a summary containing net worth,
  liquidity, investments, loan liability, emergency-fund balance, lending
  totals, and the number of tracked monthly expense transactions. It does not
  send the full transaction ledger.
- **Liquidity forecast:** sends cash, average monthly spending, estimated
  monthly income, and upcoming fixed-bill totals.

Do not submit data to Gemini unless you are comfortable sharing those request
contents with Google. The API key is stored in the local SQLite database and
is not encrypted by KashVault.

### Google Sign-In

If you choose Google Sign-In or Drive access, the app exchanges sign-in or
authorization requests directly with Google. OAuth session information and
tokens are stored in the local SQLite database, which KashVault does not
encrypt. Drive access is limited to the configured app-data scope.

## 3. Permissions and access

- **`INTERNET`**: Google Sign-In, optional Google Drive backup, and optional
  direct Gemini API requests.
- **`READ_SMS`**: optional, on-demand and explicitly enabled hourly SMS scans
  after runtime permission is granted. KashVault does not request
  `RECEIVE_SMS`.
- **Notification access**: optional, separately enabled by the user in Android
  settings to process bank and UPI notification text locally.
- **`POST_NOTIFICATIONS`** and **`RECEIVE_BOOT_COMPLETED`**: optional inbox
  reminders, scheduled-scan alerts, and restoring schedules after reboot.
- **`SCHEDULE_EXACT_ALARM`**: scheduling reminders at the selected time where
  Android requires this access.
- **Biometrics**: optional app-launch authentication using Android's biometric
  prompt. Device credentials are not offered as a fallback.
- **Photo picker**: used only if you select a profile photo.

## 4. Retention and deletion

Local data remains on the device until you delete it, reset the vault, clear
app data, or uninstall the app. Exported files remain wherever you save or
share them. Google Drive backups remain in your Google account until you
delete them using the app or Google Drive.

## 5. Children

KashVault is not directed to children under 13, and the developer does not
knowingly collect personal information from children.

## 6. Changes and contact

This policy may be updated when the app's data practices change. Questions
about this policy can be sent to **shripardhi92@gmail.com**.
