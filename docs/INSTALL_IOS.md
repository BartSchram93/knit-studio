# Building and installing on iPhone

> **Status:** the iOS app builds on macOS in CI (simulator, device and an unsigned IPA) and the
> shared engine tests pass on the iOS simulator. It has **not yet been launched on an iPhone**.

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

> **Pick one route and stay with it.** An app installed from Xcode and the same app installed by
> AltStore are registered differently (AltStore may add your team ID to the identifier), so iOS
> treats them as two separate apps with separate data. If you use AltStore, use only AltStore.
> If Xcode reports that the identifier `studio.knit.app` is not available for your team, use
> AltStore (Option B) instead of changing the identifier.

Requirements: an Apple-silicon Mac (M1 or later; the simulator build excludes Intel) with Xcode 16 or newer, JDK 17+ (`brew install openjdk@21`), your iPhone and a cable.

1. Clone the repository on the Mac.
2. Open `iosApp/iosApp.xcodeproj` in Xcode.
3. Leave `BUNDLE_ID` in `iosApp/Configuration/Config.xcconfig` as `studio.knit.app`. It is the
   app's permanent identity, and the builds refuse to run if it changes. Optionally put your team
   ID in `TEAM_ID`.
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

## Option B — no Mac: AltStore with the IPA from GitHub (recommended for Windows)

CI builds an **unsigned IPA** on every push and publishes it at a fixed link. AltStore signs it
with your free Apple ID and keeps it refreshed. Complete guide:
**[IOS_ALTSTORE_SETUP.md](../IOS_ALTSTORE_SETUP.md)**.

Sideloadly also accepts the same IPA, but it does not refresh apps automatically, and signing
with several tools under one free Apple ID can revoke AltStore's certificate. Pick one tool and
stay with it.

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
