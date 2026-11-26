# Heart Rate Training

Heart Rate Training is a planned Android app for recording workouts with a Bluetooth chest strap. Its first use case is cycling with a navigation app open. A glance at the color of a small window over the map will tell the rider which heart rate zone they are in. After the ride, they can review how their heart rate changed and how long they spent in each zone.

**Development is at the planning stage.** The project contains a Kotlin and Jetpack Compose starter app. The planned public distribution is source code and build instructions on GitHub. There are currently no plans for a Google Play release.

## Planned rider workflow

1. **Connect the strap.** Discover a compatible sensor, connect to see live beats per minute (BPM), and disconnect on demand. Live viewing works independently of starting a workout. The app will target the standard Bluetooth Low Energy Heart Rate Service, with the Coospo H9Z as the first strap for testing. [Bluetooth Heart Rate Service](https://www.bluetooth.com/specifications/specs/heart-rate-service-1-0/) · [Coospo H9Z](https://www.coospo.com/products/h9z-chest-heart-rate-monitor)
2. **Set heart rate zones.** Enter zone boundaries in BPM or calculate them from a personal profile. One candidate is to estimate maximum heart rate as `220 − age` and use percentages of that estimate for the boundaries. The choice of method and percentages will be researched during implementation.
3. **Start a workout and open the navigator.** The app will record timestamped readings while another app is in front or the screen is off. The optional overlay will use its whole background to show the current zone. Its only number will be the current BPM. Android requires the rider to grant “display over other apps” access for this window. [Android overlay access](https://developer.android.com/reference/android/provider/Settings#canDrawOverlays(android.content.Context))
4. **Pause or resume when you choose.** If the strap drops out during recording, the workout timer keeps running and the overlay shows a dash on a black background until fresh readings return. The rider controls pauses.
5. **Stop and choose Save or Discard.** Saved workouts will be available from a local database. Open a ride to see the heart rate timeline, the zones reached and the time spent in each zone. An estimated calorie total is conditional on finding a suitable method for cycling and having the inputs it requires.

The planned interface languages are English (default), Russian and Spanish.

## Zone colors

| Zone | 1 | 2 | 3 | 4 | 5 |
| --- | --- | --- | --- | --- | --- |
| Background | Gray | Green | Blue | Orange | Red |

The workout screen, overlay and saved timeline will use this same color order. Gray always means zone 1.

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
