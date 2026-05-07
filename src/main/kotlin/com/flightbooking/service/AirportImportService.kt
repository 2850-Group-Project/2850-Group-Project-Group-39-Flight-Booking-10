package com.flightbooking.service

import com.flightbooking.access.AirportTableAccess
import com.flightbooking.api.AviationStackClient
import com.flightbooking.models.Airport

const val IMPORT_LIMIT: Int = 100

/**
 * Service object for airport importing using AviationStack API
 */
class AirportImportService(
    private val client: AviationStackClient,
    private val access: AirportTableAccess,
) {
    /**
     * Imports airport data from AviationStack API
     * @param limit: import limit
     * @param offset
     * @param total: how many total
     * @param done
     */
    suspend fun importAllAirports() {
        val limit = IMPORT_LIMIT
        var offset = 0
        var total = Int.MAX_VALUE
        var done = false

        while (offset < total && !done) {
            val response = client.getAirports(limit, offset)
            val pagination = response.pagination
            if (pagination == null || pagination.total == null) {
                done = true
            } else {
                total = pagination.total
                response.data
                    .filter { !it.iataCode.isNullOrBlank() }
                    .forEach { apiAirport ->
                        val airport =
                            Airport(
                                id = 0,
                                iataCode = apiAirport.iataCode!!,
                                name = apiAirport.airportName ?: "Unknown Airport",
                                city = apiAirport.city ?: "Unknown City",
                                country = apiAirport.countryName ?: "Unknown Country",
                            )
                        access.upsertByIata(airport)
                    }
                offset += limit
            }
        }
    }
}
