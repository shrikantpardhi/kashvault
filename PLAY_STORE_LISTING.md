# Google Play Store Listing & Submission Notes

This is a draft listing. Confirm every statement and complete the Play Console
Data safety form against the final release build and Google's current policies.

## Store listing

### App title

```text
KashVault - Expense Tracker
```

### Short description

```text
Offline-first personal finance and expense tracker with an optional AI advisor.
```

### Full description

```text
Track personal finances with KashVault, an offline-first Android expense and
budget app.

• Record income, expenses, accounts, budgets, loans, savings goals, and lending.
• Review transactions from notification alerts or scan SMS on demand.
• Use the optional Google Gemini AI features for transaction parsing, cash-flow
  forecasts, and financial summaries. Each request asks before sending the
  described input directly to Google.
• Export or restore a SQLite database backup locally or through Google Drive.

Financial records are stored in a standard SQLite database in the app's private
storage. KashVault does not encrypt the database or backups. Optional Google
Drive backups are unencrypted copies of the database. Review the privacy policy
before enabling online services.

KashVault is a personal finance tracker, not a bank or payments provider.
```

## Contact

- **Developer:** Shripad Pardhi
- **Email:** shripardhi92@gmail.com
- **Category:** Finance

## Data safety and permissions

Do not copy preset answers into Play Console. Complete Data safety and
permission declarations for the actual release and all enabled integrations.
In particular, account identifiers/tokens, user-entered finance data included
in optional Gemini requests, and Drive backups must be assessed under Google's
current definitions for collection and sharing.

The Android build declares `READ_SMS` for user-initiated SMS scanning. This is
a restricted permission; obtain Google's approval if required for the chosen
distribution. Do not claim automatic SMS capture: KashVault does not register
an SMS receiver.

## Privacy policy

Host [PRIVACY_POLICY.md](PRIVACY_POLICY.md) at a stable public URL and provide
that URL in Play Console. Re-check the policy against the release build before
publishing.
