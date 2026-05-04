package com.flightbooking.routes

import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.ComplaintTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.SeatAssignmentTable
import com.flightbooking.tables.SeatTable
import com.flightbooking.tables.StaffTable
import io.ktor.server.routing.get
import io.ktor.server.sessions.get
import io.ktor.server.sessions.set
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.castTo
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate

private const val FLIGHT_LIST_LIMIT = 8
private const val ISO_TIME_START_INDEX = 0
private const val ISO_TIME_END_INDEX = 16
private const val DEFAULT_CAPACITY = 180
private const val STAFF_FLIGHTS_PAGE_LIMIT = 10

/**
 * Helper function that searches and returns a list of Airports mapping:
 * id, iataCode and name (defaults to "")
 * @return list of airports
 */
fun queryAirports(): List<Map<String, Any>> =
    AirportTable
        .selectAll()
        .orderBy(AirportTable.iataCode, SortOrder.ASC)
        .map {
            mapOf(
                "id" to it[AirportTable.id],
                "iata" to it[AirportTable.iataCode],
                "name" to (it[AirportTable.name] ?: ""),
            )
        }

/**
 * Queries flight list for staff dashboard to display
 * @param q search text
 * @return flights list
 */
fun queryFlightList(q: String): List<Map<String, Any>> {
    val origin = AirportTable.alias("origin")
    val dest = AirportTable.alias("dest")

    return FlightTable
        .join(origin, JoinType.INNER, additionalConstraint = {
            FlightTable.originAirport eq origin[AirportTable.id]
        })
        .join(dest, JoinType.INNER, additionalConstraint = {
            FlightTable.destinationAirport eq dest[AirportTable.id]
        })
        .slice(
            FlightTable.id,
            FlightTable.flightNumber,
            FlightTable.originAirport,
            FlightTable.destinationAirport,
            FlightTable.scheduledDepartureTime,
            FlightTable.scheduledArrivalTime,
            FlightTable.status,
            FlightTable.capacity,
            origin[AirportTable.iataCode],
            dest[AirportTable.iataCode],
        )
        .select {
            if (q.isBlank()) {
                Op.TRUE
            } else {
                FlightTable.flightNumber.castTo<String>(VarCharColumnType()).like("%$q%")
            }
        }
        .orderBy(FlightTable.id, SortOrder.DESC)
        .limit(STAFF_FLIGHTS_PAGE_LIMIT)
        .map { row ->
            mapOf(
                "id" to row[FlightTable.id],
                "flightNumber" to (row[FlightTable.flightNumber]?.toString() ?: ""),
                "originId" to row[FlightTable.originAirport],
                "destId" to row[FlightTable.destinationAirport],
                "originIata" to row[origin[AirportTable.iataCode]],
                "destIata" to row[dest[AirportTable.iataCode]],
                "dep" to (row[FlightTable.scheduledDepartureTime] ?: ""),
                "arr" to (row[FlightTable.scheduledArrivalTime] ?: ""),
                "status" to row[FlightTable.status],
                "capacity" to (row[FlightTable.capacity]?.toString() ?: ""),
            )
        }
}

/**
 * Builds the flights model for the staff flights page to display
 * @param q search text
 * @param editId id to edit
 * @param urlError error text
 * @param urlOk ok text
 * @return flights model
 */
fun buildStaffFlightsModel(
    q: String,
    editId: Int?,
    urlError: String,
    urlOk: String,
): Map<String, Any> =
    transaction {
        val airports = queryAirports()
        val flights = queryFlightList(q)
        val editFlight = if (editId != null) flights.firstOrNull { (it["id"] as Int) == editId } else null

        mapOf(
            "airports" to airports,
            "flights" to flights,
            "q" to q,
            "editId" to (editId ?: 0),
            "editFlight" to (editFlight ?: mapOf<String, Any>()),
            "error" to urlError,
            "ok" to urlOk,
        )
    }

/**
 * Queries and returns a list of active flights
 * @return active flights list
 */
