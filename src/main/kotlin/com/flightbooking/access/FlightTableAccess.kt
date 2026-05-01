package com.flightbooking.access

import com.flightbooking.constants.DAYS_BEFORE_AND_AFTER_TO_SHOW
import com.flightbooking.mappers.toFlight
import com.flightbooking.models.FareOption
import com.flightbooking.models.Flight
import com.flightbooking.models.FlightWithFares
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.FareClassTable
import com.flightbooking.tables.FlightFareTable
import com.flightbooking.tables.FlightTable
import org.jetbrains.exposed.sql.Alias
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val HOURS_DENOMINATOR: Int = 60

/**
 * Provides database access operations for the [FlightTable].
 */
class FlightTableAccess {
    /**
     * Returns all flights in the database.
     */
    fun getAll(): List<Flight> =
        transaction {
            FlightTable.selectAll().map {
                it.toFlight()
            }
        }

    /**
     * Gets list of flights from DB, filtering by attribute and value you want it to be
     */
    fun <T> getByAttribute(
        attribute: Column<T>,
        value: T,
    ): List<Flight> =
        transaction {
            FlightTable.select { attribute eq value }
                .map { it.toFlight() }
        }

    /**
     * Calculate duration based on departure and arrival time
     */
    private fun calculateDuration(
        dep: LocalDateTime?,
        arr: LocalDateTime?,
    ): String {
        return if (dep != null && arr != null) {
            val mins = Duration.between(dep, arr).toMinutes()
            val hours = mins / HOURS_DENOMINATOR
            val remaining = mins % HOURS_DENOMINATOR
            if (remaining == 0L) "${hours}h" else "${hours}h ${remaining}m"
        } else {
            "N/A"
        }
    }

    /**
     * Maps exposed to FareOption
     */
    private fun mapFares(rows: List<ResultRow>): List<FareOption> {
        return rows.map { row ->
            FareOption(
                fareId = row[FlightFareTable.id],
                fareClassId = row[FlightFareTable.fareClassId],
                displayName = row[FareClassTable.displayName],
                cabinClass = row[FareClassTable.cabinClass],
                price = row[FlightFareTable.price],
                currency = row[FlightFareTable.currency],
                seatsAvailable = row[FlightFareTable.seatsAvailable],
            )
        }
    }

    /**
     * Returns flights within ±5 days of [date] between [originCode] and [destinationCode],
     * grouped by flight with all available fares attached.
     *
     * @param originCode IATA code of the origin airport (e.g. "LHR")
     * @param destinationCode IATA code of the destination airport (e.g. "DXB")
     * @param date the target departure date
     * @return list of [FlightWithFares], each containing flight info and available fare options
     */
    fun getFlightsAroundDate(
        originCode: String,
        destinationCode: String,
        date: LocalDate,
    ): List<FlightWithFares> {
        // clamp to today so we don't show flights that have already departed (previous days)
        val dateFrom =
            maxOf(
                date
                    .minusDays(DAYS_BEFORE_AND_AFTER_TO_SHOW),
                LocalDate.now(),
            ).toString() + "T00:00:00+00:00"
        val dateTo =
            date
                .plusDays(DAYS_BEFORE_AND_AFTER_TO_SHOW)
                .toString() + "T23:59:59+00:00"

        // debugging
        println("dateFrom: $dateFrom")
        println("dateTo: $dateTo")
        println("originCode: $originCode")
        println("destinationCode: $destinationCode")

        val originAirport = AirportTable.alias("origin")
        val destinationAirport = AirportTable.alias("destination")

        return transaction {
            addLogger(StdOutSqlLogger)
            FlightTable
                .join(originAirport, JoinType.INNER, FlightTable.originAirport, originAirport[AirportTable.id])
                .join(
                    destinationAirport,
                    JoinType.INNER,
                    FlightTable.destinationAirport,
                    destinationAirport[AirportTable.id],
                )
                .join(FlightFareTable, JoinType.LEFT, FlightTable.id, FlightFareTable.flightId)
                .join(FareClassTable, JoinType.LEFT, FlightFareTable.fareClassId, FareClassTable.id)
                .select {
                    (originAirport[AirportTable.iataCode] eq originCode) and
                        (destinationAirport[AirportTable.iataCode] eq destinationCode) and
                        (FlightTable.scheduledDepartureTime greaterEq dateFrom) and
                        (FlightTable.scheduledDepartureTime lessEq dateTo)
                }
                .toList()
                .filter { row -> row.getOrNull(FlightFareTable.id) != null }
                .groupBy { it[FlightTable.id] }
                .map { (_, rows) -> mapFlightWithFares(rows, originAirport, destinationAirport) }
        }
    }

    /**
     * Maps list of flights and fares to return flight with fares
     */
    private fun mapFlightWithFares(
        rows: List<ResultRow>,
        originAirport: Alias<AirportTable>,
        destinationAirport: Alias<AirportTable>,
    ): FlightWithFares {
        val first = rows.first()
        val dep =
            first[FlightTable.scheduledDepartureTime]?.let {
                LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            }
        val arr =
            first[FlightTable.scheduledArrivalTime]?.let {
                LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            }
        return FlightWithFares(
            flightId = first[FlightTable.id],
            flightNumber = first[FlightTable.flightNumber],
            departureDay = dep?.format(DateTimeFormatter.ofPattern("d MMMM")),
            departureTime = dep?.format(DateTimeFormatter.ofPattern("HH:mm")),
            arrivalTime = arr?.format(DateTimeFormatter.ofPattern("HH:mm")),
            duration = calculateDuration(dep, arr),
            status = first[FlightTable.status],
            capacity = first[FlightTable.capacity],
            originCode = first[originAirport[AirportTable.iataCode]],
            destinationCode = first[destinationAirport[AirportTable.iataCode]],
            fares = mapFares(rows),
        )
    }

    /**
     * Inserts a new flight record into the database.
     * @return true if the insert succeeded
     * @throws ExposedSQLException if the insert fails
     */
    fun createFlight(flight: Flight): Boolean =
        transaction {
            FlightTable.insert {
                it[FlightTable.flightNumber] = flight.flightNumber
                it[FlightTable.originAirport] = flight.originAirport
                it[FlightTable.destinationAirport] = flight.destinationAirport
                it[FlightTable.scheduledDepartureTime] = flight.scheduledDepartureTime
                it[FlightTable.scheduledArrivalTime] = flight.scheduledArrivalTime
                it[FlightTable.status] = flight.status
                it[FlightTable.capacity] = flight.capacity
            }
            true
        }

    /**
     * Deletes a flight by searching with it's ID
     */
    fun deleteByID(id: Int) =
        transaction {
            FlightTable.deleteWhere { FlightTable.id eq id }
        }

    /**
     * Updates a record's attribute with a value passed in
     */
    fun <T> updateRecordByAttribute(
        id: Int,
        column: Column<T>,
        value: T,
    ): Boolean =
        transaction {
            val rows = FlightTable.update({ FlightTable.id eq id }) { stmt -> stmt[column] = value }
            rows > 0
        }

    /**
     * Returns flights where both origin and destination are UK airports.
     * @param ukAirportIDs list of airport IDs in the UK
     */
    fun getDomesticUKFlights(ukAirportIDs: List<Int>): List<Flight> =
        transaction {
            FlightTable.select {
                (FlightTable.originAirport inList ukAirportIDs) and
                    (FlightTable.destinationAirport inList ukAirportIDs)
            }.map { it.toFlight() }
        }
}
