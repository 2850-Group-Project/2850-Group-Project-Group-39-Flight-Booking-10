package com.flightbooking.routes

import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.pebble.*
import io.ktor.server.sessions.*

import io.ktor.http.HttpStatusCode

import com.flightbooking.models.UserSession
import com.flightbooking.models.FlightSearch
import com.flightbooking.models.BookingSession

fun Route.flightRoutes() {
    post("/flights/select") {
        val session = call.sessions.get<UserSession>()

        if (session == null) {
            call.respondRedirect("/login")
            return@post
        }

        val params = call.receiveParameters()
        val flightId = params["flightId"]?.toIntOrNull() ?: return@post call.respond(
            HttpStatusCode.BadRequest, "Missing flightId")
        val fareId = params["fareId"]?.toIntOrNull() ?: return@post call.respond(
            HttpStatusCode.BadRequest, "Missing fareId")
        val leg = params["leg"]?.toString() ?: return@post call.respond(
            HttpStatusCode.BadRequest, "Missing leg")
        
        val search = FlightSearch(
            tripType = params["tripType"],
            origin = params["origin"],
            destination = params["destination"],
            departureDate = params["departureDate"],
            returnDate = params["returnDate"],
            adults = params["adults"],
            children = params["children"],
            infants = params["infants"]
        )

        val booking = call.sessions.get<BookingSession>() ?: BookingSession()

        println("====================================")
        println(flightId)

        val updated = when (leg) {
            "outbound" -> booking.copy(outboundFlightId = flightId, outboundFareId = fareId, search = search)
            "return"   -> booking.copy(returnFlightId = flightId, returnFareId = fareId, search = search)
            else -> {
                call.respond(HttpStatusCode.BadRequest, "Invalid leg")
                return@post
            }
        }

        call.sessions.set(updated)

        println(updated)

        call.respond(HttpStatusCode.OK, "ok") // stops ktor from hanging
    }
}
