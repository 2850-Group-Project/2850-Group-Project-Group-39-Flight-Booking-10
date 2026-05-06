package com.flightbooking

import com.flightbooking.tables.BookingTable
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaffBookingsRoutesTest : IntegrationTestSupport() {
    // Unauthenticated staff users should be sent to the staff login page.
    @Test
    fun unauthenticatedStaffBookingsRedirectsToStaffLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/staff/bookings")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/login", response.headers[HttpHeaders.Location])
        }

    // Authenticated staff users should be able to load the bookings page.
    @Test
    fun authenticatedStaffBookingsPageLoads() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()

            val response = client.get("/staff/bookings")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("Bookings"))
            assertTrue(body.contains("Existing Bookings"))
        }

    // The bookings list should show seeded booking, passenger, and seat details.
    @Test
    fun bookingsListShowsSeededBookingPassengerAndSeat() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")
            val booking = seedStaffBookingWithSeat()

            val response = client.get("/staff/bookings")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains(booking.reference))
            assertTrue(body.contains("Pat Smith"))
            assertTrue(body.contains("passenger@example.com"))
            assertTrue(body.contains("Current: 1A"))
        }

    // Searching by booking id should filter the list to that booking.
    @Test
    fun bookingsSearchByBookingIdFiltersList() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")
            val booking = seedStaffBookingWithSeat(reference = "BOOKONE")
            val otherBooking =
                seedStaffBookingWithSeat(
                    reference = "BOOKTWO",
                    email = "other@example.com",
                    originCode = "CDG",
                    destinationCode = "JFK",
                    fareClassId = booking.fareClassId,
                )

            val response = client.get("/staff/bookings?q=${booking.bookingId}")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("BOOKONE"))
            assertFalse(body.contains("BOOKTWO"))
            assertFalse(body.contains(otherBooking.email))
        }

    // Non-matching text search should show an empty bookings list.
    @Test
    fun bookingsSearchWithUnknownTextReturnsEmptyList() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")
            seedStaffBookingWithSeat(reference = "BOOKONE")

            val response = client.get("/staff/bookings?q=does-not-exist")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("No bookings found."))
            assertFalse(body.contains("BOOKONE"))
        }

    // Staff should be able to create a booking from the bookings management page.
    @Test
    fun createBookingRedirectsWithSuccessMessage() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")

            val originAirportId = seedAirport("LHR", "London Heathrow")
            val destinationAirportId = seedAirport("DXB", "Dubai International")
            val flightId = seedFlight(originAirportId, destinationAirportId)
            val fareClassId = seedFareClass()
            seedFlightFare(flightId, fareClassId)
            seedUser("passenger@example.com", "Pat", "Smith")

            val response =
                client.submitForm(
                    url = "/staff/bookings/create",
                    formParameters =
                        parameters {
                            append("passengerEmail", "passenger@example.com")
                            append("passengerFirstName", "Pat")
                            append("passengerLastName", "Smith")
                            append("flightId", flightId.toString())
                            append("bookingStatus", "confirmed")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/bookings", response.headers[HttpHeaders.Location])
        }

    // Booking creation should reject missing required inputs without creating a booking.
    @Test
    fun createBookingRejectsMissingRequiredParams() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")

            val response = client.submitForm(url = "/staff/bookings/create")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/bookings", response.headers[HttpHeaders.Location])
            assertEquals(0, bookingCount())
        }

    // Booking creation should reject passenger emails that do not belong to a user.
    @Test
    fun createBookingRejectsUnknownPassengerEmail() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")
            val originAirportId = seedAirport("LHR", "London Heathrow")
            val destinationAirportId = seedAirport("DXB", "Dubai International")
            val flightId = seedFlight(originAirportId, destinationAirportId)

            val response =
                client.submitForm(
                    url = "/staff/bookings/create",
                    formParameters =
                        parameters {
                            append("passengerEmail", "missing@example.com")
                            append("passengerFirstName", "Missing")
                            append("passengerLastName", "User")
                            append("flightId", flightId.toString())
                            append("bookingStatus", "confirmed")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertTrue(response.headers[HttpHeaders.Location].orEmpty().contains("No user found for this email"))
            assertEquals(0, bookingCount())
        }

    // Booking creation with a selected seat should assign and occupy that seat.
    @Test
    fun createBookingWithSelectedSeatAssignsAndOccupiesSeat() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")
            val originAirportId = seedAirport("LHR", "London Heathrow")
            val destinationAirportId = seedAirport("DXB", "Dubai International")
            val flightId = seedFlight(originAirportId, destinationAirportId)
            seedUser("passenger@example.com", "Pat", "Smith")
            val seatId = seedSeat(flightId, "1A")

            val response =
                client.submitForm(
                    url = "/staff/bookings/create",
                    formParameters =
                        parameters {
                            append("passengerEmail", "passenger@example.com")
                            append("passengerFirstName", "Pat")
                            append("passengerLastName", "Smith")
                            append("flightId", flightId.toString())
                            append("bookingStatus", "confirmed")
                            append("seatId", seatId.toString())
                        },
                )

            val bookingId = latestBookingId()

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals(seatId, assignedSeatIdForBooking(bookingId))
            assertEquals("occupied", seatStatus(seatId))
        }

    // Staff should be able to update a booking status successfully.
    @Test
    fun updateBookingStatusRedirectsWithSuccessMessage() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")

            val originAirportId = seedAirport("LHR", "London Heathrow")
            val destinationAirportId = seedAirport("DXB", "Dubai International")
            val flightId = seedFlight(originAirportId, destinationAirportId)
            val fareClassId = seedFareClass()
            seedFlightFare(flightId, fareClassId)
            seedUser("passenger@example.com", "Pat", "Smith")

            val createResponse =
                client.submitForm(
                    url = "/staff/bookings/create",
                    formParameters =
                        parameters {
                            append("passengerEmail", "passenger@example.com")
                            append("passengerFirstName", "Pat")
                            append("passengerLastName", "Smith")
                            append("flightId", flightId.toString())
                            append("bookingStatus", "pending")
                        },
                )
            assertEquals(HttpStatusCode.Found, createResponse.status)

            val bookingId = latestBookingId()
            val response =
                client.submitForm(
                    url = "/staff/bookings/update",
                    formParameters =
                        parameters {
                            append("bookingId", bookingId.toString())
                            append("bookingStatus", "confirmed")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/bookings", response.headers[HttpHeaders.Location])
        }

    // Booking updates without a booking id should redirect safely.
    @Test
    fun updateBookingMissingBookingIdRedirectsSafely() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()

            val response = client.updateBooking("bookingStatus" to "confirmed")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/bookings", response.headers[HttpHeaders.Location])
        }

    // A seat from another flight should not be assigned to the booking.
    @Test
    fun updateBookingWithSeatFromDifferentFlightDoesNotAssignIt() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")
            val booking = seedStaffBookingWithSeat()
            val otherFlightId = seedFlight(booking.destinationAirportId, booking.originAirportId)
            val wrongFlightSeatId = seedSeat(otherFlightId, "9C")

            val response =
                client.updateBooking(
                    "bookingId" to booking.bookingId.toString(),
                    "bookingStatus" to "confirmed",
                    "flightId" to booking.flightId.toString(),
                    "seatId" to wrongFlightSeatId.toString(),
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals(booking.seatId, assignedSeatIdForBooking(booking.bookingId))
            assertEquals("available", seatStatus(wrongFlightSeatId))
        }

    // Removing a seat from the same flight should free the old seat.
    @Test
    fun removingSeatFreesOldSeat() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")
            val booking = seedStaffBookingWithSeat()

            val response =
                client.updateBooking(
                    "bookingId" to booking.bookingId.toString(),
                    "bookingStatus" to "confirmed",
                    "flightId" to booking.flightId.toString(),
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals(null, assignedSeatIdForBooking(booking.bookingId))
            assertEquals("available", seatStatus(booking.seatId))
        }

    // Staff should be able to change the seat assignment for a booking.
    @Test
    fun changeSeatAssignmentRedirectsWithSuccessMessage() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")

            val originAirportId = seedAirport("LHR", "London Heathrow")
            val destinationAirportId = seedAirport("DXB", "Dubai International")
            val flightId = seedFlight(originAirportId, destinationAirportId)
            val fareClassId = seedFareClass()
            seedFlightFare(flightId, fareClassId)
            seedUser("passenger@example.com", "Pat", "Smith")
            val seatId = seedSeat(flightId, "1A")

            val createResponse =
                client.submitForm(
                    url = "/staff/bookings/create",
                    formParameters =
                        parameters {
                            append("passengerEmail", "passenger@example.com")
                            append("passengerFirstName", "Pat")
                            append("passengerLastName", "Smith")
                            append("flightId", flightId.toString())
                            append("bookingStatus", "pending")
                        },
                )
            assertEquals(HttpStatusCode.Found, createResponse.status)

            val bookingId = latestBookingId()
            val response =
                client.submitForm(
                    url = "/staff/bookings/update",
                    formParameters =
                        parameters {
                            append("bookingId", bookingId.toString())
                            append("bookingStatus", "confirmed")
                            append("flightId", flightId.toString())
                            append("seatId", seatId.toString())
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/bookings", response.headers[HttpHeaders.Location])
            assertEquals(seatId, assignedSeatIdForBooking(bookingId))
        }

    // Reassigning a booking to a different flight should handle seat state correctly.
    @Test
    fun reassignBookingFlightHandlesSeatCorrectly() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
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

            assertEquals(
                HttpStatusCode.Found,
                client.submitForm(
                    url = "/staff/bookings/create",
                    formParameters =
                        parameters {
                            append("passengerEmail", "passenger@example.com")
                            append("passengerFirstName", "Pat")
                            append("passengerLastName", "Smith")
                            append("flightId", originalFlightId.toString())
                            append("bookingStatus", "pending")
                        },
                ).status,
            )
            val bookingId = latestBookingId()
            assertEquals(
                HttpStatusCode.Found,
                client.updateBooking(
                    "bookingId" to bookingId.toString(),
                    "bookingStatus" to "confirmed",
                    "flightId" to originalFlightId.toString(),
                    "seatId" to originalSeatId.toString(),
                ).status,
            )
            val response =
                client
                    .updateBooking(
                        "bookingId" to bookingId.toString(),
                        "bookingStatus" to "confirmed",
                        "flightId" to newFlightId.toString(),
                    )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/bookings", response.headers[HttpHeaders.Location])
            assertEquals(newFlightId, segmentFlightIdForBooking(bookingId))
            assertEquals("available", seatStatus(originalSeatId))
            assertEquals(null, assignedSeatIdForBooking(bookingId))
        }

    private fun seedStaffBookingWithSeat(
        reference: String = "STAFFBOOK",
        email: String = "passenger@example.com",
        originCode: String = "LHR",
        destinationCode: String = "DXB",
        fareClassId: Int? = null,
    ): SeededStaffBooking {
        val originAirportId = seedAirport(originCode, "$originCode Airport")
        val destinationAirportId = seedAirport(destinationCode, "$destinationCode Airport")
        val flightId = seedFlight(originAirportId, destinationAirportId)
        val actualFareClassId = fareClassId ?: seedFareClass()
        val fareId = seedFlightFare(flightId, actualFareClassId)
        val userId = seedUser(email, "Pat", "Smith")
        val bookingId = seedBooking(userId, reference, "confirmed")
        val passengerId = seedPassenger(bookingId, "Pat", "Smith")
        val segmentId = seedBookingSegment(bookingId, flightId, fareId)
        val seatId = seedSeat(flightId, "1A")
        seedSeatAssignment(passengerId, segmentId, seatId)

        return SeededStaffBooking(
            bookingId = bookingId,
            reference = reference,
            email = email,
            originAirportId = originAirportId,
            destinationAirportId = destinationAirportId,
            flightId = flightId,
            fareClassId = actualFareClassId,
            seatId = seatId,
        )
    }

    private fun bookingCount(): Int =
        transaction {
            BookingTable.selectAll().count().toInt()
        }

    private suspend fun HttpClient.updateBooking(vararg params: Pair<String, String>) =
        submitForm(
            url = "/staff/bookings/update",
            formParameters = parameters { params.forEach { (k, v) -> append(k, v) } },
        )

    private data class SeededStaffBooking(
        val bookingId: Int,
        val reference: String,
        val email: String,
        val originAirportId: Int,
        val destinationAirportId: Int,
        val flightId: Int,
        val fareClassId: Int,
        val seatId: Int,
    )
}
