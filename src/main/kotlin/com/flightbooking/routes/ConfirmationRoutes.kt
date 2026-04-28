package com.flightbooking.routes

import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.pebble.*
import io.ktor.server.sessions.*

import com.flightbooking.models.UserSession
import com.flightbooking.models.BookingSession

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

        call.respond(
            PebbleContent(
                "confirmation.peb",
                mapOf(
                    "bookingSession" to bookingSession,
                )
            )
        )
    }
}