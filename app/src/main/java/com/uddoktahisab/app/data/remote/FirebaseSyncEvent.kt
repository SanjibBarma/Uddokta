package com.uddoktahisab.app.data.remote

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import com.uddoktahisab.app.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Cloud Firestore Data Source (100% Free Spark Plan).
 *
 * ডাটা ফ্লো:
 * 1. অ্যাপের সব Create / Read / Update / Delete প্রথমে সরাসরি Firebase Firestore-এ সম্পন্ন হয়।
 * 2. প্রতিটি পরিবর্তনের পর FirebaseSyncEvent তৈরি হয়, যা ব্যাকগ্রাউন্ডে Google Apps Script (Code.gs)-কে
 *    ট্রিগার করে এবং Firebase থেকে Google Sheet-এর সংশ্লিষ্ট ট্যাবে ডাটা সিঙ্ক করে।
 */
data class FirebaseSyncEvent(
    val operation: String, // UPSERT or DELETE or FULL_SYNC
    val collection: String,
    val docId: String,
    val document: Map<String, Any?> = emptyMap(),
    val audit: Map<String, Any?>? = null,
    val secondaryCollection: String? = null,
    val secondaryDocId: String? = null,
    val secondaryDocument: Map<String, Any?>? = null
)

data class FirebaseActionResult(
    val message: String,
    val syncEvents: List<FirebaseSyncEvent>
)

