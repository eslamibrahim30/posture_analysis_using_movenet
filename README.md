# Posture Analyzer

**Posture Analyzer** is a lightweight Android application that uses Google's MoveNet (Lightning model) for real-time human pose estimation to monitor and correct sitting posture. The app processes live camera feed, detects body keypoints, calculates key angles (neck, torso, shoulders, etc.), visualizes the pose skeleton and angles, and provides audio feedback for poor posture.

## Features

- **Real-time Posture Monitoring**: Uses the device camera to continuously analyze sitting posture via MoveNet.
- **Pose Visualization**: Overlays detected keypoints, skeleton connections, and angle measurements on the live feed.
- **Posture Assessment**: Evaluates neck inclination, torso inclination, and other angles to classify posture as "good" or "bad".
- **Audio Alerts**: Plays a warning sound if bad posture is detected for a sustained period.
- **User Guidance**: Onboarding dialog with instructions and a reference image for proper camera setup (side view for sitting posture).
- **MoveNet Integration**: Utilizes TensorFlow Lite models (`lightning.tflite` and `thunder.tflite` available).

## How It Works

The app:
1. Captures live preview via `TextureView` and Camera2 API.
2. Preprocesses frames and runs inference with the MoveNet Lightning model.
3. Extracts 17 keypoints and draws them + skeleton lines.
4. Computes angles between keypoints (e.g., neck, torso, elbows, knees).
5. Determines posture quality based on thresholds (primarily neck ~75-95° and torso ~85-95° for good sitting).
6. Displays results and triggers alerts as needed.

**Note**: Optimized for side-view sitting posture monitoring. Ensure good lighting and full upper-body visibility.

## Tech Stack

- **Language**: Kotlin
- **UI**: Android XML layouts + Camera2 API
- **ML**: TensorFlow Lite (MoveNet Lightning)
- **Build System**: Gradle (Kotlin DSL)

## Setup & Installation

### Prerequisites
- Android Studio (latest stable recommended)
- Android device or emulator with camera support (API level 21+)
- Camera permissions

### Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/eslamibrahim30/posture_analysis_using_movenet.git
   cd posture_analysis_using_movenet
   ```

2. Open the project in Android Studio.

3. Sync Gradle and build the project.

4. Run on a physical device (recommended for camera performance).

### Models
- `app/src/main/ml/lightning.tflite` (default, faster)
- `app/src/main/ml/thunder.tflite` (more accurate, can be swapped in code)

## Project Structure

```
├── app/
│   ├── src/main/
│   │   ├── java/com/example/grad_project/MainActivity.kt  # Core logic
│   │   ├── ml/                                            # TFLite models
│   │   ├── res/
│   │   │   ├── layout/                                    # activity_main.xml, dialog
│   │   │   ├── raw/warning.mp3                            # Alert sound
│   │   │   └── drawable/                                  # Icons, reference images
│   │   └── AndroidManifest.xml
├── build.gradle.kts
└── ...
```
*Last updated: June 2026*
