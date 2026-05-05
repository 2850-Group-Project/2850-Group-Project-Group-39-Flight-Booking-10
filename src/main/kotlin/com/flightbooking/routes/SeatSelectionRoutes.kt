package com.flightbooking.routes

import com.flightbooking.access.AirportTableAccess
import com.flightbooking.access.ComplaintResponseTableAccess
import com.flightbooking.access.FlightFareTableAccess
import com.flightbooking.access.FlightTableAccess
import com.flightbooking.access.SeatTableAccess
import com.flightbooking.models.BookingSession
import com.flightbooking.service.AuthService
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.FlightFareTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.PassengerTable
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
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

private const val PAYMENT_REDIRECT = "/payment?ok=Seats assigned successfully"
private const val SEARCH_REDIRECT = "/flights/search"

/**
 * Seat selection routes (booking flow).
 *
 * This version allows users to select seats for multiple passengers without page reloads:
 * - GET  /flights/seats ->
 *   renders `seat_selection.peb` for the **selected outbound flight** stored in [BookingSession].
 *   Seat map is generated from flight capacity (3 aircraft categories).
 *   If seat rows exist in DB for that flight, their status is used.
 *
 * - POST /flights/seats ->
 *   Accepts a JSON payload with all seat selections at once (selectedSeats: { passengerId: seatCode })
 *   Validates all seats are available, creates booking segment + seat assignments,
 *   then redirects to the next step (typically payment).
 */
fun Route.seatSelectionRoutes() {
    get("/flights/seats") {
        handleGetSeats(call, leg = "outbound")
    }
    post("/flights/seats") {
        handlePostSeats(call, leg = "outbound")
    }
    // Saves outbound seats then redirects to the return seat selection page
    post("/flights/seats/outbound") {
        handlePostSeats(call, leg = "outbound", nextStep = "/flights/seats/return")
    }
    get("/flights/seats/return") {
        handleGetSeats(call, leg = "return")
    }
    post("/flights/seats/return") {
        println("DEBUG /flights/seats/return was hit")
        handlePostSeats(call, leg = "return")
    }
}

/**
 * Resolves the flight and fare IDs for the given leg from the booking session.
 */
private fun resolveIds(
    bookingSession: BookingSession,
    leg: String,
): Pair<Int?, Int?> {
    val flightId = if (leg == "return") bookingSession.returnFlightId else bookingSession.outboundFlightId
    val fareId = if (leg == "return") bookingSession.returnFareId else bookingSession.outboundFareId
    return flightId to fareId
}

/**
 * Builds the Pebble model for the seat selection page.
 */
private fun buildSeatPageModel(params: SeatPageParams): Map<String, Any>? {
    val flight =
        FlightTableAccess()
            .getByAttribute(FlightTable.id, params.flightId)
            .firstOrNull() ?: return null

    val airportAccess = AirportTableAccess()
    val origin = airportAccess.getByAttribute(AirportTable.id, flight.originAirport).firstOrNull()
    val dest = airportAccess.getByAttribute(AirportTable.id, flight.destinationAirport).firstOrNull()

    val flightFare =
        params.fareId?.let {
            FlightFareTableAccess().getByAttribute(FlightFareTable.id, it).firstOrNull()
        }
    val farePrice = flightFare?.price ?: 0.0
    val fareCurrency = flightFare?.currency ?: "GBP"

    val passengers =
        transaction {
            PassengerTable
                .select { PassengerTable.bookingId eq params.bookingSession.bookingId }
                .map { row -> passengersMapper(row) }
        }

    val capacity = (flight.capacity ?: SMALL_AIRCRAFT_CAP_THRESHOLD).coerceAtLeast(1)
    val layout = getLayout(capacity)
    val seatStatusByCode =
        SeatTableAccess()
            .getByAttribute(SeatTable.flightId, flight.id)
            .associate { it.seatCode to it.status }
    val seatRows = buildSeatRows(capacity, layout, seatStatusByCode)
    val unreadCount =
        ComplaintResponseTableAccess()
            .getUnreadResponsesCountForUser(params.userId)

    val base =
        buildSeatsModel(
            SeatsModelParams(
                flight = flight,
                origin = origin,
                dest = dest,
                passengers = passengers,
                seatRows = seatRows,
                error = params.call.request.queryParameters["error"] ?: "",
                ok = params.call.request.queryParameters["ok"] ?: "",
                unreadCount = unreadCount,
                farePrice = farePrice,
                fareCurrency = fareCurrency,
                passengerCount = passengers.size,
            ),
        )

    return base.toMutableMap().apply {
        put("hasReturnFlight", params.bookingSession.returnFlightId != null)
        put("leg", params.leg)
    }
}

