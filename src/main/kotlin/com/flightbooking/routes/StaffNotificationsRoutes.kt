package com.flightbooking.routes

import com.flightbooking.access.ChangeRequestTableAccess
import com.flightbooking.access.StaffTableAccess
import com.flightbooking.models.StaffSession
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.ChangeRequestTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.SeatTable
import com.flightbooking.tables.UserTable
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import org.jetbrains.exposed.sql.Alias
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Staff notifications routes (Change Requests inbox).
 *
 * Routes:
 * - GET  /staff/notifications ->
 *   - Requires [StaffSession]; redirects to `/staff/login` if missing.
 *   - Loads staff display info and lists change requests.
 *   - Supports optional query param `q` (numeric) to search by change_request_id.
 *   - Renders `staff_notifications.peb`.
 *
 * - POST /staff/notifications/status ->
 *   - Requires [StaffSession].
 *   - Updates change request status (supports `complete`).
 *   - Redirects back to `/staff/notifications`.
 *
 * - POST /staff/notifications/delete ->
 *   - Requires [StaffSession].
 *   - Deletes a change request row.
 *   - Redirects back to `/staff/notifications`.
 *
 * Status rules ->
 * - `complete` means processed but not deleted (record remains visible).
 * - `delete` is a separate action and removes the record.
 */
fun Route.staffNotificationsRoutes() {
    val changeAccess = ChangeRequestTableAccess()
    get("/staff/notifications") {
        val session = call.sessions.get<StaffSession>()
        if (session == null) {
            call.respondRedirect("/staff/login")
            return@get
        }
        val q = call.request.queryParameters["q"]?.trim().orEmpty()
        val model = loadNotificationsModel(session, q, call)
        if (model["error"].toString().isNotBlank()) {
            call.respondText(model["error"].toString())
            return@get
        }
        call.respond(PebbleContent("staff_notifications.peb", model))
    }

    post("/staff/notifications/status") {
        val session = call.sessions.get<StaffSession>()
        if (session == null) {
            call.respondRedirect("/staff/login")
            return@post
        }
        val params = call.receiveParameters()
        val redirectUrl = handleStatusUpdate(params, changeAccess)
        call.respondRedirect(redirectUrl)
    }

    post("/staff/notifications/delete") {
        val session = call.sessions.get<StaffSession>()
        if (session == null) {
            call.respondRedirect("/staff/login")
            return@post
        }
        val params = call.receiveParameters()
        val redirectUrl = handleDelete(params, changeAccess)
        call.respondRedirect(redirectUrl)
    }
}

/**
 * Function that creates model for notifications dashboard for staff
 * @param session staff session
 * @param q search text
 * @param call request call
 * @return notifications model
 */
private fun loadNotificationsModel(
    session: StaffSession,
    q: String,
    call: ApplicationCall,
): Map<String, Any> =
    transaction {
        val staff =
            StaffTableAccess().findByEmail(session.staffEmail)
                ?: return@transaction mapOf("error" to "Staff not found, please login again.")

        val requests = fetchChangeRequests(q)

        mapOf(
            "staffName" to listOfNotNull(staff.firstName, staff.lastName).joinToString(" ").ifBlank { "Staff" },
            "staffRole" to (staff.role ?: "Staff"),
            "q" to q,
            "requests" to requests,
            "error" to (call.request.queryParameters["error"] ?: ""),
            "ok" to (call.request.queryParameters["ok"] ?: ""),
        )
    }

/**
 * Data class to hold Alias which joins ChangeRequest with other tables
 */
private data class ChangeRequestAliases(
    val origin: Alias<AirportTable>,
    val dest: Alias<AirportTable>,
    val currentFlight: Alias<FlightTable>,
    val requestedFlight: Alias<FlightTable>,
    val requestedSeat: Alias<SeatTable>,
)

/**
 * Fetches change‑request records for the staff dashboard, optionally filtered
 * by a numeric query string
 * @param q search text
 * @return list of change requests
 */
