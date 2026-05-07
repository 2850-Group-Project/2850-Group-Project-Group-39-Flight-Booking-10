package com.flightbooking

import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.ChangeRequestTable
import com.flightbooking.tables.ComplaintTable
import com.flightbooking.tables.FareClassTable
import com.flightbooking.tables.FlightFareTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.PassengerTable
import com.flightbooking.tables.SeatAssignmentTable
import com.flightbooking.tables.SeatTable
import com.flightbooking.tables.UserTable
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.assertEquals

/**
 * Creates a client, registers a user, logs them in, and keeps session cookies.
 */
suspend fun ApplicationTestBuilder.createAuthenticatedUserClient(
    email: String = "student@example.com",
    password: String = "Password123!",
): HttpClient {
    val client =
        createClient {
            followRedirects = false
            install(HttpCookies)
        }

    assertEquals(HttpStatusCode.Found, client.registerUser(email, password).status)
    assertEquals(HttpStatusCode.Found, client.loginUser(email, password).status)

    return client
}

/**
 * Creates a client, registers staff, logs them in, and keeps session cookies.
 */
suspend fun ApplicationTestBuilder.createAuthenticatedStaffClient(
    email: String = "staff@example.com",
    password: String = "StrongPass123!",
): HttpClient {
    val client =
        createClient {
            followRedirects = false
            install(HttpCookies)
        }

    assertEquals(HttpStatusCode.Found, client.registerStaff(email, password).status)
    assertEquals(HttpStatusCode.Found, client.loginStaff(email, password).status)

    return client
}

/**
 * Submit a valid user registration form.
 */
suspend fun HttpClient.registerUser(
    email: String = "student@example.com",
    password: String = "Password123!",
) = post("/register") {
    contentType(ContentType.Application.FormUrlEncoded)
    setBody(
        listOf(
            "email" to email,
            "password" to password,
            "confirmPassword" to password,
            "firstName" to "Student",
            "lastName" to "Alex",
        ).formUrlEncode(),
    )
}

/**
 * Submit a valid user login form.
 */
suspend fun HttpClient.loginUser(
    email: String = "student@example.com",
    password: String = "Password123!",
) = post("/login") {
    contentType(ContentType.Application.FormUrlEncoded)
    setBody(
        listOf(
            "email" to email,
            "password" to password,
        ).formUrlEncode(),
    )
}

/**
 * Submit a valid staff registration form.
 */
suspend fun HttpClient.registerStaff(
    email: String = "staff@example.com",
    password: String = "StrongPass123!",
) = submitForm(
    url = "/staff/register",
    formParameters =
        parameters {
            append("firstName", "Alex")
            append("lastName", "Admin")
            append("email", email)
            append("password", password)
            append("confirmPassword", password)
            append("role", "admin")
            append("inviteCode", "STAFF-CHECK")
        },
)

/**
 * Submit a valid staff login form.
 */
suspend fun HttpClient.loginStaff(
    email: String = "staff@example.com",
    password: String = "StrongPass123!",
) = submitForm(
    url = "/staff/login",
    formParameters =
        parameters {
            append("email", email)
            append("password", password)
        },
)

/**
 * Seed a booking session through the flight selection route.
 */
suspend fun HttpClient.seedBookingSession(adults: String = "1") {
    val response =
        submitForm(
            url = "/flights/select",
            formParameters =
                parameters {
                    append("flightId", "10")
                    append("fareId", "20")
                    append("leg", "outbound")
                    append("tripType", "oneway")
                    append("origin", "LHR")
                    append("destination", "DXB")
                    append("departureDate", "2026-04-01")
                    append("adults", adults)
                },
        )

    assertEquals(HttpStatusCode.OK, response.status)
}

/**
 * Insert an airport row for route tests that need valid airport ids.
 */
fun seedAirport(
    iataCode: String,
    name: String,
): Int =
    transaction {
        AirportTable.insert {
            it[AirportTable.iataCode] = iataCode
            it[AirportTable.name] = name
            it[city] = null
            it[country] = null
        }.resultedValues!!.first()[AirportTable.id]
    }

