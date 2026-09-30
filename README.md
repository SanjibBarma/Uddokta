উদ্যোক্তা হিসাব (Uddokta Hisab) — Firebase + Google Sheet Edition
Jetpack Compose, MVVM, Hilt, Room, WorkManager, Firebase Cloud Firestore (Free Spark Plan) এবং Google Apps Script-এর সমন্বয়ে তৈরি ব্যবসার বিক্রি, স্টক (SKU) ও হিসাব ব্যবস্থাপনা Android অ্যাপ্লিকেশন।

এই সংস্করণে অ্যাপ সরাসরি Google Sheet-এ ডাটা পাঠায় না; বরং যেকোনো ডাটা Create, Update বা Delete হলে তা প্রথমে সাথে সাথে Firebase Firestore-এ সংরক্ষিত হয় এবং সেখান থেকে স্বয়ংক্রিয়ভাবে Google Sheet-এ আপডেট হয়ে যায়। পুরো সিস্টেমটি ১০০% ফ্রি (Firebase Spark Plan — কোনো ক্রেডিট কার্ড ছাড়াই) ব্যবহারের উপযোগী।

১. ডাটা ফ্লো ও আর্কিটেকচার (Firebase ➔ Google Sheet)
text

📱 Android App (Jetpack Compose + MVVM)
│
│ ১. মিলিসেকেন্ডে Read / Create / Update / Delete + Real-time Listener
▼
🔥 Firebase Cloud Firestore (Primary Database + Offline Cache)
│
│ ২. Background Sync (syncFromFirebase / pullAllFromFirestore)
▼
📊 Google Spreadsheet (USERS, TASKS, ASSIGNMENTS, CHANGE_REQUESTS, SKUS, SESSIONS, AUDIT_LOG, TASK_*)
কেন এই আর্কিটেকচার সেরা?
সুপার ফাস্ট পারফরম্যান্স: অ্যাপ থেকে সরাসরি Google Sheet-এ কলের জন্য ৩–৮ সেকেন্ড অপেক্ষা করতে হয় না। Firebase Firestore-এ ডাটা মিলিসেকেন্ডে সেভ ও লোড হয়।
Real-Time Update: কোনো ইউজার বিক্রি যোগ করলে বা পরিবর্তনের রিকোয়েস্ট পাঠালে Firestore Snapshot Listener-এর মাধ্যমে ড্যাশবোর্ডে সাথে সাথে আপডেট দেখা যায়।
Firebase থেকে Google Sheet অটো-সিঙ্ক: Firebase-এ যেকোনো ডাটা Create, Update বা Delete হওয়া মাত্রই ব্যাকগ্রাউন্ডে WorkManager ও Code.gs-এর মাধ্যমে Google Sheet-এর নির্দিষ্ট ট্যাবে তা হুবহু আপডেট হয়ে যায়।
সব ফোনে সমান Navigation Bar সাপোর্ট: যেকোনো সাইজের ফোন (ছোট বা বড় স্ক্রিন), 3-Button Navigation Bar, 2-Button Navigation Bar, কিংবা Gesture Navigation Bar এবং পাঞ্চ-হোল/নচ ডিসপ্লেতে WindowInsets.safeDrawing ও রেসপনসিভ ট্যাব লেআউট সমানভাবে কাজ করে।
২. সিস্টেমে কারা থাকবে (কোনো ফাংশনালিটি পরিবর্তন ছাড়াই)
Super Admin (SUPER_ADMIN)
নতুন সাধারণ User তৈরি করতে পারবেন (createUser)
User-কে checkbox দিয়ে এক বা একাধিক Task assign করতে পারবেন (assignTasks)
নিজের Sales entry দিতে পারবেন (addRecord)
সব User-এর আজকের ও মাসিক বিক্রির সম্মিলিত হিসাব দেখতে পারবেন
নির্দিষ্ট User-এর বিস্তারিত sales records এবং সকলের বিক্রির হিসাব দেখতে পারবেন
User-এর record পরিবর্তনের request অনুমোদন বা বাতিল (Approve/Reject) করতে পারবেন (decideChangeRequest)
SKU স্টক ব্যবস্থাপনা: নতুন SKU যোগ করা (addSku), নতুন কেনা স্টক যোগ করা (addPurchase), SKU মুছে ফেলা (deleteSku) এবং কেনা-বিক্রি-বাকি ও লাভ/ক্ষতির হিসাব দেখা।
Normal User (USER)
Admin-এর দেওয়া username/password দিয়ে login করতে পারবেন
প্রথম login-এর পর বাধ্যতামূলক প্রোফাইল ১০০% পূরণ করতে পারবেন (completeProfile)
শুধু assigned Task-এ sales entry দিতে পারবেন (addRecord)
নিজের daily/monthly dashboard এবং নিজের বিক্রির ইতিহাস দেখতে পারবেন
রেকর্ড পরিবর্তনের জন্য কারণসহ Admin-এর কাছে request পাঠাতে পারবেন (requestChange)
একজন সাধারণ User অন্য কোনো User-এর প্রোফাইল, ড্যাশবোর্ড বা বিক্রির তথ্য দেখতে পারবেন না।
৩. ধাপে ধাপে সম্পূর্ণ সেটআপ গাইড (Step-by-Step Setup)
ধাপ ১: Firebase Console সেটআপ (১০০% ফ্রি Spark Plan)
১. Firebase Console-এ যান এবং Create a project (অথবা Add project)-এ ক্লিক করে একটি প্রজেক্ট তৈরি করুন (যেমন: uddokta-hisab)।
২. প্রজেক্ট ড্যাশবোর্ড থেকে Android আইকন-এ ক্লিক করে নতুন অ্যাপ রেজিস্টার করুন:

