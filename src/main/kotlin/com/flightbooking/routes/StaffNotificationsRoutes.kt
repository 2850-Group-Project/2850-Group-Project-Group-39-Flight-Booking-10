package com.flightbooking.routes

import com.flightbooking.access.StaffTableAccess
import com.flightbooking.models.StaffSession
import com.flightbooking.tables.*
import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Staff notifications routes (Change Requests inbox).
 *
 * Routes:
 * - GET  /staff/notifications:
 *   - Requires [StaffSession]; redirects to `/staff/login` if missing.
 *   - Loads staff display info and lists change requests.
 *   - Supports optional query param `q` (numeric) to search by change_request_id.
 *   - Renders `staff_notifications.peb`.
 *
 * - POST /staff/notifications/status:
 *   - Requires [StaffSession].
 *   - Updates change request status (supports `complete`).
 *   - Redirects back to `/staff/notifications`.
 *
 * - POST /staff/notifications/delete:
 *   - Requires [StaffSession].
 *   - Deletes a change request row.
 *   - Redirects back to `/staff/notifications`.
 *
 * Status rules:
 * - `complete` means processed but not deleted (record remains visible).
 * - `delete` is a separate action and removes the record.
 */
fun Route.staffNotificationsRoutes() {

    get("/staff/notifications") {
        val session = call.sessions.get<StaffSession>()
        if (session == null) {
            call.respondRedirect("/staff/login")
            return@get
        }

        val q = call.request.queryParameters["q"]?.trim().orEmpty()
        val qId = q.toIntOrNull()

        val model = transaction {
            // Use Access Logic where available (staff)
            val staff = StaffTableAccess().findByEmail(session.staffEmail)
            if (staff == null) {
                return@transaction mapOf<String, Any>("error" to "Staff not found, please login again.")
            }

            // Change requests: Access logic does not exist -> use Exposed directly.
            // Join User for email display, join current/requested flight for flight number,
            // join airports for current route, join requested seat for seat code.
            val origin = AirportTable.alias("origin")
            val dest = AirportTable.alias("dest")

            val currentFlight = FlightTable.alias("currentFlight")
            val requestedFlight = FlightTable.alias("requestedFlight")
            val requestedSeat = SeatTable.alias("requestedSeat")

            val cond: Op<Boolean> =
                if (q.isBlank()) Op.TRUE
                else if (qId == null) Op.FALSE
                else ChangeRequestTable.id eq qId

            val requests = (ChangeRequestTable
                .join(UserTable, JoinType.LEFT, additionalConstraint = { ChangeRequestTable.userId eq UserTable.id })
                .join(currentFlight, JoinType.LEFT, additionalConstraint = { ChangeRequestTable.currentFlightId eq currentFlight[FlightTable.id] })
                .join(origin, JoinType.LEFT, additionalConstraint = { currentFlight[FlightTable.originAirport] eq origin[AirportTable.id] })
                .join(dest, JoinType.LEFT, additionalConstraint = { currentFlight[FlightTable.destinationAirport] eq dest[AirportTable.id] })
                .join(requestedFlight, JoinType.LEFT, additionalConstraint = { ChangeRequestTable.requestedFlightId eq requestedFlight[FlightTable.id] })
                .join(requestedSeat, JoinType.LEFT, additionalConstraint = { ChangeRequestTable.requestedSeatId eq requestedSeat[SeatTable.id] })
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

                    requestedSeat[SeatTable.seatCode]
                )
                .select { cond }
                .orderBy(ChangeRequestTable.id, SortOrder.DESC)
                .map { r ->
                    val o = r.getOrNull(origin[AirportTable.iataCode])
                    val d = r.getOrNull(dest[AirportTable.iataCode])
                    val route = if (!o.isNullOrBlank() && !d.isNullOrBlank()) "$o → $d" else null

                    mapOf(
                        "requestId" to r[ChangeRequestTable.id],
                        "userId" to r[ChangeRequestTable.userId],
                        "userEmail" to r.getOrNull(UserTable.email),
                        "bookingId" to r[ChangeRequestTable.bookingId],
                        "segmentId" to r[ChangeRequestTable.bookingSegmentId],
                        "reason" to r.getOrNull(ChangeRequestTable.reason),
                        "status" to (r.getOrNull(ChangeRequestTable.status) ?: "pending"),
                        "createdAt" to (r.getOrNull(ChangeRequestTable.createdAt) ?: ""),
                        "updatedAt" to (r.getOrNull(ChangeRequestTable.updatedAt) ?: ""),

                        "currentFlightNo" to (r.getOrNull(currentFlight[FlightTable.flightNumber])?.toString() ?: ""),
                        "requestedFlightNo" to (r.getOrNull(requestedFlight[FlightTable.flightNumber])?.toString() ?: ""),

                        "currentRoute" to route,
                        "requestedSeatCode" to r.getOrNull(requestedSeat[SeatTable.seatCode])
                    )
                })

            mapOf(
                "staffName" to listOfNotNull(staff.firstName, staff.lastName).joinToString(" ").ifBlank { "Staff" },
                "staffRole" to (staff.role ?: "Staff"),
                "q" to q,
                "requests" to requests,
                "error" to (call.request.queryParameters["error"] ?: ""),
                "ok" to (call.request.queryParameters["ok"] ?: "")
            )
        }

        if (model.containsKey("error") && model["error"].toString().isNotBlank()) {
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

        val p = call.receiveParameters()
        val requestId = p["requestId"]?.toIntOrNull()
        val newStatus = p["status"]?.trim()?.lowercase()

        if (requestId == null || newStatus.isNullOrBlank()) {
            call.respondRedirect("/staff/notifications?error=Missing requestId/status")
            return@post
        }

        val allowed = setOf("pending", "approved", "rejected", "cancelled", "complete")
        if (newStatus !in allowed) {
            call.respondRedirect("/staff/notifications?error=Invalid status")
            return@post
        }

        transaction {
            ChangeRequestTable.update({ ChangeRequestTable.id eq requestId }) {
                it[status] = newStatus
                it[updatedAt] = Instant.now().toString()
            }
        }

        call.respondRedirect("/staff/notifications?ok=Request updated")
    }

    post("/staff/notifications/delete") {
        val session = call.sessions.get<StaffSession>()
        if (session == null) {
            call.respondRedirect("/staff/login")
            return@post
        }

        val p = call.receiveParameters()
        val requestId = p["requestId"]?.toIntOrNull()
        if (requestId == null) {
            call.respondRedirect("/staff/notifications?error=Missing requestId")
            return@post
        }

        transaction {
            ChangeRequestTable.deleteWhere { ChangeRequestTable.id eq requestId }
        }

        call.respondRedirect("/staff/notifications?ok=Request deleted")
    }
}