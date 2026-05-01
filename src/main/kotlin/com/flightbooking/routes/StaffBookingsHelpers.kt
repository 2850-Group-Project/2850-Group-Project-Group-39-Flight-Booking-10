package com.flightbooking.routes

import com.flightbooking.models.StaffSession
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.PassengerTable
import com.flightbooking.tables.SeatAssignmentTable
import com.flightbooking.tables.SeatTable
import com.flightbooking.tables.StaffTable
import com.flightbooking.tables.UserTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

private const val BOOKING_REF_LENGTH = 10
private const val BOOKING_LIST_LIMIT = 50
private const val FLIGHT_LIST_LIMIT = 50

/**
 * Data class to pass parameters into createFullBooking
 */
data class FullBookingInput(
    val passengerEmail: String,
    val passengerFirstName: String?,
    val passengerLastName: String?,
    val flightId: Int,
    val bookingStatus: String,
    val seatId: Int?,
)

/**
 * Function to create a whole booking, creating and inserting into 
 * booking table, passenger table, booking segment table, seat assignment table
 * and updating it so that foreign keys match
 * @param input booking input
 * @return error message or null
 */
fun createFullBooking(input: FullBookingInput): String? {
    var errMsg: String? = null
    transaction {
        val userRow = UserTable.select { UserTable.email eq input.passengerEmail }.limit(1).firstOrNull()
        if (userRow == null) {
            errMsg = "No user found for this email"
            return@transaction
        }
        val bookingId =
            BookingTable.insert {
                it[BookingTable.bookingReference] = "BK-" +
                    UUID.randomUUID()
                        .toString().replace("-", "").take(BOOKING_REF_LENGTH).uppercase()
                it[BookingTable.paymentId] = null
                it[BookingTable.createdAt] = Instant.now().toString()
                it[BookingTable.bookingStatus] = input.bookingStatus
                it[BookingTable.cancelledAt] = null
                it[BookingTable.amendable] = 1
                it[BookingTable.userId] = userRow[UserTable.id]
            } get BookingTable.id
        val passengerId =
            PassengerTable.insert {
                it[PassengerTable.bookingId] = bookingId
                it[PassengerTable.email] = input.passengerEmail
                it[PassengerTable.checkedIn] = 0
                it[PassengerTable.firstName] = input.passengerFirstName
                it[PassengerTable.lastName] = input.passengerLastName
                it[PassengerTable.title] = null
                it[PassengerTable.dateOfBirth] = null
                it[PassengerTable.gender] = null
                it[PassengerTable.nationality] = null
                it[PassengerTable.documentType] = null
                it[PassengerTable.documentNumber] = null
                it[PassengerTable.documentCountry] = null
                it[PassengerTable.documentExpiry] = null
            } get PassengerTable.id

        val segmentId =
            BookingSegmentTable.insert {
                it[BookingSegmentTable.bookingId] = bookingId
                it[BookingSegmentTable.flightId] = input.flightId
                it[BookingSegmentTable.flightFareId] = 1
            } get BookingSegmentTable.id

        val seatAssignmentId =
            SeatAssignmentTable.insert {
                it[SeatAssignmentTable.passengerId] = passengerId
                it[SeatAssignmentTable.bookingSegmentId] = segmentId
                it[SeatAssignmentTable.seatId] = null
            } get SeatAssignmentTable.id

        if (input.seatId != null) assignSeatIfAvailable(seatAssignmentId, input)
    }
    return errMsg
}

/**
 * Assigns a seat to an existing seat‑assignment record if the seat is valid
 * and still currently available.
 * @param seatAssignmentId assignment id
 * @param input booking input
 */
private fun assignSeatIfAvailable(
    seatAssignmentId: Int,
    input: FullBookingInput,
) {
    val seatRow =
        SeatTable
            .select { SeatTable.id eq input.seatId!! }
            .limit(1)
            .firstOrNull()
    if (seatRow != null &&
        seatRow[SeatTable.flightId] == input.flightId &&
        seatRow[SeatTable.status] == "available"
    ) {
        SeatTable.update({ SeatTable.id eq input.seatId!! }) { it[SeatTable.status] = "occupied" }
        SeatAssignmentTable.update({ SeatAssignmentTable.id eq seatAssignmentId }) {
            it[SeatAssignmentTable.seatId] = input.seatId
        }
    }
}

