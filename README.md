# iMe Cache Cleaner (source project)

Android Studio project for a separate Shizuku-based cache cleaner targeting iMe (`com.iMe.android`).

## What it does
- Uses Shizuku shell identity to run `pm clear --cache-only com.iMe.android`.
- Never falls back to `pm clear com.iMe.android` without `--cache-only`; that destructive command can erase app data and login state.
- Manual “Очистить кеш сейчас” action.
- Optional periodic schedule: 3, 6, 12 or 24 hours. WorkManager's periodic work is inexact and may be delayed by Android battery management.
- Requires Android 13+ command support for `pm clear --cache-only` and a running/authorized Shizuku service.

## Build
1. Open this folder in Android Studio (recent version with Android Gradle Plugin 8.7.3 support).
2. Allow Gradle to download dependencies.
3. Build > Build APK(s).

## Setup on device
1. Install and start Shizuku. On non-rooted devices, start it with Wireless debugging after reboot when required.
2. Install this app and open it.
3. Tap “Подключить / проверить Shizuku” and grant permission.
4. Enable automatic cleaning and choose an interval.
5. Disable battery optimization for this cleaner if Android delays scheduled work.

## Important limitations
- This is source code, not a prebuilt APK. The current environment has no Android SDK/Gradle installation, so an APK could not be compiled or device-tested here.
- Android may delay periodic jobs; exact execution times are not guaranteed.
- If the system rejects `--cache-only`, the cleaner stops safely rather than wiping iMe's data.
