package com.uddoktahisab.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session token DataStore-এ persist করে যাতে app reopen এ আবার login লাগে না।
 * Memory cache + DataStore disk — দুই জায়গায় রাখি।
 */
private val Context.sessionStore: DataStore<Preferences> by preferencesDataStore(name = "uddokta_session")

@Singleton
class SessionManager @Inject constructor(@ApplicationContext private val context: Context) {
    @Volatile private var sessionToken: String? = null

    suspend fun token(): String? {
        // Memory cache miss হলে DataStore থেকে lazy load
        if (sessionToken == null) {
            sessionToken = context.sessionStore.data.first()[TOKEN_KEY]
        }
        return sessionToken
    }

    suspend fun save(token: String) {
        sessionToken = token
        context.sessionStore.edit { it[TOKEN_KEY] = token }
    }

    suspend fun clear() {
        sessionToken = null
        context.sessionStore.edit { it.remove(TOKEN_KEY) }
    }

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("session_token")
    }
}