private fun fetchChangeRequests(q: String): List<Map<String, Any?>> =
    transaction {
        val qId = q.toIntOrNull()
        val cond =
            when {
                q.isBlank() -> Op.TRUE
                qId == null -> Op.FALSE
                else -> ChangeRequestTable.id eq qId
            }
        val origin = AirportTable.alias("origin")
        val dest = AirportTable.alias("dest")
        val currentFlight = FlightTable.alias("currentFlight")
        val requestedFlight = FlightTable.alias("requestedFlight")
        val requestedSeat = SeatTable.alias("requestedSeat")
        val aliases = ChangeRequestAliases(origin, dest, currentFlight, requestedFlight, requestedSeat)
        ChangeRequestTable
            .join(UserTable, JoinType.LEFT) { ChangeRequestTable.userId eq UserTable.id }
            .join(currentFlight, JoinType.LEFT) { ChangeRequestTable.currentFlightId eq currentFlight[FlightTable.id] }
            .join(origin, JoinType.LEFT) { currentFlight[FlightTable.originAirport] eq origin[AirportTable.id] }
            .join(dest, JoinType.LEFT) { currentFlight[FlightTable.destinationAirport] eq dest[AirportTable.id] }
            .join(requestedFlight, JoinType.LEFT) {
                ChangeRequestTable.requestedFlightId eq requestedFlight[FlightTable.id]
            }
            .join(requestedSeat, JoinType.LEFT) { ChangeRequestTable.requestedSeatId eq requestedSeat[SeatTable.id] }
            .slice(
                ChangeRequestTable.id,
                ChangeRequestTable.userId,
                ChangeRequestTable.bookingId,
                ChangeRequestTable.bookingSegmentId,
                ChangeRequestTable.reason,
                ChangeRequestTable.status,
                ChangeRequestTable.createdAt,
                ChangeRequestTable.updatedAt,
                UserTable.email,
                currentFlight[FlightTable.flightNumber],
                requestedFlight[FlightTable.flightNumber],
                origin[AirportTable.iataCode],
                dest[AirportTable.iataCode],
                requestedSeat[SeatTable.seatCode],
            )
            .select { cond }
            .orderBy(ChangeRequestTable.id, SortOrder.DESC)
            .map { r -> mapChangeRequestRow(r, aliases) }
    }

/**
 * Maps single ChangeRequest to a template‑friendly structure
 * extracts from multiple tables with ChangeRequestAliases preventing collisions
 * @param r result row
 * @param aliases alias set
 * @return mapped change request
 */
private fun mapChangeRequestRow(
    r: ResultRow,
    aliases: ChangeRequestAliases,
): Map<String, Any?> {
    val route =
        r.getOrNull(aliases.origin[AirportTable.iataCode])?.let { o ->
            r.getOrNull(aliases.dest[AirportTable.iataCode])?.let { d -> "$o → $d" }
        }
    return mapOf(
        "requestId" to r[ChangeRequestTable.id],
        "userId" to r[ChangeRequestTable.userId],
        "userEmail" to r.getOrNull(UserTable.email),
        "bookingId" to r[ChangeRequestTable.bookingId],
        "segmentId" to r[ChangeRequestTable.bookingSegmentId],
        "reason" to r.getOrNull(ChangeRequestTable.reason),
        "status" to (r.getOrNull(ChangeRequestTable.status) ?: "pending"),
        "createdAt" to (r.getOrNull(ChangeRequestTable.createdAt) ?: ""),
        "updatedAt" to (r.getOrNull(ChangeRequestTable.updatedAt) ?: ""),
        "currentFlightNo" to (r.getOrNull(aliases.currentFlight[FlightTable.flightNumber])?.toString() ?: ""),
        "requestedFlightNo" to (r.getOrNull(aliases.requestedFlight[FlightTable.flightNumber])?.toString() ?: ""),
        "currentRoute" to route,
        "requestedSeatCode" to r.getOrNull(aliases.requestedSeat[SeatTable.seatCode]),
    )
}

/**
 * Updates the status of a ChangeRequest record, using id to search,
 * returns url with success or fail
 * @param params request params
 * @param access table access
 * @return redirect URL
 */
private fun handleStatusUpdate(
    params: Parameters,
    access: ChangeRequestTableAccess,
): String {
    val requestId = params["requestId"]?.toIntOrNull()
    val newStatus = params["status"]?.trim()?.lowercase()
    val error =
        when {
            requestId == null || newStatus.isNullOrBlank() ->
                "Missing requestId/status"
            newStatus !in setOf("pending", "approved", "rejected", "cancelled", "complete") ->
                "Invalid status"
            else -> null
        }
    if (error != null) {
        return "/staff/notifications?error=$error"
    }
    val ok = access.updateStatus(requestId!!, newStatus!!)
    return if (ok) {
        "/staff/notifications?ok=Request updated"
    } else {
        "/staff/notifications?error=Update failed"
    }
}

/**
 * Handles deleting ChangeRequest record,
 * returns url with success or fail
 * @param params request params
 * @param access table access
 * @return redirect URL
 */
private fun handleDelete(
    params: Parameters,
    access: ChangeRequestTableAccess,
): String {
    val requestId = params["requestId"]?.toIntOrNull()
    if (requestId == null) {
        return "/staff/notifications?error=Missing requestId"
    }
    val ok = access.deleteById(requestId)
    return if (ok) {
        "/staff/notifications?ok=Request deleted"
    } else {
        "/staff/notifications?error=Delete failed"
    }
}
