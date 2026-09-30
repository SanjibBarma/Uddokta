package com.uddoktahisab.app.data.repository

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.google.firebase.firestore.ListenerRegistration
import com.uddoktahisab.app.data.local.*
import com.uddoktahisab.app.data.model.*
import com.uddoktahisab.app.data.remote.FirebaseDataSource
import com.uddoktahisab.app.worker.PendingSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AppRepository — 100% Pure Firebase Cloud Firestore + Room Offline Sync.
 */
@Singleton
class AppRepository @Inject constructor(
    private val firebase: FirebaseDataSource,
    private val session: SessionManager,
    private val local: LocalStore,
    private val network: NetworkMonitor,
    @ApplicationContext context: Context
) {
    private val work = WorkManager.getInstance(context)

    private suspend fun token() =
        session.token() ?: throw IllegalStateException("সেশন শেষ হয়েছে। আবার লগইন করুন।")

    fun isOnline() = network.isOnline()

    fun observeRealtime(onChanged: () -> Unit): List<ListenerRegistration> =
        firebase.observeCollections(onChanged)

    suspend fun initialize() {
        if (!network.isOnline()) return
        firebase.initializeSystem()
    }

    suspend fun login(username: String, password: String): User {
        if (!network.isOnline()) {
            throw IllegalStateException("প্রথমবার লগইনের জন্য ইন্টারনেট সংযোগ প্রয়োজন")
        }
        val loginData = firebase.login(username.trim(), password)
        session.save(loginData.token)
        return loginData.user
    }

    suspend fun cachedBootstrap() = local.get()

    suspend fun remoteBootstrap(): BootstrapData {
        if (!network.isOnline()) {
            return local.get() ?: throw IllegalStateException("অফলাইন ডাটা পাওয়া যায়নি")
        }
        syncPending()
        val d = firebase.bootstrap(token())
        local.save(d)
        return d
    }

    suspend fun action(name: String, payload: Map<String, Any?> = emptyMap()): String {
        val t = token()
        local.optimistic(name, payload)

        if (!network.isOnline()) {
            queueOfflineAction(name, payload)
            return "ইন্টারনেট নেই—ডাটা অফলাইনে সেভ হয়েছে, পরে সিঙ্ক হবে"
        }

        return try {
            val message = firebase.performAction(t, name, payload)
            runCatching {
                val fresh = firebase.bootstrap(t)
                local.save(fresh)
            }
            message
        } catch (e: IllegalStateException) {
            local.revertOptimistic(name, payload)
            throw e
        } catch (e: Exception) {
            queueOfflineAction(name, payload)
            "নেটওয়ার্ক ধীর—ডাটা অফলাইনে সেভ হয়েছে, পরে sync হবে"
        }
    }

    private suspend fun queueOfflineAction(name: String, payload: Map<String, Any?>) {
        local.enqueue(name, payload)
        work.enqueueUniqueWork(
            "uddokta_pending_sync",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            PendingSyncWorker.request()
        )
    }

    suspend fun syncPending(): Boolean {
        if (!network.isOnline()) return false
        val currentToken = session.token() ?: return false
        val items = local.pending()
        if (items.isEmpty()) return true

        for (item in items) {
            try {
                val p = local.payload(item.payloadJson)
                firebase.performAction(currentToken, item.action, p)
                local.remove(item.id)
            } catch (_: IllegalStateException) {
                // বিজনেস ভ্যালিডেশন ফেইল হলে কিউ থেকে সরিয়ে ফেলি যাতে আটকে না থাকে
                local.remove(item.id)
            } catch (_: Exception) {
                local.failed(item.id)
                return false
            }
        }

        runCatching {
            val fresh = firebase.bootstrap(currentToken)
            local.save(fresh)
        }
        return true
    }

    suspend fun hasSession() = session.token() != null

    suspend fun logout() {
        val t = session.token()
        if (t != null && network.isOnline()) {
            runCatching { firebase.performAction(t, "logout", emptyMap()) }
        }
        session.clear()
    }
}