fun queryActiveFlightList(): List<Map<String, String>> {
    val origin = AirportTable.alias("origin")
    val dest = AirportTable.alias("dest")

    return FlightTable
        .join(origin, JoinType.INNER, additionalConstraint = {
            FlightTable.originAirport eq origin[AirportTable.id]
        })
        .join(dest, JoinType.INNER, additionalConstraint = {
            FlightTable.destinationAirport eq dest[AirportTable.id]
        })
        .slice(
            FlightTable.id,
            FlightTable.flightNumber,
            FlightTable.scheduledDepartureTime,
            FlightTable.status,
            FlightTable.capacity,
            origin[AirportTable.iataCode],
            dest[AirportTable.iataCode],
            dest[AirportTable.city],
        )
        .select { FlightTable.status neq "cancelled" }
        .orderBy(FlightTable.scheduledDepartureTime, SortOrder.ASC)
        .limit(FLIGHT_LIST_LIMIT)
        .map { row ->
            val no = row[FlightTable.flightNumber]?.toString() ?: row[FlightTable.id].toString()
            val destinationCity = row[dest[AirportTable.city]] ?: ""
            val destinationIata = row[dest[AirportTable.iataCode]]
            val destination = "$destinationCity ($destinationIata)"
            val departureTimeRaw = row[FlightTable.scheduledDepartureTime] ?: ""

            departureTimeRaw.substring(ISO_TIME_START_INDEX, ISO_TIME_END_INDEX)
            val splitDepartureTime = departureTimeRaw.split(" ")
            val departureDate = splitDepartureTime[0]
            val departureTime = splitDepartureTime[1]

            val assignedSeats =
                SeatAssignmentTable
                    .join(BookingSegmentTable, JoinType.INNER, additionalConstraint = {
                        SeatAssignmentTable.bookingSegmentId eq BookingSegmentTable.id
                    })
                    .select { BookingSegmentTable.flightId eq row[FlightTable.id] }
                    .count()

            mapOf(
                "no" to no,
                "dest" to destination,
                "depatureDate" to departureDate,
                "depatureTime" to departureTime,
                "status" to row[FlightTable.status],
                "capacity" to (row[FlightTable.capacity]?.toString() ?: "0"),
                "assignedSeats" to (assignedSeats.toString()),
            )
        }
}

/**
 * Function builds the model for staff dashboard to display
 * @param staffEmail staff email
 * @return dashboard model
 */
fun buildStaffDashboardModel(staffEmail: String): Map<String, Any> =
    transaction {
        val staffRow =
            StaffTable
                .select { StaffTable.email eq staffEmail }
                .limit(1)
                .firstOrNull()
                ?: return@transaction mapOf("error" to "Staff not found, please login again.")

        val staffName =
            listOfNotNull(
                staffRow[StaffTable.firstName],
                staffRow[StaffTable.lastName],
            ).joinToString(" ").ifBlank { "Staff" }

        val staffRole = staffRow[StaffTable.role] ?: "Staff"

        val todayPrefix = LocalDate.now().toString()

        val activeFlightsCount =
            FlightTable
                .select { FlightTable.status neq "cancelled" }
                .count()

        val departuresTodayCount =
            FlightTable
                .select { FlightTable.scheduledDepartureTime like "$todayPrefix%" }
                .count()

        val customerInquiries =
            ComplaintTable
                .select { ComplaintTable.status eq "open" }
                .count()

        val flightList = queryActiveFlightList()

        mapOf(
            "staffEmail" to staffEmail,
            "staffName" to staffName,
            "staffRole" to staffRole,
            "activeFlights" to activeFlightsCount,
            "departuresToday" to departuresTodayCount,
            "customerInquiries" to customerInquiries,
            "flights" to flightList,
        )
    }

/**
 * Generates the full set of seats for a flight if none exist yet
 * @param flightId flight id
 * @param capacity seat capacity
 */
fun createSeatsForFlight(
    flightId: Int,
    capacity: Int?,
) {
    val existing = SeatTable.select { SeatTable.flightId eq flightId }.count()
    if (existing > 0L) return

    val total = (capacity ?: DEFAULT_CAPACITY).coerceAtLeast(1)
    val letters = listOf("A", "B", "C", "D", "E", "F")
    val seatMaps = ArrayList<Map<String, Any>>(total)

    for (i in 0 until total) {
        val row = (i / letters.size) + 1
        val col = letters[i % letters.size]
        val code = "$row$col"
        seatMaps.add(mapOf("flightId" to flightId, "seatCode" to code))
    }

    SeatTable.batchInsert(seatMaps) { s ->
        this[SeatTable.flightId] = s["flightId"] as Int
        this[SeatTable.seatCode] = s["seatCode"] as String
        this[SeatTable.cabinClass] = null
        this[SeatTable.position] = null
        this[SeatTable.extraLegroom] = 0
        this[SeatTable.exitRow] = 0
        this[SeatTable.reducedMobility] = 0
        this[SeatTable.status] = "available"
    }
}