Android package name: com.uddoktahisab.app (অবশ্যই হুবহু এটি দেবেন)
App nickname: Uddokta Hisab (ঐচ্ছিক)
Register app-এ ক্লিক করুন।
৩. google-services.json ডাউনলোড করুন এবং আপনার প্রজেক্টের নিচের লোকেশনে থাকা ফাইলটি রিপ্লেস করুন:
text

app/google-services.json
৪. বাম পাশের মেনু থেকে Build ➔ Firestore Database-এ যান:

Create database-এ ক্লিক করুন।
লোকেশন নির্বাচন করুন (যেমন: asia-south1 বা ডিফল্ট) এবং Start in test mode নির্বাচন করে Create দিন।
৫. Firestore তৈরি হয়ে গেলে উপরের Rules ট্যাবে যান এবং নিচের রুলসটি দিয়ে Publish করুন (যাতে আপনার অ্যাপ এবং Google Apps Script নির্বিঘ্নে কাজ করতে পারে):
JavaScript

rules_version = '2';
service cloud.firestore {
match /databases/{database}/documents {
match /{document=**} {
allow read, write: if true;
}
}
}
ধাপ ২: Google Sheet এবং Apps Script সেটআপ
১. আপনার Google Spreadsheet খুলুন।
২. উপরের মেনু থেকে Extensions ➔ Apps Script-এ যান।
৩. সেখানে থাকা পুরোনো কোড মুছে এই প্রজেক্টের
Code.gs
ফাইলের সম্পূর্ণ কোড পেস্ট করুন এবং Save (Ctrl+S) করুন।
৪. উপরের ফাংশন ড্রপডাউন থেকে setupSystem সিলেক্ট করে একবার Run করুন (Google Authorization চাইলে Allow দিন)।
৫. এবার উপরে ডান পাশে Deploy ➔ New deployment (অথবা আগের ডিপ্লয়মেন্ট থাকলে Deploy ➔ Manage deployments ➔ Edit ➔ Version: New version)-এ যান:

Select type: Web app
Execute as: Me
Who has access: Anyone
Deploy করে /exec যুক্ত Web App URL কপি করুন।
৬. যদি নতুন URL তৈরি করে থাকেন, তবে
build.gradle.kts
ফাইলে API_URL-এ আপনার /exec URL-টি বসিয়ে দিন:
Kotlin

