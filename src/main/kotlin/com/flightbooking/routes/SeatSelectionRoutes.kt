package com.flightbooking.routes

import com.flightbooking.access.AirportTableAccess
import com.flightbooking.access.ComplaintResponseTableAccess
import com.flightbooking.access.FlightTableAccess
import com.flightbooking.access.SeatTableAccess
import com.flightbooking.access.FlightFareTableAccess
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
 * Renders the seat selection page for the current booking session.
 *
 * GET /flights/seats
 * @param call request call
 */
private suspend fun handleGetSeats(call: ApplicationCall, leg: String = "outbound") {
    val (_, userId) = AuthService.requireUser(call) ?: return
    val bookingSession = AuthService.requireBooking(call) ?: return

    val flightId = if (leg == "return") bookingSession.returnFlightId else bookingSession.outboundFlightId
    val fareId = if (leg == "return") bookingSession.returnFareId else bookingSession.outboundFareId

    if (flightId == null) {
        call.respondRedirect("/flights/search")
        return
    }

    val flightAccess = FlightTableAccess()
    val airportAccess = AirportTableAccess()
    val flight = flightAccess.getByAttribute(FlightTable.id, flightId).firstOrNull()
        ?: run { call.respondRedirect("/flights/search"); return }

    val origin = airportAccess.getByAttribute(AirportTable.id, flight.originAirport).firstOrNull()
    val dest = airportAccess.getByAttribute(AirportTable.id, flight.destinationAirport).firstOrNull()

    val flightFare = fareId?.let {
        FlightFareTableAccess().getByAttribute(FlightFareTable.id, it).firstOrNull()
    }
    val farePrice = flightFare?.price ?: 0.0
    val fareCurrency = flightFare?.currency ?: "GBP"

    val passengers = transaction {
        PassengerTable
            .select { PassengerTable.bookingId eq bookingSession.bookingId }
            .map { row -> passengersMapper(row) }
    }
    
    val capacity = (flight.capacity ?: SMALL_AIRCRAFT_CAP_THRESHOLD).coerceAtLeast(1)
    val layout = getLayout(capacity)
    val seatStatusByCode = SeatTableAccess().getByAttribute(SeatTable.flightId, flight.id)
        .associate { it.seatCode to it.status }
    val seatRows = buildSeatRows(capacity, layout, seatStatusByCode)
    val unreadCount = ComplaintResponseTableAccess().getUnreadResponsesCountForUser(userId)

    val baseModel = buildSeatsModel(
        SeatsModelParams(
            flight = flight,
            origin = origin,
            dest = dest,
            passengers = passengers,
            seatRows = seatRows,
            error = call.request.queryParameters["error"] ?: "",
            ok = call.request.queryParameters["ok"] ?: "",
            unreadCount = unreadCount,
            farePrice = farePrice,
            fareCurrency = fareCurrency,
            passengerCount = passengers.size,
        )
    )
    val model = baseModel.toMutableMap().apply {
        put("hasReturnFlight", bookingSession.returnFlightId != null)
        put("leg", leg)
    }
    
    call.respond(PebbleContent("seat_selection.peb", model))
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

    println("DEBUG leg=$leg outbound=${bookingSession.outboundFlightId} return=${bookingSession.returnFlightId}")

    val flightId = if (leg == "return") bookingSession.returnFlightId else bookingSession.outboundFlightId
    if (flightId == null) {
        call.respondRedirect("/flights/search")
        return
    }
    // If next step is provided go to it, otherwise go to payment
    submitSeatSelection(call, bookingSession, flightId, redirectTo = nextStep ?: "/payment?ok=Seats assigned successfully")
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
    redirectTo: String = "/payment?ok=Seats assigned successfully",
) {
    val selectedSeatsJson = call.receiveParameters()["selectedSeats"]?.trim().orEmpty()
    val selectedSeats = parseSelectedSeats(call, selectedSeatsJson) ?: return


    val seatAccess = SeatTableAccess()
    println("DEBUG submitSeatSelection flightId=$flightId seatMapSize=${seatAccess.getByAttribute(SeatTable.flightId, flightId).size} selectedSeats=$selectedSeats")

    val seatRows = seatAccess.getByAttribute(SeatTable.flightId, flightId)
    val seatMap = seatRows.associateBy { it.seatCode }
    println("DEBUG flightId=$flightId seatMapKeys=${seatMap.keys.take(5)} selectedSeats=$selectedSeats")
    val validateSeatsError = validateSeats(selectedSeats, seatMap)

    if (validateSeatsError != null) {
        call.respondRedirect("/flights/seats?error=$validateSeatsError")
    } else {
        val bookingSegmentId = createBookingSegment(bookingSession, flightId)
        assignSeats(selectedSeats, seatMap, bookingSegmentId)
        call.respondRedirect(redirectTo)
    }
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
