package com.flightbooking.routes

import com.flightbooking.models.BookingSession
import com.flightbooking.models.PassengerInput
import com.flightbooking.service.AuthService
import com.flightbooking.tables.PassengerTable
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Routes for submitting passenger information
 *
 * Routes:
 * - POST /passengers/submit accepts passenger details and stores them in the database
 */
fun Route.bookingRoutes() {
    post("/flights/passengers/submit") { handlePostPassengersSubmit(call) }
}

/**
 * Handles post route for submitting passengers info
 * @param call application call
 */
private suspend fun handlePostPassengersSubmit(call: ApplicationCall) {
    val (_, _) = AuthService.requireUser(call)
    val bookingSession = AuthService.requireBooking(call)

    val params = call.receiveParameters()
    val numberOfPassengers = calculatePassengerCount(bookingSession)

    val passengers =
        (0 until numberOfPassengers).map { i ->
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
            bookingId = bookingId,
        ),
    )

    call.respondRedirect("/flights/seats")
}

/**
 * Calculates passenger count in booking session
 * @param bookingSession booking session
 * @return total passenger count
 */
private suspend fun calculatePassengerCount(bookingSession: BookingSession): Int {
    val adults = bookingSession.search?.adults?.toIntOrNull() ?: 0
    val children = bookingSession.search?.children?.toIntOrNull() ?: 0
    val infants = bookingSession.search?.infants?.toIntOrNull() ?: 0
    return adults + children + infants
}
