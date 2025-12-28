# Firestore Debugging Guide - Step by Step

## 🔍 Problem: No data visible in Firebase Console Firestore Database

Follow these steps **in order** to diagnose and fix the issue.

---

## **PART 1: FIREBASE CONSOLE CHECKS** (Do this first!)

### Step 1: Verify Firestore Database Exists

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Select your project: **smsarchiver-44469**
3. In the left sidebar, click **"Build"** → **"Firestore Database"**

**What you should see:**
- ✅ **If Firestore is enabled:** You'll see the database page with collections
- ❌ **If not enabled:** You'll see a button "Create database"

**If you see "Create database":**
```
ACTION REQUIRED: Click "Create database" and follow these steps:
1. Choose "Start in TEST MODE" (for now - we'll secure it later)
2. Select a location (choose closest to you, e.g., "us-central1")
3. Click "Enable"
4. Wait 1-2 minutes for provisioning
```

---

### Step 2: Check Firestore Mode

**CRITICAL:** Make sure you're using **Cloud Firestore** (Native mode), NOT Datastore mode.

In the Firestore Database page, look at the top:
- ✅ Should say: **"Cloud Firestore"** or **"(default)"**
- ❌ If it says: **"Datastore"** → This is the WRONG database type!

**If you accidentally created Datastore mode:**
```
You CANNOT switch modes. You must:
1. Delete the current database (if empty)
2. Create a new project, OR
3. Wait 24 hours and then create Cloud Firestore
```

---

### Step 3: Check Security Rules

1. In Firestore Database page, click **"Rules"** tab at the top
2. Check your current rules

**For TESTING (temporarily), use these rules:**
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // TEMPORARY TEST RULES - Allow all reads/writes
    match /{document=**} {
      allow read, write: if true;
    }
  }
}
```

**Important:** 
- Click **"Publish"** after changing rules
- These are INSECURE rules for testing only
- We'll secure them later

**Correct production rules (use after testing):**
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Allow users to read/write their own data
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

---

### Step 4: Enable Anonymous Authentication

1. In Firebase Console, go to **"Build"** → **"Authentication"**
2. Click **"Get started"** if not enabled yet
3. Click **"Sign-in method"** tab
4. Find **"Anonymous"** in the providers list
5. Click on it, toggle **"Enable"**, and click **"Save"**

**Verify:**
- ✅ Anonymous should show "Enabled" with a green checkmark

---

### Step 5: Check Network Settings (Advanced)

1. Go to **"Project Settings"** (gear icon top left)
2. Scroll down to **"Your apps"** section
3. Verify your Android app is listed
4. Check the **"google-services.json"** was downloaded correctly

**If you updated google-services.json recently:**
```bash
# Rebuild the app completely
./gradlew clean
./gradlew installDebug
```

---

## **PART 2: CODE-SIDE DEBUGGING**

### Step 6: Run the Enhanced Test

1. **Clear logs and start monitoring:**
```bash
adb logcat -c
adb logcat -s MainViewModel:V FirebaseAuth:V FirebaseFirestore:V | tee firestore_test.log
```

2. **Launch the app:**
```bash
adb shell am start -n com.prasad.smsarchiver/.ui.MainActivity
```

3. **Grant all permissions when prompted** (SMS, Notifications)

4. **Tap the "Test Firestore" button**

5. **Watch the console output** - You should see:
```
═══════════════════════════════════════════════════
 FIRESTORE DIAGNOSTIC TEST START
═══════════════════════════════════════════════════
📱 Step 1: Firebase SDK Check
  └─ Auth instance: [DEFAULT]
  └─ Firestore instance: [DEFAULT]
  └─ Project ID: smsarchiver-44469
  └─ App ID: 1:xxxxx:android:xxxxx

🔐 Step 2: Authentication
  ✅ Already authenticated (or signing in...)
  └─ User ID: xxxxx

📝 Step 3: Prepare Test Data
  └─ Test data: {message=Hello...}

📍 Step 4: Document Path
  └─ Full path: users/xxxxx/diagnostics/smokeTest

✍️ Step 5: Write to Firestore
  ✅ Write operation completed successfully!

📖 Step 6: Read from Firestore
  ✅ Document exists!
  └─ Document ID: smokeTest
  └─ Data: {...}

═══════════════════════════════════════════════════
 TEST RESULT: SUCCESS ✅