/**
 * Renders the seat selection page for the current booking session.
 *
 * GET /flights/seats
 * @param call request call
 */
private suspend fun handleGetSeats(
    call: ApplicationCall,
    leg: String = "outbound",
) {
    val (_, userId) = AuthService.requireUser(call) ?: return
    val bookingSession = AuthService.requireBooking(call) ?: return
    val (flightId, fareId) = resolveIds(bookingSession, leg)

    val model =
        flightId?.let {
            buildSeatPageModel(SeatPageParams(call, bookingSession, leg, it, fareId, userId))
        }

    if (model != null) {
        call.respond(PebbleContent("seat_selection.peb", model))
    } else {
        call.respondRedirect(SEARCH_REDIRECT)
    }
}

/**
 * Handles batch seat selection submit.
 *
 * POST /flights/seats
 * Form params:
 * - selectedSeats (JSON string): { passengerId: seatCode, ... }
 * @param call request call
 * @param leg leg
 * @param nextStep next link to go to
 */
private suspend fun handlePostSeats(
    call: ApplicationCall,
    leg: String = "outbound",
    nextStep: String? = null,
) {
    if (AuthService.requireUser(call) == null) return
    val bookingSession = AuthService.requireBooking(call) ?: return
    val (flightId, _) = resolveIds(bookingSession, leg)

    flightId?.also { id ->
        submitSeatSelection(call, bookingSession, id, redirectTo = nextStep ?: PAYMENT_REDIRECT)
    } ?: call.respondRedirect(SEARCH_REDIRECT)
}

/**
 * Validates submitted seat selections, creates a booking segment, and assigns the selected seats.
 * @param call request call
 * @param bookingSession active booking session
 * @param flightId selected outbound flight id
 */
private suspend fun submitSeatSelection(
    call: ApplicationCall,
    bookingSession: BookingSession,
    flightId: Int,
    redirectTo: String = PAYMENT_REDIRECT,
) {
    val selectedSeatsJson = call.receiveParameters()["selectedSeats"]?.trim().orEmpty()
    val selectedSeats = parseSelectedSeats(call, selectedSeatsJson) ?: return

    val seatAccess = SeatTableAccess()

    val seatRows = seatAccess.getByAttribute(SeatTable.flightId, flightId)
    val seatMap = seatRows.associateBy { it.seatCode }
    val validateSeatsError = validateSeats(selectedSeats, seatMap)

    if (validateSeatsError != null) {
        call.respondRedirect("/flights/seats?error=$validateSeatsError")
    }

    val bookingSegmentId = createBookingSegment(bookingSession, flightId)
    assignSeats(selectedSeats, seatMap, bookingSegmentId)
    call.respondRedirect(redirectTo)
}

/**
 * Mapper function for Exposed's result row to kotlin usable format
 * @param row result row
 * @return mapped passenger
 */
private fun passengersMapper(row: ResultRow) =
    mapOf(
        "id" to row[PassengerTable.id],
        "firstName" to row[PassengerTable.firstName],
        "lastName" to row[PassengerTable.lastName],
    )

/**
 * Parameters for building the seat page model.
 */
data class SeatPageParams(
    val call: ApplicationCall,
    val bookingSession: BookingSession,
    val leg: String,
    val flightId: Int,
    val fareId: Int?,
    val userId: Int,
)
