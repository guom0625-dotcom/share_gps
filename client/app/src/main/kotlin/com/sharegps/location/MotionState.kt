package com.sharegps.location

import kotlinx.coroutines.flow.MutableStateFlow

object MotionState {
    val isStill = MutableStateFlow(false)
}
