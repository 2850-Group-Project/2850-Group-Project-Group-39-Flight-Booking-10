package com.flightbooking.routes

import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.*
import io.ktor.server.pebble.*
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.sessions.*

import com.flightbooking.access.FlightTableAccess
import com.flightbooking.access.AirportTableAccess

import com.flightbooking.tables.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import com.flightbooking.models.UserSession
import com.flightbooking.models.BookingSession
import com.flightbooking.models.Complaint

import com.flightbooking.routes.authRoutes

fun Route.complaintsRoutes() {
    // difference between complaints and profile complaints
    // complaints is to make a complaint
    // profile complaints is to see all the complaints you have made and updates to them?
    get("/complaints") {
        val userSession = call.sessions.get<UserSession>()
        
        if (userSession == null) {
            call.respondRedirect("/login")
            return@get
        }

        println("at complaints page")

        call.respond(PebbleContent("complaints.peb", mapOf(
            "userSession" to userSession,
        )))
    }

    post("/complaints/submit") {
        val userSession = call.sessions.get<UserSession>()
        
        if (userSession == null) {
            call.respondRedirect("/login")
            return@post
        }

        val params = call.receiveParameters()
        val complaintText = params["message"] ?: ""
        if (complaintText == null || complaintText == "" || complaintText.length < 2) {
            // return an error?
            return@post
        }

        
    }
}