/**
 * Builds the model used for the staff dashboard page.
 * @param session staff session
 * @param q search text
 * @return dashboard model
 */
fun fetchStaffModel(
    session: StaffSession,
    q: String,
): Map<String, Any> =
    transaction {
        val staffRow =
            StaffTable.select { StaffTable.email eq session.staffEmail }.limit(1).firstOrNull()
                ?: return@transaction mapOf("error" to "Staff not found, please login again.")

        val staffName =
            listOfNotNull(staffRow[StaffTable.firstName], staffRow[StaffTable.lastName])
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
            "bookings" to bookingsList,
        )
    }

/**
 * Searches for and returns a list of flights, based on origin and destination
 * @return flights list
 */
private fun fetchFlights(): List<Map<String, Any>> {
    val origin = AirportTable.alias("origin")
    val dest = AirportTable.alias("dest")
    return FlightTable
        .join(origin, JoinType.INNER, additionalConstraint = { FlightTable.originAirport eq origin[AirportTable.id] })
        .join(dest, JoinType.INNER, additionalConstraint = { FlightTable.destinationAirport eq dest[AirportTable.id] })
        .slice(
            FlightTable.id,
            FlightTable.flightNumber,
            FlightTable.status,
            FlightTable.scheduledDepartureTime,
            FlightTable.scheduledArrivalTime,
            origin[AirportTable.iataCode],
            dest[AirportTable.iataCode],
        )
        .selectAll()
        .orderBy(FlightTable.id, SortOrder.DESC)
        .limit(FLIGHT_LIST_LIMIT)
        .map { r ->
            val flightNo = r[FlightTable.flightNumber]?.toString() ?: r[FlightTable.id].toString()
            val label =
                "${r[origin[AirportTable.iataCode]]} → " +
                    "${r[dest[AirportTable.iataCode]]} | " +
                    "$flightNo | ${r[FlightTable.status]}"
            mapOf(
                "id" to r[FlightTable.id],
                "label" to label,
            )
        }
}

/**
 * Searches for and returns a list of bookings for the staff dashboard
 * @param q search text
 * @return bookings list
 */
private fun fetchBookings(q: String): List<Map<String, Any?>> =
    BookingTable
        .join(
            PassengerTable,
            JoinType.LEFT,
            additionalConstraint = { PassengerTable.bookingId eq BookingTable.id },
        )
        .join(
            BookingSegmentTable,
            JoinType.LEFT,
            additionalConstraint = { BookingSegmentTable.bookingId eq BookingTable.id },
        )
        .join(
            FlightTable,
            JoinType.LEFT,
            additionalConstraint = { FlightTable.id eq BookingSegmentTable.flightId },
        )
        .join(
            SeatAssignmentTable,
            JoinType.LEFT,
            additionalConstraint = { SeatAssignmentTable.bookingSegmentId eq BookingSegmentTable.id },
        )
        .join(
            SeatTable,
            JoinType.LEFT,
            additionalConstraint = { SeatTable.id eq SeatAssignmentTable.seatId },
        )
        .slice(
            BookingTable.id, BookingTable.bookingReference, BookingTable.bookingStatus, BookingTable.createdAt,
            PassengerTable.id, PassengerTable.title, PassengerTable.firstName,
            PassengerTable.lastName, PassengerTable.email,
            BookingSegmentTable.id, BookingSegmentTable.flightId,
            SeatAssignmentTable.id, SeatAssignmentTable.seatId, SeatTable.seatCode,
        )
        .select {
            if (q.isBlank()) {
                Op.TRUE
            } else {
                q.toIntOrNull()?.let { BookingTable.id eq it } ?: Op.FALSE
            }
        }
        .orderBy(BookingTable.id, SortOrder.DESC)
        .limit(BOOKING_LIST_LIMIT)
        .map { r -> mapBookingRow(r) }

/**
 * Searches for and returns a list of bookings for the staff dashboard
 * @param r result row
 * @return mapped booking
 */
