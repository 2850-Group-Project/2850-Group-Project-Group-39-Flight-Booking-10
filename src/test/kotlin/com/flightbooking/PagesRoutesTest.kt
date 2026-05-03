package com.flightbooking

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PagesRoutesTest : IntegrationTestSupport() {
    // Unauthenticated users should be redirected to login from the profile page.
    @Test
    fun unauthenticatedProfileRedirectsToLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/profile")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login", response.headers[HttpHeaders.Location])
        }

    // Unauthenticated users should be redirected to login from their bookings page.
    @Test
    fun unauthenticatedBookingsRedirectsToLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/profile/bookings")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login", response.headers[HttpHeaders.Location])
        }

    // The shared 404 route should render with a not found status.
    @Test
    fun notFoundPageReturnsNotFoundStatus() =
        testApplication {
            configureApp()

            val response = client.get("/404")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    // Authenticated users should be able to open their profile page.
    @Test
    fun authenticatedProfilePageLoads() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response = client.get("/profile")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("My Profile"))
            assertTrue(body.contains("Loyalty Points"))
        }

    // The bookings page should show bookings owned by the logged-in user.
    @Test
    fun bookingsPageShowsUserBookings() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            seedUserBooking()

            val response = client.get("/profile/bookings")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("My Bookings"))
            assertTrue(body.contains("TEST123"))
            assertTrue(body.contains("Status: confirmed"))
            assertTrue(body.contains("LHR"))
            assertTrue(body.contains("DXB"))
            assertTrue(body.contains("1A"))
        }

    // Cancelling a booking should mark it cancelled and free the assigned seat.
    @Test
    fun cancelBookingCancelsBookingAndFreesSeat() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val booking = seedUserBooking()

            val response =
                client.submitForm(
                    url = "/profile/bookings/cancel",
                    formParameters =
                        parameters {
                            append("bookingId", booking.bookingId.toString())
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/profile/bookings", response.headers[HttpHeaders.Location])
            assertEquals("cancelled", bookingStatus(booking.bookingId))
            assertEquals("available", seatStatus(booking.seatId))
            assertFalse(seatAssignmentExists(booking.segmentId))
        }

    // Deleting a booking should remove its booking and related segment rows.
    @Test
    fun deleteBookingDeletesBookingAndSegments() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val booking = seedUserBooking(bookingStatus = "cancelled")

            val response =
                client.submitForm(
                    url = "/profile/bookings/delete",
                    formParameters =
                        parameters {
                            append("bookingId", booking.bookingId.toString())
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/profile/bookings", response.headers[HttpHeaders.Location])
            assertFalse(bookingExists(booking.bookingId))
            assertFalse(bookingSegmentExists(booking.bookingId))
            assertFalse(seatAssignmentExists(booking.segmentId))
        }

    private fun seedUserBooking(bookingStatus: String = "confirmed"): SeededUserBooking {
        val originAirportId = seedAirport("LHR", "London Heathrow")
        val destinationAirportId = seedAirport("DXB", "Dubai International")
        val flightId = seedFlight(originAirportId, destinationAirportId)
        val fareClassId = seedFareClass()
        val fareId = seedFlightFare(flightId, fareClassId)
        val userId = userIdByEmail()
        val bookingId = seedBooking(userId, "TEST123", bookingStatus)
        val passengerId = seedPassenger(bookingId)
        val segmentId = seedBookingSegment(bookingId, flightId, fareId)
        val seatId = seedSeat(flightId, "1A")

        seedSeatAssignment(passengerId, segmentId, seatId)

        return SeededUserBooking(
            bookingId = bookingId,
            segmentId = segmentId,
            seatId = seatId,
        )
    }

    private data class SeededUserBooking(
        val bookingId: Int,
        val segmentId: Int,
        val seatId: Int,
    )
}
