package com.uddoktahisab.app.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uddoktahisab.app.data.local.db.*
import com.uddoktahisab.app.data.model.*
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalStore @Inject constructor(private val dao: LocalDao, private val gson: Gson) {
    suspend fun get(): BootstrapData? = dao.cache()
        ?.let { runCatching { gson.fromJson(it.json, BootstrapData::class.java) }.getOrNull() }

    suspend fun save(data: BootstrapData) = dao.saveCache(CacheEntity(json = gson.toJson(data)))
    suspend fun enqueue(action: String, payload: Map<String, Any?>) =
        dao.enqueue(PendingActionEntity(action = action, payloadJson = gson.toJson(payload)))

    suspend fun pending() = dao.pending()
    suspend fun remove(id: Long) = dao.remove(id)
    suspend fun failed(id: Long) = dao.failed(id)
    fun payload(json: String): Map<String, Any?> =
        gson.fromJson(json, object : TypeToken<Map<String, Any?>>() {}.type)

    suspend fun optimistic(action: String, p: Map<String, Any?>) {
        val old = get() ?: return;
        val updated = when (action) {
            "completeProfile" -> old.copy(
                user = old.user.copy(
                    fullName = p["fullName"].toString(),
                    presentAddress = p["presentAddress"].toString(),
                    permanentAddress = p["permanentAddress"].toString(),
                    phone = p["phone"].toString(),
                    fatherPhone = p["fatherPhone"].toString(),
                    nid = p["nid"].toString(),
                    profileComplete = true
                )
            )

            "addRecord" -> {
                val task = old.tasks.find { it.id == p["taskId"].toString() } ?: return;
                val q = (p["quantity"] as? Number)?.toDouble() ?: 0.0;
                val price = (p["unitPrice"] as? Number)?.toDouble() ?: 0.0;
                val r = SaleRecord(
                    "LOCAL-${UUID.randomUUID()}",
                    old.user.id,
                    old.user.fullName,
                    task.id,
                    task.name,
                    p["date"].toString(),
                    q,
                    task.unit,
                    price,
                    q * price,
                    p["note"].toString()
                );
                val d = old.dashboard;
                val today = r.date == LocalDate.now().toString();
                val month = r.date.startsWith(LocalDate.now().toString().take(7)); old.copy(
                    dashboard = d.copy(
                        todayQuantity = d.todayQuantity + (if (today) q else 0.0),
                        todaySales = d.todaySales + (if (today) r.total else 0.0),
                        monthQuantity = d.monthQuantity + (if (month) q else 0.0),
                        monthSales = d.monthSales + (if (month) r.total else 0.0),
                        recordCount = d.recordCount + 1,
                        recentRecords = listOf(r) + d.recentRecords
                    )
                )
            }

            "createUser" -> {
                val u = User(
                    id = "LOCAL-${UUID.randomUUID()}",
                    username = p["username"].toString()
                ); old.copy(users = old.users + u)
            }

            "assignTasks" -> {
                val uid = p["userId"].toString();
                val ids = (p["taskIds"] as? List<*>)?.map { it.toString() }.orEmpty(); old.copy(
                    assignments = old.assignments.filterNot { it.userId == uid } + ids.map {
                        Assignment(
                            uid,
                            it,
                            true
                        )
                    })
            }

            "decideChangeRequest" -> {
                val id = p["requestId"].toString();
                val approved = p["approve"] == true; old.copy(requests = old.requests.map {
                    if (it.id == id) it.copy(
                        status = if (approved) "APPROVED" else "REJECTED"
                    ) else it
                })
            }

            else -> old
        }; save(updated)
    }

    // নতুন মেথড যোগ করুন
    suspend fun revertOptimistic(action: String, p: Map<String, Any?>) {
        val old = get() ?: return
        val reverted = when (action) {
            "completeProfile" -> old.copy(
                user = old.user.copy(
                    fullName = "", presentAddress = "", permanentAddress = "",
                    phone = "", fatherPhone = "", nid = "", profileComplete = false
                )
            )
            else -> old
        }
        save(reverted)
    }
}
