package com.uddoktahisab.app.data.repository

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.google.firebase.firestore.ListenerRegistration
import com.uddoktahisab.app.BuildConfig
import com.uddoktahisab.app.data.local.*
import com.uddoktahisab.app.data.model.*
import com.uddoktahisab.app.data.remote.ApiService
import com.uddoktahisab.app.data.remote.FirebaseDataSource
import com.uddoktahisab.app.data.remote.FirebaseSyncEvent
import com.uddoktahisab.app.worker.PendingSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AppRepository — Firebase-First Architecture with Automatic Google Sheet Sync.
 *
 * ডাটা ফ্লো:
 * 1. যেকোনো ডাটা Create / Update / Delete অথবা Read প্রথমে Firebase Firestore-এ হয়।
 * 2. সফলভাবে Firebase-এ সেভ হওয়ার সাথে সাথে একটি `syncFromFirebase` ইভেন্ট কিউ করা হয়।
 * 3. ব্যাকগ্রাউন্ডে (WorkManager + Coroutine) সেই ইভেন্টটি Google Apps Script (`Code.gs`)-এ যায়
 *    এবং Firebase থেকে Google Sheet-এর নির্দিষ্ট ট্যাবে ডাটা আপডেট করে।
 */
@Singleton
class AppRepository @Inject constructor(
    private val api: ApiService,
    private val firebase: FirebaseDataSource,
    private val session: SessionManager,
    private val local: LocalStore,
    private val network: NetworkMonitor,
    @ApplicationContext context: Context
) {
    private val work = WorkManager.getInstance(context)
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deployment = BuildConfig.API_URL.substringAfter("/s/").substringBefore("/exec")

    private suspend fun token() =
        session.token() ?: throw IllegalStateException("সেশন শেষ হয়েছে। আবার লগইন করুন।")

    fun isOnline() = network.isOnline()

    fun observeRealtime(onChanged: () -> Unit): List<ListenerRegistration> =
        firebase.observeCollections(onChanged)

    suspend fun initialize() {
        if (!network.isOnline()) return
        if (firebase.isConfigured()) {
            runCatching {
                val seedEvents = firebase.initializeSystem()
                if (seedEvents.isNotEmpty()) {
                    enqueueFirebaseEventsToSheet(seedEvents)
                }
            }
            // Google Sheet-এর মূল ট্যাবগুলোও ব্যাকগ্রাউন্ডে প্রস্তুত রাখি
            bgScope.launch {
                runCatching {
                    api.action(
                        BuildConfig.API_URL,
                        ApiRequest(
                            "initialize",
                            payload = mapOf("deploymentId" to deployment) + firebase.firebaseMetadata()
                        )
                    )
                    syncPending()
                }
            }
        } else {
            val r = api.action(
                BuildConfig.API_URL,
                ApiRequest("initialize", payload = mapOf("deploymentId" to deployment))
            )
            if (!r.success) throw IllegalStateException(r.message)
        }
    }

    suspend fun login(username: String, password: String): User {
        if (!network.isOnline()) {
            throw IllegalStateException("প্রথমবার লগইনের জন্য ইন্টারনেট সংযোগ প্রয়োজন")
        }

        if (firebase.isConfigured()) {
            val loginResult = try {
                firebase.login(username.trim(), password)
            } catch (e: Exception) {
                // যদি পুরোনো Google Sheet-এ ইউজার থেকে থাকে কিন্তু এখনো Firebase-এ না থাকে,
                // তাহলে একবার Sheet থেকে যাচাই করে স্বয়ংক্রিয়ভাবে Firebase-এ মাইগ্রেট করে নেবে
                val migrated = runCatching {
                    val sheetResp = api.login(
                        BuildConfig.API_URL,
                        ApiRequest(
                            "login",
                            payload = mapOf(
                                "username" to username.trim(),
                                "password" to password,
                                "deploymentId" to deployment
                            )
                        )
                    )
                    val sheetData = sheetResp.data ?: return@runCatching null
                    val ev = firebase.upsertUserFromSheet(sheetData.user, password)
                    enqueueFirebaseEventsToSheet(listOf(ev))
                    firebase.login(username.trim(), password)
                }.getOrNull()

                migrated ?: throw e
            }

            val (loginData, syncEvents) = loginResult
            session.save(loginData.token)
            enqueueFirebaseEventsToSheet(syncEvents)
            bgScope.launch { runCatching { syncPending() } }
            return loginData.user
        }

        // Fallback if google-services.json is still placeholder
        val r = api.login(
            BuildConfig.API_URL,
            ApiRequest(
                "login",
                payload = mapOf(
                    "username" to username.trim(),
                    "password" to password,
                    "deploymentId" to deployment
                )
            )
        )
        val d = r.data ?: throw IllegalStateException(r.message.ifBlank { "লগইন ব্যর্থ" })
        session.save(d.token)
        return d.user
    }

    suspend fun cachedBootstrap() = local.get()

    suspend fun remoteBootstrap(): BootstrapData {
        if (firebase.isConfigured()) {
            return try {
                val d = firebase.bootstrap(token())
                local.save(d)
                if (network.isOnline()) {
                    bgScope.launch { runCatching { syncPending() } }
                }
                d
            } catch (e: Exception) {
                if (!network.isOnline()) {
                    local.get() ?: throw IllegalStateException("অফলাইন ডাটা পাওয়া যায়নি")
                } else {
                    throw e
                }
            }
        }

        if (!network.isOnline()) {
            return local.get() ?: throw IllegalStateException("অফলাইন ডাটা পাওয়া যায়নি")
        }
        syncPending()
        val r = api.bootstrap(
            BuildConfig.API_URL,
            ApiRequest("bootstrap", token(), mapOf("deploymentId" to deployment))
        )
        val d = r.data ?: throw IllegalStateException(r.message)
        local.save(d)
        return d
    }

    suspend fun action(name: String, payload: Map<String, Any?> = emptyMap()): String {
        val t = token()
        local.optimistic(name, payload)

        if (firebase.isConfigured()) {
            if (!network.isOnline()) {
                // অফলাইনে থাকলে আগে লোকাল কিউতে রাখি; অনলাইনে এলে প্রথমে Firebase-এ যাবে, তারপর Sheet-এ
                queueOfflineFirebaseAction(name, payload)
                return "ইন্টারনেট নেই—ডাটা অফলাইনে সেভ হয়েছে (অনলাইনে এলে Firebase ও Google Sheet-এ সিঙ্ক হবে)"
            }
            return try {
                // ধাপ ১: প্রথমে সরাসরি Firebase Firestore-এ Create / Update / Delete
                val res = firebase.performAction(t, name, payload)
                // ধাপ ২: Firebase থেকে ফ্রেশ ডাটা ক্যাশে সংরক্ষণ
                runCatching {
                    val fresh = firebase.bootstrap(t)
                    local.save(fresh)
                }
                // ধাপ ৩: Firebase থেকে Google Sheet-এ আপডেট করার জন্য ব্যাকগ্রাউন্ড সিঙ্ক কিউ
                enqueueFirebaseEventsToSheet(res.syncEvents)
                bgScope.launch { runCatching { syncPending() } }
                res.message
            } catch (e: IllegalStateException) {
                local.revertOptimistic(name, payload)
                throw e
            } catch (e: Exception) {
                queueOfflineFirebaseAction(name, payload)
                "নেটওয়ার্ক ধীর—ডাটা অফলাইনে সেভ হয়েছে, পরে Firebase ও Google Sheet-এ sync হবে"
            }
        }

        // Fallback if google-services.json is still placeholder
        if (!network.isOnline()) {
            queueDirectSheetAction(name, payload)
            return "ইন্টারনেট নেই—ডাটা অফলাইনে সেভ হয়েছে"
        }
        return try {
            val r = api.action(
                BuildConfig.API_URL,
                ApiRequest(name, t, payload + ("deploymentId" to deployment))
            )
            if (!r.success) {
                local.revertOptimistic(name, payload)
                throw IllegalStateException(r.message)
            }
            r.message
        } catch (e: IllegalStateException) {
            local.revertOptimistic(name, payload)
            throw e
        } catch (e: Exception) {
            queueDirectSheetAction(name, payload)
            "নেটওয়ার্ক ধীর—ডাটা অফলাইনে সেভ হয়েছে, পরে sync হবে"
        }
    }

    private suspend fun enqueueFirebaseEventsToSheet(events: List<FirebaseSyncEvent>) {
        if (events.isEmpty()) return
        val meta = firebase.firebaseMetadata()
        for (ev in events) {
            val syncPayload = mutableMapOf<String, Any?>(
                "operation" to ev.operation,
                "collection" to ev.collection,
                "docId" to ev.docId,
                "document" to ev.document,
                "firebaseProjectId" to meta["firebaseProjectId"],
                "firebaseApiKey" to meta["firebaseApiKey"]
            )
            if (ev.audit != null) syncPayload["audit"] = ev.audit
            if (ev.secondaryCollection != null) syncPayload["secondaryCollection"] = ev.secondaryCollection
            if (ev.secondaryDocId != null) syncPayload["secondaryDocId"] = ev.secondaryDocId
            if (ev.secondaryDocument != null) syncPayload["secondaryDocument"] = ev.secondaryDocument

            local.enqueue("syncFromFirebase", syncPayload)
        }
        scheduleSyncWorker()
    }

    private suspend fun queueOfflineFirebaseAction(name: String, payload: Map<String, Any?>) {
        local.enqueue("firebase_offline:$name", payload)
        scheduleSyncWorker()
    }

    private suspend fun queueDirectSheetAction(name: String, payload: Map<String, Any?>) {
        local.enqueue(name, payload)
        scheduleSyncWorker()
    }

    private fun scheduleSyncWorker() {
        work.enqueueUniqueWork(
            "uddokta_pending_sync",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            PendingSyncWorker.request()
        )
    }

    suspend fun syncPending(): Boolean {
        if (!network.isOnline()) return false
        val currentToken = session.token()
        val items = local.pending()
        if (items.isEmpty()) return true

        for (item in items) {
            try {
                val p = local.payload(item.payloadJson)
                when {
                    item.action.startsWith("firebase_offline:") -> {
                        // অফলাইনে করা কাজ প্রথমে Firebase-এ পাঠাই, তারপর সেখান থেকে Sheet সিঙ্ক কিউতে যোগ করি
                        val realAction = item.action.removePrefix("firebase_offline:")
                        if (currentToken != null && firebase.isConfigured()) {
                            val res = firebase.performAction(currentToken, realAction, p)
                            local.remove(item.id)
                            enqueueFirebaseEventsToSheet(res.syncEvents)
                        }
                    }

                    item.action == "syncFromFirebase" -> {
                        // Firebase-এ ইতিমধ্যে সেভ হওয়া ডাটা Google Sheet-এ আপডেট করি
                        val r = api.action(
                            BuildConfig.API_URL,
                            ApiRequest(
                                action = "syncFromFirebase",
                                token = currentToken,
                                payload = p + ("deploymentId" to deployment)
                            )
                        )
                        if (r.success) {
                            local.remove(item.id)
                        } else {
                            local.failed(item.id)
                        }
                    }

                    else -> {
                        if (currentToken == null) continue
                        val r = api.action(
                            BuildConfig.API_URL,
                            ApiRequest(item.action, currentToken, p + ("deploymentId" to deployment))
                        )
                        if (r.success) local.remove(item.id) else local.remove(item.id)
                    }
                }
            } catch (_: Exception) {
                local.failed(item.id)
                return false
            }
        }

        // সব সিঙ্ক শেষে লোকাল ক্যাশ আপডেট করি
        if (currentToken != null) {
            runCatching {
                val fresh = if (firebase.isConfigured()) {
                    firebase.bootstrap(currentToken)
                } else {
                    api.bootstrap(
                        BuildConfig.API_URL,
                        ApiRequest("bootstrap", currentToken, mapOf("deploymentId" to deployment))
                    ).data
                }
                fresh?.let { local.save(it) }
            }
        }
        return true
    }

    suspend fun hasSession() = session.token() != null

    suspend fun logout() {
        val t = session.token()
        if (t != null) {
            if (firebase.isConfigured() && network.isOnline()) {
                runCatching {
                    val res = firebase.performAction(t, "logout", emptyMap())
                    enqueueFirebaseEventsToSheet(res.syncEvents)
                    bgScope.launch { runCatching { syncPending() } }
                }
            } else if (network.isOnline()) {
                runCatching {
                    api.action(
                        BuildConfig.API_URL,
                        ApiRequest("logout", t, mapOf("deploymentId" to deployment))
                    )
                }
            }
        }
        session.clear()
    }
}
