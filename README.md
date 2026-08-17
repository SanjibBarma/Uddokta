উদ্যোক্তা হিসাব (Uddokta Hisab)
Jetpack Compose, MVVM, Room এবং Google Apps Script দিয়ে তৈরি ব্যবসার বিক্রি ও হিসাব ব্যবস্থাপনা
Android application। একই Google Spreadsheet backend হিসেবে ব্যবহৃত হয়, কিন্তু সাধারণ ব্যবহারকারী
সরাসরি Spreadsheet access করে না।

১. সিস্টেমে কারা থাকবে
Super Admin
Super Admin পারবে:

নতুন সাধারণ User তৈরি করতে
User-কে checkbox দিয়ে এক বা একাধিক Task assign করতে
নিজের Sales entry দিতে
সব User-এর আজকের ও মাসিক বিক্রির summary দেখতে
নির্দিষ্ট User-এর বিস্তারিত sales records দেখতে
User-এর record পরিবর্তনের request approve/reject করতে
Normal User
Normal User পারবে:

Admin-এর দেওয়া username/password দিয়ে login করতে
প্রথম login-এর পর বাধ্যতামূলক profile পূরণ করতে
শুধু assigned Task-এ sales entry দিতে
নিজের daily/monthly dashboard দেখতে
শুধু নিজের records দেখতে
record পরিবর্তনের জন্য Admin-এর কাছে request পাঠাতে
একজন User অন্য User-এর profile, dashboard বা sales data দেখতে পারবে না।

২. Application flow
text

App চালু
↓
Login Screen
↓
Online username/password verification
↓
profileComplete = FALSE হলে
Mandatory Profile Completion Screen
↓
Profile submit
↓
Dashboard
Profile অসম্পূর্ণ থাকলে Dashboard, Sales, History বা Admin feature খোলা যাবে না।

Authentication token শুধু app process-এর memory-তে থাকে। Room, DataStore বা SharedPreferences-এ
login token, login username অথবা login password সংরক্ষণ করা হয় না। App force-stop/process kill/fresh
launch হলে আবার online login করতে হবে।

৩. Google Sheet এবং Apps Script সেটআপ
Apps Script file
Project-এর backend code:

text

backend/Code.gs
সহজে আলাদা করে দেওয়া file:

text

Google-Sheet-Code.gs
Installation
প্রয়োজনীয় Google Spreadsheet খুলুন।
Extensions → Apps Script নির্বাচন করুন।
default myFunction() code সম্পূর্ণ মুছে দিন।
Google-Sheet-Code.gs-এর সম্পূর্ণ code paste করুন।
Save করুন।
function dropdown থেকে setupSystem নির্বাচন করে একবার Run করুন।
Google authorization চাইলে Allow করুন।
Deploy → New deployment → Web app নির্বাচন করুন।
Execute as: Me নির্বাচন করুন।
Who has access: Anyone নির্বাচন করুন।
Deploy করে /exec URL সংগ্রহ করুন।
বর্তমান Android project-এ ব্যবহৃত URL:

text

https://script.google.com/macros/s/AKfycbxGobcCIqPwoPArb97gJ-48Q-Fw5KJ4cXWem4yx7USX-w60mPhLcscqTttEzxoUpNDX/exec
URL পরিবর্তন করতে
build.gradle.kts
:

Kotlin

buildConfigField(
"String",
"API_URL",
"\"YOUR_APPS_SCRIPT_EXEC_URL\""
)
Apps Script পরিবর্তনের পর শুধু Save যথেষ্ট নয়:

text

Deploy
→ Manage deployments
→ Edit
→ Version: New version
→ Deploy
৪. কোন কোন Google Sheet tab তৈরি হবে
setupSystem() অথবা app-এর initialize API প্রথমবার চললে নিচের core tabs তৈরি হবে:

text

USERS
TASKS
ASSIGNMENTS
CHANGE_REQUESTS
SESSIONS
AUDIT_LOG
প্রথম sales record যোগ হলে সংশ্লিষ্ট Task-এর জন্য আলাদা sheet tab তৈরি হবে। উদাহরণ:

text

TASK_TASK_SALES_PCS_দৈনিক পিস বিক্রি
TASK_TASK_SALES_KG_দৈনিক কেজি বিক্রি
প্রতিটি Task-এর records আলাদা Task sheet-এ যাবে।

৫. USERS sheet
সব Admin এবং User account ও profile এখানে থাকবে।

