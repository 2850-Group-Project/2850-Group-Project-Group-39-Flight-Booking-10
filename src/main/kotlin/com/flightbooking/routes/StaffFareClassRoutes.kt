package com.flightbooking.routes

import com.flightbooking.access.AirportTableAccess
import com.flightbooking.access.ComplaintResponseTableAccess
import com.flightbooking.access.FlightFareTableAccess
import com.flightbooking.access.FlightTableAccess
import com.flightbooking.access.SeatTableAccess
import com.flightbooking.models.BookingSession
import com.flightbooking.models.Seat
import com.flightbooking.service.AuthService
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.FareClassTable
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
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
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
fun Route.staffFareClassRoutes() {
    get("/staff/fare-class") {
        handleGetStaffFareClass(call)
    }
    post("/staff/fare-class/create") {
        handlePostFareClassCreate(call)
    }
    post("/staff/fare-class/update") {
        handlePostFareClassUpdate(call)
    }
    post("/staff/fare-class/delete") {
        handlePostFareClassDelete(call)
    }
}

private suspend fun handleGetStaffFareClass(call: ApplicationCall) {
    val session = call.sessions.get<StaffSession>()
    if (session == null) {
        call.respondRedirect("/staff/login")
        return
    }

    
}

private suspend fun handlePostFareClassCreate(call: ApplicationCall) {
    val session = call.sessions.get<StaffSession>()
    if (session == null) {
        call.respondRedirect("/staff/login")
        return
    }


}

private suspend fun handlePostFareClassUpdate(call: ApplicationCall) {
    val session = call.sessions.get<StaffSession>()
    if (session == null) {
        call.respondRedirect("/staff/login")
        return
    }


}

private suspend fun handlePostFareClassDelete(call: ApplicationCall) {
    val session = call.sessions.get<StaffSession>()
    if (session == null) {
        call.respondRedirect("/staff/login")
        return
    }


}