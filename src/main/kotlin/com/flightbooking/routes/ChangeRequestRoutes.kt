package com.flightbooking.routes

import com.flightbooking.access.*
import com.flightbooking.models.UserSession
import com.flightbooking.tables.*
import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * User change-request routes (amendment workflow).
 *
 * This flow does NOT directly modify bookings. Instead, it creates a row in `change_request`
 * that staff can review and update later.
 *
 * Routes:
 * - GET  /profile/bookings/change
 *   Renders the change-request page for a booking (requires UserSession).
 *   Supports searching flights by flight number via query param `flightQ`.
 * - POST /profile/bookings/change
 *   Creates a new change request in the database (requires UserSession).
 */
fun Route.changeRequestRoutes() {

    /**
     * Renders the change-request page for a booking.
     *
     * GET "/profile/bookings/change"
     * Query params:
     * - bookingId (required): booking id the user wants to request a change for
     * - flightQ (optional): flight number search input (string)
     * - selectedFlightId (optional): flight id selected from search results (int)
     *
     * Behaviour:
     * - Requires UserSession. If missing -> redirect /login
     * - Verifies the booking belongs to the logged-in user. If not -> redirect /404
     * - Uses existing access logic where available (UserTableAccess, BookingTableAccess, BookingSegmentTableAccess, SeatTableAccess, AirportTableAccess)
     * - Uses direct Exposed query in-route for flight number searching (since access does not provide it)
     * - Renders "change_request.peb"
     */
    get("/profile/bookings/change") {
        val session = call.sessions.get<UserSession>()
        if (session == null) {
            call.respondRedirect("/login")
            return@get
        }

        val bookingId = call.request.queryParameters["bookingId"]?.toIntOrNull()
        if (bookingId == null) {
            call.respondRedirect("/404")
            return@get
        }

        val flightQ = call.request.queryParameters["flightQ"]?.trim().orEmpty()
        val selectedFlightId = call.request.queryParameters["selectedFlightId"]?.toIntOrNull()

        val userAccess = UserTableAccess()
        val bookingAccess = BookingTableAccess()
        val segmentAccess = BookingSegmentTableAccess()
        val seatAccess = SeatTableAccess()
        val airportAccess = AirportTableAccess()

        val model = transaction {
            // Resolve user by session email (access)
            val user = userAccess.findByEmail(session.userEmail)
                ?: return@transaction mapOf<String, Any>("__notFound" to true)

            // Load booking (access) and verify ownership
            val booking = bookingAccess
                .getByAttribute(BookingTable.id, bookingId)
                .firstOrNull()
                ?: return@transaction mapOf<String, Any>("__notFound" to true)

            if (booking.userId != user.id) {
                return@transaction mapOf<String, Any>("__notFound" to true)
            }

            // Load booking segments (access) - most projects have at least one segment
            val segments = segmentAccess.getByAttribute(BookingSegmentTable.bookingId, bookingId)
            val segment = segments.firstOrNull()
                ?: return@transaction mapOf<String, Any>("__notFound" to true)

            // Current flight (direct Exposed, because access only supports simple getByAttribute but that's fine)
            val currentFlightRow = FlightTable.select { FlightTable.id eq segment.flightId }.limit(1).firstOrNull()

            val currentFlightId = currentFlightRow?.get(FlightTable.id)
            val currentFlightNumber = currentFlightRow?.get(FlightTable.flightNumber)?.toString().orEmpty()
            val currentDep = currentFlightRow?.get(FlightTable.scheduledDepartureTime).orEmpty()
            val currentArr = currentFlightRow?.get(FlightTable.scheduledArrivalTime).orEmpty()
            val currentFlightStatus = currentFlightRow?.get(FlightTable.status).orEmpty()

            // Current origin/destination airport details (access)
            val originAirport = currentFlightRow?.get(FlightTable.originAirport)?.let { oid ->
                airportAccess.getByAttribute(AirportTable.id, oid).firstOrNull()
            }
            val destAirport = currentFlightRow?.get(FlightTable.destinationAirport)?.let { did ->
                airportAccess.getByAttribute(AirportTable.id, did).firstOrNull()
            }

            // Current seat code (direct Exposed join-ish)
            val seatCode = (SeatAssignmentTable
                .join(SeatTable, JoinType.LEFT, additionalConstraint = { SeatTable.id eq SeatAssignmentTable.seatId })
                .slice(SeatTable.seatCode)
                .select { SeatAssignmentTable.bookingSegmentId eq segment.id }
                .limit(1)
                .firstOrNull()
                ?.getOrNull(SeatTable.seatCode)) ?: "No seat"

            // Flight search results by flight number (direct Exposed, because access does not have a search method)
            val flightResults: List<Map<String, Any>> = if (flightQ.isBlank()) {
                emptyList()
            } else {
                FlightTable
                    .select {
                        // flight_number is an Integer column; cast to text for LIKE
                        FlightTable.flightNumber.castTo<String>(VarCharColumnType()).like("%$flightQ%")
                    }
                    .orderBy(FlightTable.id, SortOrder.DESC)
                    .limit(30)
                    .map { r ->
                        mapOf(
                            "id" to r[FlightTable.id],
                            "flightNumber" to (r[FlightTable.flightNumber]?.toString() ?: ""),
                            "dep" to (r[FlightTable.scheduledDepartureTime] ?: ""),
                            "arr" to (r[FlightTable.scheduledArrivalTime] ?: ""),
                            "status" to r[FlightTable.status]
                        )
                    }
            }

            // Decide which flight's seats to show (selected flight or current flight)
            val targetFlightId = selectedFlightId ?: currentFlightId

            // Seats for selected flight (access getByAttribute + filter in Kotlin)
            val availableSeats: List<Map<String, Any>> = if (targetFlightId != null) {
                seatAccess
                    .getByAttribute(SeatTable.flightId, targetFlightId)
                    .filter { it.status == "available" }
                    .take(200)
                    .map { s ->
                        mapOf(
                            "id" to s.id,
                            "seatCode" to s.seatCode,
                            "cabinClass" to (s.cabinClass ?: "")
                        )
                    }
            } else emptyList()

            mapOf(
                "userSession" to session,
                "bookingId" to bookingId,
                "segmentId" to segment.id,

                "currentFlightId" to (currentFlightId ?: 0),
                "currentFlightNumber" to currentFlightNumber,
                "currentFlightStatus" to currentFlightStatus,
                "currentDep" to currentDep,
                "currentArr" to currentArr,

                "currentOrigin" to (originAirport?.iataCode ?: ""),
                "currentOriginName" to (originAirport?.name ?: ""),
                "currentDest" to (destAirport?.iataCode ?: ""),
                "currentDestName" to (destAirport?.name ?: ""),

                "currentSeatCode" to seatCode,

                "flightQ" to flightQ,
                "selectedFlightId" to (targetFlightId ?: 0),
                "flightResults" to flightResults,
                "availableSeats" to availableSeats,

                "error" to (call.request.queryParameters["error"] ?: ""),
                "ok" to (call.request.queryParameters["ok"] ?: "")
            )
        }

        if (model.containsKey("__notFound")) {
            call.respondRedirect("/404")
            return@get
        }

        call.respond(PebbleContent("change_request.peb", model))
    }

    /**
     * Submits a new change request (does not update the booking directly).
     *
     * POST "/profile/bookings/change"
     * Form params:
     * - bookingId (required)
     * - segmentId (required)
     * - requestedFlightId (required)
     * - requestedSeatId (optional)
     * - reason (optional)
     *
     * Behaviour:
     * - Requires UserSession. If missing -> redirect /login
     * - Verifies booking ownership (access where possible)
     * - Validates requested seat belongs to requested flight (direct Exposed)
     * - Inserts a new row into change_request (direct Exposed; no access logic added)
     * - Redirects back to /profile/bookings with a success message
     */
    post("/profile/bookings/change") {
        val session = call.sessions.get<UserSession>()
        if (session == null) {
            call.respondRedirect("/login")
            return@post
        }

        val p = call.receiveParameters()
        val bookingId = p["bookingId"]?.toIntOrNull()
        val segmentId = p["segmentId"]?.toIntOrNull()
        val requestedFlightId = p["requestedFlightId"]?.toIntOrNull()
        val requestedSeatId = p["requestedSeatId"]?.toIntOrNull()
        val reason = p["reason"]?.trim()

        if (bookingId == null || segmentId == null || requestedFlightId == null) {
            call.respondRedirect("/404")
            return@post
        }

        val userAccess = UserTableAccess()
        val bookingAccess = BookingTableAccess()
        val segmentAccess = BookingSegmentTableAccess()

        var err: String? = null

        transaction {
            // Resolve user (access)
            val user = userAccess.findByEmail(session.userEmail)
            if (user == null) {
                err = "User not found"
                return@transaction
            }

            // Verify booking ownership (access)
            val booking = bookingAccess.getByAttribute(BookingTable.id, bookingId).firstOrNull()
            if (booking == null || booking.userId != user.id) {
                err = "Booking not found"
                return@transaction
            }

            // Verify segment belongs to booking (access)
            val seg = segmentAccess.getByAttribute(BookingSegmentTable.id, segmentId).firstOrNull()
            if (seg == null || seg.bookingId != bookingId) {
                err = "Invalid booking segment"
                return@transaction
            }

            // Validate requested flight exists (direct Exposed)
            val flightExists = FlightTable.select { FlightTable.id eq requestedFlightId }.limit(1).any()
            if (!flightExists) {
                err = "Flight not found"
                return@transaction
            }

            // Validate seat (optional) belongs to requested flight and is available (direct Exposed)
            if (requestedSeatId != null) {
                val seatRow = SeatTable.select { SeatTable.id eq requestedSeatId }.limit(1).firstOrNull()
                if (seatRow == null) {
                    err = "Seat not found"
                    return@transaction
                }
                val seatFlightId = seatRow[SeatTable.flightId]
                val seatStatus = seatRow[SeatTable.status]
                if (seatFlightId != requestedFlightId) {
                    err = "Seat does not belong to selected flight"
                    return@transaction
                }
                if (seatStatus != "available") {
                    err = "Seat is not available"
                    return@transaction
                }
            }

            // Insert change request (direct Exposed; no access logic)
            val now = Instant.now().toString()
            ChangeRequestTable.insert {
                it[ChangeRequestTable.userId] = user.id
                it[ChangeRequestTable.bookingId] = bookingId
                it[ChangeRequestTable.bookingSegmentId] = segmentId
                it[ChangeRequestTable.currentFlightId] = seg.flightId
                it[ChangeRequestTable.requestedFlightId] = requestedFlightId
                it[ChangeRequestTable.requestedSeatId] = requestedSeatId
                it[ChangeRequestTable.reason] = reason
                it[ChangeRequestTable.status] = "pending"
                it[ChangeRequestTable.createdAt] = now
                it[ChangeRequestTable.updatedAt] = now
            }
        }

        if (err != null) {
            call.respondRedirect("/profile/bookings/change?bookingId=$bookingId&error=${err!!.replace(" ", "+")}")
            return@post
        }

        call.respondRedirect("/profile/bookings?ok=Change+request+submitted")
    }
}