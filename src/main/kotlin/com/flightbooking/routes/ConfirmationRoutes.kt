package com.flightbooking.routes

import com.flightbooking.access.ComplaintResponseTableAccess
import com.flightbooking.service.AuthService
import com.flightbooking.service.PointsService
import com.flightbooking.service.calculateEarning
import com.flightbooking.models.BookingSession
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.application.call
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.get

/**
 * Confirmation page routes
 *
 * Routes:
 * - GET  /confirmation -> renders page for the confirmation page
 * Checks UserSession and BookingSession
 */
fun Route.confirmationRoutes() {
    get("/confirmation") {
        val (_, userId) = AuthService.requireUser(call) ?: return@get
        val bookingSession = AuthService.requireBooking(call) ?: return@get

        val totalPrice = bookingSession.totalPrice

        val membershipStatus = PointsService.getUserPointsRow(userId)?.membershipStatus ?: "Bronze"
        val pointsEarned =
            calculateEarning(
                amountPaid = totalPrice,
                fareEarnRate = PointsService.fetchMilesEarnRate(bookingSession.outboundFareId),
                membershipStatus = membershipStatus,
            )

        val unreadCount = ComplaintResponseTableAccess().getUnreadResponsesCountForUser(userId)

        call.respond(
            PebbleContent(
                "confirmation.peb",
                mapOf(
                    "bookingSession" to bookingSession,
                    "pointsEarned" to pointsEarned,
                    "unreadCount" to unreadCount,
                ),
            ),
        )

        call.sessions.set("BOOKING_SESSION", BookingSession())
    }
}
