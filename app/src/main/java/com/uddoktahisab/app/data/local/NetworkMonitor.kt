package com.uddoktahisab.app.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class NetworkMonitor @Inject constructor(@ApplicationContext context:Context){
    private val manager=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    fun isOnline():Boolean { val network=manager.activeNetwork?:return false;val c=manager.getNetworkCapabilities(network)?:return false;return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) }
}
