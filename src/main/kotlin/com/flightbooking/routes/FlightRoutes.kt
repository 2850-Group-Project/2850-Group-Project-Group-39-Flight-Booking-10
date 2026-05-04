package com.flightbooking.routes

import com.flightbooking.access.AirportTableAccess
import com.flightbooking.models.Airport
import com.flightbooking.models.BookingSession
import com.flightbooking.models.FlightSearch
import com.flightbooking.service.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.call
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import java.util.UUID

/**
 * Flight selection page routes
 *
 * Routes:
 * - POST /flights/select -> accepts and validates data for flight selection
 */
fun Route.flightSelectRoutes() {
    post("/flights/select") {
        AuthService.requireUser(call) ?: return@post

        val params = call.receiveParameters()
        val flightId =
            params["flightId"]?.toIntOrNull() ?: return@post call.respond(
                HttpStatusCode.BadRequest, "Missing flightId",
            )
        val fareId =
            params["fareId"]?.toIntOrNull() ?: return@post call.respond(
                HttpStatusCode.BadRequest, "Missing fareId",
            )
        val leg =
            params["leg"]?.toString() ?: return@post call.respond(
                HttpStatusCode.BadRequest, "Missing leg",
            )

        val search = buildFlightSearch(params)

        val booking = call.sessions.get<BookingSession>() ?: BookingSession()
        val bookingId = (UUID.randomUUID().mostSignificantBits % Int.MAX_VALUE).toInt()

        val updated =
            when (leg) {
                "outbound" ->
                    booking.copy(
                        bookingId = bookingId,
                        outboundFlightId = flightId,
                        outboundFareId = fareId,
                        search = search,
                    )
                "return" ->
                    booking.copy(
                        bookingId = bookingId,
                        returnFlightId = flightId,
                        returnFareId = fareId,
                        search = search,
                    )
                else -> {
                    call.respond(HttpStatusCode.BadRequest, "Invalid leg")
                    return@post
                }
            }

        call.sessions.set(updated)
        call.respond(HttpStatusCode.OK, "ok")
    }
}

/**
 * Routes for airport search
 * Routes:
 * - GET /airports/search -> renders airport search page
 * Takes search query as input and suggests/autofills airports
 */
fun Route.airportSearchRoutes() {
    get("/airports/search") {
        val query = call.request.queryParameters["q"]?.trim() ?: ""
        println(query)

        // only start returning flights after more than 1 character entered
        if (query.length < 2) {
            call.respond(emptyList<Airport>())
            return@get
        }

        val airportTable = AirportTableAccess()
        val suggestedAirports = airportTable.searchAirports(query)

        call.respond(suggestedAirports)
    }
}

/**
 * Constructs a `FlightSearch` model inputted parameters
 * @param params request parameters
 * @return flight search model
 */
private fun buildFlightSearch(params: Parameters): FlightSearch =
    FlightSearch(
        tripType = params["tripType"],
        origin = params["origin"],
        destination = params["destination"],
        departureDate = params["departureDate"],
        returnDate = params["returnDate"],
        adults = params["adults"],
        children = params["children"],
        infants = params["infants"],
    )
