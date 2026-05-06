package com.flightbooking.routes

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.flightbooking.models.BookingSession
import com.flightbooking.models.UserSession
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.PassengerTable
import com.flightbooking.tables.SeatAssignmentTable
import com.flightbooking.tables.SeatTable
import com.flightbooking.tables.UserTable
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.routing.get
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import org.jetbrains.exposed.sql.Alias
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Helper function to handle redirects from invalid UserSession or BookingSession
 * @param call request call
 * @param bookingSession booking session
 * @return redirect URL or null
 */
fun resolveGetFlightRedirects(
    call: ApplicationCall,
    bookingSession: BookingSession?,
): String? =
    when {
        call.sessions.get<UserSession>() == null -> "/login"
        bookingSession == null -> "/home"
        bookingSession.search == null -> "/home"
        else -> null
    }

/**
 * Searches User table for record with email matching UserSession's email and returns it, null if not found
 * @param session user session
 * @return user id or null
 */
fun fetchUserId(session: UserSession): Int? =
    transaction {
        UserTable
            .select { UserTable.email eq session.userEmail }
            .limit(1)
            .firstOrNull()
            ?.get(UserTable.id)
    }

/**
 * Fetches all booking rows matching the given condition, including joined flight,
 * airport, and seat information, and returns them as a list of structured maps.
 * @param cond filter condition
 * @param origin origin alias
 * @param dest destination alias
 * @return list of booking rows
 */
fun fetchBookingRows(
    cond: Op<Boolean>,
    origin: Alias<AirportTable>,
    dest: Alias<AirportTable>,
): List<Map<String, Any?>> {
    return BookingTable
        // Used Claude AI to debug incorrect JoinType on line 74 (JoinType.INNER -> JoinType.LEFT)
        .join(BookingSegmentTable, JoinType.LEFT, additionalConstraint = {
            BookingSegmentTable.bookingId eq BookingTable.id
        })
        .join(FlightTable, JoinType.LEFT, additionalConstraint = {
            FlightTable.id eq BookingSegmentTable.flightId
        })
        .join(origin, JoinType.LEFT, additionalConstraint = {
            FlightTable.originAirport eq origin[AirportTable.id]
        })
        .join(dest, JoinType.LEFT, additionalConstraint = {
            FlightTable.destinationAirport eq dest[AirportTable.id]
        })
        .join(SeatAssignmentTable, JoinType.LEFT, additionalConstraint = {
            SeatAssignmentTable.bookingSegmentId eq BookingSegmentTable.id
        })
        .join(SeatTable, JoinType.LEFT, additionalConstraint = {
            SeatTable.id eq SeatAssignmentTable.seatId
        })
        // Used Claude AI to generate the joins for the passenger table and booking table, lines 93-106
        .join(PassengerTable, JoinType.LEFT, additionalConstraint = {
            PassengerTable.id eq SeatAssignmentTable.passengerId
        })
        .slice(
            BookingTable.id, BookingTable.bookingReference, BookingTable.bookingStatus,
            BookingTable.createdAt, BookingSegmentTable.id, FlightTable.id,
            FlightTable.flightNumber, FlightTable.status,
            FlightTable.scheduledDepartureTime, FlightTable.scheduledArrivalTime,
            origin[AirportTable.iataCode], origin[AirportTable.name],
            dest[AirportTable.iataCode], dest[AirportTable.name],
            SeatTable.seatCode,
            PassengerTable.id, PassengerTable.firstName, PassengerTable.lastName,
            PassengerTable.dateOfBirth, PassengerTable.nationality, PassengerTable.documentType,
            PassengerTable.documentNumber, PassengerTable.checkedIn,
        )
        .select { cond }
        .orderBy(BookingTable.createdAt, SortOrder.DESC)
        .map { r -> mapBookingRow(r, origin, dest) }
}

/**
 * Maps a single joined booking/segment/flight/airport/seat row into a
 * structured map used by the bookings page
 * @param r result row
 * @param origin origin alias
 * @param dest destination alias
 * @return mapped booking row
 */
