package com.flightbooking

import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.FareClassTable
import com.flightbooking.tables.FlightFareTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.SeatAssignmentTable
import com.flightbooking.tables.SeatTable
import com.flightbooking.tables.UserTable
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

class StaffBookingsRoutesTest : IntegrationTestSupport() {
    @Test
    // Staff should be able to create a booking from the bookings management page.
    fun createBookingRedirectsWithSuccessMessage() = testApplication {
        configureApp()
        val client = createClient {
            followRedirects = false
            install(HttpCookies)
        }
        client.get("/__health")

        val originAirportId = seedAirport("LHR", "London Heathrow")
        val destinationAirportId = seedAirport("DXB", "Dubai International")
        val flightId = seedFlight(originAirportId, destinationAirportId)
        val fareClassId = seedFareClass()
        seedFlightFare(flightId, fareClassId)
        seedUser("passenger@example.com", "Pat", "Smith")

        client.registerStaff()
        val loginResponse = client.loginStaff()
        assertEquals(HttpStatusCode.Found, loginResponse.status)
        assertEquals("/staff/dashboard", loginResponse.headers[HttpHeaders.Location])

        val response = client.submitForm(
            url = "/staff/bookings/create",
            formParameters = parameters {
                append("passengerEmail", "passenger@example.com")
                append("passengerFirstName", "Pat")
                append("passengerLastName", "Smith")
                append("flightId", flightId.toString())
                append("bookingStatus", "confirmed")
            }
        )

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/staff/bookings", response.headers[HttpHeaders.Location])
    }

    @Test
    // Staff should be able to update a booking status successfully.
    fun updateBookingStatusRedirectsWithSuccessMessage() = testApplication {
        configureApp()
        val client = createClient {
            followRedirects = false
            install(HttpCookies)
        }
        client.get("/__health")

        val originAirportId = seedAirport("LHR", "London Heathrow")
        val destinationAirportId = seedAirport("DXB", "Dubai International")
        val flightId = seedFlight(originAirportId, destinationAirportId)
        val fareClassId = seedFareClass()
        seedFlightFare(flightId, fareClassId)
        seedUser("passenger@example.com", "Pat", "Smith")

        client.registerStaff()
        val loginResponse = client.loginStaff()
        assertEquals(HttpStatusCode.Found, loginResponse.status)

        val createResponse = client.submitForm(
            url = "/staff/bookings/create",
            formParameters = parameters {
                append("passengerEmail", "passenger@example.com")
                append("passengerFirstName", "Pat")
                append("passengerLastName", "Smith")
                append("flightId", flightId.toString())
                append("bookingStatus", "pending")
            }
        )
        assertEquals(HttpStatusCode.Found, createResponse.status)

        val bookingId = latestBookingId()
        val response = client.submitForm(
            url = "/staff/bookings/update",
            formParameters = parameters {
                append("bookingId", bookingId.toString())
                append("bookingStatus", "confirmed")
            }
        )

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/staff/bookings", response.headers[HttpHeaders.Location])
    }

    @Test
    // Staff should be able to change the seat assignment for a booking.
    fun changeSeatAssignmentRedirectsWithSuccessMessage() = testApplication {
        configureApp()
        val client = createClient {
            followRedirects = false
            install(HttpCookies)
        }
        client.get("/__health")

        val originAirportId = seedAirport("LHR", "London Heathrow")
        val destinationAirportId = seedAirport("DXB", "Dubai International")
        val flightId = seedFlight(originAirportId, destinationAirportId)
        val fareClassId = seedFareClass()
        seedFlightFare(flightId, fareClassId)
        seedUser("passenger@example.com", "Pat", "Smith")
        val seatId = seedSeat(flightId, "1A")

        client.registerStaff()
        val loginResponse = client.loginStaff()
        assertEquals(HttpStatusCode.Found, loginResponse.status)

        val createResponse = client.submitForm(
            url = "/staff/bookings/create",
            formParameters = parameters {
                append("passengerEmail", "passenger@example.com")
                append("passengerFirstName", "Pat")
                append("passengerLastName", "Smith")
                append("flightId", flightId.toString())
                append("bookingStatus", "pending")
            }
        )
        assertEquals(HttpStatusCode.Found, createResponse.status)

        val bookingId = latestBookingId()
        val response = client.submitForm(
            url = "/staff/bookings/update",
            formParameters = parameters {
                append("bookingId", bookingId.toString())
                append("bookingStatus", "confirmed")
                append("flightId", flightId.toString())
                append("seatId", seatId.toString())
            }
        )

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/staff/bookings", response.headers[HttpHeaders.Location])
        assertEquals(seatId, assignedSeatIdForBooking(bookingId))
    }

