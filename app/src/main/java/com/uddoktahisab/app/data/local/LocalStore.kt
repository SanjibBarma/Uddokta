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
                val newSelfDash = d.copy(
                    todayQuantity = d.todayQuantity + (if (today) q else 0.0),
                    todaySales = d.todaySales + (if (today) r.total else 0.0),
                    monthQuantity = d.monthQuantity + q,
                    monthSales = d.monthSales + r.total,
                    recordCount = d.recordCount + 1,
                    recentRecords = listOf(r) + d.recentRecords
                )
                val newSummaries = old.userSummaries.map { s ->
                    if (s.user.id == old.user.id) s.copy(dashboard = newSelfDash) else s
                }
                old.copy(
                    dashboard = newSelfDash,
                    userSummaries = newSummaries
                )
            }

            "deleteRecord" -> {
                val recId = p["recordId"]?.toString().orEmpty()
                val todayStr = LocalDate.now().toString()

                // Find the record to know its unit, quantity, and total for SKU recalculation
                val targetRec = (old.userSummaries.flatMap { it.dashboard.recentRecords } + old.dashboard.recentRecords)
                    .find { it.id == recId }

                fun removeFromDashboard(d: Dashboard): Dashboard {
                    val removed = d.recentRecords.find { it.id == recId } ?: return d
                    val remaining = d.recentRecords.filterNot { it.id == recId }
                    if (d.recordCount <= d.recentRecords.size) {
                        var tq = 0.0
                        var ts = 0.0
                        var mq = 0.0
                        var ms = 0.0
                        for (r in remaining) {
                            if (r.date == todayStr) {
                                tq += r.quantity
                                ts += r.total
                            }
                            mq += r.quantity
                            ms += r.total
                        }
                        return d.copy(
                            todayQuantity = tq,
                            todaySales = ts,
                            monthQuantity = mq,
                            monthSales = ms,
                            recordCount = remaining.size,
                            recentRecords = remaining
                        )
                    }
                    val isToday = removed.date == todayStr
                    return d.copy(
                        todayQuantity = (d.todayQuantity - (if (isToday) removed.quantity else 0.0)).coerceAtLeast(0.0),
                        todaySales = (d.todaySales - (if (isToday) removed.total else 0.0)).coerceAtLeast(0.0),
                        monthQuantity = (d.monthQuantity - removed.quantity).coerceAtLeast(0.0),
                        monthSales = (d.monthSales - removed.total).coerceAtLeast(0.0),
                        recordCount = (d.recordCount - 1).coerceAtLeast(0),
                        recentRecords = remaining
                    )
                }

                val newSkus = if (targetRec != null && old.skus != null) {
                    old.skus.map { s ->
                        if (s.unit == targetRec.unit) {
                            val newSold = (s.totalSold - targetRec.quantity).coerceAtLeast(0.0)
                            val newRev = (s.totalRevenue - targetRec.total).coerceAtLeast(0.0)
                            s.copy(
                                totalSold = newSold,
                                remaining = (s.totalStock - newSold).coerceAtLeast(0.0),
                                totalRevenue = newRev,
                                profit = newRev - s.totalCost
                            )
                        } else s
                    }
                } else old.skus

                old.copy(
                    dashboard = removeFromDashboard(old.dashboard),
                    userSummaries = old.userSummaries.map { s ->
                        s.copy(dashboard = removeFromDashboard(s.dashboard))
                    },
                    skus = newSkus
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

            "deleteUser" -> {
                val uid = p["userId"]?.toString().orEmpty()
                old.copy(
                    users = old.users.filterNot { it.id == uid },
                    assignments = old.assignments.filterNot { it.userId == uid },
                    userSummaries = old.userSummaries.filterNot { it.user.id == uid }
                )
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
