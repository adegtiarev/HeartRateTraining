# Heart Rate Training

Heart Rate Training is a planned Android app for recording workouts with a Bluetooth chest strap. Its first use case is cycling with a navigation app open. A glance at the color of a small window over the map will tell the rider which heart rate zone they are in. After the ride, they can review how their heart rate changed and how long they spent in each zone.

**Development is at the planning stage.** The project contains a Kotlin and Jetpack Compose starter app. The planned public distribution is source code and build instructions on GitHub. There are currently no plans for a Google Play release.

## Planned rider workflow

1. **Put on the strap and open the app.** On first use, choose the strap from nearby devices and connect it to the phone. On later launches, the app looks for the remembered strap and connects automatically when it is available. You can also disconnect it manually. The app targets the standard Bluetooth Low Energy Heart Rate Service, with the Coospo H9Z as the first strap for testing. [Bluetooth Heart Rate Service](https://www.bluetooth.com/specifications/specs/heart-rate-service-1-0/) · [Coospo H9Z](https://www.coospo.com/products/h9z-chest-heart-rate-monitor)
2. **Check your pulse on Home.** Before you start recording, the home screen shows a large heart rate reading in beats per minute (BPM). The screen background takes the color of your current zone. A large **Start workout** button is the main action. **History** opens the list of saved workouts. **Settings** opens your zone boundaries and colors.
3. **Start the workout.** The workout screen keeps the large BPM reading and zone-colored background and adds an elapsed workout timer and a **Pause** button. Recording continues while another app is in front or the screen is off. For a reading over your navigator, enable the optional overlay. It shows BPM on the same zone color and requires Android's “display over other apps” access. [Android overlay access](https://developer.android.com/reference/android/provider/Settings#canDrawOverlays(android.content.Context))
4. **Pause, then resume or finish.** Tap **Pause** to stay on the workout screen and see live BPM while the recorder skips new readings. **Resume** continues recording into the same workout. Only **Finish** ends the workout and opens its results. A lost signal leaves the timer running during recording. The overlay shows a dash on a black background until fresh readings return.
5. **Review the results and choose Save or Discard.** See the heart rate timeline, the zones reached and time in each zone before deciding. **Save** adds the workout to History in the local database. **Discard** removes the draft and its readings. A calorie estimate depends on finding a suitable method for cycling and having the inputs it requires.

The planned interface languages are English (default), Russian and Spanish.

## Zone colors

In **Settings**, you can edit each of the five zones' BPM boundaries and colors. You can also calculate boundaries from a personal profile. One candidate is to estimate maximum heart rate as `220 − age` and derive the boundaries as percentages of that estimate. The calculation method and percentages will be researched during implementation.

The default palette runs from the lowest to the highest intensity.

| Zone | 1 | 2 | 3 | 4 | 5 |
| --- | --- | --- | --- | --- | --- |
| Background | Gray | Green | Blue | Orange | Red |

The app saves your boundaries and colors locally between sessions. Home uses your latest settings. Each workout keeps the boundaries and colors it started with for its live screen, overlay and results, including when you reopen it from History.

## Run the starter app

Open the project root in Android Studio and sync Gradle. The current app module supports Android 10 (API 29) and later. Use the compile SDK configured in the [app build file](app/build.gradle.kts) and the JDK selected by the [Gradle daemon toolchain](gradle/gradle-daemon-jvm.properties). Select the `app` run configuration and a device or emulator.

To build a debug APK and run the local unit tests on Windows, use the Gradle wrapper from the project root.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
```

On macOS or Linux, run `./gradlew assembleDebug testDebugUnitTest`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Project documents

- [Implementation plan](IMPLEMENTATION_PLAN.md) — milestones, acceptance checks and decisions to make during development.
- [Contributor and agent instructions](AGENTS.md) — architecture, language conventions and verification.
