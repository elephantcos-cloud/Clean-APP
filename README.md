# CleanSpace — App Cache Cleaner & Force Stop

A simple, single-purpose Android app cache cleaner with Shizuku-powered force stop, built with Jetpack Compose, MVVM, and Shizuku.

## Features

| Tool | Description |
|------|-------------|
| **Storage Overview** | Accurate Total/Used/Free, plus total reclaimable app cache |
| **App Cache Cleaner** | Per-app or bulk (multi-select) cache clearing via Shizuku |
| **Force Stop** | Per-app or bulk force-stop via Shizuku, with confirmation |
| **App Filter** | All / User Apps / System Apps |
| **Cache Clean tab** | Shows only apps that currently have cache to clear |
| **Force Stop tab** | Shows only apps that are running and safe to stop (hides already-stopped apps and persistent system processes Android itself won't let you force-stop) |
| **Sort** | By size, last-used date, or name — tap again to flip ascending/descending |
| **Search** | Filter the app list by name |
| **App icons** | Real app icons shown in the list |
| **Default protected apps** | System UI, current launcher/dialer/SMS app, and CleanSpace itself are auto-protected on first run |
| **Show protected apps** | Toggle to reveal why an app is hidden from Force Stop (ignored / already stopped / protected by Android) |
| **Disable / Enable app** | Long-press an app name for this and the ignore-list toggle |
| **Clear Data** | Full data wipe (long-press menu), separate from the quick cache-only clear, with a strong confirmation |
| **Swipe to act** | Swipe a row to clear its cache / force stop it via the same confirmation dialog |
| **History** | Settings → History shows a log of every cache clear / force stop / data clear / disable / enable, with timestamps |
| **Relaunch after stop** | The snackbar after force-stopping an app offers a one-tap "Relaunch" |
| **Ignore list export/import** | Settings → copy your ignore list as text, or paste one back in (e.g. after reinstalling) |
| **Material You** | Optional wallpaper-based dynamic color on Android 12+ (Settings toggle) |
| **Auto-Clean** | Rule-based background cache cleaning (Settings) — not a real AI/LLM model, works fully offline. Scores every app by cache size + idle time, cleans only the top candidates per run. A 12-hour per-app cooldown guarantees no app is ever re-cleaned more than once in that window; an app whose cache regrows fast 2 runs in a row is temporarily skipped entirely (the skip lifts on its own after ~2 days). Escalates through Low (<1GB/<10%) and Critical (<300MB) storage tiers, cleaning just enough to recover. Never touches ignored apps or anything used in the last hour. The schedule itself is only ever recreated when you change a setting — just opening the app never resets or re-triggers it |
| **Remembers your view** | Last filter/tab/sort/show-protected choice persists across restarts |
| **Ignore List** | Long-press an app name to protect it from ever being force-stopped |
| **Tap app name** | Opens that app's native App Info screen |
| **Live progress** | Bulk actions show which app is being processed in real time |

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
| Usage Access | Show per-app cache/storage size |
| Shizuku | Required to actually clear cache / force-stop apps |

## Shizuku Setup (Wireless Debugging — no PC needed)

1. Install **Shizuku** from Play Store
2. Enable Developer Options (tap Build Number 7 times)
3. Enable **Wireless Debugging** in Developer Options
4. Open Shizuku → "Pair using wireless debugging" → tap Start
5. In CleanSpace → tap "Grant Permission"
6. After phone restart: open Shizuku and tap Start again

## Technical

- Kotlin 1.9.10 · Compose BOM 2023.10.01 · Material Design 3
- `compileSdk`/`targetSdk` 34 · `minSdk` 24 · Java 17 · AGP 8.1.4
- Storage overview: StatFs (accurate, no category double-counting); per-app stats via StorageStatsManager
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
