# Blocker2

Blocker2 is an offline-first Android application for user-configured word, Telegram, app-usage, and DNS-domain filtering.

## Project status

The repository is built with Kotlin, Jetpack Compose, Room, Android Accessibility Services, and a local DNS-only VPN service. The CI workflow runs unit tests and lint, and builds both Debug and optimized Release APK artifacts.

**Important:** A Release APK built without a signing keystore is an unsigned APK. It is useful for verifying the optimized Release build, but Android will not install it until it is signed. Configure the signing secrets below to produce an installable, consistently signed Release APK.

## Android requirements

- Minimum Android version: Android 10 (API 29)
- Compile/target SDK: 36
- JDK: 17
- Gradle: 8.13
- Application ID: `com.blocker2.app`

## CI artifacts

After a successful run, open the workflow run's **Artifacts** section:

- `Blocker2-debug-apk`: debug build for development checks.
- `Blocker2-release-apk`: optimized Release APK. It is signed only when the signing secrets are configured.

## Configure Release signing

Create a release keystore that you control and keep a secure backup. Never commit the keystore or passwords to this repository.

Add these GitHub Actions repository secrets:

- `BLOCKER2_KEYSTORE_BASE64`: base64-encoded keystore file.
- `BLOCKER2_KEYSTORE_PASSWORD`: keystore password.
- `BLOCKER2_KEY_ALIAS`: key alias.
- `BLOCKER2_KEY_PASSWORD`: key password.

On Linux, encode the keystore as one line with:

```sh
base64 -w 0 blocker2-release.jks
```

Use the same keystore for every future update of the app. Losing it prevents signing updates that Android will accept over an existing installation.

## Important platform limitations

- Accessibility filtering depends on the user enabling the service in Android Settings. Android/OEM battery management may stop background work.
- A normal app cannot prevent the user from force-stopping or uninstalling it, or from revoking its permissions.
- The local VPN inspects DNS hostnames only. It cannot inspect HTTPS page paths, query strings, or encrypted page content. Direct-IP connections and apps that bypass Android DNS can evade hostname filtering.
- The site filter is designed to fail open if its DNS tunnel cannot safely process traffic. Word filtering is handled separately.
- Telegram matching is based on text exposed by Telegram's accessibility tree; inaccessible or virtualized content may not be visible to Android Accessibility Services.

## Data and privacy

Rules and activity data are stored locally using Room. The app does not require an account. Network access is used only by the DNS filtering service to contact its configured upstream DNS resolvers.
