package com.uddoktahisab.app.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="app_cache") data class CacheEntity(@PrimaryKey val id:Int=1,val json:String,val updatedAt:Long=System.currentTimeMillis())
@Entity(tableName="pending_actions") data class PendingActionEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val action:String,val payloadJson:String,val createdAt:Long=System.currentTimeMillis(),val attempts:Int=0)

@Dao interface LocalDao {
    @Query("SELECT * FROM app_cache WHERE id=1") suspend fun cache():CacheEntity?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveCache(value:CacheEntity)
    @Insert suspend fun enqueue(value:PendingActionEntity):Long
    @Query("SELECT * FROM pending_actions ORDER BY createdAt ASC") suspend fun pending():List<PendingActionEntity>
    @Query("DELETE FROM pending_actions WHERE id=:id") suspend fun remove(id:Long)
    @Query("UPDATE pending_actions SET attempts=attempts+1 WHERE id=:id") suspend fun failed(id:Long)
    @Query("SELECT COUNT(*) FROM pending_actions") fun pendingCount():Flow<Int>
}

@Database(entities=[CacheEntity::class,PendingActionEntity::class],version=2,exportSchema=false)
abstract class AppDatabase:RoomDatabase(){abstract fun dao():LocalDao}
