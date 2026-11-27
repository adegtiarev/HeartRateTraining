# Working on Heart Rate Training

These instructions apply throughout the repository. Communicate with the maintainer in Russian. Write repository documentation in English and place new development documents in `docs/`. The initial feature set is complete. Use feedback from rides to guide fixes and improvements. Read the [README](README.md) for the rider's workflow and the [implementation record](IMPLEMENTATION_PLAN.md) for completed milestones and verification results.

## Architecture

- Apply Clean Architecture where it keeps workout rules independent of Android and storage details. Follow SOLID, KISS and DRY. Start in the existing Kotlin and Jetpack Compose app module. Introduce another module or abstraction when a feature needs a distinct owner, a replaceable dependency or independently testable behavior. [Google's Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
- Use unidirectional data flow. Screen-level ViewModels expose immutable UI state and accept user actions. Composables collect state with lifecycle awareness, render it and handle Android UI interactions.
- Keep every ViewModel free of `Context`, including application context. Use plain `ViewModel` and keep `Application`, activities, views, resource lookups and permission launchers outside it. Expose a typed state such as “sensor disconnected” and let the UI resolve its localized text.
- Keep Bluetooth, storage and Android service APIs behind the data or platform boundary. A ViewModel may call a repository directly. Introduce a use case when it owns a business rule or coordinates operations. Omit single-method use cases that only forward a call.
- Give the sensor connection and the active workout a lifetime independent of any screen. Share one stream of readings between the recorder, the workout screen and the overlay. Keep zone classification and duration calculations in plain Kotlin so all three agree at zone boundaries.
- Keep editable zone boundaries and colors together in persistent settings. Use the default palette only to initialize those settings. Read the saved configuration on Home and capture it when starting a workout. Use that snapshot for the workout screen, overlay and results.

## Dependencies and localization

- Use current, mutually compatible stable releases. When adding or updating a dependency, check its official release notes and compatibility with the project's toolchain. Pin versions in the [version catalog](gradle/libs.versions.toml) and use the existing Compose BOM for the libraries it manages.
- Provide complete English default resources in `values/` and translations in `values-ru/` and `values-es/`. Include permission explanations, notification text, overlay labels and accessibility descriptions. Use Android plural and locale-aware formatting for durations, dates and units.

## Verification and handoff

- Match verification to the change. Exercise zone boundaries and time gaps with deterministic inputs. Check save, discard and recovery against the database. Validate Bluetooth and overlay behavior on a physical phone with a chest strap and a navigation app.
- Keep credentials, local SDK paths, device identifiers and personal workout captures out of the public repository. Use synthetic readings in examples and tests.
- Update the [README](README.md) when the build instructions or available features change.
