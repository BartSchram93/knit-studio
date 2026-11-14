# Building and installing on iPhone

> **Status:** the iOS app's Kotlin code compiles for device and simulator, but the Xcode project
> in `iosApp/` was written without access to a Mac and has **not been built yet**. The first build
> may need small fixes in the project settings. Everything below is the intended procedure.

Apple only allows apps onto an iPhone if they are signed. Without the App Store there are three
practical ways, all free or cheap.

## What signing means

| Term | In plain words |
|---|---|
| **Certificate** | Proves the app was built by you. Xcode creates it for your Apple ID automatically. |
| **Provisioning profile** | Says "this app, signed by this certificate, may run on these devices". Also created automatically. |
| **Bundle identifier** | The app's unique name, e.g. `com.yourname.KnitStudio`. Must be unique to you. |
| **Team** | Your Apple ID acting as a developer. A free Apple ID gives a "Personal Team". |

| | Free Apple ID (Personal Team) | Apple Developer Program (99 USD/year) |
|---|---|---|
| App keeps working for | **7 days**, then it must be re-installed from Xcode (data is kept) | 1 year |
| Apps at once | 3 | unlimited |
| Needs a Mac | to build | to build |

## Option A — Xcode on a Mac (simplest and most reliable)

Requirements: a Mac with Xcode 16 or newer, JDK 17+ (`brew install openjdk@21`), your iPhone and a cable.

1. Clone the repository on the Mac.
2. Open `iosApp/iosApp.xcodeproj` in Xcode.
3. Edit `iosApp/Configuration/Config.xcconfig`: change `BUNDLE_ID` to something of your own,
   e.g. `com.yourname.KnitStudio`.
4. In Xcode select the **iosApp** target › **Signing & Capabilities** › tick *Automatically manage
   signing* and choose your **Team** (add your Apple ID under Xcode › Settings › Accounts first).
5. Connect the iPhone, unlock it, trust the computer, and select it as the run destination.
6. On the iPhone enable **Settings › Privacy & Security › Developer Mode** (it appears after the
   first connection; the phone restarts).
7. Press **Run** (⌘R). The first build compiles the Kotlin framework and takes several minutes.
8. First launch only: on the iPhone open **Settings › General › VPN & Device Management**, tap
   your Apple ID and choose **Trust**.

With a free Apple ID, repeat step 7 once a week. Your patterns stay on the phone as long as you
do not delete the app; make a backup from Settings › Back up everything regardless.

## Option B — no Mac: GitHub Actions + a sideloading tool

The repository's CI builds an **unsigned IPA** on a GitHub-hosted Mac.

1. Push to GitHub and open **Actions › CI**. Download the artifact `knit-studio-ios-unsigned`.
   (macOS minutes on private repositories count tenfold against the free quota; a build takes
   roughly 15–25 minutes.)
2. Sign and install the IPA with your own Apple ID using one of:
   - **Sideloadly** (Windows/macOS) — drag the IPA in, enter your Apple ID, connect the phone.
   - **AltStore** / **SideStore** — installs and re-signs the app on a schedule so the 7-day
     limit takes care of itself.
3. Trust your Apple ID on the phone as in step A8.

These tools sign with the same free Personal Team, so the same 7-day and 3-app limits apply.

## Option C — Apple Developer Program

With a paid membership, archive in Xcode (**Product › Archive**), then **Distribute App ›
Development** (or Ad Hoc) to export a signed IPA valid for a year, installable with Apple
Configurator, Xcode's Devices window or TestFlight.

## Checking the build without a phone

```bash
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

## If the first Xcode build fails

- *"Framework not found ComposeApp"* — run `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`
  once from Xcode's build (it needs Xcode's environment), and check that the **Compile Kotlin
  Framework** phase is the first build phase.
- *"Undefined symbol _sqlite3_…"* — `-lsqlite3` must be in *Other Linker Flags* (it is set in the project).
- *Sandbox errors from the script phase* — *User Script Sandboxing* must be **No** (it is set in the project).
- *"Command PhaseScriptExecution failed" with a Java error* — make sure `java -version` works in
  Terminal; Xcode uses the same JDK.
