package com.flightbooking.routes

import com.flightbooking.access.AirportTableAccess
import com.flightbooking.access.FlightTableAccess
import com.flightbooking.access.SeatTableAccess
import com.flightbooking.access.SeatAssignmentTableAccess
import com.flightbooking.access.BookingSegmentTableAccess
import com.flightbooking.access.PassengerTableAccess
import com.flightbooking.models.BookingSession
import com.flightbooking.models.UserSession
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.SeatTable
import io.ktor.server.application.call
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlin.math.ceil

import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.and

import com.flightbooking.tables.PassengerTable
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.SeatAssignmentTable
/**
 * Seat selection routes (booking flow).
 *
 * This version matches the current flow:
 * - Passenger Details POST saves passengers into [BookingSession]
 * Pages:
 * - GET  /flights/seats
 *   Renders `seat_selection.peb` for the **selected outbound flight** stored in [BookingSession].
 *   Seat map is generated from flight capacity (3 aircraft categories).
 *   If seat rows exist in DB for that flight, their status is used to mark seats occupied/available.
 *
 * - POST /flights/seats
 *   Validates selected seat is available (based on DB seat rows if present),
 *   then redirects to the next step (typically payment) with `seatCode` as a query param.
 *
 * Notes:
 * - This route does NOT update `seat_assignment` or `seat.status` because booking/segment/assignment
 *   may not exist yet in the DB at this stage.
 */

private const val SMALL_AISLE_GAP_INDEX = 3
private const val MEDIUM_FIRST_AISLE_GAP_INDEX = 2
private const val MEDIUM_SECOND_AISLE_GAP_INDEX = 6
private const val LARGE_FIRST_AISLE_GAP_INDEX = 3
private const val LARGE_SECOND_AISLE_GAP_INDEX = 7
private const val SMALL_SEATS_PER_ROW = 6
private const val MEDIUM_SEATS_PER_ROW = 8
private const val SMALL_AIRCRAFT_CAP_THRESHOLD = 180
private const val MEDIUM_AIRCRAFT_CAP_THRESHOLD = 350

