package com.flightbooking.routes

import com.flightbooking.tables.FlightTable
import com.flightbooking.models.StaffSession
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.PassengerTable
import com.flightbooking.tables.SeatAssignmentTable
import com.flightbooking.tables.SeatTable
import com.flightbooking.tables.StaffTable
import com.flightbooking.tables.UserTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.set
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

/**
 * Staff bookings management routes.
 *
 * Routes:
 * - GET  /staff/bookings:
 *   - Requires [StaffSession]; redirects to `/staff/login` if missing.
 *   - Loads staff display info, flights list, seats grouped by flight, and booking records.
 *   - Supports optional query param `q` (booking id filter) for listing existing bookings.
 *   - Renders `staff_bookings.peb` with the model data.
 *
 * - POST /staff/bookings/create:
 *   - Requires [StaffSession]; redirects to `/staff/login` if missing.
 *   - Creates a booking for a passenger email + flight, optionally assigns a seat.
 *   - Redirects back to `/staff/bookings` (with `error` query param if validation fails).
 *
 * - POST /staff/bookings/update:
 *   - Requires [StaffSession]; redirects to `/staff/login` if missing.
 *   - Updates booking status and allows updating flight/seat for the booking segment.
 *   - Enforces that a seat (if selected) belongs to the selected flight.
 *   - Redirects back to `/staff/bookings`.
 */

private const val BOOKING_REF_LENGTH = 10
private const val BOOKING_LIST_LIMIT = 300
private const val FLIGHT_LIST_LIMIT = 300

fun Route.staffBookingsRoutes() {
    get("/staff/bookings") {
        val session = call.sessions.get<StaffSession>() ?: return@get call.respondRedirect("/staff/login")
        val q = call.request.queryParameters["q"]?.trim().orEmpty()
        val model = fetchStaffModel(session, q)
        if (model.containsKey("error")) return@get call.respondText(model["error"].toString())
        call.respond(PebbleContent("staff_bookings.peb", model))
    }

    post("/staff/bookings/update") {
        call.sessions.get<StaffSession>() ?: return@post call.respondRedirect("/staff/login")
        val params = call.receiveParameters()
        val bookingId = params["bookingId"]?.toIntOrNull() ?: return@post call.respondRedirect("/staff/bookings")
        val newStatus = params["bookingStatus"]?.trim()
        val newFlightId = params["flightId"]?.toIntOrNull()
        val newSeatId = params["seatId"]?.toIntOrNull()

        transaction {
            if (!newStatus.isNullOrBlank()) 
                BookingTable.update({ BookingTable.id eq bookingId }) { it[bookingStatus] = newStatus }
            val segRow = BookingSegmentTable
                .select { BookingSegmentTable.bookingId eq bookingId }
                .limit(1)
                .firstOrNull()
            val segId = segRow?.get(BookingSegmentTable.id)
            if (segId != null && newFlightId != null) updateBookingSegment(segId, segRow, newFlightId, newSeatId)
        }
        call.respondRedirect("/staff/bookings")
    }

    post("/staff/bookings/create") {
        call.sessions.get<StaffSession>() ?: return@post call.respondRedirect("/staff/login")
        val params = call.receiveParameters()
        val passengerEmail = params["passengerEmail"]?.trim().orEmpty()
        val passengerFirstName = params["passengerFirstName"]?.trim()
        val passengerLastName = params["passengerLastName"]?.trim()
        val flightId = params["flightId"]?.toIntOrNull()
        val bookingStatus = params["bookingStatus"]?.trim().orEmpty()
        val seatId = params["seatId"]?.toIntOrNull()

        if (passengerEmail.isBlank() || flightId == null || bookingStatus.isBlank()) 
            return@post call.respondRedirect("/staff/bookings")

        var errMsg: String? = null
        transaction {
            val userRow = UserTable.select { UserTable.email eq passengerEmail }.limit(1).firstOrNull()
            if (userRow == null) { errMsg = "No user found for this email"; return@transaction }

            val bookingId = BookingTable.insert {
                it[BookingTable.bookingReference] = "BK-" + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .take(BOOKING_REF_LENGTH)
                    .uppercase()
                it[BookingTable.paymentId] = null
                it[BookingTable.createdAt] = Instant.now().toString()
                it[BookingTable.bookingStatus] = bookingStatus
                it[BookingTable.cancelledAt] = null
                it[BookingTable.amendable] = 1
                it[BookingTable.userId] = userRow[UserTable.id]
            } get BookingTable.id

            val passengerId = PassengerTable.insert {
                it[PassengerTable.bookingId] = bookingId
                it[PassengerTable.email] = passengerEmail
                it[PassengerTable.checkedIn] = 0
                it[PassengerTable.firstName] = passengerFirstName
                it[PassengerTable.lastName] = passengerLastName
                it[PassengerTable.title] = null; it[PassengerTable.dateOfBirth] = null
                it[PassengerTable.gender] = null; it[PassengerTable.nationality] = null
                it[PassengerTable.documentType] = null; it[PassengerTable.documentNumber] = null
                it[PassengerTable.documentCountry] = null; it[PassengerTable.documentExpiry] = null
            } get PassengerTable.id

            val segmentId = BookingSegmentTable.insert {
                it[BookingSegmentTable.bookingId] = bookingId
                it[BookingSegmentTable.flightId] = flightId
                it[BookingSegmentTable.flightFareId] = 1
            } get BookingSegmentTable.id

            val seatAssignmentId = SeatAssignmentTable.insert {
                it[SeatAssignmentTable.passengerId] = passengerId
                it[SeatAssignmentTable.bookingSegmentId] = segmentId
                it[SeatAssignmentTable.seatId] = null
            } get SeatAssignmentTable.id

            if (seatId != null) {
                val seatRow = SeatTable
                    .select { SeatTable.id eq seatId }
                    .limit(1)
                    .firstOrNull()
                if (seatRow != null 
                    && seatRow[SeatTable.flightId] == flightId 
                    && seatRow[SeatTable.status] == "available") {
                    SeatTable.update({ SeatTable.id eq seatId }) { it[SeatTable.status] = "occupied" }
                    SeatAssignmentTable
                        .update({ SeatAssignmentTable.id eq seatAssignmentId }) { 
                            it[SeatAssignmentTable.seatId] = seatId }
                }
            }
        }
        if (errMsg != null) return@post call.respondRedirect("/staff/bookings?error=$errMsg")
        call.respondRedirect("/staff/bookings")
    }
}

