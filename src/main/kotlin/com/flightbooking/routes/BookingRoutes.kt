package com.flightbooking.routes

import com.flightbooking.models.UserSession
import com.flightbooking.models.FlightSearch
import com.flightbooking.models.BookingSession
import com.flightbooking.models.PassengerInput
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.application.call
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.get
import io.ktor.server.sessions.set
import io.ktor.server.sessions.sessions
import io.ktor.http.HttpStatusCode

/**
 * Page routes for user-facing pages (home, profile, profile sub-pages, bookings) and a shared 404 page.
 * Pages that are not implemented yet redirect to `/404`.
 */
fun Route.bookingRoutes() {
    post("/flights/passengers/submit") {
        // need to add check to make sure that booking session exists 
        val userSession = call.sessions.get<UserSession>()
        val bookingSession = call.sessions.get<BookingSession>()

        if (userSession == null) {
            call.respondRedirect("/login")
            return@post
        }

        if (bookingSession == null) {
            call.respondRedirect("/home")
            return@post
        }

        val params = call.receiveParameters()

        val adultsCount = bookingSession.search?.adults?.toIntOrNull() ?: 0
        val childrenCount = bookingSession.search?.children?.toIntOrNull() ?: 0
        val infantsCount = bookingSession.search?.infants?.toIntOrNull() ?: 0

        val numberOfPassengers = adultsCount + childrenCount + infantsCount

        val passengers = (0 until numberOfPassengers).map { i ->
            PassengerInput(
                type = params["passengers[$i][type]"] ?: "adult",
                title = params["passengers[$i][title]"],
                firstName = params["passengers[$i][firstName]"] ?: "",
                lastName = params["passengers[$i][lastName]"] ?: "",
                dateOfBirth = params["passengers[$i][dateOfBirth]"],
                gender = params["passengers[$i][gender]"],
                email = params["passengers[$i][email]"],
                nationality = params["passengers[$i][nationality]"],
                documentType = params["passengers[$i][documentType]"],
                documentNumber = params["passengers[$i][documentNumber]"],
                documentCountry = params["passengers[$i][documentCountry]"],
                documentExpiry = params["passengers[$i][documentExpiry]"],
            )
        }

        println(passengers)

        println(bookingSession)

        call.sessions.set(bookingSession.copy(passengers = passengers))

        val newBookingSession = call.sessions.get<BookingSession>()
        println(newBookingSession)

        call.respondRedirect("/flights/seats")
    }
}
