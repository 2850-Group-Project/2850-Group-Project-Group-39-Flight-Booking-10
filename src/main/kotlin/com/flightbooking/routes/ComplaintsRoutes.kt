package com.flightbooking.routes

import com.flightbooking.access.ComplaintResponseTableAccess
import com.flightbooking.access.ComplaintTableAccess
import com.flightbooking.access.UserTableAccess
import com.flightbooking.service.AuthService
import io.ktor.server.application.call
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get

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
    post("/profile/complaints/view-responses") { handleViewResponses(call) }
}

/**
 * Get function to render the complaints page
 * @param call request call
 */
private suspend fun handleGetComplaints(call: io.ktor.server.application.ApplicationCall) {
    val (userSession, _) = AuthService.requireUser(call) ?: return
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
    val (userSession, _) = AuthService.requireUser(call) ?: return

    val params = call.receiveParameters()
    val complaintText = params["message"] ?: ""
    val type = params["type"] ?: ""
    val user = UserTableAccess().findByEmail(userSession.userEmail)

    if (complaintText.length < 2 || user == null) {
        val error = if (complaintText.length < 2) "missing_fields" else "server_error"
        call.respondRedirect("/complaints?error=$error")
    } else {
        ComplaintTableAccess().createComplaint(user.id, type, complaintText, "open", null)
        call.respondRedirect("/complaints?success=true")
    }
}

/**
 * Get function for complaints page to display current complaints that the user has made
 * @param call request call
 */
private suspend fun handleProfileComplaints(call: io.ktor.server.application.ApplicationCall) {
    val (userSession, _) = AuthService.requireUser(call) ?: return

    val user = UserTableAccess().findByEmail(userSession.userEmail) ?: return call.respondRedirect("/login")
    val complaints = ComplaintTableAccess().findByUserId(user.id)
    val responsesByComplaint =
        complaints.associate { complaint ->
            complaint.id to ComplaintResponseTableAccess().getResponsesForComplaint(complaint.id)
        }
    val unreadByComplaint =
        complaints.associate { complaint ->
            complaint.id to ComplaintResponseTableAccess().getUnreadCountForComplaint(complaint.id)
        }

    call.respond(
        PebbleContent(
            "profile_complaints.peb",
            mapOf<String, Any>(
                "userSession" to userSession,
                "complaints" to complaints,
                "responsesByComplaint" to responsesByComplaint,
                "unreadByComplaint" to unreadByComplaint,
            ),
        ),
    )
}

/**
 * Handler function to submit responses, and updates view status of complaint response
 * @param call request call
 */
private suspend fun handleViewResponses(call: io.ktor.server.application.ApplicationCall) {
    val (_, _) = AuthService.requireUser(call) ?: return
    val params = call.receiveParameters()
    val complaintId = params["complaintId"]?.toIntOrNull() ?: return
    ComplaintResponseTableAccess().markResponseView(complaintId)
    call.respond(io.ktor.http.HttpStatusCode.OK)
}
