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
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import com.flightbooking.tables.PassengerTable

fun Route.bookingRoutes() {
    post("/flights/passengers/submit") {
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

        val bookingId = bookingSession.bookingId

        transaction {
            passengers.forEach { p ->
                PassengerTable.insert {
                    it[PassengerTable.bookingId] = bookingId
                    it[PassengerTable.email] = p.email
                    it[PassengerTable.checkedIn] = 0
                    it[PassengerTable.title] = p.title
                    it[PassengerTable.firstName] = p.firstName
                    it[PassengerTable.lastName] = p.lastName
                    it[PassengerTable.dateOfBirth] = p.dateOfBirth
                    it[PassengerTable.gender] = p.gender
                    it[PassengerTable.nationality] = p.nationality
                    it[PassengerTable.documentType] = p.documentType
                    it[PassengerTable.documentNumber] = p.documentNumber
                    it[PassengerTable.documentCountry] = p.documentCountry
                    it[PassengerTable.documentExpiry] = p.documentExpiry
                }
            }
        }

        call.sessions.set(
            bookingSession.copy(
                bookingId = bookingId
            )
        )

        call.respondRedirect("/flights/seats")
    }
}