═══════════════════════════════════════════════════
```

---

### Step 7: Interpret Error Messages

**ERROR: "PERMISSION_DENIED"**
```
CAUSE: Firestore security rules are blocking writes
FIX: Use the TEST MODE rules from Step 3 above
```

**ERROR: "NOT_FOUND" or "Database not created"**
```
CAUSE: Firestore database doesn't exist
FIX: Complete Step 1 - Create the database
```

**ERROR: Logs stop at "Writing test document..." (hangs forever)**
```
CAUSE: No internet connection OR Firestore not responding
FIX: 
1. Check device has internet: adb shell ping -c 3 8.8.8.8
2. Restart the device
3. Wait 5 minutes after creating Firestore (provisioning time)
```

**ERROR: "Failed to authenticate"**
```
CAUSE: Anonymous authentication not enabled
FIX: Complete Step 4 - Enable anonymous auth
```

---

## **PART 3: VERIFY IN FIREBASE CONSOLE**

### Step 8: Check for Data in Console

1. After successful test, go back to Firebase Console
2. **Firestore Database** → **Data** tab
3. You should see:
   ```
   📁 users/
     └─ 📁 {userId}/
          └─ 📁 diagnostics/
               └─ 📄 smokeTest
   ```

4. Click on **smokeTest** document
5. You should see fields:
   - `message`: "Hello from SMSArchiver"
   - `timestamp`: (number)
   - `testId`: "test_xxxxx"
   - `deviceInfo`: (your phone model)
   - `androidVersion`: (number)

---

## **PART 4: TEST SMS UPLOAD**

### Step 9: Test Real SMS Upload

Once the Firestore test succeeds:

1. **Start monitoring:**
```bash
adb logcat -c
adb logcat -s SmsUploadWorker:V | tee sms_upload.log
```

2. **In the app, tap "Start Monitoring"**

3. **Send a test SMS to your device:**
```bash
adb emu sms send 1234567890 "Test message for Firestore"
```

4. **Watch logs for:**
```
═══════════════════════════════════════
 STARTING SMS UPLOAD WORK
═══════════════════════════════════════
📌 Timestamp filter: ...
📨 Found 1 SMS messages to process
  └─ SIM 0: 1 messages

🔐 Step 1: Authentication
  ✅ Already authenticated as: xxxxx

...

✅ Successful uploads: 1
```

5. **Check Firebase Console:**
   - Go to Firestore Database
   - Look for: `users/{userId}/sms/`
   - You should see SMS documents with fields like:
     - `address`: "1234567890"
     - `body`: "Test message..."
     - `timestamp`: (number)
     - `subscriptionId`: 0 or 1 (SIM card ID)
     - `uploadedAt`: (number)

---

## **COMMON ISSUES & SOLUTIONS**

### Issue: "Write successful but no data in console"
**Cause:** Looking at wrong project or wrong collection
**Fix:** 
1. Verify project ID matches in logs: `smsarchiver-44469`
2. Look in the exact path: `users/{userId}/diagnostics/smokeTest`
3. Copy userId from logs and search for it in Firestore

### Issue: "Test times out after 30 seconds"
**Cause:** Firestore database is still provisioning
**Fix:** Wait 5-10 minutes after creating database, then try again

### Issue: "Multiple projects showing in console"
**Cause:** Wrong project selected
**Fix:** 
1. Check app's `google-services.json` for correct project_id
2. Select matching project in console dropdown (top left)

### Issue: "Data appears but disappears quickly"
**Cause:** Security rules or TTL cleanup
**Fix:** Check rules aren't deleting data, verify no Cloud Functions cleanup

---

## **NEXT STEPS AFTER SUCCESS**

Once you see data in Firestore:

1. **Secure your rules** (replace test rules with production rules from Step 3)
2. **Set up indexes** if querying by timestamp or subscription ID
3. **Monitor usage** in Firebase Console → Usage tab
4. **Set up billing alerts** to avoid surprise costs

---

## **EMERGENCY DEBUGGING**

If nothing works, capture full logs:

```bash
# Capture ALL logs for 60 seconds
adb logcat -v time > full_debug.log &
LOGCAT_PID=$!

# In app: tap Test Firestore button

# Wait 30 seconds

# Stop logcat
kill $LOGCAT_PID

# Share full_debug.log for analysis
```

Look for these specific errors in full_debug.log:
- "FirebaseApp initialization"
- "google-services.json"
- "FirebaseFirestore"
- "PERMISSION_DENIED"
- "NOT_FOUND"
- "Network error"

---

## **CONTACT INFO**

Project: SMSArchiver  
Firebase Project ID: smsarchiver-44469  
Database type: Cloud Firestore (Native mode)  
Region: (check in console)
