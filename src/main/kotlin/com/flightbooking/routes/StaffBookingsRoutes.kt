package com.flightbooking.routes

import com.flightbooking.models.StaffSession
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.BookingTable
import io.ktor.server.application.call
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * Staff bookings management routes.
 *
 * Routes:
 * - GET  /staff/bookings:
 *   - Requires [StaffSession]; redirects to `/staff/login` if missing.
 *   - Loads staff display info, flights list, seats grouped by flight, and booking records.
 *   - Supports optional query param `q` (booking id filter) for listing existing bookings.
 *   - Renders `staff_bookings.peb` with the model data.
 *
 * - POST /staff/bookings/create:
 *   - Requires [StaffSession]; redirects to `/staff/login` if missing.
 *   - Creates a booking for a passenger email + flight, optionally assigns a seat.
 *   - Redirects back to `/staff/bookings` (with `error` query param if validation fails).
 *
 * - POST /staff/bookings/update:
 *   - Requires [StaffSession]; redirects to `/staff/login` if missing.
 *   - Updates booking status and allows updating flight/seat for the booking segment.
 *   - Enforces that a seat (if selected) belongs to the selected flight.
 *   - Redirects back to `/staff/bookings`.
 */

fun Route.staffBookingsRoutes() {
    get("/staff/bookings") {
        val session = call.sessions.get<StaffSession>() ?: return@get call.respondRedirect("/staff/login")
        val q = call.request.queryParameters["q"]?.trim().orEmpty()
        val model = fetchStaffModel(session, q)
        if (model.containsKey("error")) return@get call.respondText(model["error"].toString())
        call.respond(PebbleContent("staff_bookings.peb", model))
    }

    post("/staff/bookings/update") {
        call.sessions.get<StaffSession>() ?: return@post call.respondRedirect("/staff/login")
        val params = call.receiveParameters()
        val bookingId = params["bookingId"]?.toIntOrNull() ?: return@post call.respondRedirect("/staff/bookings")
        val newStatus = params["bookingStatus"]?.trim()
        val newFlightId = params["flightId"]?.toIntOrNull()
        val newSeatId = params["seatId"]?.toIntOrNull()

        transaction {
            if (!newStatus.isNullOrBlank()) {
                BookingTable.update({ BookingTable.id eq bookingId }) { it[bookingStatus] = newStatus }
            }
            val segRow =
                BookingSegmentTable
                    .select { BookingSegmentTable.bookingId eq bookingId }
                    .limit(1)
                    .firstOrNull()
            val segId = segRow?.get(BookingSegmentTable.id)
            if (segId != null && newFlightId != null) updateBookingSegment(segId, segRow, newFlightId, newSeatId)
        }
        call.respondRedirect("/staff/bookings")
    }

    post("/staff/bookings/create") {
        call.sessions.get<StaffSession>() ?: return@post call.respondRedirect("/staff/login")
        val params = call.receiveParameters()
        val passengerEmail = params["passengerEmail"]?.trim().orEmpty()
        val passengerFirstName = params["passengerFirstName"]?.trim()
        val passengerLastName = params["passengerLastName"]?.trim()
        val flightId = params["flightId"]?.toIntOrNull()
        val bookingStatus = params["bookingStatus"]?.trim().orEmpty()
        val seatId = params["seatId"]?.toIntOrNull()

        if (passengerEmail.isBlank() || flightId == null || bookingStatus.isBlank()) {
            return@post call.respondRedirect("/staff/bookings")
        }

        val errMsg =
            createFullBooking(
                FullBookingInput(
                    passengerEmail = passengerEmail,
                    passengerFirstName = passengerFirstName,
                    passengerLastName = passengerLastName,
                    flightId = flightId,
                    bookingStatus = bookingStatus,
                    seatId = seatId,
                ),
            )
        if (errMsg != null) return@post call.respondRedirect("/staff/bookings?error=$errMsg")
        call.respondRedirect("/staff/bookings")
    }
}
