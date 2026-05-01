package com.flightbooking.routes

import com.flightbooking.models.BookingSession
import com.flightbooking.models.UserSession
import com.flightbooking.service.PointsService
import io.ktor.server.application.call
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

/**
 * Confirmation page routes
 *
 * Routes:
 * - GET  /confirmation -> renders page for the confirmation page
 * Checks UserSession and BookingSession
 */
fun Route.confirmationRoutes() {
    get("/confirmation") {
        val bookingSession = call.sessions.get<BookingSession>()
        val userSession = call.sessions.get<UserSession>()

        if (userSession == null) {
            call.respondRedirect("/login")
            return@get
        }

        if (bookingSession == null) {
            call.respondRedirect("/home")
            return@get
        }

        val totalPrice = bookingSession.totalPrice
        val pointsEarned = PointsService.calculatePointsEarned(totalPrice)
        println(totalPrice)
        println(pointsEarned)

        call.respond(
            PebbleContent(
                "confirmation.peb",
                mapOf(
                    "bookingSession" to bookingSession,
                    "pointsEarned" to pointsEarned,
                ),
            ),
        )
    }
}
