# Realtime Database Debugging Guide - Step by Step

## 🔍 Problem: No data visible in Firebase Console Realtime Database

**This guide helps you debug why SMS aren't appearing in Firebase Realtime Database.**

---

## 📋 Diagnostic Checklist

### Step 1: Verify Realtime Database Exists

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select project: **smsarchiver-edf5d**
3. In the left sidebar, click **"Build"** → **"Realtime Database"**

**What you should see:**

- ✅ **If Realtime Database is enabled:** You'll see the database page with data tree
- ❌ **If NOT enabled:** You'll see "Create Database" button

**Action if NOT enabled:**
1. Click **"Create Database"**
2. Choose database location (e.g., `us-central1`)
3. Start in **test mode** (we'll secure it later)
4. Wait 1-2 minutes for provisioning

---

### Step 2: Check Database Rules

**CRITICAL:** Make sure your security rules allow authenticated writes.

In the Realtime Database page, click **"Rules"** tab at the top.

**❌ BAD RULES (blocks all writes):**
```json
{
  "rules": {
    ".read": false,
    ".write": false
  }
}
```

**✅ GOOD RULES (allows user-scoped access):**
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

**Action:**
1. Copy the GOOD RULES above
2. Paste into Rules editor
3. Click **"Publish"**

---

### Step 3: Verify Firebase Authentication

1. In Firebase Console, go to **"Authentication"**
2. Check **"Users"** tab
3. **You should see at least 1 user** (anonymous or email)

**Action if NO users:**
1. Go to **"Sign-in method"** tab
2. Enable **"Anonymous"** provider
3. Click **"Save"**

---

### Step 4: Run Diagnostic Test

**Purpose:** This test writes a single value to Realtime Database to verify connectivity.

1. **Start logcat BEFORE tapping button:**

```bash
adb logcat -s MainViewModel:V FirebaseAuth:V FirebaseDatabase:V | tee realtime_db_test.log
```

2. **Launch app on emulator/device:**

```bash
./gradlew installDebug
adb shell am start -n com.prasad.smsarchiver/.ui.MainActivity
```

3. **Grant permissions:**
   - Allow READ_SMS
   - Allow RECEIVE_SMS
   - Allow POST_NOTIFICATIONS

4. **Tap the "Test Realtime DB" button**

---

### Step 5: Read Test Logs

**✅ GOOD LOGS (Success):**
```
🟢 REALTIME DB DIAGNOSTIC TEST START
  └─ Test Path: users/{userId}/test
  └─ Database instance: [DEFAULT]

1️⃣ Checking Firebase Auth...
  └─ User ID: abc123xyz456789

2️⃣ Getting database reference...
  └─ Reference: FirebaseDatabase{url='https://smsarchiver-default-rtdb.firebaseio.com'}

✍️ Step 3: Write to Realtime Database
  └─ Writing to: users/abc123xyz456789/test
  └─ Data: {message=Hello Firebase!, timestamp=1640000000000}

📖 Step 4: Read from Realtime Database
  └─ Read value: Hello Firebase!

🎉 REALTIME DB DIAGNOSTIC TEST: ✅ SUCCESS
```

**❌ BAD LOGS (Errors):**

#### Error 1: PERMISSION_DENIED
```
DatabaseException: Permission denied
```

**CAUSE:** Realtime Database security rules are blocking writes

**FIX:**
1. Go to Firebase Console → Realtime Database → Rules
2. Use the GOOD RULES from Step 2 above
3. Click "Publish"

---

#### Error 2: Database not found
```
DatabaseException: Can't determine Firebase Database URL
```

**CAUSE:** Realtime Database doesn't exist

**FIX:**
1. Go to Firebase Console → Realtime Database
2. Click "Create Database"
3. Wait 2 minutes for provisioning

---

#### Error 3: Network timeout
```
Task timed out OR FirebaseNetworkException
```

**CAUSE:** No internet connection OR Realtime Database not responding

**FIX:**
1. Check emulator/device has internet
2. Restart emulator
3. Wait 5 minutes after creating Realtime Database (provisioning time)

---

### Step 6: Verify Data in Console

1. Go to Firebase Console
2. **Realtime Database** → **Data** tab
3. Expand nodes:
   ```
   users
     └─ abc123xyz456789  (your user ID)
         └─ test
             ├─ message: "Hello Firebase!"
             └─ timestamp: 1640000000000
   ```

**✅ If you see this:** Realtime Database is working!

**❌ If empty:** Check logs from Step 5 for errors

---

## 🔄 Step 7: Test SMS Upload Worker

Once the Realtime Database test succeeds:

1. **Start worker-specific logs:**

```bash
adb logcat | grep -E "SmsUploadWorker|FirebaseAuth|FirebaseDatabase" | tee worker_test.log
```

2. **Send test SMS to emulator:**

```bash
adb emu sms send 1234567890 "Test message for Realtime DB"
```

3. **Check logs for upload:**

**✅ SUCCESS:**
```
D SmsUploadWorker: Starting upload for user: abc123xyz
D SmsUploadWorker: Uploading SMS: 1234567890_1640000000000
D SmsUploadWorker: Successfully uploaded SMS: 1234567890_1640000000000
D SmsUploadWorker: Uploaded 1 SMS to Realtime Database
```

4. **Verify in Firebase Console:**
   ```
   users
     └─ abc123xyz456789
         └─ sms
             └─ 12345_1640000000000
                 ├─ address: "+1234567890"
                 ├─ body: "Test message for Realtime DB"
                 ├─ timestamp: 1640000000000
                 ├─ type: 1
                 ├─ read: true
                 └─ threadId: 12345
   ```

**❌ If no logs appear:**
   - Check SMS permissions granted
   - Verify BroadcastReceiver registered in AndroidManifest.xml
   - Check WorkManager is not battery-optimized

**❌ If "Permission denied":**
   - Go to Realtime Database → Rules
   - Verify rules from Step 2 are published

**❌ If "User not authenticated":**
   - Check Firebase Auth is enabled
   - Verify anonymous sign-in is enabled
   - Check userId in logs and compare with Firebase Console Users tab

---

## 🎯 Common Issues Summary

**Issue:** No data in Realtime Database console after test button
- **Cause:** Realtime Database not enabled
- **Fix:** Create database in Firebase Console, wait 2 minutes

**Issue:** PERMISSION_DENIED error
- **Cause:** Security rules blocking writes
- **Fix:** Use user-scoped rules from Step 2

**Issue:** "User not authenticated"
- **Cause:** Firebase Auth not working
- **Fix:** Enable Anonymous auth, verify user exists in console

**Issue:** Worker doesn't trigger on SMS
- **Cause:** Realtime Database database is still provisioning
- **Fix:** Wait 5 minutes, restart app, grant SMS permissions

---

## ✅ Verification Complete

Once you see data in Realtime Database:
- ✅ Firebase Authentication working
- ✅ Realtime Database created and accessible
- ✅ Security rules configured correctly
- ✅ App can write and read data

**Next:** Send more SMS to test full archiving workflow!

---

## 📝 Quick Reference

### Logcat filters
```bash
# Test button diagnostic
adb logcat -s MainViewModel:V FirebaseAuth:V FirebaseDatabase:V

# SMS Worker
adb logcat | grep -E "SmsUploadWorker"

# All Firebase
adb logcat | grep -E "Firebase"
```

### Test SMS
```bash
# In app: tap Test Realtime DB button
# In terminal:
adb emu sms send 1234567890 "Test message"
```

### Expected tags in logs
- "MainViewModel"
- "SmsUploadWorker"
- "FirebaseAuth"
- "FirebaseDatabase"
- "SmsReceiver"

### Firebase Console Quick Links
- **Project:** smsarchiver-edf5d
- **Database:** Realtime Database → Data tab
- **Rules:** Realtime Database → Rules tab
- **Auth:** Authentication → Users tab

### Working Configuration Checklist
- ✅ Realtime Database enabled
- ✅ Database type: Firebase Realtime Database  
- ✅ Rules: User-scoped (auth.uid)
- ✅ Auth: Anonymous provider enabled
- ✅ App: Permissions granted (READ_SMS, RECEIVE_SMS)
