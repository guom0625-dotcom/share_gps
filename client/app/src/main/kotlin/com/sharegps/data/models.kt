package com.sharegps.data

import kotlinx.serialization.Serializable

@Serializable
data class CurrentLocation(
    val lat: Double,
    val lng: Double,
    val accuracy: Double? = null,
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
    val recordedAt: Long,
)
