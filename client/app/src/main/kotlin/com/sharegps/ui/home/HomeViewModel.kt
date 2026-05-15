package com.sharegps.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.sharegps.data.ApiRepository
import com.sharegps.data.FamilyMember
import com.sharegps.data.KeyStore
import com.sharegps.data.LocationUpdateMsg
import com.sharegps.data.WebSocketClient
import com.sharegps.data.resolveServerUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    private var myId: String? = null

    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) = stopWatching()
        override fun onStart(owner: LifecycleOwner) {
            if (_members.value.isNotEmpty()) startWatching(_members.value)
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching {
                myId = repo.me().id
                repo.family()
            }.onSuccess { members ->
                _members.value = members
                val initial = members.mapNotNull { m ->
                    m.current?.let { loc ->
                        m.id to LocationUpdateMsg(m.id, loc.lat, loc.lng, loc.accuracy, loc.recordedAt)
                    }
                }.toMap()
                _positions.update { it + initial }
                startWatching(members)
            }.onFailure { _error.value = it.message }
            _loading.value = false
        }
    }

    fun selectMember(memberId: String) {
        _selectedId.value = if (_selectedId.value == memberId) null else memberId
    }

    private fun startWatching(members: List<FamilyMember>) {
        val ws = WebSocketClient.get(getApplication()) ?: return
        for (member in members.filter { it.id != myId }) ws.watchStart(member.id)
        viewModelScope.launch {
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