fun mapBookingRow(
    r: ResultRow,
    origin: Alias<AirportTable>,
    dest: Alias<AirportTable>,
): Map<String, Any?> =
    mapOf(
        "bookingId" to r[BookingTable.id],
        "bookingReference" to r[BookingTable.bookingReference],
        "bookingStatus" to r[BookingTable.bookingStatus],
        "createdAt" to r[BookingTable.createdAt],
        "segmentId" to r[BookingSegmentTable.id],
        "flightNumber" to (r.getOrNull(FlightTable.flightNumber)?.toString() ?: ""),
        "flightStatus" to (r.getOrNull(FlightTable.status) ?: ""),
        "dep" to (r.getOrNull(FlightTable.scheduledDepartureTime) ?: ""),
        "arr" to (r.getOrNull(FlightTable.scheduledArrivalTime) ?: ""),
        "originIata" to r.getOrNull(origin[AirportTable.iataCode]),
        "originName" to r.getOrNull(origin[AirportTable.name]),
        "destIata" to r.getOrNull(dest[AirportTable.iataCode]),
        "destName" to r.getOrNull(dest[AirportTable.name]),
        "seatCode" to r.getOrNull(SeatTable.seatCode),
        "passengerId" to r.getOrNull(PassengerTable.id),
        "passengerFirstName" to r.getOrNull(PassengerTable.firstName),
        "passengerLastName" to r.getOrNull(PassengerTable.lastName),
        "passengerDob" to r.getOrNull(PassengerTable.dateOfBirth),
        "passengerNationality" to r.getOrNull(PassengerTable.nationality),
        "passengerDocType" to r.getOrNull(PassengerTable.documentType),
        "passengerDocNumber" to r.getOrNull(PassengerTable.documentNumber),
        "passengerCheckedIn" to r.getOrNull(PassengerTable.checkedIn),
    )

/**
 * Helper function that creates cond, which is a filtered BookingTable
 * @param userId user id
 * @param qId parsed search id
 * @param statusFilter status filter
 * @return booking condition
 */
fun buildBookingCondition(
    userId: Int,
    qId: Int?,
    statusFilter: String,
): Op<Boolean> {
    var cond: Op<Boolean> = BookingTable.userId eq userId
    if (qId != null) {
        cond = cond and (BookingTable.id eq qId)
    }
    if (statusFilter.isNotBlank()) {
        cond = cond and (BookingTable.bookingStatus.lowerCase() eq statusFilter)
    }
    return cond
}

/**
 * Groups flat booking/segment rows into a hierarchical booking structure
 * @param rows flat booking rows
 * @return grouped bookings
 */
fun groupIntoBookings(rows: List<Map<String, Any?>>): List<Map<String, Any?>> =
    rows.groupBy { it["bookingId"] as Int }
        .map { (bid, items) ->
            val first = items.first()
            mapOf(
                "bookingId" to bid,
                "bookingReference" to (first["bookingReference"] ?: ""),
                "bookingStatus" to (first["bookingStatus"] ?: ""),
                "createdAt" to (first["createdAt"] ?: ""),
                "segments" to
                    items.groupBy { it["segmentId"] }
                        .map { (_, segRows) ->
                            val seg = segRows.first()
                            val passengers =
                                segRows
                                    .filter { it["passengerId"] != null }
                                    .map { p ->
                                        mapOf(
                                            "id" to p["passengerId"],
                                            "firstName" to p["passengerFirstName"],
                                            "lastName" to p["passengerLastName"],
                                            "dob" to p["passengerDob"],
                                            "nationality" to p["passengerNationality"],
                                            "docType" to p["passengerDocType"],
                                            "docNumber" to p["passengerDocNumber"],
                                            "checkedIn" to p["passengerCheckedIn"],
                                            "seatCode" to p["seatCode"],
                                        )
                                    }

                            mapOf(
                                "segmentId" to seg["segmentId"],
                                "flightNumber" to (seg["flightNumber"] ?: ""),
                                "flightStatus" to (seg["flightStatus"] ?: ""),
                                "dep" to (seg["dep"] ?: ""),
                                "arr" to (seg["arr"] ?: ""),
                                "originIata" to seg["originIata"],
                                "originName" to seg["originName"],
                                "destIata" to seg["destIata"],
                                "destName" to seg["destName"],
                                "seatCode" to seg["seatCode"],
                                "passengers" to jacksonObjectMapper().writeValueAsString(passengers),
                            )
                        },
            )
        }

/**
 * Filters booking rows by airport search query against origin/dest iata, city, and name
 * @param rows flat booking rows
 * @param qSearchQuery search query
 * @return filtered rows
 */
fun filterRowsByAirport(
    rows: List<Map<String, Any?>>,
    qSearchQuery: String,
): List<Map<String, Any?>> {
    if (qSearchQuery.isBlank()) return rows
    val pattern = qSearchQuery.lowercase()

    return rows.filter { row ->
        listOf(
            row["originIata"],
            row["originName"],
            row["destIata"],
            row["destName"],
        ).any { it?.toString()?.lowercase()?.contains(pattern) == true }
    }
}