fun Route.seatSelectionRoutes() {

    /**
     * Renders the seat selection page for the current booking session.
     *
     * GET /flights/seats
     * Requires:
     * - [UserSession] (logged in)
     * - [BookingSession] with `outboundFlightId` present
     *
     * Optional query params:
     * - selected (String): previously selected seat code (for UI highlight)
     * - error (String): error message to show
     * - ok (String): success message to show
     */
    get("/flights/seats") {
        val session = call.sessions.get<UserSession>()
        if (session == null) {
            call.respondRedirect("/login")
            return@get
        }

        val bookingSession = call.sessions.get<BookingSession>()
        if (bookingSession == null) {
            call.respondRedirect("/home")
            return@get
        }

        val flightId = bookingSession.outboundFlightId
        if (flightId == null) {
            // user hasn't picked a flight yet
            call.respondRedirect("/flights/search")
            return@get
        }

        val flightAccess = FlightTableAccess()
        val airportAccess = AirportTableAccess()
        val seatAccess = SeatTableAccess()

        val flight = flightAccess.getByAttribute(FlightTable.id, flightId).firstOrNull()
        if (flight == null) {
            call.respondRedirect("/flights/search")
            return@get
        }

        val origin = airportAccess.getByAttribute(AirportTable.id, flight.originAirport).firstOrNull()
        val dest = airportAccess.getByAttribute(AirportTable.id, flight.destinationAirport).firstOrNull()

        val passengers = transaction {
            PassengerTable.select(PassengerTable.bookingId eq bookingSession.bookingId).map {
                mapOf(
                    "id" to it[PassengerTable.id],
                    "firstName" to it[PassengerTable.firstName],
                    "lastName" to it[PassengerTable.lastName],
                )
            }
        }

        val capacity = (flight.capacity ?: SMALL_AIRCRAFT_CAP_THRESHOLD).coerceAtLeast(1)
        val aircraftType = when {
            capacity <= SMALL_AIRCRAFT_CAP_THRESHOLD -> "Narrow-body"
            capacity <= MEDIUM_AIRCRAFT_CAP_THRESHOLD -> "Wide-body"
            else -> "Jumbo-jet"
        }

        // If seats exist in DB for this flight, use them (status available/occupied/etc).
        // If not, we default everything to available (since we're only drawing a map from capacity).
        val seatRowsFromDb = seatAccess.getByAttribute(SeatTable.flightId, flight.id)
        val seatStatusByCode = seatRowsFromDb.associate { it.seatCode to it.status }

        val layout = when {
            capacity <= SMALL_AIRCRAFT_CAP_THRESHOLD -> SeatLayout(
                seatsPerRow = SMALL_SEATS_PER_ROW,
                letters = listOf("A", "B", "C", "D", "E", "F"),
                aisleGapsAfterIndex = setOf(SMALL_AISLE_GAP_INDEX) // ABC | DEF
            )

            capacity <= MEDIUM_AIRCRAFT_CAP_THRESHOLD -> SeatLayout(
                seatsPerRow = MEDIUM_SEATS_PER_ROW,
                letters = listOf("A", "B", "C", "D", "E", "F", "G", "H"),
                aisleGapsAfterIndex = setOf(
                    MEDIUM_FIRST_AISLE_GAP_INDEX, 
                    MEDIUM_SECOND_AISLE_GAP_INDEX
                ) // AB | CDEF | GH
            )

            else -> SeatLayout(
                seatsPerRow = 10,
                letters = listOf("A", "B", "C", "D", "E", "F", "G", "H", "J", "K"),
                aisleGapsAfterIndex = setOf(
                    LARGE_FIRST_AISLE_GAP_INDEX, 
                    LARGE_SECOND_AISLE_GAP_INDEX
                ) // ABC | DEFG | HJK
            )

        }

        val totalRows = ceil(capacity / layout.seatsPerRow.toDouble()).toInt().coerceAtLeast(1)

        val seatRows: List<Map<String, Any>> = (1..totalRows).map { rowNum ->
            val seatsInRow = mutableListOf<Map<String, Any>>()

            layout.letters.forEachIndexed { idx, letter ->
                val code = "$rowNum$letter"
                val pos = layout.positionFor(idx)
                val status = seatStatusByCode[code] ?: "available"

                seatsInRow.add(
                    mapOf(
                        "code" to code,
                        "letter" to letter,
                        "position" to pos,
                        "status" to status,
                        "isAisleGap" to false
                    )
                )

                val after = idx + 1
                if (layout.aisleGapsAfterIndex.contains(after)) {
                    seatsInRow.add(mapOf("isAisleGap" to true))
                }
            }

            mapOf(
                "rowNumber" to rowNum,
                "seats" to seatsInRow
            )
        }

        val assignedSeats = transaction {
            SeatAssignmentTable.selectAll().map {
                it[SeatAssignmentTable.passengerId]
            }.toSet()
        }

        val unassignedPassenger = passengers.firstOrNull { passenger ->
            passenger["id"] as? Int !in assignedSeats
        }

        val currentPassengerName = unassignedPassenger?.let { 
            "${it["firstName"]} ${it["lastName"]}"
        } ?: "All assigned"

        val selectedSeatCode = call.request.queryParameters["selected"]?.trim().orEmpty()

        val model: Map<String, Any> = mapOf(
            "flow" to "booking", // used by the template if you want different back links
            "flightId" to flight.id,
            "flightNumber" to (flight.flightNumber?.toString() ?: flight.id.toString()),
            "originIata" to (origin?.iataCode ?: "—"),
            "originName" to (origin?.name ?: "—"),
            "destIata" to (dest?.iataCode ?: "—"),
            "destName" to (dest?.name ?: "—"),
            "aircraftType" to aircraftType,
            "currentSeatCode" to selectedSeatCode,
            "currentPassenger" to currentPassengerName,
            "seatRows" to seatRows,
            "passengers" to passengers,
            "error" to (call.request.queryParameters["error"] ?: ""),
            "ok" to (call.request.queryParameters["ok"] ?: "")
        )

        call.respond(PebbleContent("seat_selection.peb", model))
    }

    /**
     * Handles seat selection submit for the booking flow.
     *
     * POST /flights/seats
     * Form params:
     * - seatCode (String, required)
     *
     * Behaviour:
     * - Validates booking session exists and outbound flight exists.
     * - If seat rows exist in DB for this flight, enforces that the selected seat exists and is available.
     * - Redirects to the next step (payment) with `seatCode` carried as a query parameter.
     */
    post("/flights/seats") {
        val session = call.sessions.get<UserSession>()
        if (session == null) {
            call.respondRedirect("/login")
            return@post
        }

        val bookingSession = call.sessions.get<BookingSession>()
        if (bookingSession == null) {
            call.respondRedirect("/home")
            return@post
        }

        val flightId = bookingSession.outboundFlightId
        if (flightId == null) {
            call.respondRedirect("/flights/search")
            return@post
        }

        val params = call.receiveParameters()
        val seatCode = params["seatCode"]?.trim().orEmpty()
        if (seatCode.isBlank()) {
            call.respondRedirect("/flights/seats?error=Please select a seat")
            return@post
        }

        val passengers = transaction {
            PassengerTable.select(PassengerTable.bookingId eq bookingSession.bookingId).map {
                it[PassengerTable.id]
            }
        }

        val assignedPassengers = transaction {
            (SeatAssignmentTable innerJoin PassengerTable)
                .select { PassengerTable.bookingId eq bookingSession.bookingId }
                .map { it[SeatAssignmentTable.passengerId] }
                .toSet()
        }
        println(assignedPassengers)

        val unassignedPassenger = passengers.firstOrNull { it !in assignedPassengers }
        if (unassignedPassenger == null) {
            call.respondRedirect("/flights/seats?error=All passengers assigned")
            return@post
        }

        val seatAccess = SeatTableAccess()
        val seats = seatAccess.getByAttribute(SeatTable.flightId, flightId)

        // Find seat_id by code
        val seatId = seats.firstOrNull { it.seatCode == seatCode }?.id
        if (seatId == null) {
            call.respondRedirect("/flights/seats?error=Seat not found for this flight")
            return@post
        }

        val seatRow = seats.firstOrNull { it.seatCode == seatCode }
        if (seatRow?.status != "available") {
            call.respondRedirect("/flights/seats?error=Seat is already occupied")
            return@post
        }

        // create booking segment and get its id
        val bookingSegmentId = transaction {
            val condition =
                (BookingSegmentTable.bookingId eq bookingSession.bookingId) and
                (BookingSegmentTable.flightId eq flightId)

            val existing = BookingSegmentTable
                .select { condition }
                .firstOrNull()

            if (existing != null) {
                existing[BookingSegmentTable.id]
            } else {
                BookingSegmentTable.insert {
                    it[BookingSegmentTable.bookingId] = bookingSession.bookingId
                    it[BookingSegmentTable.flightId] = flightId
                    it[BookingSegmentTable.flightFareId] =
                        bookingSession.outboundFareId?.toInt() ?: 0
                }

                BookingSegmentTable
                    .selectAll()
                    .orderBy(BookingSegmentTable.id, SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()
                    ?.get(BookingSegmentTable.id) ?: 0
            }
        }


        // puts seat assignment in
        transaction {
            SeatAssignmentTable.insert {
                it[SeatAssignmentTable.passengerId] = unassignedPassenger
                it[SeatAssignmentTable.seatId] = seatId
                it[SeatAssignmentTable.bookingSegmentId] = bookingSegmentId
            }
        }

        println(assignedPassengers.size)
        println(passengers.size)

        if (assignedPassengers.size + 1 < passengers.size) {
            call.respondRedirect("/flights/seats?selected=$seatCode&ok=Seat assigned. Next passenger...")
        } else {
            call.respondRedirect("/payment")
        }
    }
}

private data class SeatLayout(
    val seatsPerRow: Int,
    val letters: List<String>,
    val aisleGapsAfterIndex: Set<Int>
) {
    fun positionFor(index: Int): String {
        return when {
            index == 0 -> "window"
            index == letters.size - 1 -> "window"
            else -> {
                val leftEdge = index
                val rightEdge = index + 1
                if (
                    aisleGapsAfterIndex.contains(leftEdge) || 
                    aisleGapsAfterIndex.contains(rightEdge)
                    ) "aisle" else "middle"
            }
        }
    }
}
