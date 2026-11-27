# Step 3 — Record a workout

**Outcome.** A rider taps **Start workout**, switches to a navigator and locks the phone for part of the ride. The app records heart rate throughout the session. The rider can pause, continue the same ride, then finish and review the captured data before choosing **Save** or **Discard**. If Android stops the app, the rider can make that decision for the portion already recorded. [Milestone 3](../IMPLEMENTATION_PLAN.md#3-record-a-workout)

## Starting point

- `HeartRateApplication` owns one `SensorRepository`. The Bluetooth transport timestamps each measurement with `elapsedRealtime()`. The repository exposes the latest value to Home and expires it after five seconds of silence. Today, leaving the activity can cancel a search, and a connection lost in the background waits for a visible screen before retrying. [Sensor repository](../app/src/main/java/arg/adegtiarev/heartratetraining/sensor/SensorRepository.kt) · [Bluetooth transport](../app/src/main/java/arg/adegtiarev/heartratetraining/bluetooth/AndroidSensorTransport.kt)
- Home and Settings are Navigation 3 destinations. Preferences DataStore holds four zone boundaries and five colors. The existing Kotlin classifier assigns a BPM exactly on a boundary to the higher zone. Reuse these components in the existing app module. [Screen navigation](../app/src/main/java/arg/adegtiarev/heartratetraining/ui/HeartRateScreens.kt) · [Zone rules](../app/src/main/java/arg/adegtiarev/heartratetraining/zones/HeartRateZones.kt)

## Implementation sequence

### 1. Define the workout rules in plain Kotlin

| Rider action or event | Timer | Readings and navigation |
| --- | --- | --- |
| Start | Start the active timer. | Open one recording draft, capture the saved zone configuration and show Workout. The first fresh measurement starts the trace. |
| Pause | Freeze active time. | Keep live BPM and zone color on screen. Close the recording interval and show **Resume** and **Finish**. |
| Resume | Continue the same timer. | Open another recording interval in the same draft. Append fresh measurements received from this point. |
| Signal lost during recording | Keep counting active time. | Show a dash immediately on disconnect or contact loss, or when the last reading expires. Mark a break in the trace. The next fresh reading starts a new line segment. |
| Finish from Pause | Fix the duration at its paused value. | Close the draft for recording, stop the service and open Results for the rider's decision. |

Store the wall-clock start for the date in History. Measure sample positions and transitions as monotonic offsets from Start, including time spent in deep sleep. Compute active duration from the recording intervals. This keeps a manual pause distinct from a dropout even if the phone's clock changes. [Android clocks](https://developer.android.com/reference/android/os/SystemClock)

For time in zone, use pairs of consecutive valid samples within one continuous recording interval. Credit the elapsed time between them to the zone of the earlier sample. A pause, disconnect or contact loss breaks the pair. A difference of five seconds or more between receipt timestamps also breaks it, even if the live freshness timer fired late. A single reading can establish that a zone was reached and contribute zero observed seconds. Time before the first reading and after the last stays unassigned.

**First-use behavior.** When DataStore explicitly reports unconfigured zones, allow a ride with live BPM, a neutral background and a timeline. Store that the ride began without zones and show zone totals as unavailable. Later edits apply to future rides. When settings are still loading, wait for the result. On a read or validation error, offer Retry or Settings so the rider can recover the saved configuration. [First-use behavior on Home](step-02-heart-rate-zones.md#1-specify-the-classifier-with-plain-kotlin-fixtures)

### 2. Persist the draft in Room

Add Room runtime and the matching Room compiler via KSP in `:app`. Check the stable releases against the Kotlin and Android Gradle Plugin versions in the catalog, pin the selected versions there and build the generated DAO code. Keep rider preferences in their existing DataStores. [Version catalog](../gradle/libs.versions.toml) · [Room releases and setup](https://developer.android.com/jetpack/androidx/releases/room) · [KSP releases](https://github.com/google/ksp/releases)

| Record | Data needed for recording and recovery |
| --- | --- |
| Workout | Stable ride ID, wall-clock start, recording/paused/awaiting-decision/saved state, zone snapshot and latest committed elapsed offset. |
| Measurement | Ride ID, sequence, elapsed offset and BPM. Equal BPM values and measurements arriving in the same millisecond are separate samples. |
| Transition | Ride ID, sequence, elapsed offset and event such as pause, resume, signal loss, signal return or finish. These events preserve the reason for each break. |

Serialize workout actions and sensor events in capture order. Create the draft and snapshot together. Commit each accepted measurement and its progress in one transaction. Commit transitions before acknowledging a change of state. During a prolonged dropout, periodically checkpoint elapsed time so the captured portion includes time spent recording without a signal. If a write fails, surface a recording error and preserve the last committed portion for review.

Enforce one pending ride across repeated Start actions. **Save** changes the stopped draft to saved under its original ID. Only saved rides appear in History. **Discard** deletes the pending draft in a transaction, with foreign keys cascading the deletion to its measurements and transitions. Apply each decision to its ride ID and pending state so retries respect the first committed outcome.

### 3. Feed the recorder from the existing Bluetooth connection

At the point where `SensorRepository` receives a decoded notification, deliver its original BPM and receipt timestamp to the recorder before deriving the latest value for display. The UI's `StateFlow` can skip intermediate updates. Preserve each incoming recording event, including repeated BPM, and handle queue or write failures explicitly. Keep Room I/O off the Bluetooth callback and main thread. Serialize Pause and Resume with incoming samples so a delayed callback from a paused interval cannot enter the next interval.

Keep the single GATT subscription alive while a workout is recording or paused, including across rotation, leaving the activity and screen lock. On a disconnect, retry the known strap directly through `SensorTransport.connect(strap)` with bounded attempts and backoff. Keep discovery and device selection in the visible UI. If Bluetooth access is lost, mark the gap and retry when access returns. After Finish, Home can continue its live preview when visible. [Existing sensor lifecycle](../app/src/main/java/arg/adegtiarev/heartratetraining/MainActivity.kt) · [Android background BLE guidance](https://developer.android.com/develop/connectivity/bluetooth/ble/background)

### 4. Run recording in a connected-device foreground service

- **Start from Home.** Check Bluetooth access while the activity is visible and use the existing permission, enable and selection actions as needed. Call `startForegroundService()` from the rider's Start action. The private, non-exported service promptly calls `ServiceCompat.startForeground()` with the `connectedDevice` type and notification, before database work. Report the workout as recording once its draft is durable. On startup failure, release any resources acquired for that attempt and give the rider a retry action. [Foreground service launch](https://developer.android.com/develop/background-work/services/fgs/launch)
- **Permissions and notification.** Declare `FOREGROUND_SERVICE`, the `connectedDevice` service type and `FOREGROUND_SERVICE_CONNECTED_DEVICE` for API 34+. A granted `BLUETOOTH_CONNECT` satisfies the modern Bluetooth prerequisite. Use a low-importance notification that shows whether recording is active or paused and opens the current ride. Declare `POST_NOTIFICATIONS` and request it in context on API 33+. If the rider declines, still supply the foreground notification and continue recording. Android lists the service in Active apps. [Connected-device prerequisites](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device) · [Notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- **Pause and Finish.** Keep the service, notification and sensor connection during Pause. Finish commits the stopped draft, removes the foreground notification and stops the service before opening Results. A repeated notification tap or screen recreation attaches to the same session.
- **Recover after interruption.** Use `START_NOT_STICKY` for the rider-initiated session. A live service owns the active ride when the rider returns from Maps or reopens the activity. If a fresh process finds an orphaned recording or paused draft, close it at its last committed offset and offer Results with **Save** and **Discard**. An already stopped draft opens the same decision. Recovery uses committed offsets even after reboot and excludes time between the interruption and the next launch. [Android user-initiated stops](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)

### 5. Connect Workout, Results and History to Home

Put a large **Start workout** button on Home alongside **History** and **Settings**. Workout shows large live BPM, the captured zone color, active duration and the actions for the current state. Back navigation, rotation and notification taps preserve the session. Screen-level ViewModels expose immutable state and accept actions. Compose collects state with lifecycle awareness and owns permission prompts and localized text. Keep Android services, Room and `Context` outside the ViewModels. [Repository architecture](../AGENTS.md#architecture)

Results loads the ride by ID. Show average and maximum BPM, active duration, a heart rate timeline with distinct pauses and signal gaps, and time and percentage for each captured zone. Average BPM is the arithmetic mean of valid recorded samples. The percentages use the sum of observed zone time. Provide an empty-reading state so even an interrupted ride with zero samples can be reviewed. After Save, a dedicated History screen lists saved rides by date, active duration and average BPM, newest first. Selecting any row opens the same results for that ride. After Discard, the rider returns to Home. Milestone 4 adds the window over the navigator. [Rider workflow](../README.md#record-and-review-a-ride)

Provide English, Russian and Spanish resources for screens, actions, permissions, notification text, missing-signal states and accessibility descriptions. Format dates, durations and BPM units for the locale. Make recording state, zone and chart gaps distinguishable through text as well as color.

## Verification and acceptance

### A synthetic ride with known totals

Use zone starts of 108, 126, 144 and 162 BPM and the following offsets in seconds. Feed the same events into the Kotlin rules and Room recorder.

| Offset | Action or measurement |
| --- | --- |
| 0, 2, 4 | Start at 0, then receive 125, 125 and 126 BPM at those offsets. |
| 5, 6, 8 | Pause at 5, receive 150 BPM at 6, resume at 8. |
| 9, 11 | Receive 126 and 144 BPM. |
| 12, 18, 20 | Lose contact at 12. Receive fresh readings of 162 BPM at 18 and 20. |
| 21, 24 | Pause at 21 and finish at 24. |

Expect **one draft, seven stored measurements and 18 seconds of active time**. The 150 BPM reading is visible on Pause and the database count stays at three until the next recording sample at 9. Expected durations for zones 1 through 5 are **0, 4, 2, 0 and 2 seconds**. Zone 4 was reached at 11. The timeline breaks across both the pause and the signal loss.

### Automated checks

- **JVM.** Use a fake monotonic clock and synthetic sensor events. Cover duplicate BPM, a burst of measurements, samples around Pause and Resume, explicit contact loss, and silent intervals just below and exactly at five seconds. Check that Home and the recorder share one subscription and that manual Disconnect still holds on Home. Move the wall clock during a recording and verify stable durations. Edit zone settings after Start and check the original ride against its snapshot.
- **Room on Android.** Exercise Start, Pause, Resume, Finish, repeated Save and repeated Discard. Check History row counts and cascading deletion. Close and reopen a temporary file-backed database during recording, during a dropout, while paused and after Finish. Verify the last committed offset, readings and pending decision, including a draft with zero readings. Repeat recovery after a simulated write failure. [Room database testing](https://developer.android.com/training/data-storage/room/testing-db)
- **Compose and service.** Exercise the Home action, notification return, rotation and activity recreation with a recording or paused service. Verify a service startup failure offers retry, a double tap creates one draft, and saved and unconfigured zones render correctly. Check the three locales, large text and accessible labels. Exercise the legacy Bluetooth permission path on an API 29 or 30 emulator.

### Pixel 6a with a Coospo H9Z

1. Set recognizable zone boundaries and a color, connect the worn strap, then start a workout. Keep Google Maps in front for a sustained ride and lock the phone for part of it. Verify persisted sample offsets across both periods and return to the same ride from its notification.
2. Pause while the strap sends data. Watch live BPM change, check that the timer and stored sample count stay fixed, then resume. Interrupt and restore contact while recording. Also exercise an actual disconnect and reconnect with Maps in front. Verify the timer includes the outage, the trace breaks and fresh samples append to the original draft.
3. Finish from Pause and check that the notification disappears. Compare Results to the stored samples and durations. Save and verify exactly one History entry. Repeat on a second ride with Discard and verify removal of its draft and readings.
4. On API 33+, start another ride with notification permission denied and verify recording with Maps in front. Stop the app from Android's Active apps list, then relaunch. Confirm Results offers the captured portion and exercise Save and Discard on separate interrupted runs.

Run the project checks from the repository root. The connected suite requires a phone or emulator.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
.\gradlew.bat :app:connectedDebugAndroidTest
```

After acceptance, record the build, automated and physical outcomes here, update milestone 3 in the implementation plan and describe the available rider workflow in the README. Use synthetic data in committed fixtures and report counts or timings from hardware checks without device identifiers or personal workout captures.

## Verification record

Verified on November 27, 2026.

- Debug and release APKs built. All 23 JVM tests passed. Android lint reported zero errors and seven unused template colors. On each of two emulators, API 29 and API 37, all 16 enabled Android tests passed. The two physical-strap cases and the separately driven process-stop case are opt-in. The API 29 run used the installed Android TV system image and a phone-sized display.
- Room 2.8.5 and KSP 2.3.14 compile with the project's AGP 9.5.3 and Kotlin 2.4.21. The exported Room v1 schema is in the app module. Saving a ride computes its history summary once so opening History can read the list without scanning every ride's measurements. [Room releases](https://developer.android.com/jetpack/androidx/releases/room) · [KSP 2.3.14](https://github.com/google/ksp/releases/tag/2.3.14)
- The JVM fixtures cover the reference ride, a measurement exactly at the stale threshold, identical readings and timestamps, and the distinction between zero BPM and missing data. A simulated transport delivers a burst of 100 readings and reconnects the remembered strap after the activity finishes.
- The emulator uses an instrumentation-only sensor source. Run the connected tests on a selected emulator with the runner argument `syntheticWorkout=true`. The normal synthetic suite uses an in-memory Room database, with separate file-backed tests for reopening drafts and rolling back a failed sample insert. The release APK uses the Bluetooth transport.
- On API 37 the live flow recorded 35 samples, including 15 paced at one per second with Google Maps in front and 15 at the same cadence with the display off. Notification permission was denied. A paused reading of 180 BPM stayed out of storage. After an interruption of more than five seconds, fresh readings resumed in the same ride. Results and History showed 143 BPM average, and Results showed 165 BPM maximum.
- On Android 17, Android's notification permission dialog was declined at Start. The service recorded with permission denied. Stopping the app through the system's `cmd activity stop-app` command and cold-launching opened the interrupted draft. Discard removed an empty ride. A second system stop interrupted a service after three synthetic readings of 120, 135 and 150 BPM. The cold launch showed 135 BPM average and 150 BPM maximum. Save added one row to History.
- With notifications enabled, the rider left the workout, opened History and switched to Maps. Tapping the workout notification in the Android notification shade returned to the running timer. Pause, Finish and Discard completed from that screen.
- The history fixture contains an eight-minute ride and a 37-minute ride with a one-minute pause. Selecting each row displayed its own summary and chart. The long ride had 36 minutes of active time, a mean of 137 BPM and a maximum of 168 BPM. Changing the current zone settings retained the original ranges in Results.
- A sustained physical ride with the Pixel 6a, a worn Coospo H9Z and Maps remains the hardware acceptance check for background BLE reception and radio reconnection.

To repeat the synthetic suite with a single running emulator, build and install both debug APKs, then invoke the test runner. With Maps installed, add `-e openMaps true -e pacedRide true` to exercise the real-time background interval. The locale used by the app determines the language of the results.

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb -e install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -e install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -e shell am instrument -w -e syntheticWorkout true arg.adegtiarev.heartratetraining.test/arg.adegtiarev.heartratetraining.WorkoutTestRunner
```
