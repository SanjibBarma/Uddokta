উদ্যোক্তা হিসাব (Uddokta Hisab) — Firebase Edition
Jetpack Compose, MVVM, Hilt, Room, WorkManager এবং Firebase Cloud Firestore (100% Free Spark Plan) দিয়ে তৈরি ব্যবসার বিক্রি, স্টক (SKU) ও হিসাব ব্যবস্থাপনা Android অ্যাপ্লিকেশন।

এই সংস্করণটি সম্পূর্ণভাবে Firebase Cloud Firestore দ্বারা পরিচালিত (কোনো Google Sheet বা Apps Script এর প্রয়োজন নেই)।

১. আর্কিটেকচার ও ডাটা ফ্লো (১০০% Pure Firebase)
text

📱 Android App (Jetpack Compose + Material 3)
│
▼
🧠 AppViewModel (StateFlow + Real-time Firestore Snapshot Listener)
│
▼
🗂️ AppRepository
↙          ↘
💾 Room DB     🔥 Firebase Cloud Firestore (Primary Database + Offline Cache)
(LocalStore + WorkManager PendingSync)
প্রধান বৈশিষ্ট্যসমূহ:
১০০% Firebase Cloud Firestore: ইউজার লগইন, প্রোফাইল, টাস্ক, বিক্রির হিসাব, পরিবর্তনের অনুরোধ, SKU স্টক ও লাভ-ক্ষতি—সবকিছু সরাসরি Firebase Firestore-এ সংরক্ষিত হয়।
Real-Time Update: Firestore Snapshot Listener যুক্ত থাকায় যেকোনো ইউজার বিক্রি যোগ করলে, পরিবর্তনের অনুরোধ পাঠালে বা অ্যাডমিন কোনো পরিবর্তন করলে ড্যাশবোর্ডে সাথে সাথে রিয়েল-টাইম আপডেট দেখা যায়।
অল-টাইম (All-Time) ও আজকের হিসাব: অ্যাডমিন এবং সাধারণ ইউজার সবার ড্যাশবোর্ডেই আজকের বিক্রি / আজকের পরিমাণ এবং শুরু থেকে এখন পর্যন্ত সর্বমোট বিক্রি / সর্বমোট পরিমাণ (All-Time Total) স্বয়ংক্রিয়ভাবে হিসাব হয়ে প্রদর্শিত হয়।
সর্বশেষ এন্ট্রি সবার উপরে (Latest First): ড্যাশবোর্ড এবং হিসাব ট্যাবে সবসময় সর্বশেষ যোগ করা হিসাব (createdAt অনুসারে) তালিকার সবার উপরে দেখায়।
নিজস্ব হিসাব ট্যাব (Self Hisab Only): নিচের নেভিগেশন বারের হিসাব ট্যাবে অ্যাডমিন বা সাধারণ ইউজার সবাই শুধুমাত্র নিজের এন্ট্রি করা বিক্রির হিসাব দেখতে ও ফিল্টার করতে পারবেন।
Offline-First Support: ইন্টারনেট না থাকলেও বিক্রি বা তথ্য সেভ করলে তা সাথে সাথে Room Database ও Firebase Offline Cache-এ সেভ হয় এবং ইন্টারনেট ফিরে এলে WorkManager-এর মাধ্যমে স্বয়ংক্রিয়ভাবে Firebase-এ সিঙ্ক হয়ে যায়।
সব ফোনে সমান Navigation Bar সাপোর্ট: যেকোনো সাইজের ফোন, 3-Button Navigation Bar, 2-Button Navigation Bar, কিংবা Gesture Navigation Bar এবং পাঞ্চ-হোল/নচ ডিসপ্লেতে WindowInsets.safeDrawing ও রেসপনসিভ ট্যাব লেআউট সমানভাবে কাজ করে।
২. সিস্টেমে কারা থাকবে ও তাদের সুবিধা
Super Admin (SUPER_ADMIN)
ইউজার ব্যবস্থাপনা (তৈরি, বিস্তারিত দেখা ও ডিলিট):
নতুন সাধারণ User তৈরি করতে পারবেন (createUser)।
অ্যাডমিন ➔ ইউজার সেকশনে যেকোনো ইউজারের কার্ডে একবার ক্লিক (Single Tap) করলে পপআপ ডায়ালগে সেই ইউজারের সম্পূর্ণ প্রোফাইল (পূর্ণ নাম, ইউজারনেম, রোল, ফোন নম্বর, পিতার ফোন নম্বর, বর্তমান ও স্থায়ী ঠিকানা, এনআইডি এবং আজকের ও সর্বমোট বিক্রির সারসংক্ষেপ) দেখা যাবে।
যেকোনো সাধারণ ইউজারের কার্ডে চাপ দিয়ে ধরে রাখলে (Long Press) ইউজার ডিলিট করার কনফার্মেশন অপশন আসবে এবং ইউজার মুছে ফেলা যাবে (deleteUser)।
কাজ অ্যাসাইন করা: User-কে checkbox দিয়ে এক বা একাধিক Task assign করতে পারবেন (assignTasks)।
ড্যাশবোর্ড ও হিসাব ব্যবস্থাপনা:
নিজের Sales entry দিতে পারবেন (addRecord)।
ড্যাশবোর্ডে সব User-এর আজকের বিক্রি/পরিমাণ এবং সর্বমোট (All-Time) বিক্রি/পরিমাণ ও মোট রেকর্ড সংখ্যা দেখতে পারবেন।
ড্যাশবোর্ড থেকে হিসাব ডিলিট (Long Press): শুধুমাত্র অ্যাডমিন ড্যাশবোর্ডের সাম্প্রতিক হিসাব তালিকায় যেকোনো আইটেমের ওপর চাপ দিয়ে ধরে রাখলে (Long Press) সেটি মুছে ফেলার অপশন আসবে (deleteRecord)। ডিলিট করার সাথে সাথে মোট বিক্রি, মোট পরিমাণ, মোট রেকর্ড ও SKU হিসাব থেকে স্বয়ংক্রিয়ভাবে তা বাদ হয়ে রিক্যালকুলেট হবে।
হিসাব ট্যাব: হিসাব ট্যাবে শুধুমাত্র অ্যাডমিনের নিজের এন্ট্রি করা হিসাবগুলো সর্বশেষ এন্ট্রি অনুযায়ী উপরে দেখাবে।
পরিবর্তনের অনুরোধ অনুমোদন: User-এর পাঠানো record পরিবর্তনের request অনুমোদন বা বাতিল (Approve/Reject) করতে পারবেন (decideChangeRequest)।
SKU স্টক ব্যবস্থাপনা: নতুন SKU যোগ করা (addSku), নতুন কেনা স্টক যোগ করা (addPurchase), SKU মুছে ফেলা (deleteSku) এবং কেনা-বিক্রি-বাকি ও লাভ/ক্ষতির হিসাব দেখা।
Normal User (USER)
Admin-এর দেওয়া username/password দিয়ে login করতে পারবেন।
প্রথম login-এর পর বাধ্যতামূলক প্রোফাইল ১০০% পূরণ করতে পারবেন (completeProfile)।
শুধু assigned Task-এ sales entry দিতে পারবেন (addRecord)।
নিজের ড্যাশবোর্ডে আজকের বিক্রি/পরিমাণ এবং সর্বমোট (All-Time) বিক্রি/পরিমাণ দেখতে পারবেন।
হিসাব ট্যাবে শুধুমাত্র নিজের বিক্রির ইতিহাস দেখতে পারবেন (সর্বশেষ এন্ট্রি সবার উপরে দেখাবে)।
নিজের যেকোনো রেকর্ড পরিবর্তনের জন্য কারণসহ Admin-এর কাছে request পাঠাতে পারবেন (requestChange)।
একজন সাধারণ User অন্য কোনো User-এর প্রোফাইল, ড্যাশবোর্ড বা বিক্রির তথ্য দেখতে পারবেন না।
৩. Firebase Console সেটআপ গাইড (১০০% ফ্রি Spark Plan)
১. Firebase Console-এ গিয়ে আপনার প্রজেক্ট খুলুন।
২. বাম পাশের মেনুতে Settings ⚙️ ➔ Project settings থেকে Android অ্যাপ (com.uddoktahisab.app) রেজিস্টার করে google-services.json ডাউনলোড করুন এবং প্রজেক্টের নিচের লোকেশনে বসান:

