# omnibus-android
Android native mobile client for the Omnibus library application

## Releases

Releases are cut by [release-please](https://github.com/googleapis/release-please) from the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) on `main`. Every push to `main` runs `.github/workflows/release-please.yml`, which keeps one open release PR, `chore(main): release X.Y.Z`, up to date. That PR bumps the version, updates `CHANGELOG.md`, and carries the `autorelease: pending` label.

### Cutting a release

1. Review the open release PR. Its `CHANGELOG.md` diff is the release notes.
2. Merge it like any other PR (squash) once the `Testing` checks pass.
3. The workflow tags `vX.Y.Z`, creates the GitHub Release, and its `publish-apk` job attaches a signed `omnibus-X.Y.Z.apk`. Download the APK from the release's **Assets** on the [Releases page](https://github.com/omnibus-library/omnibus-android/releases).

You don't have to do this by hand: `.github/workflows/nightly-release.yml` merges the open release PR every night at 2:00 AM EST, 07:00 UTC (auto-merge, so it waits for the `Testing` checks), and the merge triggers the steps in 3. To release right away, run **Nightly release** from the Actions tab. To hold releases back, disable the workflow in the Actions tab.

If `publish-apk` fails (for example a missing secret), fix the cause and re-run the failed job from the Actions run; the upload replaces any partial asset.

### How commits bump the version

| Commit | Before 1.0.0 | From 1.0.0 |
|---|---|---|
| `fix:` | patch (0.1.0 -> 0.1.1) | patch (1.2.3 -> 1.2.4) |
| `feat:` | patch (0.1.0 -> 0.1.1) | minor (1.2.3 -> 1.3.0) |
| `feat!:`, `fix!:` or a `BREAKING CHANGE:` footer | minor (0.1.0 -> 0.2.0) | major (1.2.3 -> 2.0.0) |
| `ci:`, `chore:`, `test:`, `docs:`, `refactor:`, `build:`, `style:` | no release on their own; hidden from the changelog | same |

`feat`, `fix`, `perf` and `revert` commits appear in the changelog. To force a specific version, add a `Release-As: X.Y.Z` footer to a commit on `main`. Going to 1.0.0 is done this way.

The version lives on one line of `omnibus/app/build.gradle.kts` (the `appVersionName` line marked `x-release-please-version`), and release-please rewrites it in each release PR. Never edit it, `versionName` or `versionCode` by hand. `versionCode` is derived as `major * 10000 + minor * 100 + patch` (0.1.0 is 100, 1.2.3 is 10203), so minor and patch must stay below 100; the build fails otherwise.

### Signing

Release APKs are signed with the Omnibus release keystore. The workflow reads it from these repository secrets (Settings > Secrets and variables > Actions):

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | The keystore file, base64-encoded on one line |
| `RELEASE_KEYSTORE_PASSWORD` | The keystore password |
| `RELEASE_KEY_ALIAS` | The key alias (`omnibus`) |
| `RELEASE_KEY_PASSWORD` | The key password |

The release-please job also uses the `ANDROID_RELEASE_PLEASE` secret, so that its release PRs trigger the `Testing` checks (PRs opened with `GITHUB_TOKEN` don't run workflows). The token needs read and write access to contents, pull requests and issues on this repository. Its owner is shown as the author of release PRs.

Add the signing secrets before merging a release PR; without them the release and tag are still created but `publish-apk` fails.

The keystore was generated once with:

```bash
keytool -genkeypair -v -keystore omnibus-release.jks -keyalg RSA -keysize 4096 \
  -validity 10000 -alias omnibus
base64 -w0 omnibus-release.jks   # paste the output into RELEASE_KEYSTORE_BASE64
```

Never commit the keystore (`*.jks` and `*.keystore` are git-ignored).

- **Held by:** _TODO(repo admin): name the person who holds the keystore and its passwords._
- **Backed up at:** _TODO(repo admin): where the keystore and passwords are backed up (for example a password manager entry)._

To build a signed release locally, run from `omnibus/` with the four values in the environment. Without them, `assembleRelease` produces an unsigned `app-release-unsigned.apk`, and debug builds and tests never need them.

```bash
OMNIBUS_RELEASE_KEYSTORE=/absolute/path/to/omnibus-release.jks \
OMNIBUS_RELEASE_KEYSTORE_PASSWORD=... \
OMNIBUS_RELEASE_KEY_ALIAS=omnibus \
OMNIBUS_RELEASE_KEY_PASSWORD=... \
./gradlew assembleRelease
```

### Rotating or losing the key

Android only installs an update over an existing app when both are signed with the same key. A new keystore therefore breaks in-place upgrades: everyone has to uninstall the app (losing its local data) and install the newly signed APK. Rotate only if the key is compromised. To rotate, generate a new keystore with the command above, update the four secrets, update the custodian and backup notes above, and call out the reinstall in the next release's notes. Losing the keystore has the same effect, so keep the backup current.