buildConfigField(
"String",
"API_URL",
"\"YOUR_APPS_SCRIPT_EXEC_URL\""
)
ধাপ ৩: প্রথমবার লগইন ও পুরোনো ডাটা মাইগ্রেশন
নতুন প্রজেক্ট হলে: অ্যাপ চালু করলে Firebase স্বয়ংক্রিয়ভাবে ৪টি ডিফল্ট Task এবং প্রথম Super Admin তৈরি করে দেবে:
Username: admin
Password: admin
লগইন করার পর প্রোফাইল ১০০% সম্পন্ন করুন এবং প্রয়োজনমতো নতুন ইউজার ও SKU তৈরি করুন।
আগের Google Sheet-এ ইতিমধ্যে ডাটা থাকলে:
অটোমেটিক মাইগ্রেশন: আপনি আগের Google Sheet-এর ইউজারনেম ও পাসওয়ার্ড দিয়ে অ্যাপে লগইন করলেই অ্যাপ স্বয়ংক্রিয়ভাবে আপনার অ্যাকাউন্ট Firebase-এ নিয়ে আসবে।
এক ক্লিকে সম্পূর্ণ শিট মাইগ্রেশন (ঐচ্ছিক): আপনি চাইলে Apps Script এডিটরে প্রথমে একবার অ্যাপে লগইন করার পর ফাংশন ড্রপডাউন থেকে pushSheetToFirestore সিলেক্ট করে Run করলে Google Sheet-এর সব পুরোনো ইউজার, টাস্ক, বিক্রি এবং SKU এক ক্লিকে Firebase Firestore-এ চলে যাবে!
৫ মিনিট পর পর অটো-পুল ট্রিগার (ঐচ্ছিক): Apps Script এডিটরে setupAutoSyncTrigger ফাংশনটি একবার Run করে রাখলে Google Sheet নিজে থেকেই প্রতি ৫ মিনিট অন্তর Firebase থেকে সব ডাটা টেনে আপডেট করে নেবে।
৪. Firebase Collections এবং Google Sheet Tabs ম্যাপিং
Firebase Firestore Collection	Google Sheet Tab	কী ডাটা থাকে
users	USERS	সব Super Admin ও সাধারণ User-এর অ্যাকাউন্ট এবং প্রোফাইল তথ্য
tasks	TASKS	কাজের মাস্টার লিস্ট (TASK_SALES_PCS, TASK_SALES_KG, TASK_PURCHASE, TASK_EXPENSE)
assignments	ASSIGNMENTS	কোন User-কে কোন Task অ্যাসাইন করা হয়েছে তার ম্যাপিং
sales_records	TASK_<taskId>_<taskName>	প্রতিটি টাস্কের বিক্রির হিসাব সংশ্লিষ্ট আলাদা টাস্ক শিটে সংরক্ষিত হয়
change_requests	CHANGE_REQUESTS	সাধারণ ইউজারের পাঠানো হিসাব পরিবর্তনের অনুরোধ ও অ্যাডমিনের সিদ্ধান্ত
skus	SKUS	পণ্যের স্টক (SKU), মোট কেনা, মোট খরচ, বিক্রি, বাকি ও লাভ-ক্ষতির হিসাব
sessions	SESSIONS	লগইন সেশন টোকেন ও মেয়াদ (expiresAt)
audit_logs	AUDIT_LOG	সব গুরুত্বপূর্ণ অপারেশনের (LOGIN, ADD_RECORD, ADD_SKU, DELETE_SKU ইত্যাদি) লগ
৫. অফলাইন সাপোর্ট এবং সিঙ্ক মেকানিজম
১. অফলাইন ক্যাশিং: অ্যাপে Firebase Persistent Disk Cache এবং Room Database (app_cache, pending_actions) উভয়ই সক্রিয় আছে।
২. অফলাইনে ডাটা এন্ট্রি: ইন্টারনেট না থাকলেও বিক্রি বা প্রোফাইল তথ্য সেভ করলে সাথে সাথে UI আপডেট হবে এবং লোকাল কিউতে জমা থাকবে।
৩. ইন্টারনেট ফিরে এলে: WorkManager (PendingSyncWorker) স্বয়ংক্রিয়ভাবে প্রথমে ডাটা Firebase Firestore-এ সেভ করবে এবং সাথে সাথে Firebase থেকে Google Sheet-এ সিঙ্ক করে দেবে।

৬. Android Studio Build নির্দেশনা
১. Android Studio দিয়ে প্রজেক্ট ফোল্ডারটি খুলুন।
২. নিশ্চিত করুন যে আপনার Firebase প্রজেক্টের আসল google-services.json ফাইলটি
google-services.json
পাথে বসানো হয়েছে।
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