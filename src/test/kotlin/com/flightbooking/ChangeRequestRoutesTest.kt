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
import kotlin.test.assertTrue

class ChangeRequestRoutesTest : IntegrationTestSupport() {
    // Unauthenticated users should be redirected to login from the change request page.
    @Test
    fun unauthenticatedChangeRequestPageRedirectsToLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/profile/bookings/change")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login", response.headers[HttpHeaders.Location])
        }

    // Unauthenticated change request submissions should be redirected to login.
    @Test
    fun unauthenticatedChangeRequestSubmitRedirectsToLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.submitForm(url = "/profile/bookings/change")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login", response.headers[HttpHeaders.Location])
        }

    // Authenticated users should be able to open the change page for their own booking.
    @Test
    fun changeRequestPageLoadsForOwnedBooking() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val booking = seedChangeRequestBooking()

            val response = client.get("/profile/bookings/change?bookingId=${booking.bookingId}")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Change Request"))
            assertTrue(body.contains("Current Flight"))
            assertTrue(body.contains("Current Seat"))
            assertTrue(body.contains("1A"))
        }

    // Unknown bookings should redirect to the shared not-found page.
    @Test
    fun changeRequestPageRedirectsToNotFoundForUnknownBooking() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response = client.get("/profile/bookings/change?bookingId=999")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/404", response.headers[HttpHeaders.Location])
        }

    // A valid change request should be stored for staff review.
    @Test
    fun changeRequestSubmitCreatesPendingRequest() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val booking = seedChangeRequestBooking()
            val requestedFlightId = seedFlight(booking.destinationAirportId, booking.originAirportId, 701)
            val requestedSeatId = seedSeat(requestedFlightId, "2A")

            val response =
                client.submitForm(
                    url = "/profile/bookings/change",
                    formParameters =
                        parameters {
                            append("bookingId", booking.bookingId.toString())
                            append("segmentId", booking.segmentId.toString())
                            append("requestedFlightId", requestedFlightId.toString())
                            append("requestedSeatId", requestedSeatId.toString())
                            append("reason", "Need a later flight")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/profile/bookings?ok=Change+request+submitted", response.headers[HttpHeaders.Location])
            assertEquals(1, changeRequestCount())
            val request = latestChangeRequest()
            assertEquals(booking.bookingId, request.bookingId)
            assertEquals(booking.segmentId, request.bookingSegmentId)
            assertEquals(booking.flightId, request.currentFlightId)
            assertEquals(requestedFlightId, request.requestedFlightId)
            assertEquals(requestedSeatId, request.requestedSeatId)
            assertEquals("Need a later flight", request.reason)
            assertEquals("pending", request.status)
        }

    // A requested seat must belong to the selected requested flight.
    @Test
    fun changeRequestSubmitRejectsSeatForDifferentFlight() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val booking = seedChangeRequestBooking()
            val requestedFlightId = seedFlight(booking.destinationAirportId, booking.originAirportId, 701)
            val otherFlightId = seedFlight(booking.originAirportId, booking.destinationAirportId, 702)
            val wrongFlightSeatId = seedSeat(otherFlightId, "9C")

            val response =
                client.submitForm(
                    url = "/profile/bookings/change",
                    formParameters =
                        parameters {
                            append("bookingId", booking.bookingId.toString())
                            append("segmentId", booking.segmentId.toString())
                            append("requestedFlightId", requestedFlightId.toString())
                            append("requestedSeatId", wrongFlightSeatId.toString())
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals(
                "/profile/bookings/change?bookingId=${booking.bookingId}&error=Seat+does+not+belong+to+selected+flight",
                response.headers[HttpHeaders.Location],
            )
            assertEquals(0, changeRequestCount())
        }

    private fun seedChangeRequestBooking(): SeededChangeRequestBooking {
        val originAirportId = seedAirport("LHR", "London Heathrow")
        val destinationAirportId = seedAirport("DXB", "Dubai International")
        val flightId = seedFlight(originAirportId, destinationAirportId)
        val fareClassId = seedFareClass()
        val fareId = seedFlightFare(flightId, fareClassId)
        val userId = userIdByEmail()
        val bookingId = seedBooking(userId, "CHG123", "confirmed")
        val passengerId = seedPassenger(bookingId)
        val segmentId = seedBookingSegment(bookingId, flightId, fareId)
        val seatId = seedSeat(flightId, "1A")

        seedSeatAssignment(passengerId, segmentId, seatId)

        return SeededChangeRequestBooking(
            bookingId = bookingId,
            segmentId = segmentId,
            flightId = flightId,
            originAirportId = originAirportId,
            destinationAirportId = destinationAirportId,
        )
    }

    private data class SeededChangeRequestBooking(
        val bookingId: Int,
        val segmentId: Int,
        val flightId: Int,
        val originAirportId: Int,
        val destinationAirportId: Int,
    )
}
