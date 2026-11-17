# Contributing

Thanks for your interest in KashVault. Contributions are welcome, especially
focused bug fixes, tests, accessibility improvements, and documentation updates.

## Before opening an issue

- Search existing issues to avoid duplicates.
- Do not include real financial records, credentials, API keys, access tokens,
  or private account details in an issue or pull request.
- For security-sensitive reports, follow [SECURITY.md](SECURITY.md) instead of
  creating a public issue.

## Pull requests

- Keep changes focused and explain the user-visible impact.
- Add or update tests for behavior changes.
- Update documentation when setup, permissions, data handling, or behavior changes.
- Do not commit credentials, signing keys, local configuration, generated builds,
  or personal financial data.
- Verify the Android tests and debug build before submitting:

  ```bash
  ./gradlew :shared:testDebugUnitTest :app:assembleDebug
  ```

- Include a concise description of validation performed and any limitations.

## Development requirements

- JDK 21
- Android SDK / Android Studio
- Gradle wrapper (use `./gradlew`, not a locally installed Gradle)

The project currently supports Android. iOS files are not a supported build
target yet.
