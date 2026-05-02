package com.flightbooking.routes

import com.flightbooking.access.AirportTableAccess
import com.flightbooking.access.BookingSegmentTableAccess
import com.flightbooking.access.BookingTableAccess
import com.flightbooking.access.SeatTableAccess
import com.flightbooking.access.UserTableAccess
import com.flightbooking.models.Booking
import com.flightbooking.models.BookingSegment
import com.flightbooking.models.User
import com.flightbooking.models.UserSession
import com.flightbooking.service.AuthService
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.ChangeRequestTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.SeatAssignmentTable
import com.flightbooking.tables.SeatTable
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.castTo
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

private const val MAX_SEAT_RESULTS = 200
private const val MAX_FLIGHT_SEARCH_RESULTS = 30

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
    get("/profile/bookings/change") {
        handleGetBookingsChange(call)
    }

    post("/profile/bookings/change") {
        handlePostBookingsChange(call)
    }
}

/**
 * Handles get route for the change bookings page
 * @param call request call
 */
private suspend fun handleGetBookingsChange(call: ApplicationCall) {
    val (userSession, _) = AuthService.requireUser(call)

    val bookingId = call.request.queryParameters["bookingId"]?.toIntOrNull()
    if (bookingId == null) {
        call.respondRedirect("/404")
        return
    }

    val flightQ = call.request.queryParameters["flightQ"]?.trim().orEmpty()
    val selectedFlightId = call.request.queryParameters["selectedFlightId"]?.toIntOrNull()
    val model =
        getModel(
            BookingChangeModelParams(
                session = userSession,
                bookingId = bookingId,
                flightQ = flightQ,
                selectedFlightId = selectedFlightId,
                error = call.request.queryParameters["error"] ?: "",
                ok = call.request.queryParameters["ok"] ?: "",
            ),
        )

    if (model == null) {
        call.respondRedirect("/404")
        return
    }

    call.respond(PebbleContent("change_request.peb", model))
}

/**
 * Resolves the user, booking, and booking segment associated with the given session and booking ID
 * @param session user session
 * @param bookingId booking id
 * @return user, booking, segment triple or null
 */
private fun resolveBookingContext(
    session: UserSession,
    bookingId: Int,
): Triple<User, Booking, BookingSegment>? {
    val user = UserTableAccess().findByEmail(session.userEmail) ?: return null
    return BookingTableAccess()
        .getByAttribute(BookingTable.id, bookingId)
        .firstOrNull()
        ?.takeIf { it.userId == user.id }
        ?.let { booking ->
            BookingSegmentTableAccess()
                .getByAttribute(BookingSegmentTable.bookingId, bookingId)
                .firstOrNull()
                ?.let { segment -> Triple(user, booking, segment) }
        }
}

/**
 * Fetches info for current flight, using the booking segment as input
 * @param segment booking segment
 * @return current flight info
 */
private fun fetchCurrentFlightInfo(segment: BookingSegment): CurrentFlightInfo {
    val flightRow =
        FlightTable
            .select { FlightTable.id eq segment.flightId }
            .limit(1)
            .firstOrNull()

    val originAirport =
        flightRow?.get(FlightTable.originAirport)?.let { oid ->
            AirportTableAccess().getByAttribute(AirportTable.id, oid).firstOrNull()
        }
    val destAirport =
        flightRow?.get(FlightTable.destinationAirport)?.let { did ->
            AirportTableAccess().getByAttribute(AirportTable.id, did).firstOrNull()
        }

    val seatCode =
        SeatAssignmentTable
            .join(SeatTable, JoinType.LEFT, additionalConstraint = { SeatTable.id eq SeatAssignmentTable.seatId })
            .slice(SeatTable.seatCode)
            .select { SeatAssignmentTable.bookingSegmentId eq segment.id }
            .limit(1)
            .firstOrNull()
            ?.getOrNull(SeatTable.seatCode) ?: "No seat"

    return CurrentFlightInfo(
        flightId = flightRow?.get(FlightTable.id),
        flightNumber = flightRow?.get(FlightTable.flightNumber)?.toString().orEmpty(),
        dep = flightRow?.get(FlightTable.scheduledDepartureTime).orEmpty(),
        arr = flightRow?.get(FlightTable.scheduledArrivalTime).orEmpty(),
        status = flightRow?.get(FlightTable.status).orEmpty(),
        originIata = originAirport?.iataCode ?: "",
        originName = originAirport?.name ?: "",
        destIata = destAirport?.iataCode ?: "",
        destName = destAirport?.name ?: "",
        seatCode = seatCode,
        segmentId = segment.id,
    )
}

