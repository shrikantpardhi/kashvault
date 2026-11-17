# Security policy

## Reporting a vulnerability

Please do not report security vulnerabilities in public issues or pull requests.
Use GitHub's private vulnerability reporting for this repository. If private
reporting is unavailable, contact the maintainer using the address in the
repository's privacy policy and include only the information needed to
understand and reproduce the issue.

Do not include real financial records, API keys, passwords, OAuth tokens,
signing keys, or other people's data in a report.

## Scope and limitations

KashVault is a personal finance application and is not a bank or a security
auditor. Financial records and settings are stored in an ordinary SQLite
database in the app's private storage; the database and exported/Drive backups
are not encrypted by KashVault. A vault PIN gates app access but does not
encrypt the database. Do not rely on it as protection against a person with
access to the device's app data or an unencrypted backup.

SHA-256 values displayed by the app are fingerprints of current record data.
They are not authenticated, tamper-evident proofs and must not be used to
verify that records are genuine or unchanged.

Please include the affected version, platform, impact, and steps to reproduce
without exposing real user data.
