# 📷 GPS Camera Pro (Research Edition)

**Version 3.0.0**

Android Studio project for **GPS Camera** (`com.gatha.gpscamera`).

GPS Camera Pro is a professional-grade spatial documentation and telemetry instrument engineered specifically for institutional fieldwork, ethnographic documentation, demographic analysis, and cultural heritage conservation.

Standard commercial camera applications are insufficient for rigorous academic data collection, requiring researchers to spend countless hours manually transcribing coordinates and field notes. GPS Camera Pro bridges the gap between visual capture and quantitative/qualitative data analysis by completely eliminating manual transcription bottlenecks. It captures high-accuracy location data, hardware telemetry, and qualitative audio memos, structuring them instantly for advanced analytical tools like QGIS, jamovi, SPSS, and R.

---

## 🏛️ Institutional Use Cases

* **Ethnographic & Demographic Surveys:** Automatically log household coordinates, elevation, and custom ward identifiers (e.g., "Project EHSAS - Ward 97") directly into a CSV file while capturing demographic subject photos.
* **Manuscript & Artifact Archiving:** Utilize the dual-axis accelerometer leveler and 3x3 grid overlay to capture distortion-free, perfectly aligned images of fragile documents or museum artifacts.
* **Site & Architectural Documentation:** Record the exact magnetic azimuth (compass bearing) of structural ruins, heritage sites, or sacred geography to document precise spatial orientation.
* **Rapid Field Observations:** Hold the shutter to record synchronized audio memos, allowing researchers to capture oral context or rapid visual observations without looking down at a screen to type.

---

## 🔒 Zero Data Collection Privacy Policy

**This application respects researcher and participant privacy absolutely.**

* **100% Local Storage:** We do not collect, transmit, upload, or monetize your data.
* **No Cloud Telemetry:** All photographs, audio files, and spatial mapping logs are processed and stored exclusively on your local device hardware.
* **Zero Accounts:** No user logins, no third-party tracking SDKs, and no analytics.

---

## 🚀 Core Research Features

### 📊 Automated Spatial Mapping & Telemetry Logging

* **Continuous CSV Field Logging:** Silently appends every capture to a master `field_log.csv` file in your device's Downloads folder. Logs the filename, precise GPS coordinates, altitude, accuracy, compass bearing, and custom notes. The file is formatted for immediate import into jamovi, SPSS, or R for statistical evaluation.
* **Automated KML / GeoJSON Generation:** Simultaneously builds a `field_log.kml` mapping file. Researchers can drag and drop this single file into QGIS or Google Earth to instantly visualize an entire site survey, complete with embedded metadata pins.
* **Deep EXIF Metadata Injection:** Custom Project or Site Notes are permanently burned into the JPEG's `UserComment` and `ImageDescription` EXIF tags. This makes your visual data natively text-searchable inside any desktop operating system's file explorer.

### 🛡️ Precision & Data Integrity

* **Anti-Spoofing Satellite Timestamps:** Bypasses the device's internal clock entirely, preventing manual time manipulation. Timestamps are pulled directly from secure atomic GPS satellites (`location.time`) to ensure unimpeachable data compliance for official government reporting.
* **Dual-Axis Leveler:** A highly responsive on-screen accelerometer crosshair ensures perfect pitch and roll alignment, eliminating perspective distortion during archival photography.
* **Compass Azimuth Bearing:** Real-time magnetic bearing (e.g., 245° WSW) is displayed on the visual stamp and injected into the `TAG_GPS_IMG_DIRECTION` EXIF header.

### 🎙️ Qualitative Documentation & Routing

* **Synchronized Audio Field Notes:** Hold the shutter button down to record spoken qualitative observations. The app saves an `.m4a` audio file paired perfectly to the photograph with the exact same base filename and timestamp.
* **Project-Based Folder Routing:** Type a custom identifier (e.g., "Banswara_Fieldwork") and the app bypasses the default gallery, routing all original photos, stamped photos, and audio files to a dedicated, automatically generated project folder.
* **Native Swipeable Gallery:** Fluid, full-screen, swipeable gallery integration allows for instant field review, deletion, and sharing capabilities directly within the app.

---

## ⚙️ Stamp Customization & UI

* **Extensive Formatting:** Toggle coordinates, address, date/time, accuracy, and altitude independently based on the specific requirements of your publication or archive.
* **Layout & Positioning:** Select Classic, Compact, or Elegant stamp styles and place them in any of the six corners/edges of the frame to avoid obscuring vital visual data.
* **Persistent Settings:** Stamp customization and Pro Field Research toggles persist locally across app restarts via `SharedPreferences`.

---

## 💻 Technical Requirements

* **IDE:** Android Studio with Android SDK 35 installed
* **Environment:** JDK 17
* **Minimum OS:** Android 10 (API 29) or newer
* **Hardware:** A physical Android device is strongly recommended for testing hardware sensors (accelerometer, magnetometer) and CameraX integration.

## 🛠️ Build and Deployment

1. Extract the project folder or clone the repository to your local machine.
2. Open the `GPSCamera` directory in Android Studio.
3. Allow Gradle Sync to finish downloading dependencies.
4. Connect a physical Android device and deploy.
5. *Note: You must grant Camera, Precise Location, and Microphone permissions on the first launch to enable the advanced research and audio features.*

---

## 📦 GitHub Hosting and Updates

See [`GITHUB_HOSTING_AND_UPDATES.md`](https://www.google.com/search?q=GITHUB_HOSTING_AND_UPDATES.md) for repository setup, GitHub Actions CI/release publishing, signing-key secrets, and version-tag instructions. The in-app Settings menu includes a GitHub Release update checker. Ensure you replace the repository owner/name constants in `GitHubUpdateChecker.kt` before publishing your fork.

**Developed by Gatha Media**
