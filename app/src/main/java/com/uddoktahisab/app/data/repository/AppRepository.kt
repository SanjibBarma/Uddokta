package com.uddoktahisab.app.data.repository

import android.content.Context
import androidx.work.WorkManager
import com.uddoktahisab.app.BuildConfig
import com.uddoktahisab.app.data.local.*
import com.uddoktahisab.app.data.model.*
import com.uddoktahisab.app.data.remote.ApiService
import com.uddoktahisab.app.worker.PendingSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    private val api: ApiService, private val session: SessionManager, private val local: LocalStore,
    private val network: NetworkMonitor, @ApplicationContext context: Context
) {
    private val work = WorkManager.getInstance(context)
    private val deployment = BuildConfig.API_URL.substringAfter("/s/").substringBefore("/exec")
    private suspend fun token() =
        session.token() ?: throw IllegalStateException("সেশন শেষ হয়েছে। আবার লগইন করুন।")

    fun isOnline() = network.isOnline()
    suspend fun initialize() {
        if (!network.isOnline()) return;
        val r = api.action(
            BuildConfig.API_URL,
            ApiRequest("initialize", payload = mapOf("deploymentId" to deployment))
        ); if (!r.success) throw IllegalStateException(r.message)
    }

    suspend fun login(username: String, password: String): User {
        if (!network.isOnline()) throw IllegalStateException("প্রথমবার লগইনের জন্য ইন্টারনেট সংযোগ প্রয়োজন")
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
        val d = r.data
            ?: throw IllegalStateException(r.message.ifBlank { "লগইন ব্যর্থ" }); session.save(d.token); return d.user
    }

    suspend fun cachedBootstrap() = local.get()
    suspend fun remoteBootstrap(): BootstrapData {
        if (!network.isOnline()) return local.get()
            ?: throw IllegalStateException("অফলাইন ডাটা পাওয়া যায়নি")
        syncPending();
        val r = api.bootstrap(
            BuildConfig.API_URL,
            ApiRequest("bootstrap", token(), mapOf("deploymentId" to deployment))
        );
        val d = r.data ?: throw IllegalStateException(r.message); local.save(d); return d
    }

    suspend fun action(name: String, payload: Map<String, Any?> = emptyMap()): String {
        val t = token()
        local.optimistic(name, payload)  // আগে optimistic save
        if (!network.isOnline()) {
            queue(name, payload)
            return "ইন্টারনেট নেই—ডাটা অফলাইনে সেভ হয়েছে"
        }
        return try {
            val r = api.action(BuildConfig.API_URL, ApiRequest(name, t, payload + ("deploymentId" to deployment)))
            if (!r.success) {
                local.revertOptimistic(name, payload)  // ← revert on failure
                runCatching { remoteBootstrap() }
                throw IllegalStateException(r.message)
            }
            r.message
        } catch (e: IllegalStateException) {
            local.revertOptimistic(name, payload)  // ← revert
            throw e
        } catch (e: Exception) {
            queue(name, payload)
            "নেটওয়ার্ক ধীর—ডাটা অফলাইনে সেভ হয়েছে, পরে sync হবে"
        }
    }

    private suspend fun queue(name: String, payload: Map<String, Any?>) {
        local.enqueue(name, payload); work.enqueueUniqueWork(
            "uddokta_pending_sync",
            androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE,
            PendingSyncWorker.request()
        )
    }

    suspend fun syncPending(): Boolean {
        if (!network.isOnline()) return false
        val currentToken = session.token() ?: return false
        val items = local.pending()
        for (item in items) try {
            val p = local.payload(item.payloadJson);
            val r = api.action(
                BuildConfig.API_URL,
                ApiRequest(item.action, currentToken, p + ("deploymentId" to deployment))
            ); if (r.success) local.remove(item.id) else local.remove(item.id)
        } catch (_: Exception) {
            local.failed(item.id); return false
        }
        if (items.isNotEmpty()) runCatching {
            val r = api.bootstrap(
                BuildConfig.API_URL,
                ApiRequest("bootstrap", currentToken, mapOf("deploymentId" to deployment))
            ); r.data?.let { local.save(it) }
        }
        return true
    }

    suspend fun hasSession() = session.token() != null
    suspend fun logout() {
        val t = session.token(); if (t != null && network.isOnline()) runCatching {
            api.action(
                BuildConfig.API_URL,
                ApiRequest("logout", t, mapOf("deploymentId" to deployment))
            )
        }; session.clear()
    }
}