private fun fetchStaffModel(session: StaffSession, q: String): Map<String, Any> = transaction {
    val staffRow = StaffTable.select { StaffTable.email eq session.staffEmail }.limit(1).firstOrNull()
        ?: return@transaction mapOf("error" to "Staff not found, please login again.")

    val staffName = listOfNotNull(staffRow[StaffTable.firstName], staffRow[StaffTable.lastName])
        .joinToString(" ").ifBlank { "Staff" }
    val staffRole = staffRow[StaffTable.role] ?: "Staff"

    val flights = fetchFlights()
    val bookingsList = fetchBookings(q)
    val seatsByFlight = fetchSeatsByFlight(bookingsList)

    mapOf(
        "staffName" to staffName,
        "staffRole" to staffRole,
        "flights" to flights,
        "q" to q,
        "seatsByFlight" to seatsByFlight,
        "bookings" to bookingsList
    )
}

private fun fetchFlights(): List<Map<String, Any>> {
    val origin = AirportTable.alias("origin")
    val dest = AirportTable.alias("dest")
    return FlightTable
        .join(origin, JoinType.INNER, additionalConstraint = { FlightTable.originAirport eq origin[AirportTable.id] })
        .join(dest, JoinType.INNER, additionalConstraint = { FlightTable.destinationAirport eq dest[AirportTable.id] })
        .slice(
            FlightTable.id, FlightTable.flightNumber, FlightTable.status,
            FlightTable.scheduledDepartureTime, FlightTable.scheduledArrivalTime,
            origin[AirportTable.iataCode], dest[AirportTable.iataCode]
        )
        .selectAll()
        .orderBy(FlightTable.id, SortOrder.DESC)
        .limit(FLIGHT_LIST_LIMIT)
        .map { r ->
            val flightNo = r[FlightTable.flightNumber]?.toString() ?: r[FlightTable.id].toString()
            val label = "${r[origin[AirportTable.iataCode]]} → " +
                "${r[dest[AirportTable.iataCode]]} | " +
                "$flightNo | ${r[FlightTable.status]}"
            mapOf(
                "id" to r[FlightTable.id],
                "label" to label
            )
        }
}

