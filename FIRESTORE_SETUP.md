# Firebase Firestore Setup Guide

## ✅ Your App Now Uses Firestore Database!

### 🎯 Benefits of Firestore vs Cloud Storage

| Feature | Firestore (NOW) | Cloud Storage (BEFORE) |
|---------|-----------------|------------------------|
| **Real-time sync** | ✅ Live updates across devices | ❌ Must download entire file |
| **Query support** | ✅ Filter, sort, paginate SMS | ❌ Can only download full JSON |
| **Scalability** | ✅ Each SMS is a document | ❌ One huge JSON file |
| **Other apps** | ✅ Easy to build dashboards, analytics | ❌ Must parse JSON files |
| **Offline support** | ✅ Built-in local caching | ❌ Manual implementation needed |
| **Live data** | ✅ Real-time listeners | ❌ Polling required |

---

## 📊 New Firestore Data Structure

```
Firestore Database/
└── users (collection)
    └── {userId} (document - auto-generated anonymous ID)
        └── sms (subcollection)
            ├── {smsId_timestamp} (document)
            │   ├── id: 12345
            │   ├── address: "+1234567890"
            │   ├── body: "Your verification code is 123456"
            │   ├── timestamp: 1766693061000
            │   ├── type: 1 (INBOX)
            │   ├── read: true
            │   ├── seen: true
            │   ├── threadId: 100
            │   ├── userId: "abc123xyz"
            │   └── uploadedAt: 1766693065000
            │
            ├── {smsId_timestamp} (document)
            └── ...
```

### Document ID Format
```
{smsId}_{timestamp}
```
Example: `12345_1766693061000`

This prevents duplicate uploads - same SMS won't be uploaded twice!

---

## 🚀 Firebase Console Setup Steps

### 1️⃣ Enable Firestore Database

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Click **"Firestore Database"** in left menu
4. Click **"Create database"**
5. Choose **"Start in test mode"** (for development)
6. Select a location (us-central, europe-west, etc.)
7. Click **"Enable"**

### 2️⃣ Enable Authentication

1. Click **"Authentication"** in left menu
2. Click **"Get started"**
3. Go to **"Sign-in method"** tab
4. Click **"Anonymous"**
5. Toggle **"Enable"**
6. Click **"Save"**

### 3️⃣ Update Security Rules (Production)

Go to **Firestore Database → Rules** and paste:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // User can only read/write their own SMS
    match /users/{userId}/sms/{smsId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // For testing: Allow all authenticated users (REMOVE IN PRODUCTION!)
    match /users/{userId}/sms/{smsId} {
      allow read, write: if request.auth != null;
    }
  }
}
```

**Publish** the rules.

---

## 🔍 How Your App Uses Firestore

### Code Flow (SmsUploadWorker.kt)

```kotlin
// 1. Sign in anonymously
auth.signInAnonymously().await()
val userId = auth.currentUser?.uid

// 2. For each SMS message
for (message in messages) {
    // Create unique document ID (prevents duplicates)
    val documentId = "${message.id}_${message.timestamp}"
    
    // 3. Upload to Firestore
    firestore.collection("users")
        .document(userId)
        .collection("sms")
        .document(documentId)
        .set(message.toMap(), SetOptions.merge())
        .await()
}
```

### What `SetOptions.merge()` Does

- **Without merge:** Overwrites entire document
- **With merge:** Only updates/adds fields, keeps existing data
- **Benefit:** If SMS already exists, it won't be duplicated or lost

---

## 📱 Building Other Apps on This Data

### Example 1: Web Dashboard

```javascript
// React/Next.js example
import { collection, query, where, onSnapshot } from 'firebase/firestore';

// Real-time listener for new SMS
const q = query(
  collection(db, 'users', userId, 'sms'),
  where('timestamp', '>', Date.now() - 86400000) // Last 24 hours
);

onSnapshot(q, (snapshot) => {
  snapshot.docChanges().forEach((change) => {
    if (change.type === 'added') {
      console.log('New SMS:', change.doc.data());
      // Update UI in real-time!
    }
  });
});
```

### Example 2: SMS Analytics App

```kotlin
// Count SMS by sender
firestore.collection("users/$userId/sms")
    .whereEqualTo("address", "+1234567890")
    .get()
    .await()
    .size() // Total messages from this number

// Get recent verification codes
firestore.collection("users/$userId/sms")
    .whereGreaterThan("timestamp", yesterday)
    .whereEqualTo("type", 1) // INBOX only
    .orderBy("timestamp", Query.Direction.DESCENDING)
    .limit(10)
    .get()
