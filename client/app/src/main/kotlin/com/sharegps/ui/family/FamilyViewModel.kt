package com.sharegps.ui.family

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sharegps.BuildConfig
import com.sharegps.data.ApiRepository
import com.sharegps.data.FamilyMember
import com.sharegps.data.KeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FamilyViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ApiRepository(BuildConfig.SERVER_URL, KeyStore(app).getKey() ?: "")

    private val _members = MutableStateFlow<List<FamilyMember>>(emptyList())
    val members: StateFlow<List<FamilyMember>> = _members

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init { load() }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching { repo.family() }
                .onSuccess { _members.value = it }
                .onFailure { _error.value = it.message }
            _loading.value = false
        }
    }
}