/**
 * Searches flight table for flight with flightQ flight number
 * @param flightQ search text
 * @return flight results
 */
private fun searchFlights(flightQ: String): List<Map<String, Any>> {
    if (flightQ.isBlank()) return emptyList()
    return FlightTable
        .select { FlightTable.flightNumber.castTo<String>(VarCharColumnType()).like("%$flightQ%") }
        .orderBy(FlightTable.id, SortOrder.DESC)
        .limit(MAX_FLIGHT_SEARCH_RESULTS)
        .map { r ->
            mapOf(
                "id" to r[FlightTable.id],
                "flightNumber" to (r[FlightTable.flightNumber]?.toString() ?: ""),
                "dep" to (r[FlightTable.scheduledDepartureTime] ?: ""),
                "arr" to (r[FlightTable.scheduledArrivalTime] ?: ""),
                "status" to r[FlightTable.status],
            )
        }
}

/**
 * Searches and returns a list of seats that are available for inputted targetFlightId
 * @param targetFlightId flight id
 * @return available seats
 */
private fun fetchAvailableSeats(targetFlightId: Int?): List<Map<String, Any>> {
    if (targetFlightId == null) return emptyList()
    return SeatTableAccess()
        .getByAttribute(SeatTable.flightId, targetFlightId)
        .filter { it.status == "available" }
        .take(MAX_SEAT_RESULTS)
        .map { s ->
            mapOf(
                "id" to s.id,
                "seatCode" to s.seatCode,
                "cabinClass" to (s.cabinClass ?: ""),
            )
        }
}

/**
 * Builds the view model for the booking‑change page
 * @param params model params
 * @return model or null
 */
