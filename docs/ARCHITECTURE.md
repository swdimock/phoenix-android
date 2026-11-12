# Architecture

Custom user-space replacement for the stock FYT software on a Phoenix Automotive Tesla-style head unit
(UIS7862(s), genuine Android 12 / API 31, MCU `AYI_53_L6315_G23P48F64_E60`) in a 2011 Ford F-150 Raptor
with Sync 2. We do **not** replace the OS. We ship APKs that replace the launcher, the Android Auto
receiver and the climate bar, talking to the vendor's IPC service (`com.syu.ms`) like the stock apps do.

Full background and phased plan: the Cursor plan file `phoenix_f-150_head_unit_replacement`.

## Modules

| Module | Type | applicationId / namespace | Purpose |
| --- | --- | --- | --- |
| `fyt-ipc` | library | `com.phoenix.fytipc` | Binder client for `com.syu.ms` toolkit, MCU frame codec, climate model, vehicle profiles, JSONL capture format |
| `ui-theme` | library | `com.phoenix.uitheme` | Shared Compose theme: `AccentPreset`, `PhoenixPalette`, `PhoenixTypography` (bundled Russo One, OFL), chamfered `PhoenixShapes`, `PhoenixTheme`, `ThemeBroadcast` |
| `mock-syu-ms` | app | **`com.syu.ms`** | Emulator stand-in for the vendor service. Same package, action, descriptors and transaction ids. Simulates the F-150 climate system, the boot audio race, and replays captures |
| `launcher` | app | `com.phoenix.launcher` | HOME app. Boots into AA, switcher AA / Radio / Phone / Settings, audio-path readiness state machine, self-update |
| `hvac-bar` | app | `com.phoenix.hvacbar` | Omnipresent climate bar overlay replacing `com.syu.air` |
| `aa-receiver` | app (external) | `com.andrerinas.headunitrevived` | Android Auto receiver: our Open Headunit fork (AGPL-3.0) at [swdimock/open-headunit](https://github.com/swdimock/open-headunit), branch `phoenix`, checked out as the sibling `../open-headunit` (remote `origin` = fork, `upstream` = andreknieriem/open-headunit), kept out of this repo. Based on upstream `ae74788d8312` (4.0.0-beta2); rebase `phoenix` onto upstream to pick up new AA releases. Built by `scripts/build-receiver.sh` (needs NDK 29.0.14206865, CMake 3.22.1). The launcher starts it with `phoenix.transport` = `tcp`/`usb`, `phoenix.host`, `phoenix.port`, `phoenix.inset_bottom` = 227, `phoenix.home_button` = false (the bar has HOME); it mutes audio while `AUDIO_PATH ready=false`. Changes: its `docs/PHOENIX-CHANGES.md`. Emulator TCP setup: `docs/ANDROID-AUTO-EMULATOR.md` |

Dependency direction: `mock-syu-ms`, `launcher`, `hvac-bar` -> `fyt-ipc`; `launcher`, `hvac-bar` -> `ui-theme`.
Apps never depend on each other.

```mermaid
flowchart TB
    subgraph device [Head unit or emulator]
        MS["com.syu.ms (vendor) / mock-syu-ms (emulator)"]
        subgraph ours [Our APKs]
            L[launcher]
            B[hvac-bar]
            AA[aa-receiver - Open Headunit fork]
            LIB[fyt-ipc]
        end
    end
    MS <-->|"Binder: com.syu.ipc.*"| LIB
    LIB --> L
    LIB --> B
    L -->|intent| AA
```

## The vendor IPC surface (what `fyt-ipc` wraps)

Bind `Intent("com.syu.ms.toolkit").setPackage("com.syu.ms")`. Three AIDL interfaces, hand-rolled (no .aidl
in the build; descriptors and transaction ids are in `SyuBinder`):

```
interface IRemoteToolkit  { IRemoteModule getRemoteModule(int moduleId); }                      // tx 1
interface IRemoteModule   { void cmd(int code, int[] i, float[] f, String[] s);                  // tx 1
                            ModuleObject get(int code, int[] i, float[] f, String[] s);          // tx 2
                            void register(IModuleCallback cb, int code, int flag);               // tx 3
                            void unregister(IModuleCallback cb, int code); }                     // tx 4
interface IModuleCallback { void update(int code, int[] i, float[] f, String[] s); }             // tx 1
```

Module ids: 0 MAIN, 1 RADIO, 2 BT, 4 SOUND, 7 CANBUS, 10 STEER (full table in `Modules`).
Facts verified by the community on live FYT hardware that our code relies on:

- No permission gate; an ordinary app can read and write.
- Registration notifies on change **and replays the cached value immediately**. `get()` returns nothing
  useful for climate codes. Registration is the only read path.
- Reads and writes use different code spaces: `U_AIR_*` ids are what you subscribe to; writes are
  `cmd(FIXED_CODE, int[]{ index, value })` with per-vehicle indices. Sending a read id to `cmd()` is
  silently discarded.
- CANBUS code 1019 streams raw MCU frames (`2E id len payload cksum`) as `int[]` of bytes; code 1008 sends.
- `com.syu.ms` force-stops ~27 third-party packages when the unit sleeps (ACC off). Our apps must
  tolerate being killed and come back on wake; the launcher, being HOME, is relaunched by the system.

## Vehicle profiles

Everything CAN-box specific sits behind `VehicleProfile` (`fyt-ipc/.../profile`). `FordF150Profile` is the
target and currently mirrors `Wk2ReferenceProfile` (the only published, verified table) until values are
recorded in the truck. `mock-syu-ms` drives its simulation from the same profile, so when the profile is
corrected the emulator follows automatically.

## Captures

`FrameLogEntry` (JSONL) is the single interchange format between the truck recorder and the emulator
replayer. Committed captures live in `docs/captures/`. Each `up` line is a `ModuleUpdate`, each `cmd` line
is a `ModuleCommand` we sent, `mark` lines are human annotations ("tapped Radio in stock UI").

## The boot audio race

Phoenix retains the OEM ACM (radio brain) and factory amp; head-unit audio enters via the ACM AUX input and
the CAN box must (a) wake/select that source and (b) assert amp-on. Today AA produces audio before that
happens, so nothing is heard until the user taps "Radio" in the stock UI (which sends a source command).

Launcher owns an **audio-path readiness state machine** (foreground service):

```mermaid
stateDiagram-v2
    [*] --> Waiting: boot / ACC on
    Waiting --> Selecting: profile.isAudioPathReadySignal(update) OR timeout
    Selecting --> Ready: send profile.audioSourceSelectCommand, short settle delay
    Ready --> Waiting: sleep / ACC off / com.syu.ms rebinds
```

While not `Ready`, the launcher asks the AA receiver to hold audio (broadcast
`com.phoenix.launcher.action.AUDIO_PATH` with `ready=false|true`; the AA fork writes silence while not ready). The mock
simulates the race: the readiness signal arrives N seconds after "boot", and audio is only "audible" after the
source-select command has been received. The mock exposes that as a debug code so the launcher can show it.

## Theme

Dark-only Raptor / VelociRaptor 600 styling from `ui-theme`: near-black graphite surfaces, chamfered (cut-corner)
shapes, Russo One for wordmarks / headings / readouts, system sans for body text. The accent is a user-selected
`AccentPreset` (`tuxedo_black` default: brushed Hennessey silver on black; alternates `molten_orange`, `race_red`, `blue_flame`; a persisted retired `ingot_silver` reads as `tuxedo_black`). The launcher owns
the choice (Settings > Appearance, DataStore `theme_prefs`) and on start and on every change sends the explicit
broadcast `com.phoenix.theme.action.ACCENT` with string extra `preset` (preset id) to `com.phoenix.hvacbar`, whose
exported `ThemeReceiver` persists it in the bar's own prefs and recolours a showing bar live. Screenshots: `docs/screenshots/theme-*.png`.

Controls are raised, chamfered keys with a bold top-lit accent gradient when latched (`PhoenixPalette.activeFill`,
`boldFill` for fixed colours such as seat heat), a faintly lit graphite slab when not, and a 1.5 dp bevel
(highlight top, shadow bottom). Two key kinds on the bar: toggles (asymmetric chamfer, fill when ON) and action keys
(HOME: even chamfer, accent outline). Climate glyphs (windshield defrost, seated figure with airflow arrows, heated
seat) are stroke-drawn `ImageVector`s in `hvac-bar/.../HvacGlyphs.kt`. The bar shows only what the 2011 Raptor has:
no seat ventilation and no rear-window defroster (both stay in the `fyt-ipc` model for other vehicles).

**Branding.** The repo bundles no Ford / SVT / Hennessey artwork (trademarks). The launcher header and the cold-start
splash look up a drawable named `hennessey_logo` at runtime (`launcher/.../ui/Branding.kt`): drop the owner's logo at
`launcher/src/main/res/drawable-nodpi/hennessey_logo.png` (or `.webp`, light-on-transparent, any size; it is scaled to
fit) and it is used; without it a type-only HENNESSEY wordmark is drawn. The badge reads VELOCIRAPTOR 600 / HPE600 /
No. 15 (`VehicleIdentity`, this truck's Hennessey build number). The splash (`SplashOverlay`) is drawn over the
launcher on every fresh process start, held 1.8 s and faded out; activity recreation does not replay it.

**HOME.** The climate bar carries a full-height HOME key on its left (`HvacBar` -> `ACTION_MAIN`/`CATEGORY_HOME`,
allowed from the overlay because the bar holds `SYSTEM_ALERT_WINDOW`), so HOME is reachable from Android Auto, the
vendor radio app and anything else without covering their UI. The launcher therefore sends the AA receiver
`phoenix.home_button=false`, which suppresses the receiver's own floating button. While the launcher is in the
foreground it also sends `com.phoenix.launcher.action.HOME` with `visible=true|false` (`LauncherHomeBroadcast`) so
the bar can fill the HOME key; the bar's `HomeReceiver` updates an ephemeral `BarSettings.launcherHome` flag.

## Emulator

AVD `phoenix_f150`: Android 12 (API 31) x86_64 google_apis, 1200x1600 @160dpi portrait (FYT convention
dp = px; confirm with `adb shell wm size` on the real unit). Scripts: `scripts/create-avd.sh`,
`scripts/run-emulator.sh`, `scripts/install-all.sh`. Toolchain is user-scope under
`C:\Users\Stephen\android-tools` (see `scripts/env.sh`).

Emulator milestone = `install-all.sh` on a fresh AVD gives: mock running, our launcher as HOME booting into the
AA pane (placeholder when no receiver installed), switcher working, HVAC bar overlay reflecting and driving the
mock climate state, audio-readiness state visibly progressing Waiting -> Selecting -> Ready.

## Conventions

- Kotlin, Jetpack Compose, Material 3, coroutines/Flow. `minSdk 31`, `targetSdk 33`, `compileSdk 34`.
- No root, no `android.uid.system`, no platform-key assumptions.
- Public API of `fyt-ipc` (`FytToolkit`, `FytModule`, `ModuleUpdate`, `ModuleCommand`, `VehicleProfile`,
  `ClimateState`, `ClimateAction`, `McuFrame`, `FrameLogEntry`) is the contract between modules; change it
  only with a matching change in every consumer.
- Unit tests under `src/test` (JUnit4 + Truth; Robolectric where Android types are needed). Keep them fast.
- Everything that is a guess about the F-150 CAN box is marked `UNVERIFIED` in code comments.
