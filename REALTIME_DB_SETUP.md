# Firebase Realtime Database Setup Guide

## ✅ Your App Now Uses Realtime Database!

### 🎯 Benefits of Realtime Database

| Feature | Realtime Database (NOW) |
|---------|-------------------------|
| **Structure** | JSON tree (hierarchical) |
| **Sync** | Real-time synchronization |
| **Offline** | Built-in offline support |
| **Pricing** | Pay per GB downloaded |
| **Queries** | Limited (shallow queries, ordering) |
| **Best For** | Real-time updates, simple data structures |

## 📊 Realtime Database Data Structure

```
Firebase Realtime Database/
└── users/
    └── {userId}/         # User's Firebase Auth UID
        └── sms/
            ├── {smsId_timestamp_1}/
            │   ├── address: "+1234567890"
            │   ├── body: "Hello world"
            │   ├── timestamp: 1640000000000
            │   ├── type: 1
            │   ├── read: true
            │   └── threadId: 12345
            ├── {smsId_timestamp_2}/
            │   └── ...
            └── {smsId_timestamp_3}/
                └── ...
```

**Path Pattern:** `users/{userId}/sms/{smsId}_{timestamp}`

## 🔧 Firebase Console Setup

### 1️⃣ Enable Realtime Database

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project: **smsarchiver-edf5d**
3. Click **"Realtime Database"** in left menu
4. Click **"Create Database"**
5. Choose **location** (e.g., `us-central1`)
6. Start in **test mode** (we'll secure it next)

### 2️⃣ Configure Security Rules

Go to **Realtime Database → Rules** tab and paste:

```json
{
  "rules": {
    "users": {
      "$userId": {
        ".read": "$userId === auth.uid",
        ".write": "$userId === auth.uid",
        "sms": {
          "$smsId": {
            ".validate": "newData.hasChildren(['address', 'body', 'timestamp', 'type'])"
          }
        }
      }
    }
  }
}
```

**What this does:**
- ✅ Users can only read/write their own data (`$userId === auth.uid`)
- ✅ Validates SMS nodes have required fields
- ❌ Blocks anonymous access to other users' data

Click **"Publish"** to activate rules.

## 🔍 How Your App Uses Realtime Database

```kotlin
// 1. Get Firebase Auth user ID
val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

// 2. Read SMS from device
val cursor = contentResolver.query(Telephony.Sms.CONTENT_URI, ...)

// 3. Upload to Realtime Database
val database = FirebaseDatabase.getInstance().reference
database.child("users")
    .child(userId)
    .child("sms")
    .child(smsId)
    .setValue(smsData)
    .await()
```

## 📥 Reading Data from Realtime Database

### Web Dashboard (JavaScript)

```javascript
import { ref, onValue, query, orderByChild } from 'firebase/database';

const db = getDatabase();
const smsRef = ref(db, `users/${userId}/sms`);

onValue(smsRef, (snapshot) => {
  snapshot.forEach((childSnapshot) => {
    const sms = childSnapshot.val();
    console.log(sms.body, sms.timestamp);
  });
});
```

### Android App (Kotlin)

```kotlin
val database = FirebaseDatabase.getInstance().reference
database.child("users")
    .child(userId)
    .child("sms")
    .addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            for (smsSnapshot in snapshot.children) {
                val address = smsSnapshot.child("address").getValue(String::class.java)
                val body = smsSnapshot.child("body").getValue(String::class.java)
                Log.d("SMS", "$address: $body")
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Log.e("SMS", "Read failed: ${error.message}")
        }
    })
```

## 🎓 Realtime Database Querying Examples

### Get SMS sorted by timestamp (newest first)
```kotlin
database.child("users/$userId/sms")
    .orderByChild("timestamp")
    .limitToLast(20)
    .addListenerForSingleValueEvent(...)
```

### Get SMS from specific sender
```kotlin
database.child("users/$userId/sms")
    .orderByChild("address")
    .equalTo("+1234567890")
    .addListenerForSingleValueEvent(...)
```

### Get unread SMS
```kotlin
database.child("users/$userId/sms")
    .orderByChild("read")
    .equalTo(false)
    .addListenerForSingleValueEvent(...)
```

## 💰 Pricing (Realtime Database vs Cloud Storage)

**Realtime Database Free Tier:**
- ✅ **1 GB stored** per month
- ✅ **10 GB downloaded** per month
- ✅ **100 simultaneous connections**

**Your usage (10,000 SMS):**
- Storage: ~5 MB (well under 1 GB)
- Reads: Depends on dashboard usage

**Verdict:** Free tier is sufficient! 🎉

## 🔧 Testing Realtime Database

### 1. Run app and trigger test button

```bash
adb logcat | grep -E "SmsUploadWorker|REALTIME"
```

**Expected logs:**
```
D MainViewModel: REALTIME DB DIAGNOSTIC TEST START
D MainViewModel: User ID: abc123xyz
D MainViewModel: Write completed successfully
D MainViewModel: Read value: Hello Firebase!
D MainViewModel: REALTIME DB DIAGNOSTIC TEST: ✅ SUCCESS
```

### 2. Verify data in Firebase Console

1. Go to Realtime Database
2. Navigate to: `users > {userId} > sms`
3. You should see SMS nodes with address, body, timestamp, etc.

## ✅ Success!

Your SMS Archiver now uses Realtime Database - a real-time, scalable database that enables:
- ✅ **Structured data storage** with JSON tree
- ✅ **Real-time synchronization** across devices
- ✅ **Secure user-scoped access** via Authentication
- ✅ **Simple querying** with ordering and filtering
- ✅ **Offline support** with automatic sync when online