private fun getModel(params: BookingChangeModelParams): Map<String, Any>? =
    transaction {
        val (_, _, segment) =
            resolveBookingContext(params.session, params.bookingId)
                ?: return@transaction null

        val flightInfo = fetchCurrentFlightInfo(segment)
        val targetFlightId = params.selectedFlightId ?: flightInfo.flightId

        mapOf(
            "userSession" to params.session,
            "bookingId" to params.bookingId,
            "segmentId" to flightInfo.segmentId,
            "currentFlightId" to (flightInfo.flightId ?: 0),
            "currentFlightNumber" to flightInfo.flightNumber,
            "currentFlightStatus" to flightInfo.status,
            "currentDep" to flightInfo.dep,
            "currentArr" to flightInfo.arr,
            "currentOrigin" to flightInfo.originIata,
            "currentOriginName" to flightInfo.originName,
            "currentDest" to flightInfo.destIata,
            "currentDestName" to flightInfo.destName,
            "currentSeatCode" to flightInfo.seatCode,
            "flightQ" to params.flightQ,
            "selectedFlightId" to (targetFlightId ?: 0),
            "flightResults" to searchFlights(params.flightQ),
            "availableSeats" to fetchAvailableSeats(targetFlightId),
            "error" to params.error,
            "ok" to params.ok,
        )
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
 * @param call request call
 */
private suspend fun handlePostBookingsChange(call: ApplicationCall) {
    val (userSession, _) = AuthService.requireUser(call)

    val p = call.receiveParameters()
    val bookingId = p["bookingId"]?.toIntOrNull()
    val segmentId = p["segmentId"]?.toIntOrNull()
    val requestedFlightId = p["requestedFlightId"]?.toIntOrNull()
    val requestedSeatId = p["requestedSeatId"]?.toIntOrNull()
    val reason = p["reason"]?.trim()

    if (bookingId == null || segmentId == null || requestedFlightId == null) {
        call.respondRedirect("/404")
        return
    }

    var err =
        submitBookingChange(
            userSession,
            BookingChangeParams(
                bookingId,
                segmentId,
                requestedFlightId,
                requestedSeatId,
                reason,
            ),
        )

    if (err != null) {
        call.respondRedirect("/profile/bookings/change?bookingId=$bookingId&error=${err.replace(" ", "+")}")
        return
    }

    call.respondRedirect("/profile/bookings?ok=Change+request+submitted")
}

/**
 * Verifies and validates data, then inserts a ChangeRequest into the DB
 * @param session user session
 * @param params change params
 * @return error message or null
 */
private suspend fun submitBookingChange(
    session: UserSession,
    params: BookingChangeParams,
): String? {
    var err: String? = null
    val userAccess = UserTableAccess()
    val bookingAccess = BookingTableAccess()
    val segmentAccess = BookingSegmentTableAccess()
    transaction {
        // resolve user
        val user = userAccess.findByEmail(session.userEmail)
        if (user == null) {
            err = "User not found"
            return@transaction
        }

        // verify booking ownership
        val booking = bookingAccess.getByAttribute(BookingTable.id, params.bookingId).firstOrNull()
        if (booking == null || booking.userId != user.id) {
            err = "Booking not found"
            return@transaction
        }

        // verify segment belongs to booking
        val seg = segmentAccess.getByAttribute(BookingSegmentTable.id, params.segmentId).firstOrNull()
        if (seg == null || seg.bookingId != params.bookingId) {
            err = "Invalid booking segment"
            return@transaction
        }

        // validate requested flight exists
        val flightExists = FlightTable.select { FlightTable.id eq params.requestedFlightId }.limit(1).any()
        if (!flightExists) {
            err = "Flight not found"
            return@transaction
        }

        // validate seat belongs to requested flight and is available
        if (params.requestedSeatId != null) {
            val seatRow = SeatTable.select { SeatTable.id eq params.requestedSeatId }.limit(1).firstOrNull()
            if (seatRow == null) {
                err = "Seat not found"
                return@transaction
            }
            val seatFlightId = seatRow[SeatTable.flightId]
            val seatStatus = seatRow[SeatTable.status]
            if (seatFlightId != params.requestedFlightId) {
                err = "Seat does not belong to selected flight"
                return@transaction
            }
            if (seatStatus != "available") {
                err = "Seat is not available"
                return@transaction
            }
        }

        ChangeRequestTable.insert {
            it[ChangeRequestTable.userId] = user.id
            it[ChangeRequestTable.bookingId] = params.bookingId
            it[ChangeRequestTable.bookingSegmentId] = params.segmentId
            it[ChangeRequestTable.currentFlightId] = seg.flightId
            it[ChangeRequestTable.requestedFlightId] = params.requestedFlightId
            it[ChangeRequestTable.requestedSeatId] = params.requestedSeatId
            it[ChangeRequestTable.reason] = params.reason
            it[ChangeRequestTable.status] = "pending"
            it[ChangeRequestTable.createdAt] = Instant.now().toString()
            it[ChangeRequestTable.updatedAt] = Instant.now().toString()
        }
    }
    return err
}

/**
 * Class for passing parameters into the function BookingChangeModel
 */
private data class BookingChangeModelParams(
    val session: UserSession,
    val bookingId: Int,
    val flightQ: String,
    val selectedFlightId: Int?,
    val error: String,
    val ok: String,
)

/**
 * Class for passing parameters out of the function fetchCurrentFlightInfo
 */
private data class CurrentFlightInfo(
    val flightId: Int?,
    val flightNumber: String,
    val dep: String,
    val arr: String,
    val status: String,
    val originIata: String,
    val originName: String,
    val destIata: String,
    val destName: String,
    val seatCode: String,
    val segmentId: Int,
)

/**
 * Class for parameters passed into submitBookingChange
 */
private data class BookingChangeParams(
    val bookingId: Int,
    val segmentId: Int,
    val requestedFlightId: Int,
    val requestedSeatId: Int?,
    val reason: String?,
)