private fun fetchBookings(q: String): List<Map<String, Any?>> = BookingTable
    .join(PassengerTable, 
        JoinType.LEFT, 
        additionalConstraint = { PassengerTable.bookingId eq BookingTable.id })
    .join(BookingSegmentTable, 
        JoinType.LEFT, 
        additionalConstraint = { BookingSegmentTable.bookingId eq BookingTable.id })
    .join(FlightTable, 
        JoinType.LEFT, 
        additionalConstraint = { FlightTable.id eq BookingSegmentTable.flightId })
    .join(SeatAssignmentTable, 
        JoinType.LEFT, 
        additionalConstraint = { SeatAssignmentTable.bookingSegmentId eq BookingSegmentTable.id })
    .join(SeatTable, 
        JoinType.LEFT, 
        additionalConstraint = { SeatTable.id eq SeatAssignmentTable.seatId })
    .slice(
        BookingTable.id, BookingTable.bookingReference, BookingTable.bookingStatus, BookingTable.createdAt,
        PassengerTable.id, PassengerTable.title, PassengerTable.firstName, 
        PassengerTable.lastName, PassengerTable.email,
        BookingSegmentTable.id, BookingSegmentTable.flightId,
        SeatAssignmentTable.id, SeatAssignmentTable.seatId, SeatTable.seatCode
    )
    .select {
        if (q.isBlank()) Op.TRUE
        else q.toIntOrNull()?.let { BookingTable.id eq it } ?: Op.FALSE
    }
    .orderBy(BookingTable.id, SortOrder.DESC)
    .limit(BOOKING_LIST_LIMIT)
    .map { r ->
        val passengerName = listOfNotNull(
            r[PassengerTable.title], 
            r[PassengerTable.firstName], 
            r[PassengerTable.lastName])
            .joinToString(" ").ifBlank { "" }
        mapOf(
            "bookingId" to r[BookingTable.id],
            "bookingReference" to r[BookingTable.bookingReference],
            "bookingStatus" to r[BookingTable.bookingStatus],
            "createdAt" to r[BookingTable.createdAt],
            "passengerId" to r.getOrNull(PassengerTable.id),
            "passengerName" to passengerName,
            "passengerEmail" to r.getOrNull(PassengerTable.email),
            "segmentId" to r.getOrNull(BookingSegmentTable.id),
            "flightId" to r.getOrNull(BookingSegmentTable.flightId),
            "seatAssignmentId" to r.getOrNull(SeatAssignmentTable.id),
            "seatId" to r.getOrNull(SeatAssignmentTable.seatId),
            "seatCode" to r.getOrNull(SeatTable.seatCode)
        )
    }

private fun fetchSeatsByFlight(bookingsList: List<Map<String, Any?>>): Map<Int, List<Map<String, Any>>> {
    val flightIds = bookingsList.mapNotNull { it["flightId"] as? Int }.distinct()
    if (flightIds.isEmpty()) return emptyMap()
    return SeatTable.select { SeatTable.flightId inList flightIds }
        .map { r -> r[SeatTable.flightId] to mapOf(
            "id" to r[SeatTable.id], 
            "seatCode" to r[SeatTable.seatCode], 
            "status" to r[SeatTable.status]) }
        .groupBy({ it.first }, { it.second })
}

private fun updateBookingSegment(
    segId: Int, segRow: org.jetbrains.exposed.sql.ResultRow, newFlightId: Int, newSeatId: Int?) {
    val currentFlightId = segRow[BookingSegmentTable.flightId]
    if (currentFlightId != newFlightId) {
        clearSeatAssignment(segId)
        BookingSegmentTable.update({ BookingSegmentTable.id eq segId }) { it[flightId] = newFlightId }
    } else {
        updateSeatAssignment(segId, currentFlightId, newSeatId)
    }
}

private fun clearSeatAssignment(segId: Int) {
    val saRow = SeatAssignmentTable
        .select { SeatAssignmentTable.bookingSegmentId eq segId }
        .limit(1)
        .firstOrNull()
    val oldSeatId = saRow?.get(SeatAssignmentTable.seatId)
    if (oldSeatId != null) SeatTable.update({ 
        SeatTable.id eq oldSeatId }) { 
            it[status] = "available" }
    if (saRow != null) SeatAssignmentTable.update({ 
        SeatAssignmentTable.id eq saRow[SeatAssignmentTable.id] }) { 
            it[seatId] = null }
}

private fun updateSeatAssignment(segId: Int, currentFlightId: Int, newSeatId: Int?) {
    val saRow = SeatAssignmentTable.select { 
        SeatAssignmentTable.bookingSegmentId eq segId }
        .limit(1)
        .firstOrNull() ?: return
    val oldSeatId = saRow[SeatAssignmentTable.seatId]
    if (newSeatId == null) {
        if (oldSeatId != null) SeatTable.update({ SeatTable.id eq oldSeatId }) { it[status] = "available" }
        SeatAssignmentTable.update({ SeatAssignmentTable.id eq saRow[SeatAssignmentTable.id] }) { it[seatId] = null }
    } else {
        val seatRow = SeatTable
            .select { SeatTable.id eq newSeatId }
            .limit(1)
            .firstOrNull()
        if (seatRow != null && seatRow[SeatTable.flightId] == currentFlightId) {
            if (oldSeatId != null && oldSeatId != newSeatId) SeatTable.update({ 
                SeatTable.id eq oldSeatId }) { 
                it[status] = "available" }
            SeatTable.update({ SeatTable.id eq newSeatId }) { 
                it[status] = "occupied" }
            SeatAssignmentTable.update({ 
                SeatAssignmentTable.id eq saRow[SeatAssignmentTable.id] }) { 
                it[seatId] = newSeatId }
        }
    }
}
