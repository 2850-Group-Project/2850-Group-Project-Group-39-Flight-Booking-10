package com.flightbooking.service

import com.flightbooking.access.AirportTableAccess
import com.flightbooking.access.FlightTableAccess
import com.flightbooking.api.ApiFlight
import com.flightbooking.api.AviationStackClient
import com.flightbooking.models.Flight
import com.flightbooking.tables.FlightTable
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction

const val FLIGHT_IMPORT_LIMIT: Int = 100

/**
 * Service object for flight importing and other functions used
 */
class FlightImportService(
    private val client: AviationStackClient,
    private val airportAccess: AirportTableAccess,
    private val flightAccess: FlightTableAccess,
) {
    /**
     * Imports all flights from the AviationStack API and saves them to the database.
     */
    suspend fun importAllFlights() {
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
    }

    /**
     * Fetches all flights from AviationStack API, handles pagination
     * @return list of ApiFlight objects
     */
    private suspend fun fetchAllFlights(): List<ApiFlight> {
        val results = mutableListOf<ApiFlight>()
        var offset = 0
        var total = Int.MAX_VALUE

        while (offset < total) {
            val response = client.getFlights(FLIGHT_IMPORT_LIMIT, offset)
            val pagination = response.pagination
            val totalFromResponse = pagination?.total ?: break
            total = totalFromResponse
            results.addAll(response.data)
            offset += FLIGHT_IMPORT_LIMIT
        }
        return results
    }

    /**
     * Validates ApiFlight data against database
     * @param apiFlight: the flight data
     * @param iataToID: map of IATAcodes to databases airport IDs
     * @return ValidatedFlight if valid, null if missing Iata or unknown airport
     */
    private fun validateFlight(
        apiFlight: ApiFlight,
        iataToID: Map<String, Int>,
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
                originID = originID!!,
                destID = destID!!,
            )
        }
    }

    /**
     * Inserts a validated flight into the database
     * @param valid: the flight data (assuming already validated)
     */
    private fun insertFlight(valid: ValidatedFlight) {
        val api = valid.apiFlight
        flightAccess.createFlight(
            Flight(
                id = 0,
                flightNumber = api.flight.number?.toIntOrNull(),
                originAirport = valid.originID,
                destinationAirport = valid.destID,
                scheduledDepartureTime = api.departure.scheduled,
                scheduledArrivalTime = api.arrival.scheduled,
                status = api.flightStatus ?: "scheduled",
                capacity = null,
            ),
        )
    }
}

/**
 * Class used to pass data into insertFlight function
 */
data class ValidatedFlight(
    val apiFlight: ApiFlight,
    val originID: Int,
    val destID: Int,
)
