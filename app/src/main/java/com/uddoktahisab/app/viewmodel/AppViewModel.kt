package com.uddoktahisab.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.uddoktahisab.app.data.model.*
import com.uddoktahisab.app.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

data class AppUiState(
    val loading: Boolean = true,
    val loggedIn: Boolean = false,
    val data: BootstrapData? = null,
    val error: String? = null,
    val notice: String? = null,
    val offline: Boolean = false,
    /** নিচের tab-এর index: 0=ড্যাশবোর্ড, 1=বিক্রি, 2=হিসাব, 3=অ্যাডমিন, 4=প্রোফাইল */
    val selectedTab: Int = 0
)

@HiltViewModel
class AppViewModel @Inject constructor(private val repo: AppRepository) : ViewModel() {
    private val _state = MutableStateFlow(AppUiState())
    val state = _state.asStateFlow()

    private var realtimeListeners: List<ListenerRegistration> = emptyList()
    private var realtimeRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            if (repo.hasSession()) {
                refresh()
                attachRealtimeIfNeeded()
            } else {
                runCatching { repo.initialize() }
                _state.value = AppUiState(loading = false, offline = !repo.isOnline())
            }
        }
        // ─── Background polling + pending sync ───
        startBackgroundPolling()
    }

    private fun attachRealtimeIfNeeded() {
        if (realtimeListeners.isNotEmpty()) return
        realtimeListeners = repo.observeRealtime {
            val s = _state.value
            if (!s.loggedIn || s.loading) return@observeRealtime
            realtimeRefreshJob?.cancel()
            realtimeRefreshJob = viewModelScope.launch {
                delay(350L) // debounce rapid Firestore snapshot events
                runCatching { repo.remoteBootstrap() }.onSuccess { fresh ->
                    _state.value = _state.value.copy(
                        data = fresh,
                        loading = false,
                        offline = false
                    )
                }
            }
        }
    }

    private fun detachRealtime() {
        realtimeRefreshJob?.cancel()
        realtimeListeners.forEach { runCatching { it.remove() } }
        realtimeListeners = emptyList()
    }

    /**
     * Foreground-এ থাকা অবস্থায় প্রতি 60 সেকেন্ডে fresh data ও pending sync নিশ্চিত করে।
     */
    private fun startBackgroundPolling() {
        viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                val s = _state.value
                if (!s.loggedIn || s.data == null || !repo.isOnline() || s.loading) continue
                runCatching { repo.remoteBootstrap() }.onSuccess { fresh ->
                    _state.value = _state.value.copy(
                        data = fresh,
                        loading = false,
                        offline = false
                    )
                }
            }
        }
    }

    fun login(user: String, pass: String) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching {
            withTimeout(60_000L) {
                repo.login(user, pass)
                repo.remoteBootstrap()
            }
        }.onSuccess {
            _state.value = AppUiState(false, true, it, offline = false)
            attachRealtimeIfNeeded()
        }.onFailure {
            val msg =
                if (it is TimeoutCancellationException) "লগইন সময় শেষ। আবার চেষ্টা করুন।" else it.message
            _state.value = AppUiState(false, false, error = msg, offline = !repo.isOnline())
        }
    }

    fun refresh() = viewModelScope.launch {
        val cached = repo.cachedBootstrap()
        if (cached != null) {
            _state.value = _state.value.copy(
                loggedIn = true,
                data = cached,
                offline = !repo.isOnline(),
                loading = false
            )
        } else {
            _state.value = _state.value.copy(loading = true, error = null)
        }
        if (repo.isOnline()) {
            runCatching { repo.remoteBootstrap() }.onSuccess { fresh ->
                _state.value = _state.value.copy(
                    loggedIn = true,
                    data = fresh,
                    loading = false,
                    offline = false
                )
                attachRealtimeIfNeeded()
            }.onFailure {
                _state.value = _state.value.copy(loading = false, error = it.message)
            }
        } else {
            _state.value = _state.value.copy(
                loading = false,
                offline = true,
                error = if (cached == null) "ইন্টারনেট নেই এবং কোনো offline data পাওয়া যায়নি" else null
            )
        }
    }

    fun completeProfile(
        fullName: String,
        present: String,
        permanent: String,
        phone: String,
        fatherPhone: String,
        nid: String
    ) = action(
        "completeProfile",
        mapOf(
            "fullName" to fullName,
            "presentAddress" to present,
            "permanentAddress" to permanent,
            "phone" to phone,
            "fatherPhone" to fatherPhone,
            "nid" to nid
        )
    )

    fun addSale(task: Task, date: String, qty: Double, price: Double, note: String) = action(
        "addRecord",
        mapOf(
            "taskId" to task.id,
            "date" to date,
            "quantity" to qty,
            "unitPrice" to price,
            "note" to note
        )
    )

    fun requestChange(
        record: SaleRecord,
        qty: Double,
        price: Double,
        note: String,
        reason: String
    ) = action(
        "requestChange",
        mapOf(
            "recordId" to record.id,
            "newQuantity" to qty,
            "newUnitPrice" to price,
            "newNote" to note,
            "reason" to reason
        )
    )

    fun createUser(username: String, password: String) =
        action("createUser", mapOf("username" to username, "password" to password))

    fun assign(userId: String, taskIds: List<String>) =
        action("assignTasks", mapOf("userId" to userId, "taskIds" to taskIds))

    fun decide(requestId: String, approve: Boolean) =
        action("decideChangeRequest", mapOf("requestId" to requestId, "approve" to approve))

    fun addSku(
        name: String,
        unit: String = "kg",
        totalStock: Double = 0.0,
        totalCost: Double = 0.0
    ) = action(
        "addSku",
        mapOf(
            "name" to name,
            "unit" to unit,
            "totalStock" to totalStock,
            "totalCost" to totalCost
        )
    )

    fun addPurchase(skuId: String, quantity: Double, cost: Double) =
        action("addPurchase", mapOf("skuId" to skuId, "quantity" to quantity, "cost" to cost))

    fun deleteSku(id: String) =
        action("deleteSku", mapOf("id" to id))

    private fun action(name: String, payload: Map<String, Any?>) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching {
            repo.action(name, payload)
        }.onSuccess { message ->
            val cached = repo.cachedBootstrap()
            _state.value = _state.value.copy(
                loading = false,
                data = cached ?: _state.value.data,
                loggedIn = true,
                notice = message,
                offline = !repo.isOnline(),
                selectedTab = 0
            )
        }.onFailure {
            _state.value = _state.value.copy(loading = false, error = it.message)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null, notice = null)
    }

    /** MainShell এর bottom-tab বদলায়; action success এ ড্যাশবোর্ডে ফেরাতেও ব্যবহৃত */
    fun selectTab(index: Int) {
        if (_state.value.selectedTab == index) return
        _state.value = _state.value.copy(selectedTab = index.coerceAtLeast(0))
    }

    fun logout() = viewModelScope.launch {
        detachRealtime()
        _state.value = _state.value.copy(loading = true, error = null)
        repo.logout()
        _state.value = AppUiState(loading = false)
    }

    override fun onCleared() {
        detachRealtime()
        super.onCleared()
    }
}
