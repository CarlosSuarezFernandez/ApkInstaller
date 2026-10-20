# ApkInstaller

A small, open source Android app to install **APK files and split APKs** — no ads, no analytics, no tracking.

Inspired by [SAI (Split APKs Installer)](https://github.com/Aefyr/SAI), but written from scratch in Kotlin + Jetpack Compose.

## Features

- Install `.apk`, `.apks` (SAI / bundletool), `.xapk` (APKPure) and unencrypted `.apkm` files
- Install several loose APKs at once (base + splits) using multi-select
- Automatically picks the splits that match your device (CPU architecture, screen density, languages); you can change the selection manually
- Shows the version to install and the installed version, and warns about downgrades and incompatible architectures
- "Open with" / "Share" from file managers and browsers
- Material You, light and dark themes
- Rootless: uses Android's official `PackageInstaller` API. Android 8.0+ (API 26)

## Usage

1. Download the latest `ApkInstaller-x.y.z-release.apk` from [Releases](../../releases) and install it.
2. Open the app and tap **Select file** (or **Select multiple APKs**), or share/open a file with ApkInstaller from another app.
3. Review the splits and tap **Install**. The first time, Android will ask you to allow ApkInstaller to install unknown apps.

> Google Play Protect may show a warning for apps it hasn't seen before. That is normal for sideloaded apps.

## Building

Requirements: JDK 17+ and the Android SDK (compileSdk 37).

```bash
./gradlew assembleDebug          # debug build
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleRelease        # local release build -> app/build/outputs/apk/release/
```

### Signing release builds

Release signing is read from a `keystore.properties` file in the project root (git-ignored). Without it, `assembleRelease` produces an unsigned APK.

```bash
keytool -genkeypair -v -keystore ~/.android/apkinstaller-release.jks -alias apkinstaller \
  -keyalg RSA -keysize 4096 -validity 10000
```

```properties
# keystore.properties
storeFile=/absolute/path/to/apkinstaller-release.jks
storePassword=...
keyAlias=apkinstaller
keyPassword=...
```

Always sign releases with the same key; otherwise users can't update without uninstalling first.

## Versioning and releases

The version lives in `gradle.properties` as `appVersionName` (`MAJOR.MINOR.PATCH`); `versionCode` is derived from it as `MAJOR * 10000 + MINOR * 100 + PATCH`.

To publish a release, bump `appVersionName` in a pull request and merge it into `main`. The [Release workflow](.github/workflows/release.yml) then builds and signs the APK and publishes it as GitHub Release `vX.Y.Z` (with a `.sha256` checksum and auto-generated notes). Pushes that don't change the version don't create a release.

CI signing uses these repository secrets: `RELEASE_KEYSTORE_BASE64` (`base64 -i release.jks`), `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`.

## Project structure

| Package | Purpose |
|---|---|
| `parser` | Binary `AndroidManifest.xml` parser, archive extraction, split classification and selection (pure Kotlin, unit tested) |
| `data` | Copies the selected documents into the app cache and builds the package description |
| `installer` | `PackageInstaller` session installer and result receiver |
| `ui` | Compose screens, `ViewModel` and theme |

## License

[MIT](LICENSE)