text

app/google-services.json
৩. বাম পাশের মেনু থেকে Project shortcuts ➔ Firestore (অথবা Databases & Storage ➔ Firestore Database)-এ যান:

Create database বাটনে ক্লিক করুন।
Standard edition সিলেক্ট করে লোকেশন দিন এবং Start in test mode দিয়ে ডাটাবেস তৈরি করুন।
৪. Firestore তৈরি হয়ে গেলে উপরে Rules ট্যাবে ক্লিক করে নিচের রুলসটি পেস্ট করে Publish করুন:
JavaScript

rules_version = '2';
service cloud.firestore {
match /databases/{database}/documents {
match /{document=**} {
allow read, write: if true;
}
}
}
৪. প্রথমবার লগইন (Default Super Admin)
অ্যাপ প্রথমবার চালু হয়ে ইন্টারনেটে যুক্ত হলে Firebase Firestore-এ স্বয়ংক্রিয়ভাবে ৪টি ডিফল্ট Task এবং প্রথম Super Admin অ্যাকাউন্ট তৈরি হয়ে যাবে:

Username: admin
Password: admin
লগইন করার পর বাধ্যতামূলক প্রোফাইল পূরণ স্ক্রিন আসবে। প্রোফাইল ১০০% সম্পন্ন করলেই ড্যাশবোর্ড, বিক্রি, হিসাব ও অ্যাডমিন প্যানেল আনলক হবে।

