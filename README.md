# GPS Camera by Gatha

Android Studio project for **GPS Camera** (`com.gatha.gpscamera`).

## Requirements
- Android Studio with Android SDK 35 installed
- JDK 17
- Android 10 (API 29) or newer

## Open and run
1. Extract this folder.
2. Open the `GPSCamera` folder in Android Studio.
3. Allow Gradle Sync to finish.
4. Connect an Android phone (Android 10+) and press Run.

## Implemented
- CameraX live preview, front/rear camera switching, flash control
- Capture original and GPS-stamped JPEGs separately in `Pictures/GPS Camera/Original` and `Pictures/GPS Camera/Stamped`
- GPS coordinates, reverse-geocoded address, capture date/time, accuracy and altitude when available
- EXIF date/time and GPS metadata when the device supplies valid location data
- Premium dark minimal camera interface
- Capture can continue if location permission is denied; unavailable GPS fields are labelled as unavailable rather than fabricated
- Flash and camera-switch controls are disabled during capture
- Last-photo shortcut opens the stamped output

## Phase 2 notes
The current phase focuses on capture experience, GPS-state clarity, safer capture behavior, and previewing the stamped result. Device-specific camera behavior and build success should still be verified in Android Studio and on the target phone after each update.


## Phase 3 — Gallery & Photo Management

- In-app gallery lists images stored under `Pictures/GPS Camera/`.
- Filter between All, Stamped, and Original photos.
- Tap a thumbnail to inspect filename, capture date, file size, and folder.
- Share an image using Android's share sheet.
- Delete images after explicit confirmation.
- Gallery queries only the app's GPS Camera folder; it does not scan unrelated photo folders.

Build and test on a real Android device. The project ZIP is archive-validated, but an Android Gradle build has not been run in this environment.


## Phase 4 — Stamp Customization

- Toggle coordinates, address, date/time, accuracy, and altitude independently.
- Select Classic, Compact, or Elegant stamp styles.
- Choose Top or Bottom placement.
- Adjust stamp text size and panel opacity.
- Settings dialog provides a reset-to-defaults action.
- Camera overlay preview reflects selected fields, style label, position, size, and opacity; settings apply to the next captured photo.
- Defaults keep all GPS stamp fields enabled.

Archive validation passed. Android Gradle compilation and physical-device testing have not been performed in this environment; please build and test before relying on the generated APK.


## Phase 5 — Privacy, Reliability & Release Readiness

- Stamp customization now persists locally across app restarts (SharedPreferences); no account or cloud sync is used.
- Camera permission-denied state provides an in-app recovery action instead of leaving the preview as a dead end.
- Stamp preference values are validated/clamped when loaded and saved to avoid invalid UI values.
- Bitmap resources used to render stamped photos are recycled in a `finally` block, including failure paths, reducing memory pressure on repeated captures.
- Version metadata updated to 1.4.0 / versionCode 5.
- Permission and local-storage behavior is described in the settings UI. The app does not request broad all-files access.

### Release validation checklist

Before distributing, verify on a physical Android 10+ device: fresh install and upgrade; camera permission denied then granted; location permission denied; approximate-only location; location unavailable; repeated captures; low-storage failure; app process restart with stamp settings; EXIF GPS/date tags for valid-location captures; gallery share/delete; and Android 13+ photo/gallery behavior. Confirm location metadata is absent when no valid location is available.

**Validation status:** ZIP archive integrity can be checked here, but Android Gradle compilation and device tests have not been run in this environment. Do not treat this ZIP as a verified release APK.


## Phase 6 — Advanced Photo Experience (1.5.0)

- Opens gallery selections in a full-screen photo viewer.
- Provides previous/next navigation through the current gallery list.
- Adds a show/hide photo details panel with filename, capture time, size, folder, and a GPS metadata note.
- Uses the detected content MIME type when sharing, with a JPEG fallback.
- Keeps delete behind a confirmation dialog and refreshes the gallery after deletion.

### Verification status
The ZIP archive can be integrity-checked, but this project has not been independently compiled in Android Studio or tested on a physical device as part of this package generation. Please run Gradle Sync and Build in Android Studio before installing.

## Version 1.6.0 — Native gallery integration

- Removed the app-owned gallery grid and in-app photo viewer.
- Original images are saved to `Pictures/GPS Camera/Original/` and stamped images to `Pictures/GPS Camera/Stamped/` through Android `MediaStore`, making them discoverable to compatible phone gallery apps.
- The photo-library button opens the latest stamped photo in Android's standard image-view intent / chooser. It also finds the latest stamped photo after restarting the app.
- The capture flow remains inside GPS Camera; after a capture, the latest stamped image can be opened with one tap.
- Sharing/viewing is delegated to installed Android apps; available choices vary by device.
- No new storage permission is required for the app's own MediaStore images.

### Manual verification checklist
1. Build and install on an Android 10+ device.
2. Capture a photo with location permission enabled and confirm both original and stamped images appear in the phone's gallery.
3. Deny location permission and capture again; confirm the photo still saves and unavailable GPS fields are not fabricated.
4. Tap the photo-library button and confirm Android offers a compatible native viewer.
5. Close and relaunch GPS Camera, then tap the photo-library button to confirm it finds the latest stamped image.
6. Confirm originals and stamped photos remain in their separate folders.


## Native gallery button behavior
The gallery button first launches the phone's gallery application using Android's `CATEGORY_APP_GALLERY` intent. GPS Camera does not implement its own gallery or photo viewer. On devices that do not expose a gallery app through this intent, the app falls back to opening the latest stamped image with a compatible native image viewer.


## Version 1.6.2 — Full-page Settings

- Settings opens as a dedicated full-screen page instead of a popup dialog.
- Added organized Photo Stamp, Stamp Appearance, Location, and About sections.
- Stamp preferences continue to save automatically on-device.
- Added About information describing the app, local photo storage, and privacy approach.
- System Back and the page back button return to the camera.


## Version 1.7.0 — Stamp placement and camera improvements

- Added six stamp positions: top-left, top-middle, top-right, bottom-left, bottom-middle, and bottom-right.
- Changed the printed GPS stamp from a full-width strip to a compact rounded card.
- Settings persist locally and apply to subsequent captured photos.
- On each fresh app launch, the app requests precise location if it has not already been granted; existing permission is not requested again. If Android suppresses repeat prompts after repeated denials, the user must enable permission in Android App Settings.
- Location capture requests high accuracy when precise location permission is available. Location fields are never fabricated when a fix is unavailable.
- CameraX remains configured for minimize-latency capture and uses JPEG quality 100; the actual maximum resolution is device/camera dependent.

Build and test this project in Android Studio on a physical device before release.


## Update 1.8.0 — Compact stamp, branding and permission settings

- Stamp cards now size to their content, with a maximum width cap to prevent full-width strips.
- Added a small location-pin mark and corrected the app/stamp branding to **Gatha Media**.
- Settings includes direct shortcuts to Android app-permission settings and device location settings.
- The app requests camera and location permissions when needed; Android controls the final permission prompt and may direct users to Settings after repeated denials.
- APK installation permission is managed by Android for the app used to install the APK; GPS Camera does not request unrelated install permissions.

Build and test in Android Studio before distributing the APK.

## GitHub hosting and updates
See [`GITHUB_HOSTING_AND_UPDATES.md`](GITHUB_HOSTING_AND_UPDATES.md) for repository setup, GitHub Actions CI/release publishing, signing-key secrets, and version-tag instructions. Settings includes a GitHub Release update check. Replace the repository owner/name constants in `GitHubUpdateChecker.kt` before publishing.
