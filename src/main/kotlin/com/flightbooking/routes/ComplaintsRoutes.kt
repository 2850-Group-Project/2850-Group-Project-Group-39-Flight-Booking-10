package com.flightbooking.routes

import com.flightbooking.access.ComplaintTableAccess
import com.flightbooking.access.UserTableAccess
import com.flightbooking.models.UserSession
import com.flightbooking.tables.ComplaintTable
import io.ktor.server.application.call
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Complaints page routes
 *
 * Routes:
 * - GET  /complaints -> renders complaints page
 * - POST /complaints/submit -> processes submitted complaint information and creates complaint
 * - GET /profile/complains -> renders page for users' complaints,
 * see all the complaints you have made and updates to them
 */
fun Route.complaintsRoutes() {
    get("/complaints") { handleGetComplaints(call) }
    post("/complaints/submit") { handleSubmitComplaint(call) }
    get("/profile/complaints") { handleProfileComplaints(call) }
}

/**
 * Get function to render the complaints page
 * @param call request call
 */
private suspend fun handleGetComplaints(call: io.ktor.server.application.ApplicationCall) {
    val userSession = call.sessions.get<UserSession>() ?: return call.respondRedirect("/login")
    call.respond(
        PebbleContent(
            "complaints.peb",
            mapOf(
                "userSession" to userSession,
                "success" to if (call.request.queryParameters["success"] == "true") "true" else "false",
                "error" to (call.request.queryParameters["error"] ?: ""),
            ),
        ),
    )
}

/**
 * Post function to accept complaint information, validates complaint length and if user is null redirects to error
 * Inserts complaint into complaint table
 * @param call request call
 */
private suspend fun handleSubmitComplaint(call: io.ktor.server.application.ApplicationCall) {
    val userSession = call.sessions.get<UserSession>() ?: return call.respondRedirect("/login")
    println(userSession)
    val params = call.receiveParameters()
    val complaintText = params["message"] ?: ""
    val type = params["type"] ?: ""
    val user = UserTableAccess().findByEmail(userSession.userEmail)

    if (complaintText.length < 2 || user == null) {
        val error = if (complaintText.length < 2) "missing_fields" else "server_error"
        call.respondRedirect("/complaints?error=$error")
    } else {
        transaction {
            ComplaintTable.insert {
                it[ComplaintTable.userId] = user.id
                it[ComplaintTable.type] = type
                it[ComplaintTable.message] = complaintText
            }
        }
        call.respondRedirect("/complaints?success=true")
    }
}

/**
 * Get function for complaints page to display current complaints that the user has made
 * @param call request call
 */
private suspend fun handleProfileComplaints(call: io.ktor.server.application.ApplicationCall) {
    val userSession = call.sessions.get<UserSession>() ?: return call.respondRedirect("/login")
    val user = UserTableAccess().findByEmail(userSession.userEmail) ?: return call.respondRedirect("/login")
    call.respond(
        PebbleContent(
            "profile_complaints.peb",
            mapOf<String, Any>(
                "userSession" to userSession,
                "complaints" to ComplaintTableAccess().findByUserId(user.id),
            ),
        ),
    )
}
