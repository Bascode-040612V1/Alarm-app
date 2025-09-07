# 🔔 Android Alarm Clock App

A feature-rich Android alarm clock application built with Kotlin, offering advanced customization options including voice recordings, text-to-speech, custom ringtones, and granular volume controls.

## 📱 Features

### Core Alarm Functionality
- ⏰ **Multiple Alarms**: Create and manage unlimited alarms
- 🔄 **Repeating Alarms**: Set alarms for specific days of the week
- 🎵 **Custom Ringtones**: Choose from system ringtones or default alarm sounds
- 🔕 **Snooze Function**: Configurable snooze duration (default 10 minutes)
- 🌙 **Lock Screen Display**: Full-screen alarm display that works over lock screen

### Advanced Audio Features
- 🎙️ **Voice Recordings**: Record custom voice messages to overlay on alarms
- 🗣️ **Text-to-Speech (TTS)**: Have your alarm notes read aloud automatically
- 🔊 **Independent Volume Controls**: Separate volume settings for:
  - Ringtone volume
  - Voice overlay volume
  - TTS volume
- 📳 **Vibration Control**: Enable/disable vibration per alarm

### User Experience
- 🌓 **Dark Theme Support**: Automatic dark/light theme switching
- 📱 **Responsive Design**: Optimized for different screen sizes and orientations
- 💾 **Persistent Storage**: All settings and preferences are saved automatically
- ⚡ **Background Processing**: Reliable alarm triggering using Android's AlarmManager

## 🛠️ Technical Stack

- **Language**: Kotlin
- **Platform**: Android (Min SDK 24, Target SDK 34)
- **Architecture**: MVC pattern with separation of concerns
- **Build System**: Gradle with Kotlin DSL
- **Storage**: SharedPreferences with JSON serialization
- **Audio**: MediaPlayer, RingtoneManager, TextToSpeech
- **UI**: Material Design components, AndroidX libraries

## 📋 Requirements

- Android device running Android 7.0 (API level 24) or higher
- Permissions required:
  - `SCHEDULE_EXACT_ALARM` - For precise alarm scheduling
  - `USE_EXACT_ALARM` - For exact alarm timing
  - `WAKE_LOCK` - To wake device when alarm triggers
  - `VIBRATE` - For vibration functionality
  - `POST_NOTIFICATIONS` - For alarm notifications
  - `RECORD_AUDIO` - For voice recording feature
  - `SYSTEM_ALERT_WINDOW` - For display over other apps

## 🚀 Installation & Setup

### Prerequisites
- Android Studio Arctic Fox or newer
- JDK 8 or 11
- Android SDK with API level 34

### Build Instructions
1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd Alarm
   ```

2. Open in Android Studio or build via command line:
   ```bash
   # Clean build
   ./gradlew.bat clean
   
   # Build debug APK
   ./gradlew.bat assembleDebug
   
   # Install on connected device
   ./gradlew.bat installDebug
   ```

3. Run tests:
   ```bash
   # Unit tests
   ./gradlew.bat test
   
   # Instrumentation tests
   ./gradlew.bat connectedAndroidTest
   ```

## 📖 Usage Guide

### Creating an Alarm
1. Tap the **+** floating action button on the main screen
2. Set your desired time using the time picker
3. Configure alarm settings:
   - **Title**: Give your alarm a custom name
   - **Note**: Add a note (will be read aloud if TTS is enabled)
   - **Repeat Days**: Select which days the alarm should repeat
   - **Ringtone**: Choose your preferred alarm sound
   - **Volume Levels**: Adjust ringtone, voice, and TTS volumes independently
   - **Voice Recording**: Record a custom message (optional)
   - **Vibration**: Enable/disable vibration

### Voice Features
- **Voice Recording**: Tap record button to capture a custom message that will play with your alarm
- **TTS (Text-to-Speech)**: Enable to have your alarm note read aloud automatically
- **Volume Controls**: Use sliders to set independent volumes for each audio component

### Managing Alarms
- **Edit**: Tap on any alarm in the list to modify its settings
- **Enable/Disable**: Use the toggle switch to quickly enable/disable alarms
- **Delete**: Use the delete button in the edit screen to remove alarms

## 🏗️ Architecture Overview

```
├── MainActivity.kt          # Main alarm list and time display
├── AlarmSetupActivity.kt    # Alarm creation/editing interface
├── AlarmScreenActivity.kt   # Full-screen alarm display
├── AlarmReceiver.kt         # Handles alarm triggers and snooze/dismiss
├── AlarmItem.kt            # Data model for alarm objects
├── AlarmStorage.kt         # Persistent storage management
├── AlarmAdapter.kt         # RecyclerView adapter for alarm list
└── VoiceRecordingManager.kt # Voice recording functionality
```

### Key Components
- **AlarmManager**: System service for scheduling precise alarms
- **BroadcastReceiver**: Receives alarm trigger events
- **MediaPlayer**: Handles ringtone and voice playback
- **TextToSpeech**: Converts text notes to speech
- **SharedPreferences**: Stores alarm data and user preferences

## 🎨 Customization

### Themes
The app supports both light and dark themes, automatically switching based on system preferences.

### Audio Options
- **Ringtones**: Choose from system alarm sounds, notifications, or ringtones
- **Voice Recordings**: Record personalized wake-up messages
- **TTS Settings**: Customize speech rate, pitch, and volume

### Repeat Patterns
- **Once**: Single occurrence
- **Daily**: Every day
- **Weekdays**: Monday through Friday
- **Weekends**: Saturday and Sunday
- **Custom**: Select specific days

## 🔧 Troubleshooting

### Common Issues

**Alarms not triggering:**
- Ensure the app has permission to display over other apps
- Check that battery optimization is disabled for the app
- Verify exact alarm permissions are granted

**Audio not playing:**
- Check system volume levels
- Verify ringtone files are accessible
- Test with different ringtone selections

**Voice recordings not working:**
- Grant microphone permissions
- Ensure sufficient storage space
- Check audio recording settings

### Permissions Setup
1. Go to Settings > Apps > Alarm App
2. Enable all requested permissions
3. Disable battery optimization
4. Allow display over other apps

## 📚 Dependencies

Major libraries and frameworks used:
- AndroidX Core KTX
- AndroidX AppCompat
- Material Design Components
- AndroidX Navigation
- Gson for JSON serialization

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🆘 Support

For issues, bug reports, or feature requests, please create an issue in the repository.

---

**Made with ❤️ for better mornings**