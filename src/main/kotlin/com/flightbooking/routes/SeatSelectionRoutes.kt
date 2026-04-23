package com.flightbooking.routes

import com.flightbooking.access.AirportTableAccess
import com.flightbooking.access.FlightTableAccess
import com.flightbooking.access.SeatTableAccess
import com.flightbooking.models.BookingSession
import com.flightbooking.models.UserSession
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.SeatTable
import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlin.math.ceil

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

        val capacity = (flight.capacity ?: 180).coerceAtLeast(1)
        val aircraftType = when {
            capacity <= 180 -> "Narrow-body"
            capacity <= 350 -> "Wide-body"
            else -> "Jumbo-jet"
        }

        // If seats exist in DB for this flight, use them (status available/occupied/etc).
        // If not, we default everything to available (since we're only drawing a map from capacity).
        val seatRowsFromDb = seatAccess.getByAttribute(SeatTable.flightId, flight.id)
        val seatStatusByCode = seatRowsFromDb.associate { it.seatCode to it.status }

        val layout = when {
            capacity <= 180 -> SeatLayout(
                seatsPerRow = 6,
                letters = listOf("A", "B", "C", "D", "E", "F"),
                aisleGapsAfterIndex = setOf(3) // ABC | DEF
            )

            capacity <= 350 -> SeatLayout(
                seatsPerRow = 8,
                letters = listOf("A", "B", "C", "D", "E", "F", "G", "H"),
                aisleGapsAfterIndex = setOf(2, 6) // AB | CDEF | GH
            )

            else -> SeatLayout(
                seatsPerRow = 10,
                letters = listOf("A", "B", "C", "D", "E", "F", "G", "H", "J", "K"),
                aisleGapsAfterIndex = setOf(3, 7) // ABC | DEFG | HJK
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
            "seatRows" to seatRows,
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

        val seatAccess = SeatTableAccess()
        val seats = seatAccess.getByAttribute(SeatTable.flightId, flightId)

        // If we have DB seats, validate against DB availability.
        if (seats.isNotEmpty()) {
            val seatRow = seats.firstOrNull { it.seatCode == seatCode }
            if (seatRow == null) {
                call.respondRedirect("/flights/seats?error=Seat not found for this flight")
                return@post
            }
            if (seatRow.status != "available") {
                call.respondRedirect("/flights/seats?error=Seat is already occupied")
                return@post
            }
        }

        // At this stage we usually haven't created booking/segment yet, so we pass seatCode forward.
        // Next step route (payment) can read seatCode from query params and/or store it into session later.
        call.respondRedirect("/flights/payment?seatCode=$seatCode")
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
                if (aisleGapsAfterIndex.contains(leftEdge) || aisleGapsAfterIndex.contains(rightEdge)) "aisle" else "middle"
            }
        }
    }
}