Column Field কী থাকবে
A id Unique User ID
B username Login username
C passwordHash SHA-256 password hash; প্রথম Admin-এর ক্ষেত্রে শুরুতে plain temporary password দেওয়া
যায়
D role SUPER_ADMIN অথবা USER
E fullName User-এর পূর্ণ নাম
F presentAddress বর্তমান ঠিকানা
G permanentAddress স্থায়ী ঠিকানা
H phone User-এর ফোন নম্বর
I fatherPhone বাবার ফোন নম্বর
J nid জাতীয় পরিচয়পত্র নম্বর
K profileComplete TRUE অথবা FALSE
L active Account সক্রিয় হলে TRUE
M createdAt Account creation timestamp
প্রথম Super Admin manually যোগ করা
USERS sheet-এর দ্বিতীয় row:

text

A2 = ADMIN-001
B2 = test
C2 = test
D2 = SUPER_ADMIN
E2:J2 = খালি
K2 = FALSE
L2 = TRUE
M2 = খালি রাখা যাবে
Login:

text

Username: test
Password: test
প্রথম সফল login-এর পর C2-এর plain password স্বয়ংক্রিয়ভাবে SHA-256 hash-এ পরিবর্তিত হবে। এরপরও login
password test-ই থাকবে।

profileComplete = FALSE থাকার কারণে login সফল হওয়ার পর Profile Completion screen দেখাবে।

App থেকে সাধারণ User তৈরি হলে
Admin শুধু temporary username এবং password দেবে। Script USERS sheet-এ এমন row তৈরি করবে:

text

id = generated UUID
username = Admin-এর দেওয়া username
passwordHash = SHA-256 hash
role = USER
profile fields = খালি
profileComplete = FALSE
active = TRUE
createdAt = server timestamp
User প্রথমবার login করে নিজে profile পূরণ করবে।

৬. TASKS sheet
কোন কোন কাজ User-কে assign করা যাবে তার master list।

Column Field কী থাকবে
A id Unique Task ID
B name Task-এর প্রদর্শিত নাম
C unit pcs, kg, tk ইত্যাদি
D active Task চালু থাকলে TRUE
E createdAt Task তৈরির সময়
Default Task:

text

TASK_SALES_PCS | দৈনিক পিস বিক্রি | pcs | TRUE
TASK_SALES_KG | দৈনিক কেজি বিক্রি | kg | TRUE
TASK_PURCHASE | ক্রয় হিসাব | pcs | TRUE
TASK_EXPENSE | দৈনিক খরচ | tk | TRUE
নতুন Task manually যোগ করা যাবে। শর্ত:

id অবশ্যই unique হতে হবে
name খালি রাখা যাবে না
unit দিতে হবে
active = TRUE হতে হবে
কোনো record তৈরি হওয়ার পর Task ID পরিবর্তন করা যাবে না
৭. ASSIGNMENTS sheet
কোন User-কে কোন Task দেওয়া হয়েছে তার mapping।

Column Field কী থাকবে
A userId USERS.id
B taskId TASKS.id
C active Assignment চালু থাকলে TRUE
D assignedBy যে Admin assign করেছে তার ID
E updatedAt সর্বশেষ assignment সময়
Admin app থেকে checkbox নির্বাচন করে Save করলে:

নির্বাচিত Task-এর assignment TRUE হবে
বাদ দেওয়া Task-এর পুরোনো assignment FALSE হবে
Normal User শুধু active assigned Tasks দেখতে পাবে
Super Admin সব active Tasks ব্যবহার করতে পারবে
৮. Task-specific sales sheets
প্রতিটি Task-এর জন্য একই Spreadsheet-এর ভেতরে আলাদা sheet তৈরি হয়। Sheet name Task ID ও Task name
থেকে তৈরি হয়।

প্রতিটি Task sheet-এর columns:

Column Field কী থাকবে
A id Unique record ID
B userId যে User entry দিয়েছে তার ID
C userName User-এর নাম
D date Sales date, YYYY-MM-DD
E quantity কয়টি/কত kg বিক্রি হয়েছে
F unit pcs, kg ইত্যাদি
G unitPrice প্রতি unit-এর বিক্রয় মূল্য
H total quantity × unitPrice
I note Optional note
J createdAt Server timestamp
K createdBy Entry creator User ID
L updatedAt Admin-approved update হলে সময়
উদাহরণ:

text

Task = দৈনিক কেজি বিক্রি
Date = 2026-08-17
Quantity = 25
Unit = kg
UnitPrice= 80
Total = 2000
Sheet row-তে total = 25 × 80 = 2000 যাবে।

Sales entry process
text

User → Sales tab
→ Assigned Task নির্বাচন
→ Date
→ Quantity
→ Unit price
→ Note
→ Save
Script server-side আবার quantity, price, Task assignment ও date format validate করে। শুধু UI
validation-এর ওপর নির্ভর করে না।

৯. Dashboard calculation
Dashboard Apps Script Task sheets থেকে logged-in User-এর records filter করে তৈরি করে।

আজকের হিসাব
শুধু বর্তমান তারিখের records:

