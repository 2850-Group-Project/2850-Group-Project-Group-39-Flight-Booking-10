package com.flightbooking.routes

import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.*
import io.ktor.server.pebble.*
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.sessions.*

import com.flightbooking.access.UserTableAccess
import com.flightbooking.access.ComplaintTableAccess
import com.flightbooking.access.StaffTableAccess

import com.flightbooking.tables.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert

import com.flightbooking.models.UserSession
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

        val success = call.request.queryParameters["success"] == "true"
        val error   = call.request.queryParameters["error"]

        call.respond(PebbleContent("complaints.peb", mapOf(
            "userSession" to userSession,
            "success" to if (success) "true" else "false",
            "error" to (error ?: "")
        )))
    }

    post("/complaints/submit") {
        val userSession = call.sessions.get<UserSession>()
        println(userSession)
        
        if (userSession == null) {
            call.respondRedirect("/login")
            return@post
        }

        val params = call.receiveParameters()
        val complaintText = params["message"] ?: ""
        val type = params["type"] ?: ""
        if (complaintText == "") {
            call.respondRedirect("/complaints?error=server_error")
            return@post
        }
        if (complaintText.length < 2) {
            call.respondRedirect("/complaints?error=missing_fields")
            return@post
        }

        val user = UserTableAccess().findByEmail(userSession.userEmail)
        if (user == null) {
            call.respondRedirect("/login")
            return@post
        }

        val userId = user.id
        
        transaction {
            ComplaintTable.insert {
                it[ComplaintTable.userId] = userId
                it[ComplaintTable.type] = type
                it[ComplaintTable.message] = complaintText
            }
        }
        
        call.respondRedirect("/complaints?success=true")
    }

    // complaints page to display current complaints that the user has made 
    // as well as updates on those complaints
    get("/profile/complaints") {
        val userSession = call.sessions.get<UserSession>()
        if (userSession == null) {
            call.respondRedirect("/login")
            return@get
        }

        val user = UserTableAccess().findByEmail(userSession.userEmail)
        if (user == null) {
            call.respondRedirect("/login")
            return@get
        }

        val userId = user.id

        val complaints = ComplaintTableAccess().findByUserId(userId)

        call.respond(
            PebbleContent(
                "profile_complaints.peb",
                mapOf<String, Any>(
                    "userSession" to userSession,
                    "complaints"  to complaints,
                )
            )
        )
    }
}