/**
 * Insert a flight row for route tests.
 */
fun seedFlight(
    originAirportId: Int,
    destinationAirportId: Int,
    flightNumberValue: Int = 700,
    capacityValue: Int = 180,
): Int =
    transaction {
        FlightTable.insert {
            it[flightNumber] = flightNumberValue
            it[originAirport] = originAirportId
            it[destinationAirport] = destinationAirportId
            it[scheduledDepartureTime] = "2026-04-10 09:00:00"
            it[scheduledArrivalTime] = "2026-04-10 17:00:00"
            it[status] = "scheduled"
            it[capacity] = capacityValue
        }.resultedValues!!.first()[FlightTable.id]
    }

/**
 * Insert a minimal fare class so a flight fare can reference it.
 */
fun seedFareClass(): Int =
    transaction {
        FareClassTable.insert {
            it[classCode] = "ECON"
            it[cabinClass] = "Economy"
            it[displayName] = "Economy"
            it[refundable] = 0
            it[cancelProtocol] = "Standard policy"
            it[advanceSeatSelection] = 0
            it[priorityCheckin] = 0
            it[priorityBoarding] = 0
            it[loungeAccess] = 0
            it[carryOnAllowed] = 1
            it[carryOnWeightKg] = 10
            it[checkedBaggagePieces] = 0
            it[checkedBaggageWeightKg] = 0
            it[milesEarnRate] = 1.0
            it[minimumMilesForBooking] = null
            it[description] = null
            it[createdAt] = "2026-04-01T00:00:00Z"
            it[updatedAt] = "2026-04-01T00:00:00Z"
        }.resultedValues!!.first()[FareClassTable.id]
    }

/**
 * Insert the flight fare row required by booking routes.
 */
fun seedFlightFare(
    flightId: Int,
    fareClassId: Int,
): Int =
    transaction {
        FlightFareTable.insert {
            it[FlightFareTable.flightId] = flightId
            it[FlightFareTable.fareClassId] = fareClassId
            it[price] = 199.99
            it[currency] = "GBP"
            it[seatsAvailable] = 50
            it[saleStart] = null
            it[saleEnd] = null
        }.resultedValues!!.first()[FlightFareTable.id]
    }

/**
 * Insert a user record for tests that need an existing passenger account.
 */
fun seedUser(
    email: String,
    firstName: String,
    lastName: String,
): Int =
    transaction {
        UserTable.insert {
            it[UserTable.email] = email
            it[passwordHash] = null
            it[UserTable.firstName] = firstName
            it[UserTable.lastName] = lastName
            it[phoneNumber] = null
            it[dateOfBirth] = null
            it[createdAt] = "2026-04-01T00:00:00Z"
            it[accountStatus] = "active"
        }.resultedValues!!.first()[UserTable.id]
    }

/**
 * Read a seeded or registered user id by email.
 */
fun userIdByEmail(email: String = "student@example.com"): Int =
    transaction {
        UserTable
            .select { UserTable.email eq email }
            .limit(1)
            .first()[UserTable.id]
    }

/**
 * Insert a booking row for page and booking-management tests.
 */
fun seedBooking(
    userId: Int,
    bookingReference: String = "TESTREF",
    bookingStatus: String = "confirmed",
): Int =
    transaction {
        BookingTable.insert {
            it[BookingTable.userId] = userId
            it[paymentId] = null
            it[BookingTable.bookingReference] = bookingReference
            it[createdAt] = "2026-04-01T00:00:00Z"
            it[BookingTable.bookingStatus] = bookingStatus
            it[cancelledAt] = null
            it[amendable] = 1
        }.resultedValues!!.first()[BookingTable.id]
    }

/**
 * Insert a passenger row for a booking.
 */
