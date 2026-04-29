package com.flightbooking.routes

import com.flightbooking.models.BookingSession
import com.flightbooking.models.UserSession
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.FlightTable
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

fun fetchUserId(session: UserSession): Int? =
    UserTable
        .select { UserTable.email eq session.userEmail }
        .limit(1)
        .firstOrNull()
        ?.get(UserTable.id)

fun fetchBookingRows(
    cond: Op<Boolean>,
    origin: Alias<AirportTable>,
    dest: Alias<AirportTable>,
): List<Map<String, Any?>> {
    return BookingTable
        .join(BookingSegmentTable, JoinType.INNER, additionalConstraint = {
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
        .slice(
            BookingTable.id, BookingTable.bookingReference, BookingTable.bookingStatus,
            BookingTable.createdAt, BookingSegmentTable.id, FlightTable.id,
            FlightTable.flightNumber, FlightTable.status,
            FlightTable.scheduledDepartureTime, FlightTable.scheduledArrivalTime,
            origin[AirportTable.iataCode], origin[AirportTable.name],
            dest[AirportTable.iataCode], dest[AirportTable.name],
            SeatTable.seatCode,
        )
        .select { cond }
        .orderBy(BookingTable.createdAt, SortOrder.DESC)
        .map { r -> mapBookingRow(r, origin, dest) }
}

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
    )

fun buildBookingCondition(
    userId: Int,
    q: String,
    qId: Int?,
    statusFilter: String,
): Op<Boolean> {
    println(q)
    var cond: Op<Boolean> = BookingTable.userId eq userId
    if (statusFilter.isNotBlank()) {
        cond = cond and (BookingTable.bookingStatus.lowerCase() eq statusFilter)
    }
    if (qId != null) {
        cond = cond and (BookingTable.id eq qId)
    }
    return cond
}

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
                    items.map { row ->
                        mapOf(
                            "segmentId" to row["segmentId"],
                            "flightNumber" to (row["flightNumber"] ?: ""),
                            "flightStatus" to (row["flightStatus"] ?: ""),
                            "dep" to (row["dep"] ?: ""),
                            "arr" to (row["arr"] ?: ""),
                            "originIata" to row["originIata"],
                            "originName" to row["originName"],
                            "destIata" to row["destIata"],
                            "destName" to row["destName"],
                            "seatCode" to row["seatCode"],
                        )
                    },
            )
        }
