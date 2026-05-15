package com.sharegps.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ApiRepository(private val serverUrl: String, private val apiKey: String) {
    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 10_000 }
    }

    suspend fun family(): List<FamilyMember> =
        http.get("$serverUrl/family") { bearerAuth(apiKey) }.body()

    suspend fun me(): Me =
        http.get("$serverUrl/me") { bearerAuth(apiKey) }.body()

    suspend fun avatarBytes(userId: String): ByteArray? = try {
        val res = http.get("$serverUrl/users/$userId/avatar")
        if (res.status.isSuccess()) res.body<ByteArray>() else null
    } catch (_: Exception) { null }

    suspend fun uploadAvatar(bytes: ByteArray): Boolean = try {
        http.post("$serverUrl/me/avatar") {
            bearerAuth(apiKey)
            contentType(ContentType.parse("image/jpeg"))
            setBody(bytes)
        }.status.isSuccess()
    } catch (_: Exception) { false }

    suspend fun setShareState(mode: String, pausedUntilMinutes: Int? = null): Boolean = try {
        http.post("$serverUrl/me/share-state") {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(ShareStateRequest(mode, pausedUntilMinutes = pausedUntilMinutes))
        }.status.isSuccess()
    } catch (_: Exception) { false }
}
