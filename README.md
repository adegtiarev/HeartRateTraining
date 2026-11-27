# Heart Rate Training

Heart Rate Training is an Android app for recording rides with a Bluetooth chest strap. It shows live heart rate over a map in the color of your current zone, records while the phone is locked and keeps saved rides in local History.

Initial development is complete. Feedback from rides guides bug fixes and improvements. You can build the app from the source in this repository. [Build instructions](#run-the-app)

## Try live heart rate

Put on the strap, open the app and grant access to nearby devices. Android 10 and 11 request location access for discovery. Tap **Find chest strap** and choose your device. Home shows live BPM as soon as the first measurement arrives. A dash appears if readings stop or the sensor reports poor contact. The app remembers the last working strap and looks for it on the next launch.

If the connected strap reports its battery level, Home and Workout show the percentage next to its name. Below 10%, the percentage blinks twice per second. Disconnecting the strap clears the battery reading.

**Disconnect** releases the strap for the rest of the current visit. Use **Connect** to reconnect manually or **Choose another strap** to select a replacement. Open **Zone settings** to color Home by heart rate, including before pairing a strap. The interface is available in English (default), Russian and Spanish.

## Record and review a ride

Tap **Start workout** on Home. The workout screen shows live pulse and active time. The workout notification takes you back from another app. On Android 13 and later you can decline notification permission and continue recording. A first ride with unconfigured zones still records pulse and time.

**Pause** freezes the timer and recording while live BPM stays visible. **Resume** continues the same ride. A lost signal leaves the active timer running and creates a break in the chart. From Pause, choose **Finish** to review average and maximum BPM, the timeline and time in each zone. Average BPM is the arithmetic mean of recorded readings. Percentages use the observed time assigned to zones. The ride keeps the boundaries and colors captured at Start.

**Save** adds the ride to **History**. Each row shows its date, active duration and average BPM. Tap any row to reopen its results. **Discard** removes the draft and its readings. If Android stops the app during a ride, the next launch offers the same choices for the captured portion.

## See your pulse over a map

Start a workout and tap **Show overlay**. The first time, tap **Open settings** and allow the app to display over other apps. If Android shows a list, select Heart Rate Training. Return to Workout, then open your navigator. The window shows live BPM on the current zone's color. TalkBack can read the BPM and zone together.

The window first opens in the upper-right corner with large digits. Drag it with one finger to leave your map controls clear. Start with two fingertips on the window and spread them to enlarge the window and digits, or pinch them together to make both smaller. The app remembers the position and size when you hide the window, start another workout or restart the app. Rotating the phone keeps the window in the same relative part of the screen.

If the ride began with unconfigured zones, the digits appear on black. A white dash on black means the app is waiting for a fresh reading. The window keeps showing live pulse during Pause. To hide it, return to Workout from the notification or from Home and tap **Hide overlay**. Recording continues. **Finish** removes the window, and each new ride starts with it hidden.

When the strap battery drops below 10%, the overlay alternates between its usual background and white twice per second. A white zone alternates with black so the warning stays visible. The digits keep their contrast in both phases. At 10% or higher the background stays steady.

## Rider workflow

1. **Put on the strap and open the app.** On first use, choose the strap from nearby devices and connect it to the phone. On later launches, the app looks for the remembered strap and connects automatically when it is available. You can also disconnect it manually. The app uses the standard Bluetooth Low Energy Heart Rate Service and has been tested with the Coospo H9Z. [Bluetooth Heart Rate Service](https://www.bluetooth.com/specifications/specs/heart-rate-service-1-0/) · [Coospo H9Z](https://www.coospo.com/products/h9z-chest-heart-rate-monitor)
2. **Check your pulse on Home.** Before you start recording, the home screen shows a large heart rate reading in beats per minute (BPM). The screen background takes the color of your current zone. A large **Start workout** button is the main action. **History** opens the list of saved workouts. **Settings** opens your zone boundaries and colors.
3. **Start the workout.** The workout screen keeps the large BPM reading and zone-colored background and adds an elapsed workout timer and a **Pause** button. Recording continues while another app is in front or the screen is off. Enable the optional overlay to see BPM over the navigator. [Overlay controls](#see-your-pulse-over-a-map)
4. **Pause, then resume or finish.** Tap **Pause** to stay on the workout screen and see live BPM while the recorder skips new readings. **Resume** continues recording into the same workout. Only **Finish** ends the workout and opens its results. A lost signal leaves the timer running during recording. The overlay shows a dash on a black background until fresh readings return.
5. **Review the results and choose Save or Discard.** See the heart rate timeline, the zones reached and time in each zone before deciding. **Save** adds the workout to History in the local database. **Discard** removes the draft and its readings.

## Zone colors

Open **Zone settings** on Home. The table shows each zone's color, start and end. Enter the starting BPM of zones 2 through 5. The previous row's end updates as you type. Zone 1 starts at zero, and the ∞ symbol marks zone 5's open end.

To fill in all four starts at once, open **Calculate thresholds** below the table. Enter a known maximum heart rate or an adult age, then tap **Apply to draft**. For age, the app estimates maximum heart rate using `208 − 0.7 × age`. It calculates thresholds at 60%, 70%, 80% and 90% of the maximum and keeps the colors you picked. For example, a maximum of 180 gives thresholds of 108, 126, 144 and 162 BPM. Exactly 126 BPM belongs to zone 3. [Tanaka study](https://pubmed.ncbi.nlm.nih.gov/11153730/) · [Five-zone percentages](https://support.polar.com/e_manuals/polar-360/polar-360-flow-user-manual-english/heart-rate-zones.htm)

The default palette runs from the lowest to the highest intensity.

| Zone | 1 | 2 | 3 | 4 | 5 |
| --- | --- | --- | --- | --- | --- |
| Background | Gray | Green | Blue | Orange | Red |

Tap a color to select a swatch or enter a six-digit RGB value. **Save** applies all four boundaries and five colors together. **Cancel** discards your edits. Home stays neutral until the first save. With a fresh reading, Home shows the zone number and fills the screen with its saved color. A change in settings takes effect even if the strap keeps reporting the same BPM. When the signal expires, Home returns to a neutral background and a dash. Settings survive a restart. Each workout captures the configuration at its start.

## Run the app

Open the project root in Android Studio and sync Gradle. The current app module supports Android 10 (API 29) and later. Use the compile SDK configured in the [app build file](app/build.gradle.kts) and the JDK selected by the [Gradle daemon toolchain](gradle/gradle-daemon-jvm.properties). Select the `app` run configuration and a device or emulator.

To build a debug APK and run the local unit tests on Windows, use the Gradle wrapper from the project root.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
```

On macOS or Linux, run `./gradlew assembleDebug testDebugUnitTest`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Project documents

The step documents preserve the original implementation and test procedures for reference.

- [Implementation record](IMPLEMENTATION_PLAN.md) — completed milestones and verification results.
- [Step 1 — Connect a chest strap and display live heart rate](docs/step-01-live-heart-rate.md) — implementation sequence and checks for the first working sensor connection.
- [Step 2 — Configure heart rate zones](docs/step-02-heart-rate-zones.md) — zone rules, settings and verification.
- [Step 3 — Record a workout](docs/step-03-record-workout.md) — background recording, draft recovery and ride verification.
- [Step 4 — Ride with the heart rate overlay](docs/step-04-workout-overlay.md) — overlay behavior, permission flow and cross-app verification.
- [Contributor and agent instructions](AGENTS.md) — architecture, language conventions and verification.

## License

The project's original code and documentation are in the public domain under The Unlicense. Anyone may use, modify, sell and redistribute them for any purpose, without attribution. The Gradle Wrapper and third-party libraries carry their own licenses. [License text](LICENSE)
