package com.sharegps.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sharegps.BuildConfig
import com.sharegps.data.ApiRepository
import com.sharegps.data.KeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ShareStateViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ApiRepository(BuildConfig.SERVER_URL, KeyStore(app).getKey() ?: "")

    private val _mode = MutableStateFlow("sharing")
    val mode: StateFlow<String> = _mode

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    init { fetchMode() }

    private fun fetchMode() {
        viewModelScope.launch {
            runCatching { repo.me() }.onSuccess { _mode.value = it.shareState.mode }
        }
    }

    fun setMode(mode: String, minutes: Int? = null) {
        viewModelScope.launch {
            _loading.value = true
            val ok = runCatching { repo.setShareState(mode, minutes) }.getOrDefault(false)
            if (ok) _mode.value = mode
            _loading.value = false
        }
    }
}
