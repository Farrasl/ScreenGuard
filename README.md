<h1 align="center">🛡️ ScreenGuard</h1>

<p align="center">
  <strong>Real-Time Shoulder Surfing Prevention for Android using Google ML Kit</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat&logo=android" alt="Platform">
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat&logo=kotlin" alt="Language">
  <img src="https://img.shields.io/badge/Min_SDK-26%20(Android%208.0)-blue?style=flat" alt="Min SDK">
  <img src="https://img.shields.io/badge/License-MIT-green?style=flat" alt="License">
</p>

---

## 📖 Overview
**ScreenGuard** is an automated, real-time privacy security application designed to detect and prevent *shoulder surfing* attacks. Utilizing **Google ML Kit Face Detection** and the **CameraX API**, ScreenGuard actively monitors the front camera's field of view to detect unauthorized observers. 

When a potential threat is identified within the primary attack zone (0.5–2 meters), the system responds instantly by locking the device screen, ensuring completely definitive protection. The entire inference process runs **100% on-device**, requiring no internet connection, cloud processing, or custom model training, thereby guaranteeing user data privacy.

## ✨ Key Features
* **Active Threat Response:** Instantly triggers an OS-level Auto-Lock via Android Device Administrator when a shoulder surfer is detected.
* **Persistent Background Monitoring:** Runs seamlessly in the background as a Foreground Service, protecting your privacy even while using other applications (e.g., Mobile Banking, Chatting).
* **Robust False Positive Mitigation:** Implements a strict 4-condition mathematical filtering logic to ensure reliable threat detection and minimize false alarms.
* **Threat Inspection History:** Locally logs every security event, providing users with the exact timestamp, response speed, and a captured screenshot of the intruder.
* **Hardware Efficient:** Utilizes CameraX's `STRATEGY_KEEP_ONLY_LATEST` to drop obsolete frames, maintaining low latency (74ms - 250ms) and optimizing battery consumption.

## 🧠 How It Works (The 4-Condition Logic)
To distinguish between a genuine shoulder surfing attack and a harmless passerby, ScreenGuard applies a rigorous face filtering pipeline. A device lock is only triggered if **multiple faces** are detected, and the secondary face passes all four of the following conditions:

1. **Facial Structure Validation:** The face must possess valid anatomical landmarks (e.g., nose or mouth).
2. **Head Rotation Threshold (`headEulerAngleY`):** The observer's horizontal head rotation must be `< 36°`, ensuring they are actively looking straight at the screen.
3. **Dynamic Eye-Open Probability:** Adapts the threshold based on ambient lighting (Lux sensor) to maintain high reliability in dim environments without compromising security.
4. **Proximity Validation (`faceAreaRatio`):** Uses a bounding box area ratio (`> 0.005f`) combined with `setMinFaceSize(0.15f)` to ensure detection is strictly focused on the critical 0.5m - 2m attack zone.

> **Note:** The system is inherently spoof-proof against common facial occlusions (e.g., medical masks, sunglasses) as the neural network is capable of inferring the overall head posture and visible features.

## 🛠️ Tech Stack
* **Language:** Kotlin
* **Camera Framework:** CameraX (`ImageAnalysis` use case)
* **Machine Learning:** Google ML Kit Face Detection (Native SDK)
* **Background Execution:** Android Foreground Service (with ongoing notification)
* **Security Action:** Device Policy Manager (Device Administrator API)
* **Local Storage:** SharedPreferences / JSON (for Threat Logs)

## ⚙️ Installation & Setup
### Prerequisites
* Android Studio (Latest Version)
* An Android Device running API Level 26 (Android 8.0) or higher.

### Build Instructions
1. Clone this repository:
   ```bash
   git clone https://github.com/Farrasl/ScreenGuard.git
