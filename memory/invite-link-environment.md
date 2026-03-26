# Invite Link Environment

- Primary Arise-under-test device: `Medium_Phone_API_36.1` AVD, `adb` serial `emulator-5554`.
- Primary official-Briar peer/baseline device: `Pixel_7` AVD, `adb` serial `emulator-5556`.
- As of 2026-03-19, both attached emulators have the official Briar package installed: `org.briarproject.briar.android`.
- The intended baseline is device-to-device validation: run Arise on `emulator-5554` and compare against the official Briar peer on `emulator-5556`.
- Same-device Briar/Arise invite experiments are not yet a trusted baseline; use them only as diagnostics unless separately validated.
