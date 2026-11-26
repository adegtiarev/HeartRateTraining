# Implementation plan

The first release should let a cyclist start a ride, switch to a navigator, see live heart rate over the map, and later review the saved ride. The starting point is the existing Compose app. All milestones below are pending.

## 1. Read the chest strap

On first use, discover compatible Bluetooth Low Energy sensors and let the rider choose a strap. Remember the successfully connected strap. On later launches, check for that device and connect automatically when it is available. Subscribe to Heart Rate Measurement and show live BPM in large digits on Home. Handle Bluetooth being off, denied permissions, an unavailable strap and an explicit disconnect.

**Verify on a Pixel 6a with a Coospo H9Z.** Select the strap on first use and see BPM before starting a workout. Relaunch with the strap present and verify automatic connection without another selection. Repeat with it absent and check that no stale BPM appears. A deliberate disconnect releases the strap and leaves it disconnected for the rest of that visit unless the rider chooses to reconnect.

## 2. Define zones

Add a Settings screen accessible from Home. Let riders edit the BPM boundaries and color of each zone, starting with the five [default zone colors](README.md#zone-colors). Persist the configuration locally, in settings or the database, and load it on launch. Add an automatic boundary calculation after comparing methods, starting with the age-based candidate in the README. Preserve custom colors when recalculating boundaries.

**Verify on Home.** Change a boundary and a color in Settings, then return to Home. Confirm that the background follows the saved zone for the live BPM. Restart the app twice, with another edit between launches, and check that the latest values persist. Exercise readings just below, at and above each threshold in both modes.

## 3. Record a workout

Place a large **Start workout** button on Home alongside access to **History** and **Settings**. Starting opens the workout screen with large live BPM, the current zone's background color, a workout timer and **Pause**. Capture the ride's boundaries and colors in a local Room draft and append timestamped readings during recording. Keep recording while the navigator is in front or the screen is off. A dropped signal leaves the timer running. Reconnect when possible and mark the gap.

**Verify pause independently of finish.** Tap **Pause** while the strap is sending measurements. The workout screen still shows live BPM and offers **Resume** and **Finish**. Check that the stored sample count stays fixed as BPM changes. **Resume** appends subsequent readings to the same draft. Repeat with the navigator open, the screen locked and the signal interrupted.

**Verify finish and review.** From a paused workout, tap **Finish**. Recording ends and Results opens on the captured ride with **Save** and **Discard**. Save creates exactly one History entry. Discard removes the draft and its readings. Reopening the app after an interruption offers the same decision for the captured portion.

## 4. Ride with the overlay

Let the rider show or hide a compact window during a workout. Give the entire background the current zone color so the rider can recognize it at a glance. BPM is the only visible number. Include the zone in the accessibility description. Request overlay access when the rider first enables it.

**Verify across apps.** On the Pixel 6a, switch between a navigator and another app with the overlay open. Check a custom palette on light and dark maps, including a black zone. Interrupt the signal and check that a dash replaces the BPM even when the background was already black. Verify map interaction outside the window, permission denial and revocation. Hiding the window keeps the recording active. Finishing the workout removes the window.

## 5. Review a saved ride

Open the saved workout list from **History** on Home. Selecting a ride shows the same timeline, zones and time in each zone that the rider saw on Results after finishing. Distinguish a manual pause from an interval when the sensor stopped reporting.

**Verify using a known ride.** Use irregularly spaced readings, a zone change, a pause and a dropout. Check zone durations, chart gaps and total duration. Edit a boundary and a color, restart the app and reopen the saved ride. Its timeline and zone totals should still reflect the original configuration. Start another ride and verify it uses the updated settings.

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