```

### Example 3: SMS Backup Export

```kotlin
// Export all SMS to JSON
val allSms = firestore.collection("users/$userId/sms")
    .get()
    .await()
    .documents
    .map { it.data }

// Save to file or send to cloud
val json = Gson().toJson(allSms)
```

---

## 🎓 Firestore Querying Examples

### Filter by Date Range
```kotlin
firestore.collection("users/$userId/sms")
    .whereGreaterThanOrEqualTo("timestamp", startDate)
    .whereLessThanOrEqualTo("timestamp", endDate)
    .get()
```

### Search by Phone Number
```kotlin
firestore.collection("users/$userId/sms")
    .whereEqualTo("address", "+1234567890")
    .orderBy("timestamp", Query.Direction.DESCENDING)
    .get()
```

### Get Unread Messages
```kotlin
firestore.collection("users/$userId/sms")
    .whereEqualTo("read", false)
    .get()
```

### Paginate Results
```kotlin
// First page
val firstBatch = firestore.collection("users/$userId/sms")
    .orderBy("timestamp", Query.Direction.DESCENDING)
    .limit(50)
    .get()
    .await()

// Next page
val lastVisible = firstBatch.documents.lastOrNull()
val nextBatch = firestore.collection("users/$userId/sms")
    .orderBy("timestamp", Query.Direction.DESCENDING)
    .startAfter(lastVisible)
    .limit(50)
    .get()
```

---

## 🔄 Real-Time Sync Example

Add this to your Android app to listen for new SMS in real-time:

```kotlin
// In ViewModel or Repository
fun observeNewSms(userId: String): Flow<List<SmsMessage>> = callbackFlow {
    val listener = firestore.collection("users/$userId/sms")
        .whereGreaterThan("timestamp", System.currentTimeMillis())
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            
            val messages = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(SmsMessage::class.java)
            } ?: emptyList()
            
            trySend(messages)
        }
    
    awaitClose { listener.remove() }
}
```

---

## 🆚 Comparison: Before vs After

### BEFORE (Cloud Storage - JSON Files)

**Pros:**
- Simple to implement
- Easy to download/backup

**Cons:**
- ❌ Can't query SMS without downloading entire file
- ❌ No real-time updates
- ❌ JSON files grow huge (40K messages = huge file!)
- ❌ Hard to build other apps
- ❌ Must parse entire JSON every time

**Use case:** Simple backup/archive only

---

### NOW (Firestore Database)

**Pros:**
- ✅ Query any SMS instantly
- ✅ Real-time sync across devices
- ✅ Build analytics dashboards
- ✅ Filter by sender, date, type
- ✅ Paginate results efficiently
- ✅ Other apps can easily access data
- ✅ Offline support built-in

**Cons:**
- More complex setup (but worth it!)
- Per-document pricing (but generous free tier)

**Use case:** Production apps with search, analytics, multi-device sync

---

## 💰 Pricing (Free Tier is Generous!)

**Firestore Free Tier:**
- 50K document reads/day
- 20K document writes/day
- 20K document deletes/day
- 1GB storage

**Your app:**
- Uploads ~50-100 SMS/day = ~100 writes
- Reads SMS on refresh = ~50 reads
- **Well within free tier!**

---

## ✅ What You Get Now

1. **Real-time SMS archive** - Every SMS stored as individual document
2. **Query support** - Filter by sender, date, type, etc.
3. **Scalable** - Each SMS is independent
4. **Multi-app ready** - Build web dashboard, analytics, export tools
5. **Live sync** - Changes appear instantly on all devices
6. **Future-proof** - Easy to add features like search, filters, reports

---

## 🔧 Testing Firestore

After Firebase setup, check logs:

```bash
adb logcat | grep -E "SmsUploadWorker|Firestore"
```

You should see:
```
D SmsUploadWorker: Starting Firestore upload for user: abc123xyz
D SmsUploadWorker: Uploaded SMS 1/10: ID=12345_1766693061000
D SmsUploadWorker: Uploaded SMS 2/10: ID=12346_1766693062000
...
D SmsUploadWorker: Firestore upload completed: 10 success, 0 errors
```

### View in Firebase Console

1. Go to Firestore Database
2. Navigate to: `users → {yourUserId} → sms`
3. You'll see all SMS as individual documents!
4. Click any document to see fields (address, body, timestamp, etc.)

---

## 🎉 Congratulations!

Your SMS Archiver now uses Firestore - a production-ready, scalable database that enables:
- Real-time data sync
- Powerful querying
- Easy integration with other apps
- Live updates across devices

You can now build:
- 📊 SMS Analytics Dashboard
- 🔍 SMS Search App
- 📱 Multi-device SMS Sync
- 📈 Message Statistics
- 🗂️ Conversation Viewer
- And much more!
