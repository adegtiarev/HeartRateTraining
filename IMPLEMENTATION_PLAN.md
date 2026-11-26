# Implementation plan

The first release should let a cyclist start a ride, switch to a navigator, see live heart rate over the map, and later review the saved ride. Live sensor preview and configurable heart rate zones are implemented in the Compose app. Milestone 3 is next.

## 1. Read the chest strap

Follow the [step 1 implementation plan](docs/step-01-live-heart-rate.md) for the verified dependency baseline, build sequence and hardware acceptance checks.

**Completed and verified on November 26, 2026.** Home receives live notifications from a Coospo H9Z on a Pixel 6a running Android 17. The hardware checks covered first selection, automatic connection on a fresh launch, rotation, manual disconnect, launch with the strap absent, recovery when it returned, Bluetooth off/on and permission denial/recovery. The app also received fresh samples with Google Maps in front. See the [verification record](docs/step-01-live-heart-rate.md#verification-record) for device and automated test coverage.

On first use, discover compatible Bluetooth Low Energy sensors and let the rider choose a strap. Remember the successfully connected strap. On later launches, check for that device and connect automatically when it is available. Subscribe to Heart Rate Measurement and show live BPM in large digits on Home. Handle Bluetooth being off, denied permissions, an unavailable strap and an explicit disconnect.

**Verify on a Pixel 6a with a Coospo H9Z.** Select the strap on first use and see BPM before starting a workout. Relaunch with the strap present and verify automatic connection without another selection. Repeat with it absent and check that no stale BPM appears. A deliberate disconnect releases the strap and leaves it disconnected for the rest of that visit unless the rider chooses to reconnect.

## 2. Define zones

**Implemented and verified on November 26, 2026.** Home and Settings use Jetpack Navigation 3. The Pixel 6a reconnected to the remembered H9Z after the update, changed the displayed zone and background after a boundary edit, and loaded the latest custom color on two cold launches with an edit between them. Removing the transmitter cleared the reading and zone and restored the neutral background. See the [step 2 verification record](docs/step-02-heart-rate-zones.md#verification-record) for the automated checks and hardware procedure.

Settings opens from Home with or without a strap. Its five-row table shows each zone's color and inclusive range. Zone 1 starts at zero. Riders can enter the other four starts manually or calculate them using a known maximum or an adult age. Each edit updates the previous zone's upper limit. Recalculation preserves custom colors. Preferences DataStore saves all four thresholds and five colors together. Home uses the latest saved values, including after relaunch. [Zone settings and default colors](README.md#zone-colors)

**Verify on Home.** Change a boundary and a color in Settings, then return to Home. Confirm that the background follows the saved zone for the live BPM. Restart the app twice, with another edit between launches, and check that the latest values persist. Exercise readings just below, at and above each threshold in both modes.

## 3. Record a workout

Place a large **Start workout** button on Home alongside access to **History** and **Settings**. Starting opens the workout screen with large live BPM, the current zone's background color, a workout timer and **Pause**. Capture the ride's boundaries and colors in a local Room draft and append timestamped readings during recording. Keep recording while the navigator is in front or the screen is off. A dropped signal leaves the timer running. Reconnect when possible and mark the gap.

**Verify pause independently of finish.** Tap **Pause** while the strap is sending measurements. The workout screen still shows live BPM and offers **Resume** and **Finish**. Check that the stored sample count stays fixed as BPM changes. **Resume** appends subsequent readings to the same draft. Repeat with the navigator open, the screen locked and the signal interrupted.

**Verify finish and review.** From a paused workout, tap **Finish**. Recording ends and Results opens on the captured ride with **Save** and **Discard**. Save creates exactly one History entry. Discard removes the draft and its readings. Reopening the app after an interruption offers the same decision for the captured portion.

### Background recording design

The Pixel 6a kept receiving H9Z notifications during a short switch to Google Maps in milestone 1. Android closes the GATT connection if the app process dies. For the rider-initiated workout, use a started foreground service of type `connectedDevice` for the duration of the session. The existing sensor repository remains the sole owner of the GATT connection. [Android background BLE guidance](https://developer.android.com/develop/connectivity/bluetooth/ble/background)

- **Start.** From the visible workout start action, verify Bluetooth access, start a private service with `startForegroundService()`, then promptly call `ServiceCompat.startForeground()` with the connected-device type and the workout notification. Declare `FOREGROUND_SERVICE` and, for API 34+, `FOREGROUND_SERVICE_CONNECTED_DEVICE`. Granted `BLUETOOTH_CONNECT` satisfies the modern Bluetooth prerequisite. Surface a recoverable error if service startup fails. [Service launch rules](https://developer.android.com/develop/background-work/services/fgs/launch) · [Connected-device permissions](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device)
- **During a ride.** Give the service ownership of the recording session across activity destruction, app switches and screen lock. Feed each timestamped sample to the Room recorder before deriving the latest value for the UI. Keep the connection and notification during Pause, suspend writes until Resume, and use the same draft. Reconnect to the known strap with bounded attempts and backoff after a dropout. Keep device selection in the visible UI.
- **Notification.** Use a low-importance workout channel and a tap action that returns to the active workout. On API 33+, request `POST_NOTIFICATIONS` in context. If denied, the service still supplies its mandatory notification and Android exposes the active service in Task Manager. [Notification permission behavior](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- **Finish and recover.** On Finish, persist the stopped draft, stop the service and remove the workout notification before opening Results. Commit readings throughout the ride so a system or user stop leaves a recoverable draft. Android can stop the whole process without a cleanup callback. At the next launch offer Save or Discard. [User-initiated stops](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)

**Acceptance on the Pixel.** Run a sustained ride with Maps in front and the screen locked for part of it. Check persisted timestamps, gaps, pause/resume and elapsed duration. Repeat with notification permission denied, interrupt the strap signal, and stop the app from Android's active-apps list. Verify draft recovery and that Finish removes the notification.

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

- **Bluetooth and background execution.** Use a single standard Heart Rate Service subscription across the recorder, workout screen and overlay. The [background recording design](#background-recording-design) describes service ownership and recovery in milestone 3.
- **Workout data.** Use a wall-clock start time to place a ride in history and elapsed time to position readings within it. A dropout counts toward active workout duration. A manual pause does not. Both interrupt the chart line and are excluded from zone totals. Calculate time in zone from observed intervals using the boundaries captured at workout start.
- **Draft recovery.** Keep active and stopped drafts out of saved history. On relaunch after an interruption, offer to keep the captured portion or discard it. Saving and discarding must each be safe to retry.
- **Overlay behavior.** Use an Android application overlay so the same window works across navigators and other foreground apps on supported phones. Android can hide it over system UI, and an app can hide overlays on its own windows. Keep recording through these visibility changes. [Android application overlay windows](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY) · [Apps that hide overlays](https://developer.android.com/reference/android/view/Window#setHideOverlayWindows(boolean))

## Questions to resolve

| When | Decision |
| --- | --- |
| When evaluating calories | Review evidence for an estimator suitable for cycling, the profile fields it needs and the effect of gaps. Include a clearly labeled estimate only if the method and available data support one. Keep this independent of the first working ride. |
| Before public distribution | Choose the license for the source repository. |
