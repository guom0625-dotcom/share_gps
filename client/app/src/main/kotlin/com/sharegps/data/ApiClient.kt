package com.sharegps.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class LocationPayload(
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val recordedAt: Long,
)

class ApiClient(private val serverUrl: String, private val apiKey: String) {
    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 15_000 }
    }

    suspend fun uploadBatch(rows: List<LocationQueueEntity>): Boolean = try {
        val response = http.post("$serverUrl/locations/batch") {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(rows.map { LocationPayload(it.lat, it.lng, it.accuracy, it.timestamp) })
        }
        response.status.isSuccess()
    } catch (_: Exception) {
        false
    }
}