@Singleton
class FirebaseDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val COL_USERS = "users"
        const val COL_TASKS = "tasks"
        const val COL_ASSIGNMENTS = "assignments"
        const val COL_RECORDS = "sales_records"
        const val COL_REQUESTS = "change_requests"
        const val COL_SKUS = "skus"
        const val COL_SESSIONS = "sessions"
        const val COL_AUDIT = "audit_logs"
        const val COL_SYNC_QUEUE = "sheet_sync_queue"

        private const val PLACEHOLDER_PROJECT_ID = "uddokta-hisab-placeholder"
        private val DHAKA_ZONE: ZoneId = ZoneId.of("Asia/Dhaka")
    }

    private val firestore: FirebaseFirestore? by lazy {
        runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            val app = FirebaseApp.getInstance()
            if (app.options.projectId.isNullOrBlank() || app.options.projectId == PLACEHOLDER_PROJECT_ID) {
                null
            } else {
                FirebaseFirestore.getInstance(app).apply {
                    val settings = FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                        .build()
                    firestoreSettings = settings
                }
            }
        }.getOrNull()
    }

    fun isConfigured(): Boolean = firestore != null

    fun firebaseMetadata(): Map<String, String> {
        return runCatching {
            val opts = FirebaseApp.getInstance().options
            mapOf(
                "firebaseProjectId" to (opts.projectId ?: ""),
                "firebaseApiKey" to (opts.apiKey ?: "")
            )
        }.getOrDefault(emptyMap())
    }

    private fun db(): FirebaseFirestore =
        firestore ?: throw IllegalStateException(
            "Firebase কনফিগার করা হয়নি। Firebase Console থেকে google-services.json ডাউনলোড করে app/google-services.json ফাইলে বসান।"
        )

    // ─── Real-Time Listener ───
    fun observeCollections(onChanged: () -> Unit): List<ListenerRegistration> {
        val f = firestore ?: return emptyList()
        val collections = listOf(COL_RECORDS, COL_REQUESTS, COL_SKUS, COL_USERS, COL_ASSIGNMENTS, COL_TASKS)
        return collections.map { col ->
            f.collection(col).addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null && !snapshot.metadata.isFromCache) {
                    onChanged()
                }
            }
        }
    }

    // ─── System Initialization (Default Tasks & Default Super Admin) ───
    suspend fun initializeSystem(): List<FirebaseSyncEvent> {
        val f = db()
        val events = mutableListOf<FirebaseSyncEvent>()

        // 1. Default Tasks
        val tasksSnap = f.collection(COL_TASKS).get().await()
        if (tasksSnap.isEmpty) {
            val defaultTasks = listOf(
                Triple("TASK_SALES_PCS", "দৈনিক পিস বিক্রি", "pcs"),
                Triple("TASK_SALES_KG", "দৈনিক কেজি বিক্রি", "kg"),
                Triple("TASK_PURCHASE", "ক্রয় হিসাব", "pcs"),
                Triple("TASK_EXPENSE", "দৈনিক খরচ", "tk")
            )
            for ((id, name, unit) in defaultTasks) {
                val doc = mapOf(
                    "id" to id,
                    "name" to name,
                    "unit" to unit,
                    "active" to true,
                    "createdAt" to nowIso()
                )
                f.collection(COL_TASKS).document(id).set(doc, SetOptions.merge()).await()
                events.add(FirebaseSyncEvent("UPSERT", COL_TASKS, id, doc))
            }
        }

        // 2. Default Super Admin if no users exist yet
        val usersSnap = f.collection(COL_USERS).get().await()
        if (usersSnap.isEmpty) {
            val adminId = "ADMIN-001"
            val adminDoc = mapOf(
                "id" to adminId,
                "username" to "admin",
                "passwordHash" to sha256("admin"),
                "role" to "SUPER_ADMIN",
                "fullName" to "",
                "presentAddress" to "",
                "permanentAddress" to "",
                "phone" to "",
                "fatherPhone" to "",
                "nid" to "",
                "profileComplete" to false,
                "active" to true,
                "createdAt" to nowIso()
            )
            f.collection(COL_USERS).document(adminId).set(adminDoc, SetOptions.merge()).await()
            events.add(FirebaseSyncEvent("UPSERT", COL_USERS, adminId, adminDoc))
        }

        return events
    }

    // ─── Import User from Google Sheet if migrating existing Sheet account ───
    suspend fun upsertUserFromSheet(user: User, plainPassword: String): FirebaseSyncEvent {
        val f = db()
        val doc = mapOf(
            "id" to user.id,
            "username" to normalizeUsername(user.username),
            "passwordHash" to sha256(plainPassword),
            "role" to user.role.name,
            "fullName" to user.fullName,
            "presentAddress" to user.presentAddress,
            "permanentAddress" to user.permanentAddress,
            "phone" to user.phone,
            "fatherPhone" to user.fatherPhone,
            "nid" to user.nid,
            "profileComplete" to user.profileComplete,
            "active" to user.active,
            "createdAt" to nowIso()
        )
        f.collection(COL_USERS).document(user.id).set(doc, SetOptions.merge()).await()
        return FirebaseSyncEvent("UPSERT", COL_USERS, user.id, doc)
    }

    // ─── Login via Firebase Firestore ───
    suspend fun login(username: String, password: String): Pair<LoginData, List<FirebaseSyncEvent>> {
        val f = db()
        initializeSystem()

        val normalized = normalizeUsername(username)
        val usersSnap = f.collection(COL_USERS).get().await()
        val userDoc = usersSnap.documents.find {
            normalizeUsername(it.getString("username").orEmpty()) == normalized
        } ?: throw IllegalStateException("ইউজারনেম অথবা পাসওয়ার্ড সঠিক নয়")

        val active = docBool(userDoc, "active", true)
        if (!active) throw IllegalStateException("ইউজারনেম অথবা পাসওয়ার্ড সঠিক নয়")

        val storedHash = userDoc.getString("passwordHash").orEmpty()
        val looksHashed = storedHash.matches(Regex("^[a-fA-F0-9]{64}$"))
        val computedHash = sha256(password)
        val valid = storedHash.equals(computedHash, ignoreCase = true) || (!looksHashed && storedHash == password)
        if (!valid) throw IllegalStateException("ইউজারনেম অথবা পাসওয়ার্ড সঠিক নয়")

        val syncEvents = mutableListOf<FirebaseSyncEvent>()
        val userId = userDoc.getString("id").orEmpty().ifBlank { userDoc.id }

        // Plain temporary password হলে স্বয়ংক্রিয়ভাবে SHA-256 hash-এ আপডেট করি
        if (!looksHashed && storedHash == password) {
            f.collection(COL_USERS).document(userId).update("passwordHash", computedHash).await()
        }

        val token = UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "")
        val expiresAt = Instant.now().plus(30, ChronoUnit.DAYS).toString()
        val sessionMap = mapOf(
            "token" to token,
            "userId" to userId,
            "expiresAt" to expiresAt
        )
        f.collection(COL_SESSIONS).document(token).set(sessionMap).await()

        val auditMap = createAudit(f, userId, "LOGIN", "")
        val user = snapshotToUser(userDoc)
        val updatedUserMap = userToMap(user, computedHash, userDoc.getString("createdAt") ?: nowIso())

        syncEvents.add(
            FirebaseSyncEvent(
                operation = "UPSERT",
                collection = COL_SESSIONS,
                docId = token,
                document = sessionMap,
                audit = auditMap,
                secondaryCollection = COL_USERS,
                secondaryDocId = userId,
                secondaryDocument = updatedUserMap
            )
        )
        recordSyncEventsInFirestore(f, syncEvents)

        return LoginData(token = token, user = user) to syncEvents
    }

    // ─── Authenticate Token ───
    suspend fun authenticate(token: String): User {
        if (token.isBlank()) throw IllegalStateException("সেশন পাওয়া যায়নি")
        val f = db()
        val sessionSnap = f.collection(COL_SESSIONS).document(token).get().await()
        if (!sessionSnap.exists()) throw IllegalStateException("সেশন শেষ হয়েছে। আবার লগইন করুন")

        val expiresAt = sessionSnap.getString("expiresAt").orEmpty()
        val expired = runCatching { Instant.parse(expiresAt).isBefore(Instant.now()) }.getOrDefault(false)
        if (expired) throw IllegalStateException("সেশন শেষ হয়েছে। আবার লগইন করুন")

        val userId = sessionSnap.getString("userId").orEmpty()
        val userSnap = f.collection(COL_USERS).document(userId).get().await()
        if (!userSnap.exists() || !docBool(userSnap, "active", true)) {
            throw IllegalStateException("অ্যাকাউন্ট নিষ্ক্রিয়")
        }
        return snapshotToUser(userSnap)
    }

    // ─── Bootstrap (Read All Needed Dashboard & Admin Data from Firebase) ───
    suspend fun bootstrap(token: String): BootstrapData {
        val actor = authenticate(token)
        val f = db()

        val allTasksSnap = f.collection(COL_TASKS).get().await()
        val activeTasks = allTasksSnap.documents
            .filter { docBool(it, "active", true) }
            .map {
                Task(
                    id = it.getString("id").orEmpty().ifBlank { it.id },
                    name = it.getString("name").orEmpty(),
                    unit = it.getString("unit").orEmpty().ifBlank { "pcs" },
                    assigned = true
                )
            }

        val allAssignSnap = f.collection(COL_ASSIGNMENTS).get().await()
        val activeAssignments = allAssignSnap.documents
            .filter { docBool(it, "active", true) }
            .map {
                Assignment(
                    userId = it.getString("userId").orEmpty(),
                    taskId = it.getString("taskId").orEmpty(),
                    active = true
                )
            }

        val actorTasks = if (actor.role == Role.SUPER_ADMIN) {
            activeTasks
        } else {
            val assignedIds = activeAssignments.filter { it.userId == actor.id }.map { it.taskId }.toSet()
            activeTasks.filter { it.id in assignedIds }
        }

        val recordsSnap = f.collection(COL_RECORDS).get().await()
        val taskMap = activeTasks.associateBy { it.id }
        val allRecords = recordsSnap.documents.map { doc ->
            val tId = doc.getString("taskId").orEmpty()
            val tName = doc.getString("taskName").orEmpty().ifBlank { taskMap[tId]?.name.orEmpty() }
            val qty = docDouble(doc, "quantity")
            val price = docDouble(doc, "unitPrice")
            val total = docDouble(doc, "total").let { if (it > 0.0) it else qty * price }
            SaleRecord(
                id = doc.getString("id").orEmpty().ifBlank { doc.id },
                userId = doc.getString("userId").orEmpty(),
                userName = doc.getString("userName").orEmpty(),
                taskId = tId,
                taskName = tName,
                date = doc.getString("date").orEmpty(),
                quantity = qty,
                unit = doc.getString("unit").orEmpty().ifBlank { taskMap[tId]?.unit ?: "pcs" },
                unitPrice = price,
                total = total,
                note = doc.getString("note").orEmpty(),
                createdAt = doc.getString("createdAt").orEmpty()
            )
        }

        val actorDashboard = buildDashboard(actor.id, allRecords)

        if (actor.role != Role.SUPER_ADMIN) {
            return BootstrapData(
                user = actor,
                tasks = actorTasks,
                dashboard = actorDashboard
            )
        }

        // SUPER_ADMIN data
        val usersSnap = f.collection(COL_USERS).get().await()
        val allUsers = usersSnap.documents.map { snapshotToUser(it) }
        val otherUsers = allUsers.filter { it.id != actor.id }
        val userSummaries = allUsers.map { u ->
            UserSummary(
                user = u,
                dashboard = buildDashboard(u.id, allRecords)
            )
        }

        val reqSnap = f.collection(COL_REQUESTS).get().await()
        val requests = reqSnap.documents.map { doc ->
            ChangeRequest(
                id = doc.getString("id").orEmpty().ifBlank { doc.id },
                recordId = doc.getString("recordId").orEmpty(),
                userId = doc.getString("userId").orEmpty(),
                userName = doc.getString("userName").orEmpty(),
                reason = doc.getString("reason").orEmpty(),
                newQuantity = docDouble(doc, "newQuantity"),
                newUnitPrice = docDouble(doc, "newUnitPrice"),
                newNote = doc.getString("newNote").orEmpty(),
                status = doc.getString("status").orEmpty().ifBlank { "PENDING" },
                createdAt = doc.getString("createdAt").orEmpty()
            )
        }.sortedByDescending { it.createdAt }

        val skusSnap = f.collection(COL_SKUS).get().await()
        val statsByUnit = mutableMapOf<String, Pair<Double, Double>>()
        for (r in allRecords) {
            val u = r.unit
            val current = statsByUnit[u] ?: (0.0 to 0.0)
            statsByUnit[u] = (current.first + r.quantity) to (current.second + r.total)
        }
        val skus = skusSnap.documents.map { doc ->
            val unit = doc.getString("unit").orEmpty().ifBlank { "kg" }
            val totalStock = docDouble(doc, "totalStock")
            val totalCost = docDouble(doc, "totalCost")
            val (sold, revenue) = statsByUnit[unit] ?: (0.0 to 0.0)
            val remaining = (totalStock - sold).coerceAtLeast(0.0)
            val profit = revenue - totalCost
            Sku(
                id = doc.getString("id").orEmpty().ifBlank { doc.id },
                name = doc.getString("name").orEmpty(),
                unit = unit,
                totalStock = totalStock,
                totalCost = totalCost,
                totalSold = sold,
                remaining = remaining,
                totalRevenue = revenue,
                profit = profit,
                createdBy = doc.getString("createdBy").orEmpty(),
                createdAt = doc.getString("createdAt").orEmpty()
            )
        }

        return BootstrapData(
            user = actor,
            tasks = actorTasks,
            dashboard = actorDashboard,
            users = otherUsers,
            requests = requests,
            assignments = activeAssignments,
            userSummaries = userSummaries,
            skus = skus
        )
    }

    // ─── Perform Business Action in Firebase First ───
    suspend fun performAction(
        token: String,
        action: String,
        payload: Map<String, Any?>
    ): FirebaseActionResult {
        val f = db()
        if (action == "logout") {
            f.collection(COL_SESSIONS).document(token).delete().await()
            val ev = FirebaseSyncEvent("DELETE", COL_SESSIONS, token, mapOf("token" to token))
            recordSyncEventsInFirestore(f, listOf(ev))
            return FirebaseActionResult("লগআউট হয়েছে", listOf(ev))
        }

        val actor = authenticate(token)
        if (action != "completeProfile" && !actor.profileComplete) {
            throw IllegalStateException("প্রথমে প্রোফাইল ১০০% সম্পন্ন করুন")
        }

        val result = when (action) {
            "completeProfile" -> completeProfile(f, actor, payload)
            "addRecord" -> addRecord(f, actor, payload)
            "requestChange" -> requestChange(f, actor, payload)
            "createUser" -> {
                requireAdmin(actor)
                createUser(f, actor, payload)
            }
            "assignTasks" -> {
                requireAdmin(actor)
                assignTasks(f, actor, payload)
            }
            "decideChangeRequest" -> {
                requireAdmin(actor)
                decideChangeRequest(f, actor, payload)
            }
            "addSku" -> {
                requireAdmin(actor)
                addSku(f, actor, payload)
            }
            "addPurchase" -> {
                requireAdmin(actor)
                addPurchase(f, actor, payload)
            }
            "deleteSku" -> {
                requireAdmin(actor)
                deleteSku(f, actor, payload)
            }
            else -> throw IllegalStateException("Unknown action: $action")
        }

        recordSyncEventsInFirestore(f, result.syncEvents)
        return result
    }

    private suspend fun completeProfile(
        f: FirebaseFirestore,
        actor: User,
        p: Map<String, Any?>
    ): FirebaseActionResult {
        if (actor.profileComplete) {
            throw IllegalStateException("সম্পন্ন প্রোফাইল সরাসরি পরিবর্তন করা যাবে না")
        }
        val requiredKeys = listOf("fullName", "presentAddress", "permanentAddress", "phone", "fatherPhone", "nid")
        for (k in requiredKeys) {
            if (p[k]?.toString()?.trim().isNullOrEmpty()) {
                throw IllegalStateException("সব তথ্য পূরণ করা আবশ্যক")
            }
        }
        val updates = mapOf(
            "fullName" to p["fullName"].toString().trim(),
            "presentAddress" to p["presentAddress"].toString().trim(),
            "permanentAddress" to p["permanentAddress"].toString().trim(),
            "phone" to p["phone"].toString().trim(),
            "fatherPhone" to p["fatherPhone"].toString().trim(),
            "nid" to p["nid"].toString().trim(),
            "profileComplete" to true
        )
        f.collection(COL_USERS).document(actor.id).set(updates, SetOptions.merge()).await()
        val updatedSnap = f.collection(COL_USERS).document(actor.id).get().await()
        val audit = createAudit(f, actor.id, "PROFILE_COMPLETED", "")

        val ev = FirebaseSyncEvent(
            operation = "UPSERT",
            collection = COL_USERS,
            docId = actor.id,
            document = updatedSnap.data.orEmpty() + updates + ("id" to actor.id),
            audit = audit
        )
        return FirebaseActionResult("প্রোফাইল সম্পন্ন হয়েছে", listOf(ev))
    }

    private suspend fun addRecord(
        f: FirebaseFirestore,
        actor: User,
        p: Map<String, Any?>
    ): FirebaseActionResult {
        val taskId = p["taskId"]?.toString().orEmpty()
        val taskSnap = f.collection(COL_TASKS).document(taskId).get().await()
        if (!taskSnap.exists() || !docBool(taskSnap, "active", true)) {
            throw IllegalStateException("এই কাজটি আপনার জন্য অ্যাসাইন করা নেই")
        }
        if (actor.role != Role.SUPER_ADMIN) {
            val assignSnap = f.collection(COL_ASSIGNMENTS).document("${actor.id}_$taskId").get().await()
            val assigned = if (assignSnap.exists()) {
                docBool(assignSnap, "active", false)
            } else {
                val query = f.collection(COL_ASSIGNMENTS)
                    .whereEqualTo("userId", actor.id)
                    .whereEqualTo("taskId", taskId)
                    .get().await()
                query.documents.any { docBool(it, "active", false) }
            }
            if (!assigned) throw IllegalStateException("এই কাজটি আপনার জন্য অ্যাসাইন করা নেই")
        }

        val qty = (p["quantity"] as? Number)?.toDouble() ?: p["quantity"]?.toString()?.toDoubleOrNull() ?: 0.0
        val price = (p["unitPrice"] as? Number)?.toDouble() ?: p["unitPrice"]?.toString()?.toDoubleOrNull() ?: -1.0
        if (qty <= 0.0 || price < 0.0 || !price.isFinite()) {
            throw IllegalStateException("পরিমাণ বা মূল্য সঠিক নয়")
        }
        val date = p["date"]?.toString().orEmpty().trim()
        if (!date.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
            throw IllegalStateException("তারিখ YYYY-MM-DD ফরম্যাটে দিন")
        }

        val taskName = taskSnap.getString("name").orEmpty()
        val taskUnit = taskSnap.getString("unit").orEmpty().ifBlank { "pcs" }
        val recordId = UUID.randomUUID().toString()
        val recordDoc = mapOf(
            "id" to recordId,
            "userId" to actor.id,
            "userName" to actor.fullName.ifBlank { actor.username },
            "taskId" to taskId,
            "taskName" to taskName,
            "date" to date,
            "quantity" to qty,
            "unit" to taskUnit,
            "unitPrice" to price,
            "total" to (qty * price),
            "note" to p["note"]?.toString().orEmpty().trim(),
            "createdAt" to nowIso(),
            "createdBy" to actor.id,
            "updatedAt" to ""
        )
        f.collection(COL_RECORDS).document(recordId).set(recordDoc).await()
        val audit = createAudit(f, actor.id, "ADD_RECORD", "$taskId:$recordId")

        val ev = FirebaseSyncEvent(
            operation = "UPSERT",
            collection = COL_RECORDS,
            docId = recordId,
            document = recordDoc,
            audit = audit
        )
        return FirebaseActionResult("বিক্রির হিসাব সংরক্ষিত হয়েছে", listOf(ev))
    }

    private suspend fun requestChange(
        f: FirebaseFirestore,
        actor: User,
        p: Map<String, Any?>
    ): FirebaseActionResult {
        val recordId = p["recordId"]?.toString().orEmpty()
        val recSnap = f.collection(COL_RECORDS).document(recordId).get().await()
        if (!recSnap.exists() || recSnap.getString("userId") != actor.id) {
            throw IllegalStateException("নিজের হিসাব ছাড়া পরিবর্তন করা যাবে না")
        }

        val existingPending = f.collection(COL_REQUESTS)
            .whereEqualTo("recordId", recordId)
            .whereEqualTo("status", "PENDING")
            .get().await()
        if (!existingPending.isEmpty) {
            throw IllegalStateException("এই হিসাবের একটি অনুরোধ অপেক্ষমাণ আছে")
        }

        val newQty = (p["newQuantity"] as? Number)?.toDouble()
            ?: p["newQuantity"]?.toString()?.toDoubleOrNull() ?: 0.0
        val newPrice = (p["newUnitPrice"] as? Number)?.toDouble()
            ?: p["newUnitPrice"]?.toString()?.toDoubleOrNull() ?: -1.0
        val reason = p["reason"]?.toString().orEmpty().trim()
        if (newQty <= 0.0 || newPrice < 0.0 || reason.isEmpty()) {
            throw IllegalStateException("নতুন তথ্য ও কারণ সঠিকভাবে দিন")
        }

        val reqId = UUID.randomUUID().toString()
        val reqDoc = mapOf(
            "id" to reqId,
            "recordId" to recordId,
            "userId" to actor.id,
            "userName" to actor.fullName.ifBlank { actor.username },
            "taskId" to recSnap.getString("taskId").orEmpty(),
            "reason" to reason,
            "newQuantity" to newQty,
            "newUnitPrice" to newPrice,
            "newNote" to p["newNote"]?.toString().orEmpty().trim(),
            "status" to "PENDING",
            "createdAt" to nowIso(),
            "decidedBy" to "",
            "decidedAt" to ""
        )
        f.collection(COL_REQUESTS).document(reqId).set(reqDoc).await()
        val audit = createAudit(f, actor.id, "REQUEST_CHANGE", recordId)

        val ev = FirebaseSyncEvent(
            operation = "UPSERT",
            collection = COL_REQUESTS,
            docId = reqId,
            document = reqDoc,
            audit = audit
        )
        return FirebaseActionResult("পরিবর্তনের অনুরোধ অ্যাডমিনকে পাঠানো হয়েছে", listOf(ev))
    }

    private suspend fun createUser(
        f: FirebaseFirestore,
        actor: User,
        p: Map<String, Any?>
    ): FirebaseActionResult {
        val username = normalizeUsername(p["username"]?.toString().orEmpty())
        val password = p["password"]?.toString().orEmpty()
        if (username.length < 3) throw IllegalStateException("ইউজারনেম কমপক্ষে ৩ অক্ষরের হতে হবে")
        if (password.length < 6) throw IllegalStateException("পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে")

        val usersSnap = f.collection(COL_USERS).get().await()
        val exists = usersSnap.documents.any {
            normalizeUsername(it.getString("username").orEmpty()) == username
        }
        if (exists) throw IllegalStateException("এই ইউজারনেম আগে থেকেই আছে")

        val newId = UUID.randomUUID().toString()
        val userDoc = mapOf(
            "id" to newId,
            "username" to username,
            "passwordHash" to sha256(password),
            "role" to "USER",
            "fullName" to "",
            "presentAddress" to "",
            "permanentAddress" to "",
            "phone" to "",
            "fatherPhone" to "",
            "nid" to "",
            "profileComplete" to false,
            "active" to true,
            "createdAt" to nowIso()
        )
        f.collection(COL_USERS).document(newId).set(userDoc).await()
        val audit = createAudit(f, actor.id, "CREATE_USER", username)

        val ev = FirebaseSyncEvent(
            operation = "UPSERT",
            collection = COL_USERS,
            docId = newId,
            document = userDoc,
            audit = audit
        )
        return FirebaseActionResult("ইউজার তৈরি হয়েছে", listOf(ev))
    }

    private suspend fun assignTasks(
        f: FirebaseFirestore,
        actor: User,
        p: Map<String, Any?>
    ): FirebaseActionResult {
        val uid = p["userId"]?.toString().orEmpty()
        val taskIds = (p["taskIds"] as? List<*>)?.map { it.toString() }.orEmpty()

        val userSnap = f.collection(COL_USERS).document(uid).get().await()
        if (!userSnap.exists()) throw IllegalStateException("ইউজার পাওয়া যায়নি")

        val existingSnap = f.collection(COL_ASSIGNMENTS).whereEqualTo("userId", uid).get().await()
        val events = mutableListOf<FirebaseSyncEvent>()
        val now = nowIso()

        // পুরোনো অ্যাসাইনমেন্ট নিষ্ক্রিয় করি
        for (doc in existingSnap.documents) {
            val tId = doc.getString("taskId").orEmpty()
            val isNowActive = tId in taskIds
            val updated = mapOf(
                "userId" to uid,
                "taskId" to tId,
                "active" to isNowActive,
                "assignedBy" to actor.id,
                "updatedAt" to now
            )
            f.collection(COL_ASSIGNMENTS).document(doc.id).set(updated, SetOptions.merge()).await()
            events.add(FirebaseSyncEvent("UPSERT", COL_ASSIGNMENTS, doc.id, updated))
        }

        // নতুন অ্যাসাইনমেন্ট যোগ করি
        val existingTaskIds = existingSnap.documents.map { it.getString("taskId").orEmpty() }.toSet()
        for (tId in taskIds) {
            if (tId !in existingTaskIds) {
                val docId = "${uid}_$tId"
                val newAssign = mapOf(
                    "userId" to uid,
                    "taskId" to tId,
                    "active" to true,
                    "assignedBy" to actor.id,
                    "updatedAt" to now
                )
                f.collection(COL_ASSIGNMENTS).document(docId).set(newAssign).await()
                events.add(FirebaseSyncEvent("UPSERT", COL_ASSIGNMENTS, docId, newAssign))
            }
        }

        val audit = createAudit(f, actor.id, "ASSIGN_TASKS", "$uid:${taskIds.joinToString(",")}")
        if (events.isNotEmpty()) {
            events[0] = events[0].copy(audit = audit)
        } else {
            events.add(
                FirebaseSyncEvent(
                    operation = "UPSERT",
                    collection = COL_ASSIGNMENTS,
                    docId = "${uid}_none",
                    document = mapOf("userId" to uid, "taskIds" to taskIds),
                    audit = audit
                )
            )
        }

        return FirebaseActionResult("কাজ অ্যাসাইন করা হয়েছে", events)
    }

    private suspend fun decideChangeRequest(
        f: FirebaseFirestore,
        actor: User,
        p: Map<String, Any?>
    ): FirebaseActionResult {
        val requestId = p["requestId"]?.toString().orEmpty()
        val reqSnap = f.collection(COL_REQUESTS).document(requestId).get().await()
        if (!reqSnap.exists() || reqSnap.getString("status") != "PENDING") {
            throw IllegalStateException("অপেক্ষমাণ অনুরোধ পাওয়া যায়নি")
        }

        val approve = p["approve"] == true || p["approve"]?.toString().equals("true", ignoreCase = true)
        val now = nowIso()
        var updatedRecordMap: Map<String, Any?>? = null
        val recordId = reqSnap.getString("recordId").orEmpty()

        if (approve) {
            val recSnap = f.collection(COL_RECORDS).document(recordId).get().await()
            if (!recSnap.exists()) throw IllegalStateException("মূল হিসাব পাওয়া যায়নি")

            val newQty = docDouble(reqSnap, "newQuantity")
            val newPrice = docDouble(reqSnap, "newUnitPrice")
            val newNote = reqSnap.getString("newNote").orEmpty()
            val recUpdates = mapOf(
                "quantity" to newQty,
                "unitPrice" to newPrice,
                "total" to (newQty * newPrice),
                "note" to newNote,
                "updatedAt" to now
            )
            f.collection(COL_RECORDS).document(recordId).set(recUpdates, SetOptions.merge()).await()
            updatedRecordMap = recSnap.data.orEmpty() + recUpdates + ("id" to recordId)
        }

        val reqUpdates = mapOf(
            "status" to if (approve) "APPROVED" else "REJECTED",
            "decidedBy" to actor.id,
            "decidedAt" to now
        )
        f.collection(COL_REQUESTS).document(requestId).set(reqUpdates, SetOptions.merge()).await()
        val fullReqMap = reqSnap.data.orEmpty() + reqUpdates + ("id" to requestId)
        val audit = createAudit(f, actor.id, if (approve) "APPROVE_CHANGE" else "REJECT_CHANGE", requestId)

        val ev = FirebaseSyncEvent(
            operation = "UPSERT",
            collection = COL_REQUESTS,
            docId = requestId,
            document = fullReqMap,
            audit = audit,
            secondaryCollection = if (approve) COL_RECORDS else null,
            secondaryDocId = if (approve) recordId else null,
            secondaryDocument = updatedRecordMap
        )
        val msg = if (approve) "পরিবর্তন অনুমোদিত হয়েছে" else "অনুরোধ বাতিল হয়েছে"
        return FirebaseActionResult(msg, listOf(ev))
    }

    private suspend fun addSku(
        f: FirebaseFirestore,
        actor: User,
        p: Map<String, Any?>
    ): FirebaseActionResult {
        val name = p["name"]?.toString().orEmpty().trim()
        val unit = p["unit"]?.toString().orEmpty().trim().ifBlank { "kg" }
        val totalStock = (p["totalStock"] as? Number)?.toDouble()
            ?: p["totalStock"]?.toString()?.toDoubleOrNull() ?: 0.0
        val totalCost = (p["totalCost"] as? Number)?.toDouble()
            ?: p["totalCost"]?.toString()?.toDoubleOrNull() ?: 0.0

        if (name.isEmpty()) throw IllegalStateException("SKU নাম দিন")
        if (totalStock < 0.0) throw IllegalStateException("স্টক সংখ্যা ০ বা তার বেশি হতে হবে")

        val skusSnap = f.collection(COL_SKUS).get().await()
        val exists = skusSnap.documents.any {
            normalizeUsername(it.getString("name").orEmpty()) == normalizeUsername(name)
        }
        if (exists) throw IllegalStateException("এই SKU আগে থেকেই আছে")

        val skuId = UUID.randomUUID().toString()
        val skuDoc = mapOf(
            "id" to skuId,
            "name" to name,
            "unit" to unit,
            "totalStock" to totalStock,
            "totalCost" to totalCost,
            "createdBy" to actor.id,
            "createdAt" to nowIso()
        )
        f.collection(COL_SKUS).document(skuId).set(skuDoc).await()
        val audit = createAudit(f, actor.id, "ADD_SKU", "$name:$totalStock")

        val ev = FirebaseSyncEvent(
            operation = "UPSERT",
            collection = COL_SKUS,
            docId = skuId,
            document = skuDoc,
            audit = audit
        )
        return FirebaseActionResult("SKU যোগ হয়েছে", listOf(ev))
    }

    private suspend fun addPurchase(
        f: FirebaseFirestore,
        actor: User,
        p: Map<String, Any?>
    ): FirebaseActionResult {
        val skuId = p["skuId"]?.toString().orEmpty()
        val quantity = (p["quantity"] as? Number)?.toDouble()
            ?: p["quantity"]?.toString()?.toDoubleOrNull() ?: 0.0
        val cost = (p["cost"] as? Number)?.toDouble()
            ?: p["cost"]?.toString()?.toDoubleOrNull() ?: 0.0

        if (skuId.isEmpty()) throw IllegalStateException("SKU আইডি দিন")
        if (quantity <= 0.0) throw IllegalStateException("পরিমাণ ০ এর বেশি হতে হবে")

        val skuSnap = f.collection(COL_SKUS).document(skuId).get().await()
        if (!skuSnap.exists()) throw IllegalStateException("SKU পাওয়া যায়নি")

        val newStock = docDouble(skuSnap, "totalStock") + quantity
        val newCost = docDouble(skuSnap, "totalCost") + cost
        val updates = mapOf(
            "totalStock" to newStock,
            "totalCost" to newCost
        )
        f.collection(COL_SKUS).document(skuId).set(updates, SetOptions.merge()).await()
        val fullSkuMap = skuSnap.data.orEmpty() + updates + ("id" to skuId)
        val audit = createAudit(f, actor.id, "ADD_PURCHASE", "$skuId:+$quantity:$cost")

        val ev = FirebaseSyncEvent(
            operation = "UPSERT",
            collection = COL_SKUS,
            docId = skuId,
            document = fullSkuMap,
            audit = audit
        )
        return FirebaseActionResult("কেনা যোগ হয়েছে", listOf(ev))
    }

    private suspend fun deleteSku(
        f: FirebaseFirestore,
        actor: User,
        p: Map<String, Any?>
    ): FirebaseActionResult {
        val id = p["id"]?.toString().orEmpty()
        if (id.isEmpty()) throw IllegalStateException("SKU আইডি দিন")

        val skuSnap = f.collection(COL_SKUS).document(id).get().await()
        if (!skuSnap.exists()) throw IllegalStateException("SKU পাওয়া যায়নি")

        f.collection(COL_SKUS).document(id).delete().await()
        val audit = createAudit(f, actor.id, "DELETE_SKU", id)

        val ev = FirebaseSyncEvent(
            operation = "DELETE",
            collection = COL_SKUS,
            docId = id,
            document = mapOf("id" to id),
            audit = audit
        )
        return FirebaseActionResult("SKU মুছে ফেলা হয়েছে", listOf(ev))
    }

    // ─── Helpers ───
    private suspend fun createAudit(
        f: FirebaseFirestore,
        userId: String,
        action: String,
        details: String
    ): Map<String, Any?> {
        val auditId = UUID.randomUUID().toString()
        val map = mapOf(
            "id" to auditId,
            "userId" to userId,
            "action" to action,
            "details" to details,
            "createdAt" to nowIso()
        )
        f.collection(COL_AUDIT).document(auditId).set(map).await()
        return map
    }

    private suspend fun recordSyncEventsInFirestore(
        f: FirebaseFirestore,
        events: List<FirebaseSyncEvent>
    ) {
        runCatching {
            for (ev in events) {
                val syncId = UUID.randomUUID().toString()
                val syncDoc = mapOf(
                    "id" to syncId,
                    "operation" to ev.operation,
                    "collection" to ev.collection,
                    "docId" to ev.docId,
                    "createdAt" to nowIso()
                )
                f.collection(COL_SYNC_QUEUE).document(syncId).set(syncDoc)
            }
        }
    }

    private fun buildDashboard(userId: String, allRecords: List<SaleRecord>): Dashboard {
        val today = LocalDate.now(DHAKA_ZONE).toString()
        val month = today.take(7)
        val userRecords = allRecords
            .filter { it.userId == userId }
            .sortedByDescending { it.createdAt.ifBlank { it.date } }

        var tq = 0.0
        var ts = 0.0
        var mq = 0.0
        var ms = 0.0
        for (r in userRecords) {
            if (r.date == today) {
                tq += r.quantity
                ts += r.total
            }
            if (r.date.startsWith(month)) {
                mq += r.quantity
                ms += r.total
            }
        }
        return Dashboard(
            todayQuantity = tq,
            todaySales = ts,
            monthQuantity = mq,
            monthSales = ms,
            recordCount = userRecords.size,
            recentRecords = userRecords.take(100)
        )
    }

    private fun snapshotToUser(doc: DocumentSnapshot): User {
        val roleStr = doc.getString("role").orEmpty()
        val role = if (roleStr.equals("SUPER_ADMIN", ignoreCase = true)) Role.SUPER_ADMIN else Role.USER
        return User(
            id = doc.getString("id").orEmpty().ifBlank { doc.id },
            username = doc.getString("username").orEmpty(),
            role = role,
            fullName = doc.getString("fullName").orEmpty(),
            presentAddress = doc.getString("presentAddress").orEmpty(),
            permanentAddress = doc.getString("permanentAddress").orEmpty(),
            phone = doc.getString("phone").orEmpty(),
            fatherPhone = doc.getString("fatherPhone").orEmpty(),
            nid = doc.getString("nid").orEmpty(),
            profileComplete = docBool(doc, "profileComplete", false),
            active = docBool(doc, "active", true)
        )
    }

    private fun userToMap(user: User, passwordHash: String, createdAt: String): Map<String, Any?> = mapOf(
        "id" to user.id,
        "username" to user.username,
        "passwordHash" to passwordHash,
        "role" to user.role.name,
        "fullName" to user.fullName,
        "presentAddress" to user.presentAddress,
        "permanentAddress" to user.permanentAddress,
        "phone" to user.phone,
        "fatherPhone" to user.fatherPhone,
        "nid" to user.nid,
        "profileComplete" to user.profileComplete,
        "active" to user.active,
        "createdAt" to createdAt
    )

    private fun requireAdmin(user: User) {
        if (user.role != Role.SUPER_ADMIN) {
            throw IllegalStateException("শুধু সুপার অ্যাডমিন এই কাজ করতে পারবেন")
        }
    }

    private fun docBool(doc: DocumentSnapshot, field: String, default: Boolean = false): Boolean {
        val raw = doc.get(field) ?: return default
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() == 1
            is String -> raw.equals("true", ignoreCase = true) || raw == "1"
            else -> default
        }
    }

    private fun docDouble(doc: DocumentSnapshot, field: String): Double {
        val raw = doc.get(field) ?: return 0.0
        return when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun normalizeUsername(s: String): String = s.trim().lowercase()

    private fun nowIso(): String = Instant.now().toString()

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
