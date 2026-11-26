# Implementation plan

The first release should let a cyclist start a ride, switch to a navigator, see live heart rate over the map, and later review the saved ride. The starting point is the existing Compose app. All milestones below are pending.

## 1. Read the chest strap

Discover Bluetooth Low Energy sensors, let the rider select a strap, subscribe to Heart Rate Measurement and display live BPM. Handle Bluetooth being off, denied permissions, a lost signal and an explicit disconnect.

**Verify on a Pixel 6a with a Coospo H9Z.** Connect and see live BPM before starting a workout. Move the strap out of range and check that the stale reading clears. Reconnect, then deliberately disconnect and check that the app releases the strap. Discover compatible straps by their service and capabilities so other models can use the same flow.

## 2. Define zones

Add editable BPM boundaries and an automatic mode for the five zones. Use the rider's [zone colors](README.md#zone-colors) consistently on the workout screen, overlay and timeline. Compare ways to estimate maximum heart rate and derive zones, starting with the age-based candidate in the README. Document the chosen inputs, rounding and boundary rules.

**Verify the boundaries.** Check values just below, at and above each threshold for both modes. Invalid ranges can be corrected, and valid settings survive a restart.

## 3. Record a workout

Support start, manual pause, resume and stop. Write timestamped readings and the ride's zone definitions to a draft in a local Room database. Keep the session active while the navigator is in front or the screen is off. A dropped signal leaves the timer running. Reconnect when possible and mark the gap. Only the rider can pause or resume.

**Verify a complete session.** Switch apps, lock and unlock the phone, interrupt the signal, and pause and resume manually. Stopping ends recording and prompts for Save or Discard. Save creates one history entry. Discard removes the draft and its readings. Reopening the app after an interruption offers the same decision for the captured portion.

## 4. Ride with the overlay

Let the rider show or hide a compact window during a workout. Give the entire background the current zone color so the rider can recognize it at a glance. BPM is the only visible number. Include the zone in the accessibility description. Request overlay access when the rider first enables it.

**Verify across apps.** On the Pixel 6a, switch between a navigator and another app with the overlay open. Check all five colors on light and dark maps. Interrupt the signal in gray zone 1 and check that the background turns black and a dash replaces the BPM. Verify map interaction outside the window, permission denial and revocation. Hiding the window keeps the recording active. Stopping the workout removes the window.

## 5. Review a saved ride

List saved workouts and show a heart rate timeline, the zones reached and time in each zone. Distinguish a manual pause from an interval when the sensor stopped reporting.

**Verify using a known ride.** Use irregularly spaced readings, a zone change, a pause and a dropout. Check the time in each zone, gaps on the chart and the total duration. Reopen the ride after restarting the app and after editing today's zones. Its results must still match the original ride.

## 6. Validate the first release

Exercise the complete ride in English, Russian and Spanish. Cover the minimum supported Android version and a sustained recording on the Pixel 6a with the navigator open. The GitHub deliverables are the source code and instructions for building it.

**Verify the release.** Build from a clean checkout. Complete a ride, save it and reopen it. Repeat and discard it. Check the workout screen, notifications, overlay and results in all three languages.

## Decisions that affect several milestones

- **Bluetooth and background execution.** Start with one strap exposing the standard Heart Rate Service. Verify the rider's hardware in milestone 1. As an early feasibility check, try receiving measurements with the navigator open. For long recordings, plan a user-started foreground service of type `connectedDevice` with a workout notification. Confirm scan, connect and foreground service permissions for the supported Android versions. [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions) · [Background BLE communication](https://developer.android.com/develop/connectivity/bluetooth/ble/background)
- **Workout data.** Use a wall-clock start time to place a ride in history and elapsed time to position readings within it. A dropout counts toward active workout duration. A manual pause does not. Both interrupt the chart line and are excluded from zone totals. Calculate time in zone from observed intervals using the boundaries captured at workout start.
- **Draft recovery.** Keep active and stopped drafts out of saved history. On relaunch after an interruption, offer to keep the captured portion or discard it. Saving and discarding must each be safe to retry.
- **Overlay behavior.** Use an Android application overlay so the same window works across navigators and other foreground apps on supported phones. Android can hide it over system UI, and an app can hide overlays on its own windows. Keep recording through these visibility changes. [Android application overlay windows](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY) · [Apps that hide overlays](https://developer.android.com/reference/android/view/Window#setHideOverlayWindows(boolean))

## Questions to resolve

| When | Decision |
| --- | --- |
| During zone implementation | Choose the automatic thresholds for the five zones, how to handle values outside the configured range, and which inputs improve the estimate enough to ask for them. |
| When evaluating calories | Review evidence for an estimator suitable for cycling, the profile fields it needs and the effect of gaps. Include a clearly labeled estimate only if the method and available data support one. Keep this independent of the first working ride. |
| Before public distribution | Choose the license for the source repository. |