text

Today Quantity = আজকের সব quantity-এর যোগফল
Today Sales = আজকের সব total-এর যোগফল
মাসিক হিসাব
বর্তমান YYYY-MM দিয়ে শুরু হওয়া records:

text

Month Quantity = এই মাসের সব quantity-এর যোগফল
Month Sales = এই মাসের সব total-এর যোগফল
Normal User-এর response-এ শুধু তার নিজের data থাকে। Super Admin-এর Admin screen-এ প্রতিটি User-এর
আলাদা summary ও recent records থাকে।

বিভিন্ন unit-এর quantity একই dashboard total-এ যোগ করা হলে সেটি combined quantity হিসেবে দেখাবে। kg
ও pcs আলাদা summary প্রয়োজন হলে ভবিষ্যতে dashboard-কে Task/unit অনুযায়ী group করতে হবে।

১০. Record পরিবর্তন ও Admin permission
Normal User সরাসরি existing record edit করতে পারে না।

Process:

text

User → History
→ একটি record-এর Edit/Request button
→ নতুন quantity
→ নতুন unit price
→ নতুন note
→ পরিবর্তনের কারণ
→ Request পাঠানো
একই record-এর একটি request PENDING থাকলে আরেকটি pending request করা যাবে না। পুরোনো request
approve/reject হওয়ার পর নতুন request করা যাবে।

১১. CHANGE_REQUESTS sheet
Column Field কী থাকবে
A id Request ID
B recordId যে sales record পরিবর্তন হবে
C userId Request করা User ID
D userName User-এর নাম
E taskId সংশ্লিষ্ট Task ID
F reason পরিবর্তনের কারণ
G newQuantity প্রস্তাবিত নতুন quantity
H newUnitPrice প্রস্তাবিত নতুন unit price
I newNote প্রস্তাবিত নতুন note
J status PENDING, APPROVED, REJECTED
K createdAt Request সময়
L decidedBy সিদ্ধান্ত নেওয়া Admin ID
M decidedAt সিদ্ধান্তের সময়
Approve হলে
মূল Task sheet-এর record update হবে
quantity, unitPrice, total, note পরিবর্তিত হবে
updatedAt বসবে
Request status APPROVED হবে
Reject হলে
মূল sales record অপরিবর্তিত থাকবে
Request status REJECTED হবে
১২. SESSIONS sheet
Successful login-এর server session এখানে থাকবে।

Column Field কী থাকবে
A token Random session token
B userId Login করা User ID
C expiresAt Server session expiry
Server session সর্বোচ্চ ৩০ দিন valid হতে পারে। তবে Android app token local persistence-এ রাখে না।
App process বন্ধ হলে local in-memory token হারিয়ে যাবে এবং আবার login প্রয়োজন হবে। Logout করলে
সংশ্লিষ্ট server session row delete করা হয়।

Google Sheet-এর SESSIONS tab শুধুমাত্র Sheet owner/Admin-এর জন্য; কখনো User-এর সঙ্গে Sheet share করা
যাবে না।

১৩. AUDIT_LOG sheet
গুরুত্বপূর্ণ operation-এর history:

Column Field কী থাকবে
A id Audit ID
B userId কাজটি করা User/Admin ID
C action Operation name
D details Record/User/Task সম্পর্কিত তথ্য
E createdAt Operation timestamp
Audit action-এর উদাহরণ:

text

LOGIN
PROFILE_COMPLETED
CREATE_USER
ASSIGN_TASKS
ADD_RECORD
REQUEST_CHANGE
APPROVE_CHANGE
REJECT_CHANGE
১৪. Offline-first operation
Business data-এর জন্য Room local database ব্যবহৃত হয়।

Local tables:

text

app_cache
pending_actions
app_cache
শেষ successful dashboard/bootstrap response JSON হিসেবে রাখে। এতে user profile, tasks, dashboard,
recent records এবং Admin হলে user summaries থাকতে পারে। এই cache successful online login ছাড়া দেখানো
হয় না।

pending_actions
Internet না থাকলে business mutation ordered queue হিসেবে রাখা হয়। উদাহরণ:

text

addRecord
completeProfile
requestChange
assignTasks
decideChangeRequest
প্রতিটি remote call-এর আগে Android NET_CAPABILITY_INTERNET এবং NET_CAPABILITY_VALIDATED check করে।

Offline sales flow
text

Sales Save
→ Local Room update
→ Pending queue
→ UI-তে সঙ্গে সঙ্গে result
→ Internet ফিরে আসে
→ WorkManager sync
→ Server response
→ Google Sheet update
→ Fresh dashboard cache
Offline indicator:

text