fun seedPassenger(
    bookingId: Int,
    firstName: String = "Pat",
    lastName: String = "Smith",
): Int =
    transaction {
        PassengerTable.insert {
            it[PassengerTable.bookingId] = bookingId
            it[email] = "passenger@example.com"
            it[checkedIn] = 0
            it[title] = "Mx"
            it[PassengerTable.firstName] = firstName
            it[PassengerTable.lastName] = lastName
            it[dateOfBirth] = "2000-01-01"
            it[gender] = null
            it[nationality] = "GB"
            it[documentType] = "Passport"
            it[documentNumber] = "P1234567"
            it[documentCountry] = "GB"
            it[documentExpiry] = "2030-01-01"
        }.resultedValues!!.first()[PassengerTable.id]
    }

/**
 * Insert a booking segment row for a booking and flight.
 */
fun seedBookingSegment(
    bookingId: Int,
    flightId: Int,
    flightFareId: Int,
): Int =
    transaction {
        BookingSegmentTable.insert {
            it[BookingSegmentTable.bookingId] = bookingId
            it[BookingSegmentTable.flightId] = flightId
            it[BookingSegmentTable.flightFareId] = flightFareId
        }.resultedValues!!.first()[BookingSegmentTable.id]
    }

/**
 * Insert a seat assignment row.
 */
fun seedSeatAssignment(
    passengerId: Int,
    bookingSegmentId: Int,
    seatId: Int?,
): Int =
    transaction {
        SeatAssignmentTable.insert {
            it[SeatAssignmentTable.passengerId] = passengerId
            it[SeatAssignmentTable.bookingSegmentId] = bookingSegmentId
            it[SeatAssignmentTable.seatId] = seatId
        }.resultedValues!!.first()[SeatAssignmentTable.id]
    }

/**
 * Insert an available seat row.
 */
fun seedSeat(
    flightId: Int,
    seatCode: String,
): Int =
    transaction {
        SeatTable.insert {
            it[SeatTable.flightId] = flightId
            it[SeatTable.seatCode] = seatCode
            it[cabinClass] = null
            it[position] = null
            it[extraLegroom] = 0
            it[exitRow] = 0
            it[reducedMobility] = 0
            it[status] = "available"
        }.resultedValues!!.first()[SeatTable.id]
    }

/**
 * Fetch the most recently created flight id.
 */
fun latestFlightId(): Int =
    transaction {
        FlightTable
            .selectAll()
            .orderBy(FlightTable.id, SortOrder.DESC)
            .limit(1)
            .first()[FlightTable.id]
    }

/**
 * Fetch the most recently created booking id.
 */
fun latestBookingId(): Int =
    transaction {
        BookingTable
            .selectAll()
            .orderBy(BookingTable.id, SortOrder.DESC)
            .limit(1)
            .first()[BookingTable.id]
    }

/**
 * Read the current status for a booking.
 */
fun bookingStatus(bookingId: Int): String =
    transaction {
        BookingTable
            .select { BookingTable.id eq bookingId }
            .limit(1)
            .first()[BookingTable.bookingStatus]
    }

/**
 * Check whether a booking row still exists.
 */
fun bookingExists(bookingId: Int): Boolean =
    transaction {
        BookingTable
            .select { BookingTable.id eq bookingId }
            .any()
    }

/**
 * Check whether a booking segment row still exists for a booking.
 */
fun bookingSegmentExists(bookingId: Int): Boolean =
    transaction {
        BookingSegmentTable
            .select { BookingSegmentTable.bookingId eq bookingId }
            .any()
    }

/**
 * Check whether a seat assignment row still exists for a segment.
 */
fun seatAssignmentExists(bookingSegmentId: Int): Boolean =
    transaction {
        SeatAssignmentTable
            .select { SeatAssignmentTable.bookingSegmentId eq bookingSegmentId }
            .any()
    }

/**
 * Count submitted change request rows.
 */
fun changeRequestCount(): Int =
    transaction {
        ChangeRequestTable.selectAll().count().toInt()
    }

/**
 * Read the most recently submitted change request.
 */
