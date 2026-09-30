package com.uddoktahisab.app.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uddoktahisab.app.data.local.db.*
import com.uddoktahisab.app.data.model.*
import java.time.Instant
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
        val old = get() ?: return
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
                val task = old.tasks.find { it.id == p["taskId"].toString() } ?: return
                val q = (p["quantity"] as? Number)?.toDouble() ?: 0.0
                val price = (p["unitPrice"] as? Number)?.toDouble() ?: 0.0
                val r = SaleRecord(
                    id = "LOCAL-${UUID.randomUUID()}",
                    userId = old.user.id,
                    userName = old.user.fullName.ifBlank { old.user.username },
                    taskId = task.id,
                    taskName = task.name,
                    date = p["date"].toString(),
                    quantity = q,
                    unit = task.unit,
                    unitPrice = price,
                    total = q * price,
                    note = p["note"].toString(),
                    createdAt = Instant.now().toString()
                )
                val d = old.dashboard
                val today = r.date == LocalDate.now().toString()
                val month = r.date.startsWith(LocalDate.now().toString().take(7))
                old.copy(
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

            "requestChange" -> {
                val req = ChangeRequest(
                    id = "LOCAL-${UUID.randomUUID()}",
                    recordId = p["recordId"].toString(),
                    userId = old.user.id,
                    userName = old.user.fullName.ifBlank { old.user.username },
                    reason = p["reason"].toString(),
                    newQuantity = (p["newQuantity"] as? Number)?.toDouble() ?: 0.0,
                    newUnitPrice = (p["newUnitPrice"] as? Number)?.toDouble() ?: 0.0,
                    newNote = p["newNote"].toString(),
                    status = "PENDING",
                    createdAt = Instant.now().toString()
                )
                old.copy(requests = listOf(req) + old.requests)
            }

            "createUser" -> {
                val u = User(
                    id = "LOCAL-${UUID.randomUUID()}",
                    username = p["username"].toString()
                )
                old.copy(users = old.users + u)
            }

            "assignTasks" -> {
                val uid = p["userId"].toString()
                val ids = (p["taskIds"] as? List<*>)?.map { it.toString() }.orEmpty()
                old.copy(
                    assignments = old.assignments.filterNot { it.userId == uid } + ids.map {
                        Assignment(uid, it, true)
                    }
                )
            }

            "decideChangeRequest" -> {
                val id = p["requestId"].toString()
                val approved = p["approve"] == true
                old.copy(
                    requests = old.requests.map {
                        if (it.id == id) it.copy(status = if (approved) "APPROVED" else "REJECTED")
                        else it
                    }
                )
            }

            "addSku" -> {
                val stock = (p["totalStock"] as? Number)?.toDouble() ?: 0.0
                val cost = (p["totalCost"] as? Number)?.toDouble() ?: 0.0
                val unit = p["unit"]?.toString().orEmpty().ifBlank { "kg" }
                val newSku = Sku(
                    id = "LOCAL-${UUID.randomUUID()}",
                    name = p["name"]?.toString().orEmpty(),
                    unit = unit,
                    totalStock = stock,
                    totalCost = cost,
                    totalSold = 0.0,
                    remaining = stock,
                    totalRevenue = 0.0,
                    profit = -cost,
                    createdBy = old.user.id,
                    createdAt = Instant.now().toString()
                )
                old.copy(skus = old.skus.orEmpty() + newSku)
            }

            "addPurchase" -> {
                val skuId = p["skuId"]?.toString().orEmpty()
                val qty = (p["quantity"] as? Number)?.toDouble() ?: 0.0
                val cost = (p["cost"] as? Number)?.toDouble() ?: 0.0
                old.copy(
                    skus = old.skus.orEmpty().map { s ->
                        if (s.id == skuId) {
                            val newStock = s.totalStock + qty
                            val newCost = s.totalCost + cost
                            s.copy(
                                totalStock = newStock,
                                totalCost = newCost,
                                remaining = (newStock - s.totalSold).coerceAtLeast(0.0),
                                profit = s.totalRevenue - newCost
                            )
                        } else s
                    }
                )
            }

            "deleteSku" -> {
                val id = p["id"]?.toString().orEmpty()
                old.copy(skus = old.skus.orEmpty().filterNot { it.id == id })
            }

            else -> old
        }
        save(updated)
    }

    suspend fun revertOptimistic(action: String, p: Map<String, Any?>) {
        val old = get() ?: return
        val reverted = when (action) {
            "completeProfile" -> old.copy(
                user = old.user.copy(
                    fullName = "",
                    presentAddress = "",
                    permanentAddress = "",
                    phone = "",
                    fatherPhone = "",
                    nid = "",
                    profileComplete = false
                )
            )
            else -> old
        }
        save(reverted)
    }
}
