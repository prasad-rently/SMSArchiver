# SMSArchiver

Android application that automatically archives SMS messages to Firebase Cloud Storage in the background.

## Features

- 📱 **Background SMS Monitoring** - Continuously listens for incoming SMS messages
- ☁️ **Cloud Backup** - Automatically uploads SMS to Firebase Cloud Storage
- 🔒 **Permission Management** - Handles runtime permissions for SMS access
- 📊 **Message Dashboard** - View SMS count and monitoring status
- 🔄 **WorkManager Integration** - Reliable background processing with battery optimization

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Cloud Storage**: Firebase Cloud Storage
- **Background Processing**: WorkManager + Foreground Service
- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)

## Architecture

### Core Components

1. **SmsReceiver** - BroadcastReceiver that listens for `SMS_RECEIVED` intents
2. **SmsMonitorService** - Foreground service for continuous monitoring
3. **SmsRepository** - Handles reading SMS from device ContentProvider
4. **SmsUploadWorker** - WorkManager worker for uploading to Firebase
5. **MainActivity** - Jetpack Compose UI with permission handling

### Data Flow

```
Incoming SMS → SmsReceiver → WorkManager → SmsUploadWorker → Firebase Cloud Storage
                    ↓
              SmsRepository (Read SMS via ContentResolver)
```

## Setup

### Prerequisites

1. **Android Studio** Hedgehog (2023.1.1) or newer
2. **JDK 17** or higher
3. **Firebase Project** - Create one at [Firebase Console](https://console.firebase.google.com/)

### Firebase Configuration

1. Create a new Firebase project
2. Add an Android app to your Firebase project
3. Download `google-services.json` 
4. Place it in `app/` directory
5. Enable Firebase Storage in Firebase Console

### Build & Run

```bash
# Clone the repository
git clone https://github.com/prasad-rently/SMSArchiver.git
cd SMSArchiver

# Build the project
./gradlew build

# Install on connected device/emulator
./gradlew installDebug
```

## Permissions

The app requires the following permissions:

- `READ_SMS` - Read existing SMS messages
- `RECEIVE_SMS` - Listen for incoming SMS
- `INTERNET` - Upload to Firebase
- `FOREGROUND_SERVICE` - Background monitoring
- `POST_NOTIFICATIONS` (Android 13+) - Show monitoring notifications

## Testing

### Send Test SMS via ADB

```bash
# For emulator
adb emu sms send <phone_number> <message>

# Example
adb emu sms send 5551234567 "Test message"
```

### View Logs

```bash
# Filter for SMS Archiver logs
adb logcat | grep -E "SmsReceiver|SmsRepository|SmsUploadWorker|MainViewModel"

# Check WorkManager status
adb shell dumpsys activity service WorkManagerService
```

## Firebase Storage Structure

Messages are stored with the following path structure:

```
users/
  └── {userId}/
      └── sms/
          └── sms_backup_{timestamp}_{count}.json
```

Each JSON file contains an array of SMS messages with fields:
- `id`, `threadId`, `address`, `body`, `timestamp`, `type`, `read`, `seen`

## Project Structure

```
app/src/main/java/com/prasad/smsarchiver/
├── data/
│   ├── model/
│   │   └── SmsMessage.kt          # SMS data class
│   ├── repository/
│   │   └── SmsRepository.kt       # SMS reading logic
│   └── worker/
│       └── SmsUploadWorker.kt     # Firebase upload worker
├── service/
│   ├── SmsReceiver.kt             # Broadcast receiver
│   └── SmsMonitorService.kt       # Foreground service
├── ui/
│   ├── MainActivity.kt            # Main Compose UI
│   ├── theme/
│   │   └── Theme.kt               # Material 3 theme
│   └── viewmodel/
│       └── MainViewModel.kt       # UI state management
└── SMSArchiverApplication.kt      # App initialization
```

## Development

### Adding New Features

When contributing, follow the existing patterns:

- Use **Kotlin Coroutines** for async operations
- Follow **MVVM architecture** pattern
- Use **StateFlow** for UI state management
- Implement proper **error handling** with try-catch
- Add **logging** for debugging (use `Log.d/e`)

### Common Development Tasks

**Read SMS messages:**
```kotlin
val messages = smsRepository.getAllMessages()
```

**Schedule upload work:**
```kotlin
val workRequest = OneTimeWorkRequestBuilder<SmsUploadWorker>()
    .setInputData(workDataOf("timestamp" to timestamp))
    .build()
WorkManager.getInstance(context).enqueue(workRequest)
```

**Upload to Firebase:**
```kotlin
val storageRef = Firebase.storage.reference
val smsRef = storageRef.child("users/$userId/sms/$filename")
smsRef.putBytes(jsonData.toByteArray()).await()
```

## Troubleshooting

### Permission Issues
- Ensure all permissions are declared in AndroidManifest.xml
- Test on Android 6.0+ where runtime permissions are required
- Check Settings → Apps → SMSArchiver → Permissions

### Firebase Upload Failures
- Verify `google-services.json` is in the correct location
- Check Firebase Storage rules allow authenticated writes
- Ensure device has internet connectivity
- Check logcat for Firebase-specific errors

### Background Service Not Running
- Check battery optimization settings
- Ensure foreground service notification is showing
- Verify service is declared in AndroidManifest.xml

## License

MIT License - see [LICENSE](LICENSE) file for details

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
