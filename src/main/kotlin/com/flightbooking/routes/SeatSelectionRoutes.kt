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
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.SeatAssignmentTable
import com.flightbooking.tables.PassengerTable
import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlin.math.ceil

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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

        val passengerAccess = PassengerTableAccess()
        val passengers = transaction {
            PassengerTable.select(PassengerTable.bookingId eq bookingSession.bookingId).map {
                mapOf(
                    "id" to it[PassengerTable.id],
                    "firstName" to it[PassengerTable.firstName],
                    "lastName" to it[PassengerTable.lastName],
                )
            }
        }

        val capacity = (flight.capacity ?: 180).coerceAtLeast(1)
        val aircraftType = when {
            capacity <= 180 -> "Narrow-body"
            capacity <= 350 -> "Wide-body"
            else -> "Jumbo-jet"
        }

        val seatRowsFromDb = seatAccess.getByAttribute(SeatTable.flightId, flight.id)
        val seatStatusByCode = seatRowsFromDb.associate { it.seatCode to it.status }

        val layout = when {
            capacity <= 180 -> SeatLayout(
                seatsPerRow = 6,
                letters = listOf("A", "B", "C", "D", "E", "F"),
                aisleGapsAfterIndex = setOf(3)
            )

            capacity <= 350 -> SeatLayout(
                seatsPerRow = 8,
                letters = listOf("A", "B", "C", "D", "E", "F", "G", "H"),
                aisleGapsAfterIndex = setOf(2, 6)
            )

            else -> SeatLayout(
                seatsPerRow = 10,
                letters = listOf("A", "B", "C", "D", "E", "F", "G", "H", "J", "K"),
                aisleGapsAfterIndex = setOf(3, 7)
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
        } catch (e: Exception) {
            call.respondRedirect("/flights/seats?error=Invalid seat selection format")
            return@post
        }

        val seatAccess = SeatTableAccess()
        val seatRows = seatAccess.getByAttribute(SeatTable.flightId, flightId)
        val seatMap = seatRows.associateBy { it.seatCode }

        // Validate all seats exist and are available
        for ((passengerId, seatCode) in selectedSeats) {
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
            val existing = BookingSegmentTable.select { 
                (BookingSegmentTable.bookingId eq bookingSession.bookingId) and 
                (BookingSegmentTable.flightId eq flightId)
            }.firstOrNull()
            
            if (existing != null) {
                existing[BookingSegmentTable.id]
            } else {
                BookingSegmentTable.insert {
                    it[BookingSegmentTable.bookingId] = bookingSession.bookingId
                    it[BookingSegmentTable.flightId] = flightId
                    it[BookingSegmentTable.flightFareId] = bookingSession.outboundFareId?.toInt() ?: 0
                }

                BookingSegmentTable.selectAll().orderBy(BookingSegmentTable.id to SortOrder.DESC).limit(1).firstOrNull()?.get(BookingSegmentTable.id) ?: 0
            }
        }

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
                if (aisleGapsAfterIndex.contains(leftEdge) || aisleGapsAfterIndex.contains(rightEdge)) "aisle" else "middle"
            }
        }
    }
}