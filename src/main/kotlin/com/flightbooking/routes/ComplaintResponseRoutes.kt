package com.flightbooking.routes

import com.flightbooking.access.ComplaintResponseTableAccess
import com.flightbooking.access.StaffTableAccess
import com.flightbooking.service.AuthService
import io.ktor.server.application.call
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.Instant

/**
 * Staff inquiries page routes
 *
 * Routes:
 * - GET  /staff/inquiries -> renders inquiries page with all complaints and their responses
 * - POST /staff/inquiries/respond -> submits a staff response to a complaint
 * - POST /staff/inquiries/status -> updates the status of a complaint
 * - POST /staff/inquiries/delete -> deletes a complaint
 * - POST /staff/inquiries/delete-response -> deletes a specific response
 */
fun Route.staffInquiriesRoutes() {
    get("/staff/inquiries") { handleGetInquiries(call) }
    post("/staff/inquiries/respond") { handleRespond(call) }
    post("/staff/inquiries/status") { handleUpdateStatus(call) }
    post("/staff/inquiries/delete") { handleDeleteComplaint(call) }
    post("/staff/inquiries/delete-response") { handleDeleteResponse(call) }
}

/**
 * Renders the inquiries page with all complaints and their responses
 * @param call request call
 */
private suspend fun handleGetInquiries(call: io.ktor.server.application.ApplicationCall) {
    val (staffSession, _) = AuthService.requireStaff(call) ?: return

    val q = call.request.queryParameters["q"] ?: ""
    val complaintAccess = ComplaintResponseTableAccess()
    val complaints = complaintAccess.getComplaints(q)

    val responsesByComplaint = complaints.associate { complaint ->
        val id = complaint["complaintId"] as Int
        id to complaintAccess.getResponsesForComplaint(id)
    }

    call.respond(
        PebbleContent(
            "staff_inquiries.peb",
            mapOf(
                "staffName" to staffSession.staffEmail,
                "q" to q,
                "complaints" to complaints,
                "responsesByComplaint" to responsesByComplaint,
                "ok" to (call.request.queryParameters["ok"] ?: ""),
                "error" to (call.request.queryParameters["error"] ?: ""),
            ),
        ),
    )
}

/**
 * Handles staff submitting a response to a complaint
 * @param call request call
 */
private suspend fun handleRespond(call: io.ktor.server.application.ApplicationCall) {
    val (staffSession, _) = AuthService.requireStaff(call) ?: return

    val params = call.receiveParameters()
    val complaintId = params["complaintId"]?.toIntOrNull()
    val message = params["message"]?.trim() ?: ""
    val staff = StaffTableAccess().findByEmail(staffSession.staffEmail)

    if (complaintId == null || message.length < 2 || staff == null) {
        call.respondRedirect("/staff/inquiries?error=missing_fields")
        return
    }

    ComplaintResponseTableAccess().createResponse(
        complaintId = complaintId,
        staffId = staff.id,
        message = message,
        createdAt = Instant.now().toString(),
    )

    call.respondRedirect("/staff/inquiries?ok=Response+sent")
}

/**
 * Handles updating the status of a complaint
 * @param call request call
 */
private suspend fun handleUpdateStatus(call: io.ktor.server.application.ApplicationCall) {
    val (staffSession, _) = AuthService.requireStaff(call) ?: return

    val params = call.receiveParameters()
    val complaintId = params["complaintId"]?.toIntOrNull()
    val status = params["status"] ?: ""
    val staff = StaffTableAccess().findByEmail(staffSession.staffEmail)

    if (complaintId == null || status.isBlank() || staff == null) {
        call.respondRedirect("/staff/inquiries?error=missing_fields")
        return
    }

    ComplaintResponseTableAccess().updateComplaintStatus(complaintId, status, staff.id)
    call.respondRedirect("/staff/inquiries?ok=Status+updated")
}

/**
 * Handles deleting a complaint and all its responses
 * @param call request call
 */
private suspend fun handleDeleteComplaint(call: io.ktor.server.application.ApplicationCall) {
    AuthService.requireStaff(call) ?: return

    val params = call.receiveParameters()
    val complaintId = params["complaintId"]?.toIntOrNull()

    if (complaintId == null) {
        call.respondRedirect("/staff/inquiries?error=missing_fields")
        return
    }

    ComplaintResponseTableAccess().deleteComplaint(complaintId)
    call.respondRedirect("/staff/inquiries?ok=Complaint+deleted")
}

/**
 * Handles deleting a single staff response
 * @param call request call
 */
private suspend fun handleDeleteResponse(call: io.ktor.server.application.ApplicationCall) {
    AuthService.requireStaff(call) ?: return

    val params = call.receiveParameters()
    val responseId = params["responseId"]?.toIntOrNull()

    if (responseId == null) {
        call.respondRedirect("/staff/inquiries?error=missing_fields")
        return
    }

    ComplaintResponseTableAccess().deleteResponse(responseId)
    call.respondRedirect("/staff/inquiries?ok=Response+deleted")
}
