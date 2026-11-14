# Knit Studio on iPhone with AltStore

This guide installs Knit Studio on your own iPhone with **AltStore Classic**, a **free Apple ID**
and a **Windows 11** PC. No paid Apple Developer membership, no App Store.

> **Status.** The iOS app is built and checked automatically by GitHub Actions (see
> [What has been verified](#what-has-been-verified)). Installing it through AltStore on a real
> iPhone has **not yet been tested**. If a step below does not match what you see, the official
> AltStore documentation linked in each step is the authority.

## How it works, in one minute

- GitHub builds `KnitStudio.ipa` on every change. It is **unsigned**: an iPhone will not run it
  as it is.
- **AltServer** runs on your Windows PC. **AltStore** runs on your iPhone. Together they sign the
  app with your Apple ID and install it.
- Apps signed with a free Apple ID stop opening after **7 days**. AltStore renews ("refreshes")
  them automatically whenever it can reach AltServer — your PC is on, AltServer is running, and
  the phone is on the same Wi-Fi. Refreshing never touches your patterns.
- A free Apple ID allows **3 active sideloaded apps** at a time — and AltStore itself is one of
  them — plus **10 App IDs per 7 days**. Knit Studio needs one app slot and one App ID
  ([App IDs](https://faq.altstore.io/altstore-classic/app-ids)).

## Before you start

| You need | Notes |
|---|---|
| A Windows 11 PC | It has to be switched on now and then for refreshing (see step 8). |
| An iPhone with iOS 16 or later | Knit Studio requires iOS 16. The current AltStore Classic (2.3.x) requires **iOS 17.4 or later**; for older iOS versions see the [AltStore release notes](https://faq.altstore.io/release-notes/altstore) for which version to use. *Settings › General › About* shows your iOS version. |
| An Apple ID | Your normal one works. Some people use a separate Apple ID just for sideloading. Use **the same Apple ID every time** — see [Rules that protect your data](#rules-that-protect-your-data). |
| A USB cable | For the first setup. Later, Wi-Fi is enough. |
| Access to the GitHub repository | The repository is private, so downloads need you to be signed in to GitHub. |

---

## 1. Install AltServer on Windows

Official guide: **[How to Install AltStore (Windows)](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows)**

1. **Install iTunes and iCloud directly from Apple — not from the Microsoft Store.** The AltStore
   Windows guide links the right installers. The Microsoft Store versions are a common cause of
   failures
   ([Troubleshooting](https://faq.altstore.io/altstore-classic/troubleshooting-guide)).
   If you already have the Store versions, uninstall them first.
2. Open iCloud and sign in with your Apple ID.
3. Download AltServer for Windows from **[altstore.io](https://altstore.io)**, unzip it and run
   the installer.
4. Start AltServer **as administrator**: search for *AltServer* in the Start menu, right-click it
   and choose *Run as administrator*. A diamond icon appears in the system tray (you may need
   the `^` arrow to see it).
5. When Windows Firewall asks, **allow AltServer on private networks**.

## 2. Install AltStore on the iPhone

1. Connect the iPhone with the USB cable, unlock it, and tap **Trust** when it asks about the
   computer.
2. Open **iTunes**, select the iPhone (the small phone icon), and on the *Summary* page tick
   **Sync with this iPhone over Wi-Fi**. Click *Apply*. This is what lets AltStore refresh apps
   later without a cable.
3. Click the AltServer icon in the system tray › **Install AltStore** › choose your iPhone.
4. Enter your Apple ID and password. AltServer sends them only to Apple
   ([Windows guide](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows)).
   If you have two-factor authentication, enter the code that appears on your devices.
5. After a moment, AltStore appears on the iPhone's Home Screen.

## 3. Enable Developer Mode and trust your Apple ID

On iOS 16 and later, apps signed this way only open with **Developer Mode** on.

1. **Trust the certificate:** *Settings › General › VPN & Device Management* (on some iOS
   versions: *Profiles & Device Management*) › tap your Apple ID › **Trust** › confirm.
2. **Developer Mode:** *Settings › Privacy & Security › Developer Mode* › switch on › the iPhone
   restarts › after restarting, confirm **Turn On** and enter your passcode.
   Apple: [Enabling Developer Mode on a device](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device).
3. Open AltStore once to check that it starts. If AltStore asks you to sign in, use the same
   Apple ID.

If *Developer Mode* is not in the list, plug the phone into the PC with AltServer running and
look again. The option usually appears only after a sideloaded app has been installed.

## 4. Download the latest Knit Studio IPA

Every push to `main` publishes the newest build under a link that never changes:

**https://github.com/BartSchram93/knit-studio/releases/download/latest/KnitStudio.ipa**

The release page — with the version, build number and date — is
**https://github.com/BartSchram93/knit-studio/releases/tag/latest**.

Numbered releases (`v1.0.0`, …) appear on the
[Releases page](https://github.com/BartSchram93/knit-studio/releases) when they are published.

Get the file onto the iPhone in either of these ways:

- **Directly on the iPhone (simplest):** open the link in Safari, sign in to GitHub when asked,
  and download. The file lands in *Files › Downloads*.
- **Via the PC:** download it in your browser, then put it in the *iCloud Drive* folder in
  Windows Explorer (you installed iCloud in step 1). On the iPhone it shows up in
  *Files › iCloud Drive*.

Each CI run also keeps the build as an artifact named `KnitStudio-iOS-<version>-build<number>`
(*Actions* tab › the run › *Artifacts*). Artifacts download as a `.zip` — unzip it first. The
release link above is easier.

> Do not tap the `.ipa` to "open" it on the iPhone. iOS cannot install it by itself — it has to
> go through AltStore.

## 5. Install Knit Studio through AltStore

1. Make sure AltServer is running on the PC and the iPhone is on the **same Wi-Fi** — or simply
   plug in the cable ([AltServer](https://faq.altstore.io/altstore-classic/altserver)).
2. On the iPhone, open **AltStore › My Apps**.
3. Tap **+** (top left) and choose `KnitStudio.ipa` from Files.
4. Wait while AltStore signs and installs it. The first time, AltStore may ask for your Apple ID.
5. Knit Studio appears on the Home Screen and under *My Apps*, with its expiry date.

## 6. Trusting the developer, if asked

If opening Knit Studio shows *"Untrusted Developer"*, repeat step 3.1: *Settings › General ›
VPN & Device Management* › your Apple ID › **Trust**. You only need to do this once per Apple ID.

## 7. Turn on automatic refreshing

In AltStore › **Settings**:

- Switch **Background Refresh** on.
- Leave the experimental **"Randomize Bundle IDs"** setting (AltStore 2.3.1 and later) **off**.
  It would give each install a different identity, so updates would no longer find your data.

On the iPhone, *Settings › General › Background App Refresh* must be on for AltStore as well.

AltStore then refreshes Knit Studio by itself, a few days before it expires, whenever it can
reach AltServer. You can also tap **Refresh All** in *My Apps* at any time.

## 8. Keep AltServer available

Refreshing only works while AltServer can be reached
([AltServer](https://faq.altstore.io/altstore-classic/altserver)):

- **Start AltServer with Windows, as administrator.** Open *Task Scheduler* › *Create Task…*:
  - *General*: name it *AltServer*, tick **Run with highest privileges**.
  - *Triggers*: **At log on** of your user.
  - *Actions*: **Start a program**, browse to `AltServer.exe` (normally under
    `C:\Program Files (x86)\AltServer\`).
  - *Conditions*: untick *Start the task only if the computer is on AC power*.
- **Same network:** the iPhone and the PC must be on the same home Wi-Fi. Work, school and public
  networks often block the devices from finding each other
  ([Troubleshooting](https://faq.altstore.io/altstore-classic/troubleshooting-guide)).
- **Keep the PC on at least every few days.** It does not have to run around the clock; it has to
  be on (and not asleep) for a while at least once within each 7-day window while the phone is at
  home.
- If you are away for more than a week, refresh just before you leave: open AltStore ›
  *My Apps* › **Refresh All**.
- AltStore 2.3 also offers **Remote AltServer** for refreshing away from home
  ([Remote AltServers](https://faq.altstore.io/altstore-classic/remote-altservers)). It is optional
  and not needed for this setup.

If Knit Studio has expired, it simply will not open. **Your data is still there.** Get the phone
and PC together, tap *Refresh All*, and it opens again with everything intact.

---

## 9. Updating Knit Studio without losing anything

An update is the same as step 5 with a newer IPA. Because the new build has the **same bundle
identifier** (`studio.knit.app`, enforced by the build) and is signed with the **same Apple ID**,
iOS replaces the app and keeps its data folder, which holds your patterns, photos, custom
stitches, stash and settings.

1. **Back up first** (one minute, see below). Updates should never lose data, but a backup costs
   nothing.
2. Download the new `KnitStudio.ipa` (step 4).
3. AltStore › *My Apps* › **+** › choose the new IPA. **Do not delete the old app first.**
4. Open Knit Studio. *Settings › About* shows the version. Check that your patterns are there.

If a **second** Knit Studio icon appears instead of the old one being replaced, something changed
the app's identity — usually a different Apple ID or the "Randomize Bundle IDs" setting. Do not
delete anything. Make a backup in the **old** app, restore it in the new one
(*Settings › Restore from a backup › Merge*), and only then remove the old icon.

### Backing up your data

In Knit Studio: *Settings › Back up everything* › in the share sheet choose
**Save to Files** › *iCloud Drive* (or *On My iPhone*). This creates one
`Knit Studio backup.knitbackup` file with every pattern, photo, custom stitch, the stash and your
settings.

To restore: *Settings › Restore from a backup* › choose the file ›

- **Merge** — adds the backup's patterns to what is already there, or
- **Replace everything** — makes the app exactly match the backup.

If the backup cannot be read, nothing is changed. A backup file also works on Android, and vice
versa. For single patterns, *Export & share › Pattern file* creates a `.knit` file.

### Rules that protect your data

| Do | Don't |
|---|---|
| Install updates over the existing app | Delete Knit Studio to "reinstall cleanly" — that deletes its data |
| Always use the same Apple ID in AltStore | Switch Apple IDs — the app gets a new identity and starts empty |
| Keep "Randomize Bundle IDs" off | Turn on *Settings › App Store › Offload Unused Apps* (an offloaded sideloaded app may not come back) — or at least exclude Knit Studio |
| Make a backup before updating and now and then | Sign other apps with the same Apple ID in Xcode, Sideloadly or a second AltStore install — it can revoke AltStore's certificate and stop refreshes (your data stays, but apps stop opening until AltStore is set up again) |

---

## 10. Troubleshooting

Official references:
[Troubleshooting guide](https://faq.altstore.io/altstore-classic/troubleshooting-guide) ·
[Error codes](https://faq.altstore.io/altstore-classic/error-codes) ·
[AltServer](https://faq.altstore.io/altstore-classic/altserver).

| Problem | What to do |
|---|---|
| **"Could not find AltServer"** / installing or refreshing hangs | AltServer is running (tray icon) · phone and PC on the same Wi-Fi · *Sync over Wi-Fi* ticked in iTunes · AltServer allowed through Windows Firewall · iCloud for Windows is running and Bonjour is installed (it comes with iTunes) · plug in the cable to be sure. If the PC does not see the phone at all: unlock the phone, then restart the **Apple Mobile Device Service** (*Win+R* › `services.msc`) |
| **Errors mentioning a socket or connection right after install** | Turn on **Developer Mode** (step 3) |
| **Errors about iCloud, "anisette" or sign-in on Windows** | iTunes and iCloud must be the versions downloaded from Apple, not the Microsoft Store. Uninstall the Store versions, install Apple's, sign in to iCloud, restart AltServer as administrator |
| **(2009) Cannot activate more than 3 apps** | A free Apple ID allows 3 active apps, AltStore included. Deactivate or remove another sideloaded app in *My Apps*. |
| **(1009 / 3013) App ID limit — "only register 10 App IDs every 7 days"** | Wait until older App IDs expire. *My Apps › View App IDs* shows when ([App IDs](https://faq.altstore.io/altstore-classic/app-ids)). Updating Knit Studio reuses its App ID; installing many different apps uses them up. |
| **"Untrusted Developer"** | Trust your Apple ID under *Settings › General › VPN & Device Management* (step 6) |
| **Knit Studio does not open (icon greyed out or closes immediately)** | It has expired. AltStore › *My Apps* › **Refresh All** with AltServer reachable. Do **not** delete it — your data is still inside. |
| **Opening the `.ipa` in Files does nothing / "cannot be installed"** | The IPA is unsigned on purpose; install it only through AltStore's **+** in *My Apps* |
| **AltStore itself expired** | Plug the phone into the PC, AltServer tray icon › **Install AltStore** again. Installed apps and their data stay. |
| **A second Knit Studio appeared after an update** | See [Updating](#9-updating-knit-studio-without-losing-anything) — back up in the old one, restore in the new one |
| **"Your session has expired" / Apple ID errors** | Sign out of AltStore (*Settings*) and sign in again with the same Apple ID. If Apple asks, update the password in AltStore as well |

Still stuck? Note the error code and message and look it up on the
[Error codes](https://faq.altstore.io/altstore-classic/error-codes) page.

---

## What has been verified

| Check | How | Status |
|---|---|---|
| iOS code compiles and links into an app for iPhone (arm64) | GitHub Actions, macOS, Xcode | automated |
| Shared tests pass on the iOS simulator, including the test that the database lives in the app's preserved data folder and survives reopening | GitHub Actions, iOS simulator | automated |
| IPA structure: `Payload/KnitStudio.app` at the root, bundle ID `studio.knit.app`, arm64 executable, version and build number, ASCII app name, no app extensions, no provisioning profile, no paid-only entitlements | `scripts/build-ios-ipa.sh` on every build; the build fails if any check fails | automated |
| Bundle identifier cannot change by accident | The build refuses to run if `Config.xcconfig` differs from `studio.knit.app` | automated |
| Android APKs still build and tests pass | GitHub Actions | automated |
| AltStore installs, signs and refreshes the IPA on an iPhone | — | **not tested** |
| An update over an installed version keeps the data | Reasoned from how iOS and AltStore handle identical bundle IDs, plus the storage test above | **not tested on a device** |
| iOS share sheet, Files picker and photo picker | — | **not tested on a device** |

Please test the [update procedure](#9-updating-knit-studio-without-losing-anything) once early on,
with a backup made and only a test pattern in the app, before you rely on it with real work.

## Why not fully automatic updates?

AltStore can follow an app *source* and offer updates on the phone. That needs the IPA at a
public download address. This repository is private, and its downloads require a GitHub login
that AltStore cannot provide. So the one manual step that remains for a new **version** is
downloading the IPA and choosing it in AltStore. The weekly **refresh** is fully automatic.

## For developers

```bash
scripts/build-ios-ipa.sh <build-number> dist
```

On a Mac with Xcode this builds the same unsigned IPA as CI and runs the same checks. The app
version comes from `versionName` in `androidApp/build.gradle.kts`. The build number is the GitHub
Actions run number, so later builds always have higher numbers.

To run on a phone straight from Xcode instead, see [docs/INSTALL_IOS.md](docs/INSTALL_IOS.md).