private fun mapBookingRow(r: ResultRow): Map<String, Any?> {
    val passengerName =
        listOfNotNull(
            r[PassengerTable.title],
            r[PassengerTable.firstName],
            r[PassengerTable.lastName],
        ).joinToString(" ").ifBlank { "" }
    return mapOf(
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
        "seatCode" to r.getOrNull(SeatTable.seatCode),
    )
}

/**
 * Searches for and returns seats for bookings, grouping by flight ID
 * @param bookingsList list of bookings
 * @return seats grouped by flight
 */
private fun fetchSeatsByFlight(bookingsList: List<Map<String, Any?>>): Map<Int, List<Map<String, Any>>> {
    val flightIds = bookingsList.mapNotNull { it["flightId"] as? Int }.distinct()
    if (flightIds.isEmpty()) return emptyMap()
    return SeatTable.select { SeatTable.flightId inList flightIds }
        .map { r ->
            r[SeatTable.flightId] to
                mapOf(
                    "id" to r[SeatTable.id],
                    "seatCode" to r[SeatTable.seatCode],
                    "status" to r[SeatTable.status],
                )
        }
        .groupBy({ it.first }, { it.second })
}

/**
 * Updates the corresponding booking segment with new flight id and new seat id,
 * searches with inputted segId and segRow
 * @param segId segment id
 * @param segRow segment row
 * @param newFlightId new flight id
 * @param newSeatId new seat id
 */
fun updateBookingSegment(
    segId: Int,
    segRow: org.jetbrains.exposed.sql.ResultRow,
    newFlightId: Int,
    newSeatId: Int?,
) {
    val currentFlightId = segRow[BookingSegmentTable.flightId]
    if (currentFlightId != newFlightId) {
        clearSeatAssignment(segId)
        BookingSegmentTable.update({ BookingSegmentTable.id eq segId }) { it[flightId] = newFlightId }
    } else {
        updateSeatAssignment(segId, currentFlightId, newSeatId)
    }
}

/**
 * Clears the seat assignment for the given booking segment, making it available
 * @param segId segment id
 */
private fun clearSeatAssignment(segId: Int) {
    val saRow =
        SeatAssignmentTable
            .select { SeatAssignmentTable.bookingSegmentId eq segId }
            .limit(1)
            .firstOrNull()
    val oldSeatId = saRow?.get(SeatAssignmentTable.seatId)
    if (oldSeatId != null) {
        SeatTable.update({
            SeatTable.id eq oldSeatId
        }) {
            it[status] = "available"
        }
    }
    if (saRow != null) {
        SeatAssignmentTable.update({
            SeatAssignmentTable.id eq saRow[SeatAssignmentTable.id]
        }) {
            it[seatId] = null
        }
    }
}

/**
 * Updates a seat assignment with new SeatId and status
 * @param segId segment id
 * @param currentFlightId flight id
 * @param newSeatId new seat id
 */
private fun updateSeatAssignment(
    segId: Int,
    currentFlightId: Int,
    newSeatId: Int?,
) {
    val saRow =
        SeatAssignmentTable.select {
            SeatAssignmentTable.bookingSegmentId eq segId
        }
            .limit(1)
            .firstOrNull() ?: return
    val oldSeatId = saRow[SeatAssignmentTable.seatId]
    if (newSeatId == null) {
        if (oldSeatId != null) SeatTable.update({ SeatTable.id eq oldSeatId }) { it[status] = "available" }
        SeatAssignmentTable.update({ SeatAssignmentTable.id eq saRow[SeatAssignmentTable.id] }) { it[seatId] = null }
    } else {
        val seatRow =
            SeatTable
                .select { SeatTable.id eq newSeatId }
                .limit(1)
                .firstOrNull()
        if (seatRow != null && seatRow[SeatTable.flightId] == currentFlightId) {
            if (oldSeatId != null && oldSeatId != newSeatId) {
                SeatTable.update({
                    SeatTable.id eq oldSeatId
                }) {
                    it[status] = "available"
                }
            }
            SeatTable.update({ SeatTable.id eq newSeatId }) {
                it[status] = "occupied"
            }
            SeatAssignmentTable.update({
                SeatAssignmentTable.id eq saRow[SeatAssignmentTable.id]
            }) {
                it[seatId] = newSeatId
            }
        }
    }
}
