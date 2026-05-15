package com.sharegps.ui.home

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.sharegps.data.ApiRepository
import com.sharegps.data.FamilyMember
import com.sharegps.data.HistoryPoint
import com.sharegps.data.KeyStore
import com.sharegps.data.LocationUpdateMsg
import com.sharegps.data.OwnLocationBroadcast
import com.sharegps.data.WebSocketClient
import com.sharegps.data.resolveServerUrl
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ApiRepository(resolveServerUrl(app), KeyStore(app).getKey() ?: "")

    private val _members = MutableStateFlow<List<FamilyMember>>(emptyList())
    val members: StateFlow<List<FamilyMember>> = _members

    private val _positions = MutableStateFlow<Map<String, LocationUpdateMsg>>(emptyMap())
    val positions: StateFlow<Map<String, LocationUpdateMsg>> = _positions

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _avatars = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val avatars: StateFlow<Map<String, Bitmap>> = _avatars

    private val _historyMemberId = MutableStateFlow<String?>(null)
    val historyMemberId: StateFlow<String?> = _historyMemberId

    private val _historyActiveDays = MutableStateFlow<Set<Int>>(emptySet())
    val historyActiveDays: StateFlow<Set<Int>> = _historyActiveDays

    private val _historyDaysLoading = MutableStateFlow(false)
    val historyDaysLoading: StateFlow<Boolean> = _historyDaysLoading

    private val _historyPath = MutableStateFlow<List<HistoryPoint>>(emptyList())
    val historyPath: StateFlow<List<HistoryPoint>> = _historyPath

    var myId: String? = null
        private set
    private var watchJob: Job? = null

    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            stopWatching()
            WebSocketClient.get(getApplication())?.disconnect()
        }
        override fun onStart(owner: LifecycleOwner) {
            WebSocketClient.get(getApplication())?.connect()
            if (_members.value.isNotEmpty()) {
                refresh()
                startWatching(_members.value)
            }
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
        load()
        viewModelScope.launch {
            OwnLocationBroadcast.flow.collect { msg ->
                myId?.let { id ->
                    _positions.update { it + (id to msg.copy(userId = id)) }
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching {
                myId = repo.me().id
                repo.family()
            }.onSuccess { members ->
                _members.value = members.sortedByDescending { it.id == myId }
                val initial = members.mapNotNull { m ->
                    m.current?.let { loc ->
                        m.id to LocationUpdateMsg(m.id, loc.lat, loc.lng, loc.accuracy, loc.battery, loc.recordedAt)
                    }
                }.toMap()
                _positions.update { it + initial }
                startWatching(members)
                loadAvatars(members)
            }.onFailure { _error.value = it.message }
            _loading.value = false
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            runCatching { repo.family() }.onSuccess { members ->
                _members.value = members.sortedByDescending { it.id == myId }
                val updated = members.mapNotNull { m ->
                    m.current?.let { loc ->
                        m.id to LocationUpdateMsg(m.id, loc.lat, loc.lng, loc.accuracy, loc.battery, loc.recordedAt)
                    }
                }.toMap()
                _positions.update { it + updated }
            }
        }
    }

    private fun loadAvatars(members: List<FamilyMember>) {
        for (member in members.filter { it.hasAvatar && !_avatars.value.containsKey(it.id) }) {
            viewModelScope.launch {
                val bytes = repo.avatarBytes(member.id) ?: return@launch
                val bmp = createPhotoMarker(bytes)
                _avatars.update { it + (member.id to bmp) }
            }
        }
    }

    fun uploadAvatar(bytes: ByteArray) {
        viewModelScope.launch {
            val compressed = resizeForUpload(bytes)
            val ok = repo.uploadAvatar(compressed)
            if (ok) {
                myId?.let { id -> _avatars.update { it - id } }
                load()
            }
        }
    }

    fun selectMember(memberId: String) {
        _selectedId.value = if (_selectedId.value == memberId) null else memberId
    }

    fun enterHistory(memberId: String) {
        _historyMemberId.value = memberId
        _historyPath.value = emptyList()
        val now = YearMonth.now()
        loadActiveDays(memberId, now.year, now.monthValue)
    }

    fun exitHistory() {
        _historyMemberId.value = null
        _historyActiveDays.value = emptySet()
        _historyPath.value = emptyList()
    }

    fun loadActiveDays(memberId: String, year: Int, month: Int) {
        _historyPath.value = emptyList()
        _historyDaysLoading.value = true
        viewModelScope.launch {
            _historyActiveDays.value = repo.activeDays(memberId, year, month)
            _historyDaysLoading.value = false
        }
    }

    fun loadHistoryDate(memberId: String, year: Int, month: Int, day: Int) {
        viewModelScope.launch {
            val date = LocalDate.of(year, month, day)
            val from = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val to   = from + 86_400_000L - 1L
            _historyPath.value = repo.historyPath(memberId, from, to)
        }
    }

    private fun startWatching(members: List<FamilyMember>) {
        val ws = WebSocketClient.get(getApplication()) ?: return
        for (member in members.filter { it.id != myId }) ws.watchStart(member.id)
        if (watchJob?.isActive == true) return
        watchJob = viewModelScope.launch {
            ws.locationUpdates.collect { msg ->
                _positions.update { current -> current + (msg.userId to msg) }
            }
        }
    }

    private fun stopWatching() {
        val ws = WebSocketClient.get(getApplication()) ?: return
        for (member in _members.value.filter { it.id != myId }) ws.watchStop(member.id)
    }

    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        stopWatching()
    }
}
