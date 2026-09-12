# Getting the CoInterpreter APK (no local Android SDK required)

This repository's build environment could not reach the Android SDK /
Google Maven / Gradle distribution servers, so **GitHub Actions is the
designated compilation environment** for CoInterpreter. Once this
repository is on GitHub, producing the APK takes about three minutes of
your time and 5–10 minutes of CI time.

## Steps

1. **Push this repository to GitHub** (create a new repo and push, or
   upload the contents of `CoInterpreter-source.zip`).
2. Open the repository on GitHub and click the **Actions** tab.
3. Select the **"Android Build"** workflow in the left sidebar.
4. Click **"Run workflow"** (or just push a commit — it also runs
   automatically on any push that touches `android-app/**`).
5. Wait for the workflow to finish (green check). It will have:
   - run the JVM unit test suite (`./gradlew testDebugUnitTest`),
   - run Android Lint (`./gradlew lintDebug`),
   - built the debug APK (`./gradlew assembleDebug`),
   - computed its SHA-256 checksum.
6. Click into the finished run, scroll to **Artifacts**, and download
   **`CoInterpreter-debug.apk`**.
7. Unzip the downloaded artifact (GitHub always wraps artifacts in a zip) —
   inside is `CoInterpreter-debug.apk`.
8. Transfer it to an Android device (Android 8.0 / API 26 or newer) and
   install it (you'll need to allow "install unknown apps" for whatever app
   you used to transfer it, since it isn't from the Play Store).

That's it — no Android Studio, no SDK download, no local Gradle install.

## Where exactly the APK appears

```
GitHub repo
 └─ Actions tab
     └─ "Android Build" workflow run
         └─ Artifacts (bottom of the run page)
             └─ CoInterpreter-debug.apk.zip   ← download this
                 └─ CoInterpreter-debug.apk   ← install this
```

## If you want a release build instead

Run the **"Android Release (manual)"** workflow the same way. Without
signing secrets configured, it produces
`CoInterpreter-release-unsigned.apk` (still a real, working build, just
unsigned — Android will refuse to install an unsigned release APK directly;
sign it yourself with `apksigner`, or configure the signing secrets
described at the top of `.github/workflows/android-release.yml` to get a
properly signed `CoInterpreter-release.apk`).

## If the workflow fails

The most likely causes, in order:
1. **A real compile error** — click into the failed step, the Kotlin
   compiler error will be in the log. This is a genuine bug to fix, not a
   CI configuration issue.
2. **Gradle/AGP/Kotlin version drift** — check `android-app/build.gradle.kts`
   (root) for the plugin versions; they were chosen as a known-compatible
   set (Gradle 8.9 / AGP 8.5.2 / Kotlin 2.0.20 / compileSdk 35) as of when
   this repository was built. If GitHub's Ubuntu runner image has since
   dropped support for one of these, bump the versions together, not
   individually.
3. **Missing `gradle-wrapper.jar`** — this repository includes a real,
   working wrapper jar (not a placeholder); if it's missing after a copy/
   paste into a new repo, run `gradle wrapper --gradle-version 8.9` once
   locally (or in the workflow) to regenerate it.
