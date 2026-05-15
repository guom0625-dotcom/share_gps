package com.sharegps.data

import kotlinx.serialization.Serializable

@Serializable
data class CurrentLocation(
    val lat: Double,
    val lng: Double,
    val accuracy: Double? = null,
    val battery: Int? = null,
    val recordedAt: Long,
)

@Serializable
data class FamilyMember(
    val id: String,
    val name: String,
    val role: String,
    val shareMode: String = "sharing",
    val current: CurrentLocation? = null,
)

data class LocationUpdateMsg(
    val userId: String,
    val lat: Double,
    val lng: Double,
    val accuracy: Double?,
    val battery: Int? = null,
    val recordedAt: Long,
)

@Serializable
data class ShareState(
    val mode: String,
    val precisionMode: String = "exact",
    val pausedUntil: Long? = null,
)

@Serializable
data class Me(
    val id: String,
    val name: String,
    val role: String,
    val shareState: ShareState,
)

@Serializable
data class ShareStateRequest(
    val mode: String,
    val precisionMode: String = "exact",
    val pausedUntilMinutes: Int? = null,
)