Offline mode • ডাটা পরে sync হবে
Authentication limitation
প্রথম login সবসময় online হতে হবে
Login credentials local database-এ রাখা হয় না
Session token memory-only
App process বন্ধ হলে আবার login
Pending business actions কোনো persisted session token রাখে না
পরবর্তী successful login-এর current session দিয়ে pending actions sync হয়
১৫. Android architecture
text

UI (Jetpack Compose)
↓
AppViewModel
↓
AppRepository
↙ ↘
Room/LocalStore Retrofit/Apps Script
↓
WorkManager Pending Sync
প্রধান technologies:

Kotlin
Jetpack Compose + Material 3
MVVM
Hilt dependency injection
Retrofit + OkHttp
Room database
WorkManager
Kotlin Coroutines/StateFlow
Google Apps Script
Google Spreadsheet
১৬. Android project build
UddoktaHisab-source.zip extract করুন।
Android Studio দিয়ে UddoktaHisab folder খুলুন।
JDK 17 নির্বাচন করুন।
Gradle Sync করুন।
Compile SDK 35 install থাকতে হবে।
Android 8.0/API 26 বা পরবর্তী device/emulator-এ Run করুন।
Configuration:

text

applicationId = com.uddoktahisab.app
minSdk = 26
targetSdk = 35
compileSdk = 35
১৭. দৈনন্দিন ব্যবহার
প্রথমবার Owner/Admin
Apps Script setup ও deploy করুন।
USERS sheet-এ প্রথম Admin row দিন।
App-এ Admin login করুন।
বাধ্যতামূলক profile পূরণ করুন।
প্রয়োজন হলে TASKS sheet-এ Task যোগ করুন।
App থেকে User তৈরি করুন।
User-কে username/password দিন।
Checkbox দিয়ে Task assign করুন।
Normal User
Admin-এর credentials দিয়ে online login।
Profile পূরণ।
Sales tab থেকে assigned Task নির্বাচন।
Sales save।
Dashboard/History থেকে নিজের হিসাব দেখা।
ভুল হলে direct edit নয়—change request পাঠানো।
Admin approval
Admin tab খুলুন।
অনুমতির অনুরোধ নির্বাচন করুন।
কারণ ও নতুন তথ্য দেখুন।
Approve অথবা Reject করুন।
Approve হলে Task sheet record স্বয়ংক্রিয়ভাবে update হবে।
১৮. গুরুত্বপূর্ণ নিরাপত্তা
Google Spreadsheet সাধারণ User-এর সঙ্গে share করবেন না।
Apps Script Execute as Me রাখুন।
Spreadsheet owner account-এ 2-Step Verification চালু রাখুন।
USERS, SESSIONS এবং profile columns-এ সংবেদনশীল তথ্য রয়েছে।
NID ও phone data-এর access সীমিত রাখুন।
Production ব্যবহারে নিয়মিত backup নিন।
Apps Script deployment URL public client-এর মধ্যে থাকে; নিরাপত্তা URL লুকানোর ওপর নয়, server-side
authentication/authorization-এর ওপর নির্ভর করে।
বড় ব্যবসা বা উচ্চ ঝুঁকির আর্থিক ব্যবহারের জন্য Firebase Authentication/Google Identity এবং dedicated
database বিবেচনা করুন।
১৯. Troubleshooting
Username/password incorrect
USERS row-এ নিশ্চিত করুন:

text

username = সঠিক
passwordHash = প্রথম login-এর আগে plain temporary password অথবা generated hash
role = SUPER_ADMIN অথবা USER
profileComplete = FALSE/TRUE
active = TRUE
active খালি থাকলে login হবে না।

Login-এর পর Login screen-এই “প্রোফাইল ১০০% সম্পন্ন করুন”
Updated Apps Script-এ incomplete profile-এর জন্য bootstrap অনুমোদিত থাকতে হবে। Updated
Google-Sheet-Code.gs paste করে New version deploy করুন।

Apps Script save করার পর পরিবর্তন দেখা যাচ্ছে না
text

Deploy → Manage deployments → Edit → New version → Deploy
Sheet tabs তৈরি হয়নি
Apps Script editor থেকে setupSystem() Run করুন অথবা deployed API-এর initialize action call করুন।
তারপর Spreadsheet refresh করুন।

Offline data Sheet-এ যায়নি
Internet validated হয়েছে কি না দেখুন
App-এ আবার online login করুন
Pending actions current session দিয়ে sync হওয়ার সময় দিন
WorkManager/background restrictions বন্ধ আছে কি না দেখুন
API ধীর
Google Apps Script cold start-এর কারণে প্রথম request ধীর হতে পারে। Backend-এ setupSystem() শুধু
initialize action-এ চলে; প্রতিটি login/bootstrap/sales request-এ নয়। Android cached data আগে দেখায়
এবং OkHttp connection reuse করে।