    @Test
    // Reassigning a booking to a different flight should handle seat state correctly.
    fun reassignBookingFlightHandlesSeatCorrectly() = testApplication {
        configureApp()
        val client = createClient {
            followRedirects = false
            install(HttpCookies)
        }
        client.get("/__health")

        val originAirportId = seedAirport("LHR", "London Heathrow")
        val destinationAirportId = seedAirport("DXB", "Dubai International")
        val originalFlightId = seedFlight(originAirportId, destinationAirportId)
        val newFlightId = seedFlight(destinationAirportId, originAirportId)
        val fareClassId = seedFareClass()
        seedFlightFare(originalFlightId, fareClassId)
        seedFlightFare(newFlightId, fareClassId)
        seedUser("passenger@example.com", "Pat", "Smith")
        val originalSeatId = seedSeat(originalFlightId, "1A")

        client.registerStaff()
        val loginResponse = client.loginStaff()
        assertEquals(HttpStatusCode.Found, loginResponse.status)

        val createResponse = client.submitForm(
            url = "/staff/bookings/create",
            formParameters = parameters {
                append("passengerEmail", "passenger@example.com")
                append("passengerFirstName", "Pat")
                append("passengerLastName", "Smith")
                append("flightId", originalFlightId.toString())
                append("bookingStatus", "pending")
            }
        )
        assertEquals(HttpStatusCode.Found, createResponse.status)

        val bookingId = latestBookingId()
        val assignSeatResponse = client.submitForm(
            url = "/staff/bookings/update",
            formParameters = parameters {
                append("bookingId", bookingId.toString())
                append("bookingStatus", "confirmed")
                append("flightId", originalFlightId.toString())
                append("seatId", originalSeatId.toString())
            }
        )
        assertEquals(HttpStatusCode.Found, assignSeatResponse.status)

        val response = client.submitForm(
            url = "/staff/bookings/update",
            formParameters = parameters {
                append("bookingId", bookingId.toString())
                append("bookingStatus", "confirmed")
                append("flightId", newFlightId.toString())
            }
        )

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/staff/bookings", response.headers[HttpHeaders.Location])
        assertEquals(newFlightId, segmentFlightIdForBooking(bookingId))
        assertEquals("available", seatStatus(originalSeatId))
        assertEquals(null, assignedSeatIdForBooking(bookingId))
    }

    // Submit a valid staff registration form for staff bookings tests.
    private suspend fun HttpClient.registerStaff(
        email: String = "staff@example.com",
        password: String = "StrongPass123!"
    ) = submitForm(
        url = "/staff/register",
        formParameters = parameters {
            append("firstName", "Alex")
            append("lastName", "Admin")
            append("email", email)
            append("password", password)
            append("confirmPassword", password)
            append("role", "admin")
            append("inviteCode", "STAFF-CHECK")
        }
    )

    // Submit a staff login form for authenticated bookings requests.
    private suspend fun HttpClient.loginStaff(
        email: String = "staff@example.com",
        password: String = "StrongPass123!"
    ) = submitForm(
        url = "/staff/login",
        formParameters = parameters {
            append("email", email)
            append("password", password)
        }
    )

    // Insert an airport row so booking tests can seed valid flights.
    private fun seedAirport(iataCode: String, name: String): Int = transaction {
        AirportTable.insert {
            it[AirportTable.iataCode] = iataCode
            it[AirportTable.name] = name
            it[city] = null
            it[country] = null
        }.resultedValues!!.first()[AirportTable.id]
    }

    // Insert a flight row for booking creation tests.
    private fun seedFlight(originAirportId: Int, destinationAirportId: Int): Int = transaction {
        FlightTable.insert {
            it[flightNumber] = 700
            it[originAirport] = originAirportId
            it[destinationAirport] = destinationAirportId
            it[scheduledDepartureTime] = "2026-04-10 09:00:00"
            it[scheduledArrivalTime] = "2026-04-10 17:00:00"
            it[status] = "scheduled"
            it[capacity] = 180
        }.resultedValues!!.first()[FlightTable.id]
    }

    // Insert a minimal fare class so a flight fare can reference it.
    private fun seedFareClass(): Int = transaction {
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

    // Insert the flight fare row required by the current staff booking create route.
    private fun seedFlightFare(flightId: Int, fareClassId: Int): Int = transaction {
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

    // Insert a user record so staff booking creation can resolve the passenger email.
    private fun seedUser(email: String, firstName: String, lastName: String): Int = transaction {
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

    // Insert an available seat row for seat-assignment booking tests.
    private fun seedSeat(flightId: Int, seatCode: String): Int = transaction {
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

    // Fetch the most recently created booking id for follow-up update assertions.
    private fun latestBookingId(): Int = transaction {
        BookingTable
            .selectAll()
            .orderBy(BookingTable.id, SortOrder.DESC)
            .limit(1)
            .first()[BookingTable.id]
    }

    // Read the seat currently assigned to the booking's first segment.
    private fun assignedSeatIdForBooking(bookingId: Int): Int? = transaction {
        val segmentId = BookingSegmentTable
            .select { BookingSegmentTable.bookingId eq bookingId }
            .limit(1)
            .first()[BookingSegmentTable.id]

        SeatAssignmentTable
            .select { SeatAssignmentTable.bookingSegmentId eq segmentId }
            .limit(1)
            .firstOrNull()
            ?.get(SeatAssignmentTable.seatId)
    }

    // Read the current flight id from the booking's first segment.
    private fun segmentFlightIdForBooking(bookingId: Int): Int = transaction {
        BookingSegmentTable
            .select { BookingSegmentTable.bookingId eq bookingId }
            .limit(1)
            .first()[BookingSegmentTable.flightId]
    }

    // Read the current seat status after reassignment operations.
    private fun seatStatus(seatId: Int): String = transaction {
        SeatTable
            .select { SeatTable.id eq seatId }
            .limit(1)
            .first()[SeatTable.status]
    }
}
