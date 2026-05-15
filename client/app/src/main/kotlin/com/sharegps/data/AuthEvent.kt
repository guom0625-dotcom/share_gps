package com.sharegps.data

import kotlinx.coroutines.flow.MutableSharedFlow

object AuthEvent {
    val needsReEnroll = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
