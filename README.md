# TradeLock

Locks MT5 (and any app you add) so that once you hit your target, you walk away.

## The rules
- **Block now** (widget or in-app button): locks until the next **10:00 AM UAE**.
- **Midnight auto-lock**: from 00:00 to 10:00 UAE, blocked apps are always locked.
- **11:45 PM warning**: notification 15 minutes before the midnight lock.
- All times are **UAE time (Asia/Dubai)**, wherever you are. Abroad, the app also shows
  the unlock time in your local time.
- Changing the phone's clock or timezone does not unlock early.

## The partner password
Set on first launch by your wife. It's needed only to:
- unlock early
- remove an app from the block list
- change the password

Adding apps and locking never need it. After 5 wrong tries, entry is refused for 15 minutes.
If the password is lost, uninstall and reinstall (turn off uninstall protection first, while unlocked).

## Get the APK (pick one)

**A. GitHub (no software to install)**
1. Create a free account at github.com and a new **private** repository.
2. Upload all files from this folder (keep the folder structure, including `.github`).
3. Open the **Actions** tab. The "Build APK" run starts automatically (about 5 minutes).
4. Open the finished run, download **TradeLock-apk**, unzip it and copy `app-debug.apk` to your phone.

**B. Android Studio**
Open this folder in Android Studio, wait for the sync, plug in your phone (USB debugging on) and press Run.

## Phone setup (one time, about 2 minutes)
1. Open the APK on your phone and allow "Install unknown apps" when asked.
2. Open TradeLock and hand the phone to your wife to set the password.
3. Under **Protection**, tap **Turn on** for:
   - **App blocker** (required). In Accessibility › Installed apps, switch on TradeLock.
     If the switch is greyed out (Android 13+): Settings › Apps › TradeLock › ⋮ ›
     **Allow restricted settings**, then try again.
   - **11:45 PM warning**: allow notifications.
   - **Uninstall protection** (recommended).
4. Add the widget: long-press the home screen › Widgets › TradeLock.

Some phones (Xiaomi, Oppo, Huawei and others) shut down background services. If the lock
stops working, set TradeLock's battery usage to **Unrestricted** in Settings › Apps › TradeLock › Battery.

## Tamper protection
While locked, TradeLock also closes any Settings or uninstall screen that shows "TradeLock",
so the blocker can't be switched off mid-lock. To change anything during a lock, your wife
unlocks with the password first.

## Honest limits
- The MT5 web terminal in a browser is not blocked (yet).
- On Android 12 and older, rebooting the phone *and* setting the clock forward could
  shorten a lock. Android 13+ uses network time and isn't affected.
- Booting into Safe Mode disables all downloaded apps, this one included.

## Code map
`app/src/main/java/com/tradelock/app/`
- `LockPolicy.kt`: every lock rule (UAE 10 AM, midnight, warning)
- `BlockerService.kt`: closes blocked apps, midnight check, warning, tamper protection
- `TrustedClock.kt`: clock that ignores manual time changes
- `Password.kt`: partner password (salted PBKDF2 hash)
- `MainActivity.kt`: main screen · `AppPickerActivity.kt`: add apps
- `BlockWidget.kt`: home-screen Block button · `BlockedActivity.kt`: "Trading is closed" screen
- `AdminReceiver.kt`: uninstall protection

Need a different unlock hour or curfew? Change `UNLOCK_HOUR` and the warning time in `LockPolicy.kt`.
