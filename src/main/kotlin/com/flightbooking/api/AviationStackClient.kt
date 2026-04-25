package com.flightbooking.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class AviationStackClient(
    val httpClient: HttpClient
) {
    private val accessKey = "c5ca96d3768b0d1ed89df7850a9a69ec"
    private val url = "https://api.aviationstack.com/v1"

    suspend fun getAirports(limit: Int, offset: Int): AirportApiResponse {
        val response: HttpResponse = httpClient.get("$url/airports") {
            parameter("access_key", accessKey)
            parameter("limit", limit)
            parameter("offset", offset)
        }
        return response.body()
    }

    suspend fun getFlights(limit: Int, offset: Int): FlightApiResponse {
        val response: HttpResponse = httpClient.get("$url/flights") {
            parameter("access_key", accessKey)
            parameter("limit", limit)
            parameter("offset", offset)
        }
        return response.body()
    }
}
@Serializable
data class AirportApiResponse(
    val pagination: Pagination? = null,
    val data: List<ApiAirport> = emptyList()
)
@Serializable
data class Pagination(
    val limit: Int? = null,
    val offset: Int? = null,
    val count: Int? = null,
    val total: Int? = null
)
@Serializable
data class ApiAirport(
    @SerialName("iata_code")
    val iataCode: String? = null,
    @SerialName("airport_name")
    val airportName: String? = null,
    @SerialName("city")
    val city: String? = null,
    @SerialName("country_name")
    val countryName: String? = null
)

@Serializable
data class FlightApiResponse(
    val pagination: Pagination? = null,
    val data: List<ApiFlight> = emptyList()
)
@Serializable
data class ApiFlight(
    @SerialName("flight_date")
    val flightDate: String? = null,
    @SerialName("flight_status")
    val flightStatus: String? = null,
    val departure: ApiFlightEndpoint,
    val arrival: ApiFlightEndpoint,
    val flight: ApiFlightInfo,
    val aircraft: ApiAircraft? = null
)
@Serializable
data class ApiFlightEndpoint(
    val airport: String? = null,
    val iata: String? = null,
    val scheduled: String? = null
)
@Serializable
data class ApiFlightInfo(
    val number: String? = null
)
@Serializable
data class ApiAircraft(
    val registration: String? = null,
    val iata: String? = null,
    val icao: String? = null,
    val icao24: String? = null
)
