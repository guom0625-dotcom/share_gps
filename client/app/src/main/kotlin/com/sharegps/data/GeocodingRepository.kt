package com.sharegps.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GeocodingRepository(private val clientId: String, private val clientSecret: String) {

    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 5_000 }
    }

    @Serializable private data class RgResponse(val results: List<RgResult> = emptyList())
    @Serializable private data class RgResult(val region: RgRegion = RgRegion())
    @Serializable private data class RgRegion(
        val area1: RgArea = RgArea(), val area2: RgArea = RgArea(),
        val area3: RgArea = RgArea(), val area4: RgArea = RgArea(),
    )
    @Serializable private data class RgArea(val name: String = "")

    suspend fun reverseGeocode(lat: Double, lng: Double): String? = try {
        val resp = http.get("https://maps.apigw.ntruss.com/map-reversegeocode/v2/gc") {
            parameter("coords", "$lng,$lat")
            parameter("output", "json")
            parameter("orders", "roadaddr,addr")
            header("X-NCP-APIGW-API-KEY-ID", clientId)
            header("X-NCP-APIGW-API-KEY", clientSecret)
        }.body<RgResponse>()
        val region = resp.results.firstOrNull()?.region ?: return null
        region.area3.name.ifEmpty { region.area2.name }.ifEmpty { null }
    } catch (_: Exception) { null }
}
