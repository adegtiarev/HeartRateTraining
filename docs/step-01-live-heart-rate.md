# Step 1 — Connect a chest strap and display live heart rate

**Outcome.** A rider puts on a Coospo H9Z, opens the app on a Pixel 6a, selects the strap once and sees live beats per minute (BPM) on Home. On a later launch the app reconnects to the remembered strap when it is nearby. The same flow should work with other straps that expose the standard Bluetooth Low Energy (BLE) Heart Rate Service.

## Starting point

- The work started in the existing `:app` module under `arg.adegtiarev.heartratetraining`, with a Compose template greeting and no Bluetooth declarations. See the [repository instructions](../AGENTS.md) and [product roadmap](../IMPLEMENTATION_PLAN.md).
- The [version catalog](../gradle/libs.versions.toml) and [app build file](../app/build.gradle.kts) already provide Nordic BLE KTX 2.11.0, Coroutines Android and Test 1.11.0, Lifecycle Compose 2.11.1 and Preferences DataStore 1.3.0. Java and Kotlin target 17. The minimum Android API is 29 and the target is 37. Debug and release builds and both template tests passed during dependency setup.
- This milestone delivers live preview in the foreground. The roadmap introduces zone colors and settings in step 2, workout controls, recording and a foreground service in step 3, and the navigation overlay in step 4. A neutral Home screen with a working sensor action is enough for step 1.

## Implementation sequence

### 1. Decode a measurement in plain Kotlin

Start with a decoder and small JVM tests. In a Heart Rate Measurement notification, byte 0 contains flags. Bit 0 selects an unsigned 8-bit BPM value or an unsigned 16-bit little-endian value starting at byte 1. Energy Expended and RR intervals, when present, follow the BPM field. If contact detection is supported and the flags indicate poor or lost contact, clear the live reading. A strap with no contact-detection feature can still report valid BPM. [Bluetooth SIG Heart Rate Service, section 3.1.1](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/HRS_v1.0/out/en/index-en.html)

Use synthetic fixtures such as `00 48` (72 BPM), `01 2C 01` (300 for the 16-bit decoder), and `10 48 00 04` (72 with an RR interval). Test supported contact loss, an empty payload and a truncated BPM field. Malformed data must neither crash the app nor refresh the age of the last valid reading.

### 2. Discover nearby straps with the rider's permission

Implement foreground discovery with Android's `BluetoothLeScanner`. Check BLE availability, permissions and adapter state before scanning. Use the system Bluetooth enable prompt when the radio is off. Keep permission launchers and Settings intents at the activity or Compose boundary. Recheck the outcome when the rider returns. [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)

| Device OS | Manifest and runtime behavior |
| --- | --- |
| API 29–30 | Declare legacy `BLUETOOTH` and `BLUETOOTH_ADMIN` and request `ACCESS_FINE_LOCATION` at runtime for the foreground scan. Cap those declarations at API 30. Handle disabled system location services when they prevent discovery. |
| API 31+ | Declare and request `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` as Nearby devices permissions. The app uses scans only to find a strap, so declare `neverForLocation` on the scan permission. Verify H9Z discovery on the target phone with this flag. |

