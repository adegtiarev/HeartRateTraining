# Implementation record

Initial development is complete as of November 27, 2026. Future fixes and improvements will come from feedback on rides.

## 1. Read the chest strap

**Completed and verified on November 26, 2026.** Home receives live notifications from a Coospo H9Z on a Pixel 6a running Android 17. The hardware checks covered first selection, automatic connection on a fresh launch, rotation, manual disconnect, launch with the strap absent, recovery when it returned, Bluetooth off/on and permission denial/recovery. The app also received fresh samples with Google Maps in front. See the [verification record](docs/step-01-live-heart-rate.md#verification-record) for device and automated test coverage.

On November 27 the Pixel also read the H9Z battery level over the same connection while receiving live heart rate. Home and Workout display the percentage beside the sensor name. Below 10% the percentage and the workout overlay background flash twice per second. The Pixel exercised the threshold and a white zone over Google Maps with synthetic battery levels. [Overlay verification](docs/step-04-workout-overlay.md#verification-record)

## 2. Define zones

**Implemented and verified on November 26, 2026.** Home and Settings use Jetpack Navigation 3. The Pixel 6a reconnected to the remembered H9Z after the update, changed the displayed zone and background after a boundary edit, and loaded the latest custom color on two cold launches with an edit between them. Removing the transmitter cleared the reading and zone and restored the neutral background. See the [step 2 verification record](docs/step-02-heart-rate-zones.md#verification-record) for the automated checks and hardware procedure.

Settings opens from Home with or without a strap. Its five-row table shows each zone's color and inclusive range. Zone 1 starts at zero. Riders can enter the other four starts manually or calculate them using a known maximum or an adult age. Each edit updates the previous zone's upper limit. Recalculation preserves custom colors. Preferences DataStore saves all four thresholds and five colors together. Home uses the latest saved values, including after relaunch. [Zone settings and default colors](README.md#zone-colors)

## 3. Record a workout

**Implemented and verified with a saved ride on the Pixel 6a.** The on-device database contained the ride and its readings from the Coospo H9Z. The results screen shows mean and maximum BPM, a timeline with signal gaps and pauses, and time in the captured zones. The rider can record with unconfigured zones and review that ride's BPM and timeline.

Emulator tests cover Pause, Resume, Finish, Save, Discard and draft recovery. The Android 17 emulator recorded synthetic readings with Google Maps in front and with the display off. After a signal gap, fresh readings appeared in the same ride. [Step 3 verification record](docs/step-03-record-workout.md#verification-record)

### Background recording design

A `connectedDevice` foreground service owns the recording session, and the sensor repository owns the Bluetooth connection. The workout notification returns the rider to the active session. During Pause the service keeps the connection alive. The recorder stores new readings once the rider taps Resume. Finish stops the service and removes its notification. [Foreground service and recovery](docs/step-03-record-workout.md#4-run-recording-in-a-connected-device-foreground-service)

## 4. Ride with the overlay

**Verified on Android 10 and 17 emulators and tested over Google Maps on the Pixel 6a.** During recording or Pause, the rider can move the pulse window with one finger and pinch with two to resize both the window and digits. The first-use scale is twice the original size, with more space around the digits. The app restores the rider's chosen scale and relative position across workouts and after a cold launch. The foreground service colors the window using the ride's zone snapshot and removes it on Finish. [Step 4 verification record](docs/step-04-workout-overlay.md#verification-record)

## 5. Review a saved ride

**Implemented with milestone 3.** Home opens a separate History screen. It lists each saved ride's date, active duration and mean BPM. Selecting a row opens that ride's Results using its original zone settings. [Step 3 verification record](docs/step-03-record-workout.md#verification-record)

## Design decisions

- **Bluetooth and background execution.** The recorder, workout screen and overlay share a single Heart Rate Service subscription.
- **Workout data.** The wall-clock start time places a ride in History. Elapsed time positions readings within it. The active timer runs through a dropout and freezes during a manual pause. Both interrupt the chart line. Zone totals count observed intervals using the boundaries captured at workout start.
- **Draft recovery.** On relaunch after an interruption, the rider can save or discard the captured portion. Only saved rides appear in History. Each decision is safe to retry.
- **Overlay behavior.** Android and foreground apps can hide the overlay on their own windows. Recording continues through these visibility changes. [Android application overlay windows](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY) · [Apps that hide overlays](https://developer.android.com/reference/android/view/Window#setHideOverlayWindows(boolean))
