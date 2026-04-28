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
import io.ktor.server.application.log
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
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.and
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.JsonSyntaxException

import com.flightbooking.tables.PassengerTable
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.SeatAssignmentTable
/**
 * Seat selection routes (booking flow).
 *
 * This version allows users to select seats for multiple passengers without page reloads:
 * - GET  /flights/seats
 *   Renders `seat_selection.peb` for the **selected outbound flight** stored in [BookingSession].
 *   Seat map is generated from flight capacity (3 aircraft categories).
 *   If seat rows exist in DB for that flight, their status is used.
 *
 * - POST /flights/seats
 *   Accepts a JSON payload with all seat selections at once (selectedSeats: { passengerId: seatCode })
 *   Validates all seats are available, creates booking segment + seat assignments,
 *   then redirects to the next step (typically payment).
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

        val currentPassengerName = passengers.firstOrNull()?.let {
            "${it["firstName"]} ${it["lastName"]}"
        } ?: "Select passenger"

        val model: Map<String, Any> = mapOf(
            "flightId" to flight.id,
            "flightNumber" to (flight.flightNumber?.toString() ?: flight.id.toString()),
            "originIata" to (origin?.iataCode ?: "—"),
            "originName" to (origin?.name ?: "—"),
            "destIata" to (dest?.iataCode ?: "—"),
            "destName" to (dest?.name ?: "—"),
            "aircraftType" to aircraftType,
            "currentSeatCode" to "",
            "currentPassenger" to currentPassengerName,
            "seatRows" to seatRows,
            "passengers" to passengers,
            "error" to (call.request.queryParameters["error"] ?: ""),
            "ok" to (call.request.queryParameters["ok"] ?: "")
        )

        call.respond(PebbleContent("seat_selection.peb", model))
    }

    /**
     * Handles batch seat selection submit.
     *
     * POST /flights/seats
     * Form params:
     * - selectedSeats (JSON string): { passengerId: seatCode, ... }
     *
     * Behaviour:
     * - Validates all seats are available
     * - Creates booking segment (if not exists)
     * - Creates seat assignments for all passengers
     * - Updates seat table status to "occupied"
     * - Redirects to payment
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
        val selectedSeatsJson = params["selectedSeats"]?.trim().orEmpty()
        
        if (selectedSeatsJson.isBlank()) {
            call.respondRedirect("/flights/seats?error=No seats selected")
            return@post
        }

        // parsing the JSON: { "1": "3A", "2": "3B", ... }
        val gson = Gson()
        val selectedSeats: Map<String, String> = try {
            gson.fromJson(selectedSeatsJson, object : TypeToken<Map<String, String>>() {}.type)
        } catch (e: JsonSyntaxException) {
            call.application.log.error("Failed to parse seat selection JSON: ${e.message}", e)
            call.respondRedirect("/flights/seats?error=Invalid seat selection format")
            return@post
        }

        val seatAccess = SeatTableAccess()
        val seatRows = seatAccess.getByAttribute(SeatTable.flightId, flightId)
        val seatMap = seatRows.associateBy { it.seatCode }

        // Validate all seats exist and are available
        for ((_, seatCode) in selectedSeats) {
            val seatRow = seatMap[seatCode]
            if (seatRow == null) {
                call.respondRedirect("/flights/seats?error=Seat $seatCode not found")
                return@post
            }
            if (seatRow.status != "available") {
                call.respondRedirect("/flights/seats?error=Seat $seatCode is already occupied")
                return@post
            }
        }

        // Create booking segment (if not exists)
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
        // seat assigment and seat table
        transaction {
            for ((passengerId, seatCode) in selectedSeats) {
                val seatId = seatMap[seatCode]?.id ?: continue

                // create new seat assignment
                SeatAssignmentTable.insert {
                    it[SeatAssignmentTable.passengerId] = passengerId.toInt()
                    it[SeatAssignmentTable.seatId] = seatId
                    it[SeatAssignmentTable.bookingSegmentId] = bookingSegmentId
                }

                // update seat status
                SeatTable.update({ SeatTable.id eq seatId }) {
                    it[SeatTable.status] = "occupied"
                }
            }
        }

        call.respondRedirect("/payment?ok=Seats assigned successfully")
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