Give scans a timeout, combine repeated advertisements from the same device into one row, and stop scanning on selection or cancellation. Give the rider a retry action when the scan ends empty. Prefer devices advertising Heart Rate Service (`0x180D`), but allow selection of a nearby device whose advertisement omits the service list. Verify compatibility during service discovery. [Android BLE scanning](https://developer.android.com/develop/connectivity/bluetooth/ble/find-ble-devices)

### 3. Connect, subscribe and keep one source of readings

Use a single sensor repository for the connection. Its Android implementation owns the scanner and Nordic BLE manager. On selection, connect, discover service `0x180D`, find the Heart Rate Measurement characteristic (`0x2A37`) and enable notifications. Handle unsupported devices and subscription failures with a recoverable state. Show “waiting for pulse” until the first valid notification arrives. [Nordic Android BLE Library 2.11.0](https://github.com/nordicsemi/Android-BLE-Library/tree/2.11.0)

Timestamp each valid notification with a monotonic clock on receipt. Repeated values, such as three successive readings of 72, are three fresh samples. Derive the latest BPM for Home from those samples. Clear it immediately on disconnect or reported contact loss. If notifications silently stop, expire it after a named freshness interval and show a dash. Choose the interval against the observed H9Z cadence and cover it with a virtual-time test.

Keep one active connection attempt and subscription across recompositions and rotation. When changing device or disconnecting, cancel scans, retries and timers, release the old connection and ignore its late callbacks. Wire the repository once through constructor injection. The screen-level ViewModel exposes immutable UI state and accepts actions. It holds no `Context`, `BluetoothDevice` or Android permission launcher.

### 4. Remember the strap and reconnect

Store the selected strap's local identifier and optional display name together in Preferences DataStore **after** verifying the service and successfully enabling notifications. A failed selection must preserve the last working strap. Use the name for display only. If Android can no longer match the saved identity, let the rider select the strap again.

| Event | Expected behavior |
| --- | --- |
| First launch | Offer a scan and let the rider choose a strap. |
| Fresh launch with a remembered strap | Once permission and Bluetooth are available, look for that strap and connect automatically. If absent, finish the bounded attempt with a dash and offer Retry or Choose strap. |
| Unexpected signal loss | Clear BPM, show the connection state and attempt a bounded reconnect to the same strap. Resume live display on the next valid sample. |
| Rider taps Disconnect | Release the connection immediately and cancel automatic retries for this visit. Rotation or a late callback must not reconnect it. Keep the saved selection for a manual Connect or the next fresh launch. |

### 5. Replace the template Home screen

Show large BPM digits, the unit and a short connection status. While a fresh reading is unavailable, show a dash. Expose the action appropriate to the state, such as Find strap, Grant permission, Enable Bluetooth, Retry or Disconnect. Show the picker within the existing Compose flow. Collect ViewModel state with `collectAsStateWithLifecycle()`. Put all visible text and accessibility labels in the English default, Russian and Spanish string resources.

## Verification and handoff

- **JVM tests.** Cover the decoder fixtures, equal consecutive readings, expiry and recovery with virtual time. A fake sensor source and fake remembered-device store should exercise first selection, relaunch, failed replacement, disconnect during a retry and late notifications from the previous connection.
- **Compose and emulator checks.** Drive Home with a fake source through permission needed, scanning, waiting, live BPM and disconnect. Check the large reading, available actions and dash. Exercise both permission paths on API 29 or 30 and API 31+ where devices are available.
- **Hardware acceptance.** On a Pixel 6a with a worn H9Z, select the strap on first use and observe fresh BPM. Rotate the phone, relaunch with the strap present, then repeat with it absent. Simulate a dropout, bring the strap back and verify recovery. Tap Disconnect and check that the app stays disconnected until the rider reconnects or starts a fresh visit. Also try Bluetooth off and permission denial followed by recovery. Record the phone's Android version and the outcomes in this document, omitting real device identifiers.

Use the Gradle wrapper from the project root on Windows.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
.\gradlew.bat :app:connectedDebugAndroidTest
```

The second command needs a connected phone or emulator. The hardware timing test runs only when the instrumentation runner receives `wornStrap=true`. It requires a previously selected, worn strap. Add `openMaps=true` to exercise reception while the default maps app is in front.

## Verification record

Verified on November 26, 2026. Debug and release APKs built successfully. All 12 JVM tests passed. Android lint reported no errors. The Pixel instrumentation suite passed with the real-strap test enabled. The emulator ran the synthetic tests.

- JVM tests exercise the decoder, duplicate advertisements, a failed replacement, permission changes, bounded discovery and reconnection, cancellation of an in-flight retry, late notifications, contact loss, and expiry with virtual time.
- The Compose test drives permission explanation, discovery, selection, waiting, live BPM and disconnect using synthetic data. Android 10 (API 29) was checked on an emulator. The installed system image has no BLE radio. Grant and revoke of the legacy location permission were checked through the package manager.
- On a Pixel 6a running Android 17 (API 37), the app discovered the Coospo H9Z, subscribed and displayed live BPM. Denying Nearby devices and then granting access recovered the flow. Rotation preserved live display. A cold launch reconnected automatically. Manual disconnect survived rotation, and tapping Connect restored live BPM.
- With the transmitter removed, Home showed a dash. A cold launch with the strap absent ended the 12-second search with a dash and a retry action. Putting the H9Z back on and tapping Retry restored live BPM without another selection.
- Switching the Pixel's Bluetooth off cleared the reading and exposed the enable action. The action opened the system Bluetooth prompt. Live BPM resumed automatically after Bluetooth was enabled.
- In a sequence of 12 H9Z measurements the median notification interval was 962 ms and the longest was 1,202 ms. Home expires a reading after 5 seconds of silence. The timing check also received fresh samples with Google Maps in front and Home stopped. Only timing and connection states are reported by the hardware test.
