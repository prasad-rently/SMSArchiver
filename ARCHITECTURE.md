# SMSArchiver - Complete System Architecture Explanation

## 📊 System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         SMS ARCHIVER FLOW                        │
└─────────────────────────────────────────────────────────────────┘

   ┌──────────┐
   │  Device  │
   │   SMS    │
   └────┬─────┘
        │ New SMS arrives
        ▼
┌──────────────────┐
│  SmsReceiver     │ ◄── BroadcastReceiver registered in Manifest
│  (Background)    │     Listens for: "android.provider.Telephony.SMS_RECEIVED"
└────┬─────────────┘
     │ Extracts: sender, message, timestamp
     │ Triggers WorkManager
     ▼
┌──────────────────┐
│  WorkManager     │ ◄── Android's background job scheduler
│  Scheduler       │     Handles battery optimization & retries
└────┬─────────────┘
     │ Schedules SmsUploadWorker
     ▼
┌──────────────────┐
│ SmsUploadWorker  │ ◄── Background worker (runs asynchronously)
│  (Background)    │     
└────┬─────────────┘
     │
     ├─► SmsRepository.getMessagesAfter(timestamp)
     │   └─► ContentResolver queries device SMS database
     │       └─► Returns List<SmsMessage>
     │
     └─► uploadToFirebase(messages)
         ├─► Firebase Auth (anonymous sign-in)
         ├─► Convert messages to JSON
         └─► Upload to Cloud Storage: users/{userId}/sms/{filename}.json

┌──────────────────┐
│  MainActivity    │ ◄── Jetpack Compose UI
│  (Foreground)    │     Shows status, messages, controls
└────┬─────────────┘
     │ User interactions
     ▼
┌──────────────────┐
│  MainViewModel   │ ◄── State management (MVVM pattern)
│                  │     Manages UI state with StateFlow
└──────────────────┘
     │ Reads SMS
     ▼
┌──────────────────┐
│  SmsRepository   │ ◄── Data layer
│                  │     Queries ContentProvider for SMS
└──────────────────┘
```

---

## 🔍 Component Deep Dive

### 1️⃣ **SmsReceiver - The SMS Listener**

**File:** `app/src/main/java/com/prasad/smsarchiver/service/SmsReceiver.kt`

**Purpose:** Catches SMS as soon as they arrive on the device

**How it works:**
```kotlin
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // 1. Android OS broadcasts SMS_RECEIVED_ACTION when SMS arrives
        // 2. This receiver catches the broadcast
        // 3. Extracts SMS data from the intent
        // 4. Schedules background upload work
    }
}
```

**Registration in AndroidManifest.xml:**
```xml
<receiver android:name=".service.SmsReceiver" 
          android:enabled="true" 
          android:exported="true">
    <intent-filter android:priority="999">
        <action android:name="android.provider.Telephony.SMS_RECEIVED" />
    </intent-filter>
</receiver>
```

**Current Issue:** The receiver is registered but may not be triggered because:
- ❌ The app might not be set as the default SMS app (not required, but helps)
- ❌ Battery optimization might be killing the receiver
- ❌ Android 13+ requires runtime notification permission
- ❌ The receiver only triggers on NEW incoming SMS, not existing ones

---

### 2️⃣ **SmsRepository - The SMS Reader**

**File:** `app/src/main/java/com/prasad/smsarchiver/data/repository/SmsRepository.kt`

**Purpose:** Reads SMS from Android's SMS database

**How it works:**
```kotlin
suspend fun getAllMessages(): List<SmsMessage> {
    // Uses ContentResolver to query SMS database
    val cursor = contentResolver.query(
        Telephony.Sms.CONTENT_URI,  // Points to: content://sms
        projection,                  // Which columns to read
        null,                       // Selection (where clause)
        null,                       // Selection args
        "date DESC"                 // Sort by newest first
    )
    
    // Loops through cursor and creates SmsMessage objects
    // Returns list of all SMS
}
```

**What it reads:**
- Phone number (address)
- Message body
- Timestamp
- Type (inbox/sent)
- Read status
- Thread ID

**Current State:** ✅ Working correctly when called

---

### 3️⃣ **SmsUploadWorker - The Firebase Uploader**

**File:** `app/src/main/java/com/prasad/smsarchiver/data/worker/SmsUploadWorker.kt`

**Purpose:** Uploads SMS to Firebase Realtime Database

**How it works:**
```kotlin
override suspend fun doWork(): Result {
    // 1. Read SMS messages (after specific timestamp or all)
    val messages = smsRepository.getMessagesAfter(timestamp)
    
    // 2. Sign in to Firebase (anonymously if no user)
    auth.signInAnonymously().await()
    
    // 3. Convert messages to JSON
    val jsonData = gson.toJson(messages.map { it.toMap() })
    
    // 4. Upload to Cloud Storage
    storage.reference
        .child("users/$userId/sms/$filename")
        .putBytes(jsonData.toByteArray())
        .await()
}
```

**Storage Structure:**
```
Firebase Cloud Storage/
└── users/
    └── {userId}/
        └── sms/
            ├── sms_backup_20251226_143022_15.json
            ├── sms_backup_20251226_150100_3.json
            └── ...
```

**Important:** Uses **Realtime Database** (NoSQL JSON tree database)

---

### 4️⃣ **MainActivity & MainViewModel - The UI**

**Files:** 
- `app/src/main/java/com/prasad/smsarchiver/ui/MainActivity.kt`
- `app/src/main/java/com/prasad/smsarchiver/ui/viewmodel/MainViewModel.kt`

**Purpose:** Display SMS, request permissions, show status

**How UI updates work:**
```kotlin
// ViewModel holds UI state
private val _uiState = MutableStateFlow(MainUiState())
val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

// UI observes state changes
val uiState by viewModel.uiState.collectAsState()

// When state changes, UI recomposes automatically
_uiState.value = _uiState.value.copy(
    messages = newMessages,
    messageCount = newMessages.size
)
```

**Current Issue:** ❌ **UI does NOT auto-update when new SMS arrives**

**Why:** The flow is:
```
New SMS → SmsReceiver → WorkManager → Firebase Upload
                                    
                                    ⚠️ NO CONNECTION TO UI!
```

The ViewModel only updates when you manually call `loadMessages()` (press Refresh button)

---

## 🚨 Why SMS Listening Doesn't Work

### **Critical Issues:**

1. **No Automatic UI Refresh**
   ```kotlin
   // Current: UI only updates when user presses "Refresh"
   // Missing: Observer or callback from SMS receiver to UI
   ```

2. **BroadcastReceiver Not Triggering**
   - Test by checking logs: `adb logcat | grep SmsReceiver`
   - If you see nothing, the receiver isn't being called

3. **WorkManager Might Be Working** (but you can't see it)
   - The SMS might be uploading to Firebase
   - But UI doesn't show it because there's no connection

4. **Testing on Emulator vs Real Device**
   - Emulator: Use `adb emu sms send`
   - Real device: Have someone text you

---

## 🔧 How to Fix Auto-Update

Let me create a solution that makes the UI update automatically when SMS arrives:

