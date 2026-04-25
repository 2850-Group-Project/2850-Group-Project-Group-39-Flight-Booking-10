package com.flightbooking.routes

import com.flightbooking.models.StaffSession
import com.flightbooking.tables.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Op
import io.ktor.server.sessions.*
import org.jetbrains.exposed.sql.*
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
fun Route.staffBookingsRoutes() {

    get("/staff/bookings") {
        val session = call.sessions.get<StaffSession>()
        if (session == null) {
            call.respondRedirect("/staff/login")
            return@get
        }
        val q = call.request.queryParameters["q"]?.trim().orEmpty()
        val model = transaction {
            val staffRow = StaffTable.select { StaffTable.email eq session.staffEmail }.limit(1).firstOrNull()
            if (staffRow == null) {
                return@transaction mapOf<String, Any>("error" to "Staff not found, please login again.")
            }

            val staffName = listOfNotNull(staffRow[StaffTable.firstName], staffRow[StaffTable.lastName]).joinToString(" ").ifBlank { "Staff" }
            val staffRole = staffRow[StaffTable.role] ?: "Staff"

            val origin = AirportTable.alias("origin")
            val dest = AirportTable.alias("dest")

            val flights = (FlightTable
                .join(origin, JoinType.INNER, additionalConstraint = { FlightTable.originAirport eq origin[AirportTable.id] })
                .join(dest, JoinType.INNER, additionalConstraint = { FlightTable.destinationAirport eq dest[AirportTable.id] })
                .slice(
                    FlightTable.id,
                    FlightTable.flightNumber,
                    FlightTable.status,
                    FlightTable.scheduledDepartureTime,
                    FlightTable.scheduledArrivalTime,
                    origin[AirportTable.iataCode],
                    dest[AirportTable.iataCode]
                )
                .selectAll()
                .orderBy(FlightTable.id, SortOrder.DESC)
                .limit(300)
                .orderBy(FlightTable.id, SortOrder.DESC)
                .map { r ->
                    val fid = r[FlightTable.id]
                    val flightNo = r[FlightTable.flightNumber]?.toString() ?: fid.toString()
                    val label = "${r[origin[AirportTable.iataCode]]} → ${r[dest[AirportTable.iataCode]]} | $flightNo | ${r[FlightTable.status]}"
                    mapOf(
                        "id" to fid,
                        "label" to label
                    )
                })

            val bookingsList = (BookingTable
                .join(PassengerTable, JoinType.LEFT, additionalConstraint = { PassengerTable.bookingId eq BookingTable.id })
                .join(BookingSegmentTable, JoinType.LEFT, additionalConstraint = { BookingSegmentTable.bookingId eq BookingTable.id })
                .join(FlightTable, JoinType.LEFT, additionalConstraint = { FlightTable.id eq BookingSegmentTable.flightId })
                .join(SeatAssignmentTable, JoinType.LEFT, additionalConstraint = { SeatAssignmentTable.bookingSegmentId eq BookingSegmentTable.id })
                .join(SeatTable, JoinType.LEFT, additionalConstraint = { SeatTable.id eq SeatAssignmentTable.seatId })
                .slice(
                    BookingTable.id,
                    BookingTable.bookingReference,
                    BookingTable.bookingStatus,
                    BookingTable.createdAt,
                    PassengerTable.id,
                    PassengerTable.title,
                    PassengerTable.firstName,
                    PassengerTable.lastName,
                    PassengerTable.email,
                    BookingSegmentTable.id,
                    BookingSegmentTable.flightId,
                    SeatAssignmentTable.id,
                    SeatAssignmentTable.seatId,
                    SeatTable.seatCode
                )
                .select {
                    if (q.isBlank()) Op.TRUE
                    else {
                        val id = q.toIntOrNull()
                        if (id == null) Op.FALSE else BookingTable.id eq id
                    }
                }
                .orderBy(BookingTable.id, SortOrder.DESC)
                .limit(300)
                .map { r ->
                    val bookingId = r[BookingTable.id]
                    val passengerName = listOfNotNull(r[PassengerTable.title], r[PassengerTable.firstName], r[PassengerTable.lastName]).joinToString(" ").ifBlank { "" }
                    val flightId = r.getOrNull(BookingSegmentTable.flightId)
                    val seatId = r.getOrNull(SeatAssignmentTable.seatId)
                    val seatCode = r.getOrNull(SeatTable.seatCode)

                    mapOf(
                        "bookingId" to bookingId,
                        "bookingReference" to r[BookingTable.bookingReference],
                        "bookingStatus" to r[BookingTable.bookingStatus],
                        "createdAt" to r[BookingTable.createdAt],
                        "passengerId" to r.getOrNull(PassengerTable.id),
                        "passengerName" to passengerName,
                        "passengerEmail" to r.getOrNull(PassengerTable.email),
                        "segmentId" to r.getOrNull(BookingSegmentTable.id),
                        "flightId" to flightId,
                        "seatAssignmentId" to r.getOrNull(SeatAssignmentTable.id),
                        "seatId" to seatId,
                        "seatCode" to seatCode
                    )
                })

            val flightIdsInBookings = bookingsList.mapNotNull { it["flightId"] as Int? }.distinct()
            val seatsByFlight: Map<Int, List<Map<String, Any>>> =
                if (flightIdsInBookings.isEmpty()) emptyMap()
                else SeatTable
                    .select { SeatTable.flightId inList flightIdsInBookings }
                    .map { r ->
                        val fid = r[SeatTable.flightId]
                        fid to mapOf(
                            "id" to r[SeatTable.id],
                            "seatCode" to r[SeatTable.seatCode],
                            "status" to r[SeatTable.status]
                        )
                    }
        .groupBy({ it.first }, { it.second })

            mapOf(
                "staffName" to staffName,
                "staffRole" to staffRole,
                "flights" to flights,
                "q" to q,
                "seatsByFlight" to seatsByFlight,
                "bookings" to bookingsList
            )
        }

        if (model.containsKey("error")) {
            call.respondText(model["error"].toString())
            return@get
        }

        call.respond(PebbleContent("staff_bookings.peb", model))
    }

    post("/staff/bookings/update") {
        val session = call.sessions.get<StaffSession>()
        if (session == null) {
            call.respondRedirect("/staff/login")
            return@post
        }

        val params = call.receiveParameters()
        val bookingId = params["bookingId"]?.toIntOrNull()
        val newStatus = params["bookingStatus"]?.trim()
        val newFlightId = params["flightId"]?.toIntOrNull()
        val newSeatId = params["seatId"]?.toIntOrNull()

        if (bookingId == null) {
            call.respondRedirect("/staff/bookings")
            return@post
        }

        transaction {
            if (!newStatus.isNullOrBlank()) {
                BookingTable.update({ BookingTable.id eq bookingId }) {
                    it[bookingStatus] = newStatus
                }
            }

            val segRow = BookingSegmentTable.select { BookingSegmentTable.bookingId eq bookingId }.limit(1).firstOrNull()
            val segId = segRow?.get(BookingSegmentTable.id)

            if (segId != null && newFlightId != null) {
                val currentFlightId = segRow[BookingSegmentTable.flightId]
                val flightChanged = currentFlightId != newFlightId

                if (flightChanged) {
                    val saRow = SeatAssignmentTable.select { SeatAssignmentTable.bookingSegmentId eq segId }.limit(1).firstOrNull()
                    val oldSeatId = saRow?.get(SeatAssignmentTable.seatId)

                    if (oldSeatId != null) {
                        SeatTable.update({ SeatTable.id eq oldSeatId }) {
                            it[status] = "available"
                        }
                    }

                    if (saRow != null) {
                        SeatAssignmentTable.update({ SeatAssignmentTable.id eq saRow[SeatAssignmentTable.id] }) {
                            it[seatId] = null
                        }
                    }

                    BookingSegmentTable.update({ BookingSegmentTable.id eq segId }) {
                        it[flightId] = newFlightId
                    }
                } else {
                    val saRow = SeatAssignmentTable.select { SeatAssignmentTable.bookingSegmentId eq segId }.limit(1).firstOrNull()
                    if (saRow != null) {
                        val oldSeatId = saRow[SeatAssignmentTable.seatId]
                        if (newSeatId == null) {
                            if (oldSeatId != null) {
                                SeatTable.update({ SeatTable.id eq oldSeatId }) { it[status] = "available" }
                            }
                            SeatAssignmentTable.update({ SeatAssignmentTable.id eq saRow[SeatAssignmentTable.id] }) { it[seatId] = null }
                        } else {
                            val seatRow = SeatTable.select { SeatTable.id eq newSeatId }.limit(1).firstOrNull()
                            val seatOk = seatRow != null && seatRow[SeatTable.flightId] == currentFlightId

                            if (seatOk) {
                                if (oldSeatId != null && oldSeatId != newSeatId) {
                                    SeatTable.update({ SeatTable.id eq oldSeatId }) { it[status] = "available" }
                                }
                                SeatTable.update({ SeatTable.id eq newSeatId }) { it[status] = "occupied" }
                                SeatAssignmentTable.update({ SeatAssignmentTable.id eq saRow[SeatAssignmentTable.id] }) { it[seatId] = newSeatId }
                            }
                        }
                    }
                }
            }
        }

        call.respondRedirect("/staff/bookings")
    }

    post("/staff/bookings/create") {
        val session = call.sessions.get<StaffSession>()
        if (session == null) {
            call.respondRedirect("/staff/login")
            return@post
        }

        val params = call.receiveParameters()
        val passengerEmail = params["passengerEmail"]?.trim().orEmpty()
        val passengerFirstName = params["passengerFirstName"]?.trim()
        val passengerLastName = params["passengerLastName"]?.trim()
        val flightId = params["flightId"]?.toIntOrNull()
        val bookingStatus = params["bookingStatus"]?.trim().orEmpty()
        val seatId = params["seatId"]?.toIntOrNull()

        if (passengerEmail.isBlank() || flightId == null || bookingStatus.isBlank()) {
            call.respondRedirect("/staff/bookings")
            return@post
        }
        var errMsg: String? = null

        transaction {
            val userRow = UserTable
                .select { UserTable.email eq passengerEmail }
                .limit(1)
                .firstOrNull()
            if (userRow == null) {
                errMsg="No user found for this email"
                return@transaction
            }

            val userIdOrNull = userRow[UserTable.id]
            val bookingRef = "BK-" + UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
            val createdAtStr = Instant.now().toString()

            val bookingId = BookingTable.insert {
                it[BookingTable.bookingReference] = bookingRef
                it[BookingTable.paymentId] = null
                it[BookingTable.createdAt] = createdAtStr
                it[BookingTable.bookingStatus] = bookingStatus
                it[BookingTable.cancelledAt] = null
                it[BookingTable.amendable] = 1
                it[BookingTable.userId] = userIdOrNull
            } get BookingTable.id

            val passengerId = PassengerTable.insert {
                it[PassengerTable.bookingId] = bookingId
                it[PassengerTable.email] = passengerEmail
                it[PassengerTable.checkedIn] = 0
                it[PassengerTable.title] = null
                it[PassengerTable.firstName] = passengerFirstName
                it[PassengerTable.lastName] = passengerLastName
                it[PassengerTable.dateOfBirth] = null
                it[PassengerTable.gender] = null
                it[PassengerTable.nationality] = null
                it[PassengerTable.documentType] = null
                it[PassengerTable.documentNumber] = null
                it[PassengerTable.documentCountry] = null
                it[PassengerTable.documentExpiry] = null
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
                val seatRow = SeatTable.select { SeatTable.id eq seatId }.limit(1).firstOrNull()
                val ok = seatRow != null &&
                        seatRow[SeatTable.flightId] == flightId &&
                        seatRow[SeatTable.status] == "available"

                if (ok) {
                    SeatTable.update({ SeatTable.id eq seatId }) { it[SeatTable.status] = "occupied" }
                    SeatAssignmentTable.update({ SeatAssignmentTable.id eq seatAssignmentId }) { it[SeatAssignmentTable.seatId] = seatId }
                }
            }
        }
        if (errMsg != null) {
            call.respondRedirect("/staff/bookings?error=$errMsg")
            return@post
        }
        call.respondRedirect("/staff/bookings")
    }
}