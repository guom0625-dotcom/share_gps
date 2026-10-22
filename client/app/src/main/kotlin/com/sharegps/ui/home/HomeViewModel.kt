package com.sharegps.ui.home

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.sharegps.BuildConfig
import com.sharegps.data.ApiRepository
import com.sharegps.data.FamilyMember
import com.sharegps.data.GeocodingRepository
import com.sharegps.data.HistoryPoint
import com.sharegps.data.KeyStore
import com.sharegps.data.LocationUpdateMsg
import com.sharegps.data.OwnLocationBroadcast
import com.sharegps.data.Prefs
import com.sharegps.data.WebSocketClient
import com.sharegps.data.resolveServerUrl
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ApiRepository({ resolveServerUrl(app) }, KeyStore(app).getKey() ?: "")
    private val geocoding = GeocodingRepository(BuildConfig.NAVER_MAPS_CLIENT_ID, BuildConfig.NAVER_GEOCODING_SECRET)

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

    private val _historyActiveDaysError = MutableStateFlow(false)
    val historyActiveDaysError: StateFlow<Boolean> = _historyActiveDaysError

    private val _historyPath = MutableStateFlow<List<HistoryPoint>>(emptyList())
    val historyPath: StateFlow<List<HistoryPoint>> = _historyPath

    private val _historyEvents = MutableStateFlow<List<PathEvent>>(emptyList())
    val historyEvents: StateFlow<List<PathEvent>> = _historyEvents

    private val _historyPlaceNames = MutableStateFlow<Map<Int, String>>(emptyMap())
    val historyPlaceNames: StateFlow<Map<Int, String>> = _historyPlaceNames

    var myId: String? = null
        private set
    private var watchJob: Job? = null
    // 현재 watch_start를 보낸 대상. 저배터리 대상은 선택+확인한 동안에만 추적한다(기존과 동일).
    private var watching: Set<String> = emptySet()
    private var lowBatteryApprovedId: String? = null
    private var cleared = false
    private val prefs = Prefs(app)
    private val _watchAllLive = MutableStateFlow(prefs.watchAllLive)
    val watchAllLive: StateFlow<Boolean> = _watchAllLive
    private var historyDateJob: Job? = null

    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (_members.value.isNotEmpty()) {
                refresh()
                viewModelScope.launch {
                    repo.requestLocations()
                    delay(3_000)
                    refresh()
                }
            }
            startLocationUpdatesJob()
            syncWatches()
        }
        override fun onStop(owner: LifecycleOwner) {
            // 백그라운드 진입 시 활성 추적 해제 — 복귀했을 때 선택 대상을 자동으로 이어 추적하지 않음
            _selectedId.value = null
            lowBatteryApprovedId = null
            syncWatches()
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
                _members.value = members.sortedBy { it.id == myId }
                val initial = members.mapNotNull { m ->
                    m.current?.let { loc ->
                        m.id to LocationUpdateMsg(m.id, loc.lat, loc.lng, loc.accuracy, loc.battery, loc.recordedAt)
                    }
                }.toMap()
                _positions.update { it + initial }
                startLocationUpdatesJob()
                syncWatches()
                loadAvatars(members)
            }.onFailure { _error.value = it.message }
            _loading.value = false
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            runCatching { repo.family() }.onSuccess { members ->
                _members.value = members.sortedBy { it.id == myId }
                val updated = members.mapNotNull { m ->
                    m.current?.let { loc ->
                        m.id to LocationUpdateMsg(m.id, loc.lat, loc.lng, loc.accuracy, loc.battery, loc.recordedAt)
                    }
                }.toMap()
                _positions.update { it + updated }
                syncWatches()
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

    private val _lowBatteryConfirmTarget = MutableStateFlow<String?>(null)
    val lowBatteryConfirmTarget: StateFlow<String?> = _lowBatteryConfirmTarget

    // 전원 실시간 모드: 선택은 지도 포커스만 담당하고, 추적은 앱이 열려 있는 동안 가족 전원에게 건다.
    // 선택 모드: 선택한 사람만 추적한다.
    // 두 모드 모두 배터리 20% 이하인 사람은 선택 후 확인을 거친 경우에만 추적한다.
    fun selectMember(memberId: String) {
        val next = if (_selectedId.value == memberId) null else memberId
        if (next != null && next != myId && next !in watching && isLowBattery(next)) {
            _lowBatteryConfirmTarget.value = memberId
            return
        }
        _selectedId.value = next
        if (lowBatteryApprovedId != next) lowBatteryApprovedId = null
        syncWatches()
    }

    fun setWatchAllLive(enabled: Boolean) {
        prefs.watchAllLive = enabled
        _watchAllLive.value = enabled
        syncWatches()
    }

    fun confirmWatch(memberId: String) {
        _lowBatteryConfirmTarget.value = null
        lowBatteryApprovedId = memberId
        _selectedId.value = memberId
        syncWatches()
    }

    fun dismissLowBatteryConfirm() {
        _lowBatteryConfirmTarget.value = null
    }

    private fun isLowBattery(memberId: String): Boolean =
        _positions.value[memberId]?.battery?.let { it <= 20 } ?: false

    // 이미 추적 중인 선택 대상은 도중에 배터리가 20% 이하로 떨어져도 끊지 않는다(기존 동작).
    private fun isTrackedSelection(memberId: String): Boolean =
        memberId == _selectedId.value && memberId in watching

    private fun syncWatches() {
        val active = !cleared &&
            _historyMemberId.value == null &&
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val candidates = when {
            !active -> emptyList()
            _watchAllLive.value -> _members.value.map { it.id }
            else -> listOfNotNull(_selectedId.value)
        }
        val desired = candidates
            .filter { it != myId && (it == lowBatteryApprovedId || !isLowBattery(it) || isTrackedSelection(it)) }
            .toSet()
        val ws = WebSocketClient.get(getApplication()) ?: return
        (desired - watching).forEach { ws.watchStart(it) }
        (watching - desired).forEach { ws.watchStop(it) }
        watching = desired
    }

    fun enterHistory(memberId: String) {
        // 히스토리 진입 시 활성 추적을 멈춰 상대 폰들의 GPS 부하/배터리 소모를 끊는다
        _selectedId.value = null
        lowBatteryApprovedId = null
        _historyMemberId.value = memberId
        syncWatches()
        _historyPath.value = emptyList()
        val now = YearMonth.now()
        loadActiveDays(memberId, now.year, now.monthValue)
    }

    fun exitHistory() {
        _historyMemberId.value = null
        syncWatches()
        _historyActiveDays.value = emptySet()
        _historyPath.value = emptyList()
        _historyEvents.value = emptyList()
        _historyPlaceNames.value = emptyMap()
    }

    fun clearHistoryDate() {
        _historyPath.value = emptyList()
        _historyEvents.value = emptyList()
        _historyPlaceNames.value = emptyMap()
    }

    fun loadActiveDays(memberId: String, year: Int, month: Int) {
        _historyPath.value = emptyList()
        _historyEvents.value = emptyList()
        _historyPlaceNames.value = emptyMap()
        _historyDaysLoading.value = true
        _historyActiveDaysError.value = false
        viewModelScope.launch {
            runCatching { repo.activeDays(memberId, year, month) }
                .onSuccess { _historyActiveDays.value = it }
                .onFailure { _historyActiveDaysError.value = true }
            _historyDaysLoading.value = false
        }
    }

    fun loadHistoryDate(memberId: String, year: Int, month: Int, day: Int) {
        historyDateJob?.cancel()
        historyDateJob = viewModelScope.launch {
            val date = LocalDate.of(year, month, day)
            val from = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val to   = from + 86_400_000L - 1L
            val path = repo.historyPath(memberId, from, to)
            _historyPath.value = path
            _historyPlaceNames.value = emptyMap()
            val events = processHistoryPath(path)
            _historyEvents.value = events
            events.forEachIndexed { idx, event ->
                if (event is PathEvent.Stay) launch {
                    val name = geocoding.reverseGeocode(event.lat, event.lng) ?: return@launch
                    _historyPlaceNames.update { it + (idx to name) }
                }
            }
        }
    }

    private fun startLocationUpdatesJob() {
        if (watchJob?.isActive == true) return
        val ws = WebSocketClient.get(getApplication()) ?: return
        watchJob = viewModelScope.launch {
            ws.locationUpdates.collect { msg ->
                _positions.update { current -> current + (msg.userId to msg) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        cleared = true
        syncWatches()
    }
}
