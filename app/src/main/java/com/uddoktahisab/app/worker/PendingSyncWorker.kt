package com.uddoktahisab.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.uddoktahisab.app.data.repository.AppRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker class PendingSyncWorker @AssistedInject constructor(@Assisted context:Context,@Assisted params:WorkerParameters,private val repo:AppRepository):CoroutineWorker(context,params){
    override suspend fun doWork():Result=if(repo.syncPending()) Result.success() else Result.retry()
    companion object { fun request()=OneTimeWorkRequestBuilder<PendingSyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,java.time.Duration.ofSeconds(15)).addTag("uddokta_pending_sync").build() }
}