fun latestChangeRequest(): TestChangeRequest =
    transaction {
        ChangeRequestTable
            .selectAll()
            .orderBy(ChangeRequestTable.id, SortOrder.DESC)
            .limit(1)
            .first()
            .let {
                TestChangeRequest(
                    userId = it[ChangeRequestTable.userId],
                    bookingId = it[ChangeRequestTable.bookingId],
                    bookingSegmentId = it[ChangeRequestTable.bookingSegmentId],
                    currentFlightId = it[ChangeRequestTable.currentFlightId],
                    requestedFlightId = it[ChangeRequestTable.requestedFlightId],
                    requestedSeatId = it[ChangeRequestTable.requestedSeatId],
                    reason = it[ChangeRequestTable.reason],
                    status = it[ChangeRequestTable.status],
                )
            }
    }

/**
 * Insert a complaint row for profile complaint tests.
 */
fun seedComplaint(
    userId: Int,
    type: String = "service",
    message: String = "Flight support was slow",
): Int =
    transaction {
        ComplaintTable.insert {
            it[ComplaintTable.userId] = userId
            it[ComplaintTable.type] = type
            it[ComplaintTable.message] = message
            it[createdAt] = "2026-04-01T00:00:00Z"
            it[status] = "open"
            it[handledByStaffId] = null
        }.resultedValues!!.first()[ComplaintTable.id]
    }

/**
 * Count complaint rows.
 */
fun complaintCount(): Int =
    transaction {
        ComplaintTable.selectAll().count().toInt()
    }

/**
 * Read the most recently submitted complaint.
 */
fun latestComplaint(): TestComplaint =
    transaction {
        ComplaintTable
            .selectAll()
            .orderBy(ComplaintTable.id, SortOrder.DESC)
            .limit(1)
            .first()
            .let {
                TestComplaint(
                    userId = it[ComplaintTable.userId],
                    type = it[ComplaintTable.type],
                    message = it[ComplaintTable.message],
                    status = it[ComplaintTable.status],
                )
            }
    }

/**
 * Read generated seat codes for a flight in creation order.
 */
fun seatCodesForFlight(flightId: Int): List<String> =
    transaction {
        SeatTable
            .select { SeatTable.flightId eq flightId }
            .orderBy(SeatTable.id, SortOrder.ASC)
            .map { it[SeatTable.seatCode] }
    }

/**
 * Read the seat currently assigned to the booking's first segment.
 */
fun assignedSeatIdForBooking(bookingId: Int): Int? =
    transaction {
        val segmentId =
            BookingSegmentTable
                .select { BookingSegmentTable.bookingId eq bookingId }
                .limit(1)
                .first()[BookingSegmentTable.id]

        SeatAssignmentTable
            .select { SeatAssignmentTable.bookingSegmentId eq segmentId }
            .limit(1)
            .firstOrNull()
            ?.get(SeatAssignmentTable.seatId)
    }

/**
 * Read the current flight id from the booking's first segment.
 */
fun segmentFlightIdForBooking(bookingId: Int): Int =
    transaction {
        BookingSegmentTable
            .select { BookingSegmentTable.bookingId eq bookingId }
            .limit(1)
            .first()[BookingSegmentTable.flightId]
    }

/**
 * Read the current seat status after reassignment operations.
 */
fun seatStatus(seatId: Int): String =
    transaction {
        SeatTable
            .select { SeatTable.id eq seatId }
            .limit(1)
            .first()[SeatTable.status]
    }

/**
 * Data class definition for change request testing
 */
data class TestChangeRequest(
    val userId: Int,
    val bookingId: Int,
    val bookingSegmentId: Int,
    val currentFlightId: Int?,
    val requestedFlightId: Int?,
    val requestedSeatId: Int?,
    val reason: String?,
    val status: String,
)

/**
 * Data class definition for complaint testing
 */
data class TestComplaint(
    val userId: Int?,
    val type: String?,
    val message: String?,
    val status: String,
)
