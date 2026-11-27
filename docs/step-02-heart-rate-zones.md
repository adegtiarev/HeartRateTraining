# Step 2 — Configure five heart rate zones and display them on Home

**Outcome.** A rider can open Settings even without a strap, set four BPM thresholds manually or calculate them, choose a color for each zone and save. Changing a saved boundary reclassifies the latest fresh BPM and updates the Home background. Recording, the workout service and the overlay belong to later milestones.

## Starting point

At the end of step 1, the `:app` module had Compose, Lifecycle, Preferences DataStore and coroutine test support. `HeartRateApplication` owned the sensor repository. `HomeViewModel` exposed sensor state directly. `HomeScreen` drew a Scaffold and `PulseDisplay` drew its own opaque Surface. Both surfaces needed to change to display a full-screen zone color. The existing sensor visit, permission actions, fresh-sample timeout and remembered strap carry across navigation. [Step 1 verification](step-01-live-heart-rate.md#verification-record)

## Implementation sequence

### 1. Specify the classifier with plain Kotlin fixtures

Use four strictly ordered thresholds marking the first BPM of zones 2 through 5. Zone 1 starts at zero. Each threshold belongs to the higher zone. Zone 5 extends upwards without a limit. Label the four editor fields by the zone they start and derive adjacent range previews from the same thresholds. For thresholds 108, 126, 144 and 162, display 0–107, 108–125, 126–143, 144–161 and 162+ BPM.

Require positive integer boundaries in strictly increasing order within the unsigned 16-bit measurement domain. Reject empty, fractional, overflowing, reversed or duplicate inputs. Keep the draft editable and associate each error with its field. Store a stable zone number and color value independent of Android types, theme and translated labels. Later the recorder can copy this complete value at workout start.

**First use.** Until the rider saves a configuration, show fresh BPM on a neutral Home with a Settings action. Once configured, every fresh nonnegative reading has a zone, including zero. An absent or expired sample shows a dash and the existing sensor status.

### 2. Calculate suggested boundaries

Offer a known maximum heart rate as a direct input. For adults who supply their age, use the Tanaka estimate, `208 − 0.7 × age`. The original candidate was `220 − age`. The Tanaka equation was derived and validated using healthy adult data. Both estimate a population average and individual prediction errors can be substantial. Label the result as an estimate. Manual entry remains available without profile information. A heart-rate-reserve method would also require resting BPM and a different percentage scale. The implementation uses percent of maximum. [Tanaka study](https://pubmed.ncbi.nlm.nih.gov/11153730/) · [Independent validation](https://pubmed.ncbi.nlm.nih.gov/23913510/)

Use the four internal transitions of the conventional percentage scheme: 60, 70, 80 and 90 percent of the chosen maximum. The app extends the outer zones down to zero and upwards without a cap. They are editable starting values. Calculate directly from the unrounded maximum, rounding each final boundary to the nearest whole BPM with halves rounded up. Reuse manual validation on the result. Recalculate only on an explicit action, preview the changes in the current draft and retain all five colors. Saving the resolved boundaries makes subsequent launches deterministic. [Polar five-zone definition](https://support.polar.com/e_manuals/polar-360/polar-360-flow-user-manual-english/heart-rate-zones.htm)

| Synthetic input | Expected boundaries in BPM |
| --- | --- |
| Known maximum 180 | 108, 126, 144, 162 |
| Age 40, estimated maximum 180 | 108, 126, 144, 162 |
| Age 35, estimated maximum 183.5 | 110, 128, 147, 165 |

### 3. Persist the entire configuration

Create one application-scoped zone settings repository backed by the existing Preferences DataStore library. Give zones a separate preferences file from the remembered Bluetooth device. Read a single immutable settings value as a Flow. Write the schema version, four thresholds and five colors together in one edit. Initialize the draft palette once in zone order (gray, green, blue, orange, red). Read custom colors back from storage in both manual and calculated workflows.

Distinguish loading, first use and read failure. Opening Settings or rotating the screen must never overwrite saved data. Validate a complete stored value before using it. For incomplete or unreadable settings keep live BPM available, show a recoverable settings status, and allow retry. On write failure retain the draft and offer Save again. Verify installation over step 1 with a remembered H9Z and no zone preferences.

### 4. Build Settings and connect Home

Add Settings as a destination within the existing activity. Preserve the active route and unsaved field values on rotation. A screen-level ViewModel owns the draft, validation and save state. Keep Android resources and color conversion in the UI. Present each zone's number, inclusive BPM range and color preview together. Offer selectable swatches and a validated six-digit RGB input so arbitrary colors, including black and white, can be selected. Save returns to Home after persistence succeeds. Cancel discards the draft. Back with unsaved changes offers discard or continue editing.

Home and Settings are typed Navigation 3 destinations. The single activity hosts a `NavDisplay` with a saveable back stack. The Settings entry owns its editor ViewModel. Leaving that entry releases the draft. The sensor repository belongs to the application throughout navigation. Navigation 3 runtime and UI use stable 1.2.2, with Lifecycle integration at the project's 2.11.1 and the serialization plugin aligned with Kotlin 2.4.21. [Navigation 3 release notes](https://developer.android.com/jetpack/androidx/releases/navigation3#1.2.2) · [Navigation state and entry-scoped ViewModels](https://developer.android.com/guide/navigation/navigation-3/save-state)

Combine saved settings and sensor state in the Home ViewModel. Reclassify when either changes. Render the full Home background, show a visible zone number alongside BPM and choose readable foreground text for the saved color. Keep the reading, connection status and actions distinguishable with a black, white or repeated zone color. Opening or closing Settings must preserve an existing connection and a deliberate disconnect. Supply English, Russian and Spanish labels, validation messages, unit formatting and accessible descriptions of the reading, zone and selected color.

## Verification and handoff

- **JVM.** Test just below, exactly at and just above all four thresholds for manually entered and calculated configurations. With maximum 180, verify 0 and 107 → Z1, 108 → Z2, 161 → Z4, and 162, 172, 180 and 181 → Z5. Cover missing samples, malformed drafts, rounding, recalculation preserving custom colors and Home reclassification with constant BPM.
- **Persistence.** Use a temporary real DataStore. Save set A, reopen, change a threshold and color to set B, reopen twice and compare every field. Exercise missing keys, invalid values, failed saves and cancellation. Confirm the saved strap survives zone changes.
- **Compose.** Drive Home with a fake sensor. Save a boundary across the current BPM, return to Home and verify both zone and background. Cancel a second edit and verify the previous result. Exercise a black zone, signal expiry, return of fresh data, rotation with an unfinished draft, large text, TalkBack descriptions and all three languages.
- **Pixel 6a + H9Z.** Connect once, note the current BPM locally and move a boundary around it in Settings. Confirm live updates continue on return. Change the active color, cold-launch twice with a different edit between launches, then remove the strap and verify the dash. Use synthetic samples for precise threshold checks.
- **Build and close.** Run `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease` and, on a connected device, `.\gradlew.bat :app:connectedDebugAndroidTest`. Record automated and physical results separately here. Update milestone 2 and the README feature list after acceptance.

## Verification record

Verified on November 26, 2026.

- **JVM and build.** All 19 JVM tests passed. The zone cases cover thresholds on both sides and exactly at equality, zero versus a missing reading, the largest sensor value, invalid and overflowing drafts, exact rounding and custom colors after recalculation. Editor tests cover cancellation, repeated Save during a pending write and retry after a failed save. Debug and release APKs built. Android lint reported zero errors and seven unused template color resources.
- **Android 10 emulator.** The connected suite passed ten tests. The two opt-in physical-strap checks were skipped on the emulator. A temporary real DataStore verified full configuration equality across reopens, invalid keys and types, an unreadable file and retry after a disk write failure. Compose tests held a synthetic reading at 130 BPM, moved the start of zone 3 from 126 to 135 and checked the actual background pixels and accessible zone description. They also exercised Cancel, signal expiry and recovery, manual disconnect, black and white backgrounds, English, Russian and Spanish, enlarged text and activity recreation with an unfinished draft.
- **Pixel 6a, Android 17, Coospo H9Z.** Updating the step 1 installation preserved the remembered strap. Editing a boundary across the current live reading changed both the zone number and background. Fresh notifications continued after returning from Settings. The first custom color survived a cold launch. A second edit survived the next cold launch. Removing the transmitter showed the missing-reading description and a neutral background with the zone label cleared. All ten autonomous Android tests also passed on the Pixel. After the checks, the phone was returned to its original unconfigured zone state with its remembered strap intact.
- **Zone table update.** All eleven autonomous Android tests passed on both devices after the editor redesign. The new test changed zone 2's start from 120 to 118 and verified that zone 1's end moved from 119 to 117. It also checked the open end of zone 5. On Android 10 at 200% system font size, a manual keyboard check confirmed that the focused field and its full value stayed visible. On the Pixel the five rows fit together at its usual font size. Opening a color showed its palette and RGB field. The demonstration thresholds were discarded.

For an opt-in hardware rerun on a test phone, use the `LiveZoneHardwareTest` instrumentation class with `zoneStage` set to `configure`, `editAfterRestart` and `verifyAfterRestart` in order. These stages write test thresholds and colors through Settings. Start a fresh app process between stages. After the last stage, leave Home open and remove the transmitter to check signal loss.
