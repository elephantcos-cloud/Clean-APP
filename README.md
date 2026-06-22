# CleanSpace — Android Storage Cleaner

A fast, privacy-respecting Android storage cleaner built with Jetpack Compose, MVVM, and Shizuku.

## Features

| Tool | Description |
|------|-------------|
| **Junk Cleaner** | Temp files, empty folders |
| **Large Files** | Files over 50 MB — images, videos, documents, APKs |
| **Duplicate Finder** | Finds exact copies using size + MD5 hash |
| **Orphaned Data** | Leftover folders from uninstalled apps (Shizuku required) |
| **Media Cleaner** | WhatsApp / Telegram / Messenger / Facebook media by category |
| **App Manager** | Per-app storage breakdown + Shizuku bulk cache clear |

## Build (GitHub Actions — no local setup needed)

> The folder name below (`CleanSpace2`) must match whatever the extracted ZIP
> actually contains — `unzip -l yourfile.zip | head` will show you the real
> top-level folder name if a future export ever uses a different one.

```bash
# First time
mkdir -p ~/projects
cd ~/storage/downloads
unzip -o CleanSpace.zip
cp -r CleanSpace2 ~/projects/CleanSpace
cd ~/projects/CleanSpace
git init
git config --global --add safe.directory $(pwd)
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin "https://$GITHUB_USERNAME:$GITHUB_TOKEN@github.com/$GITHUB_USERNAME/cleanspace-app.git"
git push -u origin main
```

```bash
# Subsequent updates (new ZIP received)
cd ~/projects/CleanSpace
unzip -o ~/storage/downloads/CleanSpace.zip -d /tmp/extract
cp -r /tmp/extract/CleanSpace2/* .
git add .
git commit -m "Update"
git push
```

GitHub Actions will automatically build a debug APK. Download it from the **Actions** tab → **Artifacts** (`CleanSpace-debug-apk`).

## Permissions

| Permission | Purpose |
|-----------|---------|
| All Files Access | Scan storage for junk and large files |
| Usage Access | Show per-app storage breakdown |
| Shizuku (optional) | One-tap bulk cache clear + Orphaned Data scan |

## Shizuku Setup (Wireless Debugging — no PC needed)

1. Install **Shizuku** from Play Store
2. Enable Developer Options (tap Build Number 7 times)
3. Enable **Wireless Debugging** in Developer Options
4. Open Shizuku → "Pair using wireless debugging" → tap Start
5. In CleanSpace → **App Manager** → tap "Grant Permission"
6. After phone restart: open Shizuku and tap Start again

## Technical

- Kotlin 1.9.10 · Compose BOM 2023.10.01 · Material Design 3
- `compileSdk`/`targetSdk` 34 · `minSdk` 24 · Java 17 · AGP 8.1.4
- Storage scanning: MediaStore API (fast, no walkTopDown for category breakdown)
- Icon: Emerald green background + upward arrow

## Local Gradle wrapper note

`gradle/wrapper/gradle-wrapper.jar` is **not included** in this ZIP (binary
jars don't survive every export/zip pipeline cleanly). The CI workflow
(`.github/workflows/build.yml`) doesn't need it — it uses the Gradle version
installed by `gradle/actions/setup-gradle`, not `./gradlew`. You only need the
jar if you want to run `./gradlew` locally (e.g. inside Termux). To regenerate
it in one step once any working Gradle install is available:

```bash
gradle wrapper --gradle-version 8.4
```

This recreates `gradle/wrapper/gradle-wrapper.jar` to match the version
already pinned in `gradle/wrapper/gradle-wrapper.properties`.
