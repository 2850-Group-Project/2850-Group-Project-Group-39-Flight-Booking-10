package com.flightbooking.service

import com.flightbooking.api.AviationStackClient
import com.flightbooking.access.AirportTableAccess
import com.flightbooking.access.FlightTableAccess
import com.flightbooking.tables.FlightTable
import com.flightbooking.api.ApiFlight
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction

const val FLIGHT_IMPORT_LIMIT: Int = 100

class FlightImportService(
    private val client: AviationStackClient,
    private val airportAccess: AirportTableAccess,
    private val flightAccess: FlightTableAccess
) {

    suspend fun importAllFlights() {
        println("importAllFlights running")
        val airports = airportAccess.getAll()
        val iataToID = airports.associate { it.iataCode to it.id }
        transaction { FlightTable.deleteAll() }
        var fetched = 0
        var skipped = 0
        var inserted = 0
        for (apiFlight in fetchAllFlights()) {
            fetched++
            val validated = validateFlight(apiFlight, iataToID)
            if (validated == null) {
                skipped++
                continue
            }
            insertFlight(validated)
            inserted++
        }
        println("Fetched: $fetched")
        println("Skipped: $skipped")
        println("Inserted: $inserted")
        println("importAllFlights done")
    }

    private suspend fun fetchAllFlights(): List<ApiFlight> {
        val results = mutableListOf<ApiFlight>()
        var offset = 0
        var total = Int.MAX_VALUE
        while (offset < total) {
            val response = client.getFlights(FLIGHT_IMPORT_LIMIT, offset)
            val pagination = response.pagination
            val totalFromResponse = pagination?.total
            if (totalFromResponse == null) {
                break
            }
            total = totalFromResponse
            results.addAll(response.data)
            offset += FLIGHT_IMPORT_LIMIT
        }
        return results
    }

    private fun validateFlight(
        apiFlight: ApiFlight,
        iataToID: Map<String, Int>
    ): ValidatedFlight? {

        val originIata = apiFlight.departure.iata
        val destIata = apiFlight.arrival.iata

        val originID = originIata?.let { iataToID[it] }
        val destID = destIata?.let { iataToID[it] }

        val isMissingIata = originIata == null || destIata == null
        val isUnknownAirport = originID == null || destID == null

        return if (isMissingIata || isUnknownAirport) {
            null
        } else {
            ValidatedFlight(
                apiFlight = apiFlight,
                originID = originID,
                destID = destID
            )
        }
    }

    private fun insertFlight(valid: ValidatedFlight) {
        val api = valid.apiFlight
        flightAccess.createFlight(
            flightNumber = api.flight.number?.toIntOrNull(),
            originAirport = valid.originID,
            destinationAirport = valid.destID,
            scheduledDepartureTime = api.departure.scheduled,
            scheduledArrivalTime = api.arrival.scheduled,
            status = api.flightStatus ?: "scheduled",
            capacity = null
        )
    }
}

data class ValidatedFlight(
    val apiFlight: ApiFlight,
    val originID: Int,
    val destID: Int
)
