package com.flightbooking.access

import com.flightbooking.models.Flight
import com.flightbooking.models.toFlight
import com.flightbooking.models.FlightWithFares
import com.flightbooking.models.FareOption

import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.FlightFareTable
import com.flightbooking.tables.FareClassTable

import com.flightbooking.constants.DAYS_BEFORE_AND_AFTER_TO_SHOW

import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.Slf4jSqlDebugLogger
import org.jetbrains.exposed.sql.StdOutSqlLogger

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.Duration

const val HOURS_DENOMINATOR: Int = 60

/**
 * Provides database access operations for the [FlightTable].
 */
class FlightTableAccess {

    /**
     * Returns all flights in the database.
     */
    fun getAll(): List<Flight> = transaction {
        FlightTable.selectAll().map {
            it.toFlight()
        }
    }

    /**
     * Returns flights matching a specific column value.
     * @param attribute the column to filter by
     * @param value the value to match
     */
    fun <T> getByAttribute(attribute: Column<T>, value: T): List<Flight> = transaction {
        FlightTable.select { attribute eq value }
            .map { it.toFlight() }
    }

    private fun calculateDuration(dep: LocalDateTime?, arr: LocalDateTime?): String {
        return if (dep != null && arr != null) {
            val mins = Duration.between(dep, arr).toMinutes()
            val hours = mins / HOURS_DENOMINATOR
            val remaining = mins % HOURS_DENOMINATOR
            if (remaining == 0L) "${hours}h" else "${hours}h ${remaining}m"
        } else "N/A"
    }

    private fun mapFares(rows: List<ResultRow>): List<FareOption> {
        return rows.map { row ->
            FareOption(
                fareId = row[FlightFareTable.id],
                fareClassId = row[FlightFareTable.fareClassId],
                displayName = row[FareClassTable.displayName],
                cabinClass = row[FareClassTable.cabinClass],
                price = row[FlightFareTable.price],
                currency = row[FlightFareTable.currency],
                seatsAvailable = row[FlightFareTable.seatsAvailable]
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
        val dateFrom = maxOf(date
            .minusDays(DAYS_BEFORE_AND_AFTER_TO_SHOW), 
            LocalDate.now()).toString() + "T00:00:00+00:00"
        val dateTo = date
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
                .join(destinationAirport, JoinType.INNER, 
                    FlightTable.destinationAirport, 
                    destinationAirport[AirportTable.id])
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
                .map { (_, rows) ->
                    val first = rows.first()
                    val dep = first[FlightTable.scheduledDepartureTime]?.let {
                        LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }
                    val arr = first[FlightTable.scheduledArrivalTime]?.let {
                        LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }

                    FlightWithFares(
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
                        fares = mapFares(rows)
                    )
                }
        }
    }

    /**
     * Inserts a new flight record into the database.
     * @return true if the insert succeeded
     * @throws ExposedSQLException if the insert fails
     */
    @Suppress("LongParameterList")
    fun createFlight(
        flightNumber: Int?,
        originAirport: Int,
        destinationAirport: Int,
        scheduledDepartureTime: String?,
        scheduledArrivalTime: String?,
        status: String,
        capacity: Int?
    ): Boolean = transaction {
        FlightTable.insert {
            it[FlightTable.flightNumber] = flightNumber
            it[FlightTable.originAirport] = originAirport
            it[FlightTable.destinationAirport] = destinationAirport
            it[FlightTable.scheduledDepartureTime] = scheduledDepartureTime
            it[FlightTable.scheduledArrivalTime] = scheduledArrivalTime
            it[FlightTable.status] = status
            it[FlightTable.capacity] = capacity
        }
        true
    }

    /**
     * Deletes a flight by its primary key.
     * @param id the flight ID to delete
     */
    fun deleteByID(id: Int) = transaction {
        FlightTable.deleteWhere { FlightTable.id eq id }
    }

    /**
     * Updates a single column on a flight record.
     * @param id the flight ID to update
     * @param column the column to update
     * @param value the new value
     * @return true if at least one row was updated
     */
    fun <T> updateRecordByAttribute(id: Int, column: Column<T>, value: T): Boolean = transaction {
        val rows = FlightTable.update({ FlightTable.id eq id }) { stmt -> stmt[column] = value }
        rows > 0
    }

    /**
     * Returns flights where both origin and destination are UK airports.
     * @param ukAirportIDs list of airport IDs in the UK
     */
    fun getDomesticUKFlights(ukAirportIDs: List<Int>): List<Flight> = transaction {
        FlightTable.select {
            (FlightTable.originAirport inList ukAirportIDs) and
            (FlightTable.destinationAirport inList ukAirportIDs)
        }.map { it.toFlight() }
    }
}
