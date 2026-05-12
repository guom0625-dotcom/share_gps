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

    suspend fun setShareState(mode: String, minutes: Int? = null): Boolean = try {
        http.post("$serverUrl/me/share-state") {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(ShareStateRequest(mode, minutes))
        }.status.isSuccess()
    } catch (_: Exception) { false }
}
