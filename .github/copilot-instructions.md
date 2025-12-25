# SMSArchiver - Copilot Instructions

## Project Overview
Android application that automatically archives SMS messages to Firebase Cloud Storage in the background. Built with Kotlin and Jetpack Compose.

## Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Cloud Database**: Firebase Firestore
- **Target Platform**: Android (minimum SDK TBD)
- **License**: MIT

## Architecture Overview

### Core Components
1. **SMS BroadcastReceiver** - Listens for incoming SMS (`SMS_RECEIVED` action)
2. **Background Service/WorkManager** - Handles SMS reading and upload operations
3. **Firebase Integration** - Manages cloud storage uploads and authentication
4. **Jetpack Compose UI** - Displays app status, permissions, and settings

### Key Design Decisions
- Use `BroadcastReceiver` to trigger on SMS receipt
- Leverage WorkManager for reliable background uploads (handles battery optimization)
- Store SMS data in Firebase Cloud Storage (not Firestore) per project requirements
- Run continuously or triggered by SMS events based on user preference

## Development Guidelines

### Permissions & Manifest
Required Android permissions:
- `READ_SMS` - Read device SMS messages
- `RECEIVE_SMS` - Listen for incoming SMS
- `INTERNET` - Upload to Firebase
- `FOREGROUND_SERVICE` (API 28+) - For persistent background operation
- `POST_NOTIFICATIONS` (API 33+) - Show upload notifications

Register `BroadcastReceiver` in `AndroidManifest.xml` with appropriate priority.

### Background Processing
- Use `WorkManager` for upload tasks (not raw Services) - handles Doze mode and battery optimization
- For continuous monitoring, use `Foreground Service` with persistent notification
- Handle `JobScheduler` constraints (network available, not low battery)
- Implement exponential backoff for failed uploads

### Firebase Integration
- Initialize Firebase in `Application` class
- Use Firebase Authentication for user-specific data paths
- Structure Firestore paths: `users/{userId}/sms/{smsId}`
- Each SMS is stored as a document with fields: id, address, body, timestamp, type, etc.
- Use `SetOptions.merge()` to prevent duplicate uploads
- Handle Firestore security rules properly

### Data Model
```kotlin
data class SmsMessage(
    val address: String,        // Phone number (sanitized)
    val body: String,           // Message content
    val timestamp: Long,        // Unix timestamp (UTC)
    val type: Int,             // MESSAGE_TYPE_INBOX/SENT
    val read: Boolean,
    val threadId: Long
)
```
- Always store timestamps in UTC
- Hash or encrypt phone numbers if privacy is a concern
- Batch multiple SMS into single upload when possible

### Jetpack Compose UI Patterns
- Use `ViewModel` to manage UI state and trigger background work
- Display permission status, last sync time, upload queue size
- Show persistent notification when service is running
- Implement Material 3 design components
- Handle runtime permission requests using `rememberPermissionState` from Accompanist

### Code Organization
```
app/
├── data/
│   ├── repository/      # SMS reading, Firebase uploads
│   ├── model/          # Data classes
│   └── worker/         # WorkManager workers
├── service/            # Foreground service, BroadcastReceiver
├── ui/
│   ├── screens/        # Compose screens
│   └── theme/          # Material 3 theme
└── util/              # Extensions, helpers
```

### Testing Strategy
- Mock `ContentResolver` for SMS reading tests
- Use Firebase Local Emulator Suite for integration tests
- Test BroadcastReceiver with fake SMS intents
- Mock WorkManager for background task tests
- UI tests with Compose Test framework

### Critical Workflows

**Initial Setup:**
```bash
# Add Firebase to project (after creating Firebase project)
# Download google-services.json to app/
./gradlew build
```

**Testing SMS Reception:**
```bash
# Send test SMS via adb
adb emu sms send <phone_number> <message>
```

**Debugging Background Work:**
```bash
# Check WorkManager status
adb shell dumpsys activity service WorkManagerService
# View logcat filtered for app
adb logcat | grep SMSArchiver
```

## Common Patterns

### Reading SMS Messages
```kotlin
val cursor = contentResolver.query(
    Telephony.Sms.CONTENT_URI,
    projection,
    null, null,
    Telephony.Sms.DEFAULT_SORT_ORDER
)
```
Use `Telephony.Sms` constants, not hardcoded URIs.

### Uploading to Firebase Storage
```kfirestore = FirebaseFirestore.getInstance()
val documentId = "${message.id}_${message.timestamp}"

firestore.collection("users")
    .document(userId)
    .collection("sms")
    .document(documentId)
    .set(message.toMap(), SetOptions.merge())
    .await()
```
Always use SetOptions.merge() to prevent overwriting and include error handling
Always include error handling and retry logic.

## Next Steps for AI Agents
1. Set up Android project structure with Kotlin and Jetpack Compose
2. Configure Firebase project and add `google-services.json`
3. Implement SMS permissions and BroadcastReceiver
4. Create data model and repository for SMS reading
5. Set up WorkManager for background uploads
6. Build Compose UI for permissions and status display