৫. Firebase Firestore Collections
Collection Name	কী ডাটা সংরক্ষিত থাকে
users	সব Super Admin ও সাধারণ User-এর অ্যাকাউন্ট, SHA-256 পাসওয়ার্ড হ্যাশ এবং প্রোফাইল তথ্য
tasks	কাজের মাস্টার লিস্ট (TASK_SALES_PCS, TASK_SALES_KG, TASK_PURCHASE, TASK_EXPENSE)
assignments	কোন User-কে কোন Task অ্যাসাইন করা হয়েছে তার ম্যাপিং (userId_taskId)
sales_records	বিক্রির হিসাব (taskId, date, quantity, unitPrice, total, note, createdAt)
change_requests	সাধারণ ইউজারের পাঠানো হিসাব পরিবর্তনের অনুরোধ (PENDING, APPROVED, REJECTED)
skus	পণ্যের স্টক (SKU), মোট কেনা (totalStock), মোট খরচ (totalCost)
sessions	লগইন সেশন টোকেন ও মেয়াদ (expiresAt)
audit_logs	সব গুরুত্বপূর্ণ অপারেশনের (LOGIN, ADD_RECORD, DELETE_RECORD, CREATE_USER, DELETE_USER, ADD_SKU, DELETE_SKU ইত্যাদি) অডিট লগ
৬. Android Studio Build নির্দেশনা
১. Android Studio দিয়ে প্রজেক্ট ফোল্ডারটি খুলুন।
২. নিশ্চিত করুন যে
google-services.json
ফাইলটি আপনার Firebase প্রজেক্টের।
৩. JDK 17 নির্বাচন করুন (File ➔ Settings ➔ Build, Execution, Deployment ➔ Build Tools ➔ Gradle)।
৪. Sync Project with Gradle Files ক্লিক করুন।
৫. Android 8.0 (API 26) বা তার উপরের যেকোনো ফোনে বা ইমুলেটরে Run করুন।

Build Configuration
applicationId = com.uddoktahisab.app
minSdk = 26
targetSdk = 35
compileSdk = 35
Keystore info:
uddokta
(uddokta)