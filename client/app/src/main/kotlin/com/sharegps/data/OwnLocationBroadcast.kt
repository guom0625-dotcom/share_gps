package com.sharegps.data

import kotlinx.coroutines.flow.MutableSharedFlow

object OwnLocationBroadcast {
    val flow = MutableSharedFlow<LocationUpdateMsg>(replay = 1, extraBufferCapacity = 1)
}
