# ByPlayer - Android Music Player

ByPlayer is a modern Android music player application developed with Kotlin and Jetpack Compose. It provides a smooth user interface and rich features for enjoying local music playback.

[中文文档](READMEZH.md)

## Key Features

- 🎵 Local Music Scanning and Playback
  - Support for common audio formats (MP3, M4A, etc.)
  - Automatic scanning of music files on device
  - Display of song title, artist, and album information

- 📝 LRC Lyrics Support
  - Automatic loading and display of LRC format lyrics
  - Real-time synchronized lyrics display
  - Gesture control for lyrics interface

- 🎮 Rich Playback Controls
  - Play/Pause, Previous/Next track
  - Progress bar with seek control
  - Shuffle mode
  - Single/List repeat modes

- 📱 Notification Controls
  - Media notification with current playback information
  - Quick playback controls in notification
  - Background playback support

- 🚗 Car System Integration
  - Bluetooth connection with lyrics display
  - Integration with car systems via AVRCP protocol
  - Steering wheel control support

## Technical Features

- Developed in Kotlin
- Modern UI built with Jetpack Compose
- Audio playback powered by Media3 ExoPlayer
- Support for Android 12+ permission system
- Following Material Design 3 guidelines

## System Requirements

- Android 6.0 (API 23) or higher
- Storage permission for accessing local music files
- Notification permission for media notifications
- Bluetooth permission for car system integration

## Development Setup

1. Clone the repository:
```bash
git clone https://github.com/yourusername/ByPlayer.git
```

2. Open the project in Android Studio Hedgehog or later

3. Sync Gradle dependencies

4. Run the application

## Main Dependencies

- androidx.compose:compose-bom:2023.10.01
- androidx.media3:media3-exoplayer:1.2.0
- androidx.media3:media3-session:1.2.0
- androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0
- com.google.accompanist:accompanist-permissions:0.32.0

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Contributing

Issues and Pull Requests are welcome!

## Acknowledgments

Thanks to all contributors who have helped with this project.
