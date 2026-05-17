package com.sharegps

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharegps.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {

    val updateTag = mutableStateOf<String?>(null)
    private var lastUpdateCheckMs = 0L

    fun checkForUpdate() {
        val now = System.currentTimeMillis()
        if (now - lastUpdateCheckMs < 30 * 60_000L) return
        lastUpdateCheckMs = now
        viewModelScope.launch(Dispatchers.IO) {
            val latest = UpdateChecker.latestTag() ?: return@launch
            if (UpdateChecker.isNewer(latest, BuildConfig.VERSION_NAME)) {
                withContext(Dispatchers.Main) { updateTag.value = latest }
            }
        }
    }
}
