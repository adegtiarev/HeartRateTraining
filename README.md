# Heart Rate Training

Heart Rate Training is an Android app that displays live heart rate from a Bluetooth chest strap. The planned workout features focus on cycling with a navigation app open. A glance at the color of a small window over the map will tell the rider which heart rate zone they are in. After the ride, they will be able to review how their heart rate changed and how long they spent in each zone.

## Try live heart rate

Put on the strap, open the app and grant access to nearby devices. Android 10 and 11 request location access for discovery. Tap **Find chest strap** and choose your device. Home shows live BPM as soon as the first measurement arrives. A dash appears if readings stop or the sensor reports poor contact. The app remembers the last working strap and looks for it on the next launch.

**Disconnect** releases the strap for the rest of the current visit. Use **Connect** to reconnect manually or **Choose another strap** to select a replacement. Open **Zone settings** to color Home by heart rate, including before pairing a strap. Home and Settings are available in English, Russian and Spanish. Workout recording, history and the navigation overlay are planned for later milestones.

## Planned rider workflow

The planned public distribution is source code and build instructions on GitHub. There are currently no plans for a Google Play release.

1. **Put on the strap and open the app.** On first use, choose the strap from nearby devices and connect it to the phone. On later launches, the app looks for the remembered strap and connects automatically when it is available. You can also disconnect it manually. The app targets the standard Bluetooth Low Energy Heart Rate Service, with the Coospo H9Z as the first strap for testing. [Bluetooth Heart Rate Service](https://www.bluetooth.com/specifications/specs/heart-rate-service-1-0/) · [Coospo H9Z](https://www.coospo.com/products/h9z-chest-heart-rate-monitor)
2. **Check your pulse on Home.** Before you start recording, the home screen shows a large heart rate reading in beats per minute (BPM). The screen background takes the color of your current zone. A large **Start workout** button is the main action. **History** opens the list of saved workouts. **Settings** opens your zone boundaries and colors.
3. **Start the workout.** The workout screen keeps the large BPM reading and zone-colored background and adds an elapsed workout timer and a **Pause** button. Recording continues while another app is in front or the screen is off. For a reading over your navigator, enable the optional overlay. It shows BPM on the same zone color and requires Android's “display over other apps” access. [Android overlay access](https://developer.android.com/reference/android/provider/Settings#canDrawOverlays(android.content.Context))
4. **Pause, then resume or finish.** Tap **Pause** to stay on the workout screen and see live BPM while the recorder skips new readings. **Resume** continues recording into the same workout. Only **Finish** ends the workout and opens its results. A lost signal leaves the timer running during recording. The overlay shows a dash on a black background until fresh readings return.
5. **Review the results and choose Save or Discard.** See the heart rate timeline, the zones reached and time in each zone before deciding. **Save** adds the workout to History in the local database. **Discard** removes the draft and its readings. A calorie estimate depends on finding a suitable method for cycling and having the inputs it requires.

The planned interface languages are English (default), Russian and Spanish.

## Zone colors

Open **Zone settings** on Home. The table shows each zone's color, start and end. Enter the starting BPM of zones 2 through 5. The previous row's end updates as you type. Zone 1 starts at zero, and the ∞ symbol marks zone 5's open end.

To fill in all four starts at once, open **Calculate thresholds** below the table. Enter a known maximum heart rate or an adult age, then tap **Apply to draft**. For age, the app estimates maximum heart rate using `208 − 0.7 × age`. It calculates thresholds at 60%, 70%, 80% and 90% of the maximum and keeps the colors you picked. For example, a maximum of 180 gives thresholds of 108, 126, 144 and 162 BPM. Exactly 126 BPM belongs to zone 3. [Tanaka study](https://pubmed.ncbi.nlm.nih.gov/11153730/) · [Five-zone percentages](https://support.polar.com/e_manuals/polar-360/polar-360-flow-user-manual-english/heart-rate-zones.htm)

The default palette runs from the lowest to the highest intensity.

| Zone | 1 | 2 | 3 | 4 | 5 |
| --- | --- | --- | --- | --- | --- |
| Background | Gray | Green | Blue | Orange | Red |

Tap a color to select a swatch or enter a six-digit RGB value. **Save** applies all four boundaries and five colors together. **Cancel** discards your edits. Home stays neutral until the first save. With a fresh reading, Home shows the zone number and fills the screen with its saved color. A change in settings takes effect even if the strap keeps reporting the same BPM. When the signal expires, Home returns to a neutral background and a dash. Settings survive a restart. Future workouts will capture the configuration at their start.

## Run the app

Open the project root in Android Studio and sync Gradle. The current app module supports Android 10 (API 29) and later. Use the compile SDK configured in the [app build file](app/build.gradle.kts) and the JDK selected by the [Gradle daemon toolchain](gradle/gradle-daemon-jvm.properties). Select the `app` run configuration and a device or emulator.

To build a debug APK and run the local unit tests on Windows, use the Gradle wrapper from the project root.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
```

On macOS or Linux, run `./gradlew assembleDebug testDebugUnitTest`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Project documents

- [Implementation plan](IMPLEMENTATION_PLAN.md) — milestones, acceptance checks and decisions to make during development.
- [Step 1 — Connect a chest strap and display live heart rate](docs/step-01-live-heart-rate.md) — implementation sequence and checks for the first working sensor connection.
- [Step 2 — Configure heart rate zones](docs/step-02-heart-rate-zones.md) — zone rules, settings and verification.
- [Contributor and agent instructions](AGENTS.md) — architecture, language conventions and verification.
