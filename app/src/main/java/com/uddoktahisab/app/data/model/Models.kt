package com.uddoktahisab.app.data.model

import com.google.gson.annotations.SerializedName

enum class Role {
    @SerializedName("SUPER_ADMIN")
    SUPER_ADMIN,

    @SerializedName("USER")
    USER
}

data class User(
    val id: String = "", val username: String = "", val role: Role = Role.USER,
    val fullName: String = "", val presentAddress: String = "", val permanentAddress: String = "",
    val phone: String = "", val fatherPhone: String = "", val nid: String = "",
    val profileComplete: Boolean = false, val active: Boolean = true
)

data class Task(
    val id: String = "",
    val name: String = "",
    val unit: String = "pcs",
    val assigned: Boolean = false
)

data class SaleRecord(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val taskId: String = "",
    val taskName: String = "",
    val date: String = "",
    val quantity: Double = 0.0,
    val unit: String = "pcs",
    val unitPrice: Double = 0.0,
    val total: Double = 0.0,
    val note: String = "",
    val createdAt: String = ""
)

data class Dashboard(
    val todayQuantity: Double = 0.0,
    val todaySales: Double = 0.0,
    val monthQuantity: Double = 0.0,
    val monthSales: Double = 0.0,
    val recordCount: Int = 0,
    val recentRecords: List<SaleRecord> = emptyList()
)

data class ChangeRequest(
    val id: String = "",
    val recordId: String = "",
    val userId: String = "",
    val userName: String = "",
    val reason: String = "",
    val newQuantity: Double = 0.0,
    val newUnitPrice: Double = 0.0,
    val newNote: String = "",
    val status: String = "PENDING",
    val createdAt: String = ""
)

data class LoginData(val token: String, val user: User)
data class ApiRequest(
    val action: String,
    val token: String? = null,
    val payload: Map<String, Any?> = emptyMap()
)

data class ApiResponse<T>(
    val success: Boolean = false,
    val message: String = "",
    val data: T? = null
)

data class Assignment(val userId: String = "", val taskId: String = "", val active: Boolean = true)
data class UserSummary(val user: User = User(), val dashboard: Dashboard = Dashboard())
data class Sku(
    val id: String = "",
    val name: String = "",
    val unit: String = "kg",
    val totalStock: Double = 0.0,
    val totalCost: Double = 0.0,
    val totalSold: Double = 0.0,
    val remaining: Double = 0.0,
    val totalRevenue: Double = 0.0,
    val profit: Double = 0.0,
    val createdBy: String = "",
    val createdAt: String = ""
)

data class BootstrapData(
    val user: User,
    val tasks: List<Task> = emptyList(),
    val dashboard: Dashboard = Dashboard(),
    val users: List<User> = emptyList(),
    val requests: List<ChangeRequest> = emptyList(),
    val assignments: List<Assignment> = emptyList(),
    val userSummaries: List<UserSummary> = emptyList(),
    val skus: List<Sku>? = null
)
