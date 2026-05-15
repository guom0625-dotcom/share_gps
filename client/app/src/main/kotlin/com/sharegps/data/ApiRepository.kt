package com.sharegps.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ApiRepository(private val serverUrl: String, private val apiKey: String) {
    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 10_000 }
        HttpResponseValidator {
            validateResponse { response ->
                if (response.status == HttpStatusCode.Unauthorized) {
                    AuthEvent.needsReEnroll.tryEmit(Unit)
                }
            }
        }
    }

    suspend fun family(): List<FamilyMember> =
        http.get("$serverUrl/family") { bearerAuth(apiKey) }.body()

    suspend fun me(): Me =
        http.get("$serverUrl/me") { bearerAuth(apiKey) }.body()

    suspend fun avatarBytes(userId: String): ByteArray? = try {
        val res = http.get("$serverUrl/users/$userId/avatar") { bearerAuth(apiKey) }
        if (res.status.isSuccess()) res.body<ByteArray>() else null
    } catch (_: Exception) { null }

    suspend fun uploadAvatar(bytes: ByteArray): Boolean = try {
        http.post("$serverUrl/me/avatar") {
            bearerAuth(apiKey)
            contentType(ContentType.parse("image/jpeg"))
            setBody(bytes)
        }.status.isSuccess()
    } catch (_: Exception) { false }

    suspend fun activeDays(userId: String, year: Int, month: Int): Set<Int> = try {
        @Serializable data class Resp(val days: List<Int>)
        http.get("$serverUrl/locations/$userId/active-days") {
            bearerAuth(apiKey)
            parameter("year", year)
            parameter("month", month)
        }.body<Resp>().days.toSet()
    } catch (_: Exception) { emptySet() }

    suspend fun historyPath(userId: String, from: Long, to: Long): List<HistoryPoint> = try {
        http.get("$serverUrl/locations/$userId/history") {
            bearerAuth(apiKey)
            parameter("from", from)
            parameter("to", to)
        }.body()
    } catch (_: Exception) { emptyList() }

    suspend fun setShareState(mode: String, pausedUntilMinutes: Int? = null): Boolean = try {
        http.post("$serverUrl/me/share-state") {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(ShareStateRequest(mode, pausedUntilMinutes = pausedUntilMinutes))
        }.status.isSuccess()
    } catch (_: Exception) { false }
}
