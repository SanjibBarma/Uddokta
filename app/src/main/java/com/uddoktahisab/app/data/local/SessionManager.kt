package com.uddoktahisab.app.data.local

import javax.inject.Inject
import javax.inject.Singleton

/** Authentication is intentionally memory-only. Closing/killing the app destroys the session. */
@Singleton class SessionManager @Inject constructor(){
    @Volatile private var sessionToken:String?=null
    suspend fun token():String?=sessionToken
    suspend fun save(token:String){sessionToken=token}
    suspend fun clear(){sessionToken=null}
}
