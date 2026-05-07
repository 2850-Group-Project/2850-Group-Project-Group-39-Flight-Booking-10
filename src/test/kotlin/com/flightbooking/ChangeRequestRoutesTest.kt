package com.flightbooking

import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.FareClassTable
import com.flightbooking.tables.SeatTable
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChangeRequestRoutesTest : IntegrationTestSupport() {
    /**
     * Unauthenticated users should be redirected to login from the change request page.
     */
    @Test
    fun unauthenticatedChangeRequestPageRedirectsToLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/profile/bookings/change")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login", response.headers[HttpHeaders.Location])
        }

    /**
     * Unauthenticated change request submissions should be redirected to login.
     */
    @Test
    fun unauthenticatedChangeRequestSubmitRedirectsToLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.submitForm(url = "/profile/bookings/change")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login", response.headers[HttpHeaders.Location])
        }

    /**
     * Missing booking ids should redirect to the shared not-found page.
     */
    @Test
    fun changeRequestPageRedirectsToNotFoundWhenBookingIdMissing() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response = client.get("/profile/bookings/change")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/404", response.headers[HttpHeaders.Location])
        }

    /**
     * Authenticated users should be able to open the change page for their own booking.
     */
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

    /**
     * Unknown bookings should redirect to the shared not-found page.
     */
    @Test
    fun changeRequestPageRedirectsToNotFoundForUnknownBooking() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response = client.get("/profile/bookings/change?bookingId=999")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/404", response.headers[HttpHeaders.Location])
        }

    /**
     * Users should not be able to open the change page for another user's booking.
     */
    @Test
    fun changeRequestPageRedirectsToNotFoundForAnotherUsersBooking() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val otherUserId = seedUser("other@example.com", "Other", "User")
            val booking = seedChangeRequestBooking(userId = otherUserId)

            val response = client.get("/profile/bookings/change?bookingId=${booking.bookingId}")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/404", response.headers[HttpHeaders.Location])
        }

    /**
     * Flight search should show matching requested flights.
     */
    @Test
    fun changeRequestPageFlightSearchShowsMatchingFlights() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val booking = seedChangeRequestBooking()
            val matchingFlightId = seedFlight(booking.destinationAirportId, booking.originAirportId, 701)
            val nonMatchingFlightId = seedFlight(booking.destinationAirportId, booking.originAirportId, 802)

            val response = client.get("/profile/bookings/change?bookingId=${booking.bookingId}&flightQ=70")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains(matchingFlightId.toString()))
            assertTrue(body.contains("701"))
            assertFalse(body.contains(nonMatchingFlightId.toString()))
            assertFalse(body.contains("802"))
        }

    /**
     * Selecting a requested flight should show its available seats.
     */
    @Test
    fun changeRequestPageSelectedFlightShowsAvailableSeats() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val booking = seedChangeRequestBooking()
            val requestedFlightId = seedFlight(booking.destinationAirportId, booking.originAirportId, 701)
            seedSeat(requestedFlightId, "2A")
            val unavailableSeatId = seedSeat(requestedFlightId, "2B")
            markSeatUnavailable(unavailableSeatId)

            val response =
                client.get(
                    "/profile/bookings/change?bookingId=${booking.bookingId}" +
                        "&selectedFlightId=$requestedFlightId",
                )

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Selected Flight ID: $requestedFlightId"))
            assertTrue(body.contains("2A"))
            assertFalse(body.contains("2B"))
        }

    /**
     * Missing required submit ids should redirect to the shared not-found page.
     */
    @Test
    fun changeRequestSubmitRedirectsToNotFoundWhenRequiredIdsMissing() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            assertMissingSubmitIdRedirectsToNotFound(
                client.submitForm(
                    url = "/profile/bookings/change",
                    formParameters =
                        parameters {
                            append("segmentId", "1")
                            append("requestedFlightId", "1")
                        },
                ),
            )
            assertMissingSubmitIdRedirectsToNotFound(
                client.submitForm(
                    url = "/profile/bookings/change",
                    formParameters =
                        parameters {
                            append("bookingId", "1")
                            append("requestedFlightId", "1")
                        },
                ),
            )
            assertMissingSubmitIdRedirectsToNotFound(
                client.submitForm(
                    url = "/profile/bookings/change",
                    formParameters =
                        parameters {
                            append("bookingId", "1")
                            append("segmentId", "1")
                        },
                ),
            )
        }

    /**
     * A valid change request should be stored for staff review.
     */
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

    /**
     * Requested flights must exist before a change request is created.
     */
    @Test
    fun changeRequestSubmitRejectsUnknownRequestedFlight() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val booking = seedChangeRequestBooking()

            val response =
                client.submitForm(
                    url = "/profile/bookings/change",
                    formParameters =
                        parameters {
                            append("bookingId", booking.bookingId.toString())
                            append("segmentId", booking.segmentId.toString())
                            append("requestedFlightId", "999")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals(
                "/profile/bookings/change?bookingId=${booking.bookingId}&error=Flight+not+found",
                response.headers[HttpHeaders.Location],
            )
            assertEquals(0, changeRequestCount())
        }

    /**
     * Requested seats must exist before a change request is created.
     */
    @Test
    fun changeRequestSubmitRejectsUnknownRequestedSeat() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val booking = seedChangeRequestBooking()
            val requestedFlightId = seedFlight(booking.destinationAirportId, booking.originAirportId, 701)

            val response =
                client.submitForm(
                    url = "/profile/bookings/change",
                    formParameters =
                        parameters {
                            append("bookingId", booking.bookingId.toString())
                            append("segmentId", booking.segmentId.toString())
                            append("requestedFlightId", requestedFlightId.toString())
                            append("requestedSeatId", "999")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals(
                "/profile/bookings/change?bookingId=${booking.bookingId}&error=Seat+not+found",
                response.headers[HttpHeaders.Location],
            )
            assertEquals(0, changeRequestCount())
        }

    /**
     * A requested seat must belong to the selected requested flight.
     */
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

    /**
     * Requested seats must be available before a change request is created.
     */
    @Test
    fun changeRequestSubmitRejectsUnavailableRequestedSeat() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val booking = seedChangeRequestBooking()
            val requestedFlightId = seedFlight(booking.destinationAirportId, booking.originAirportId, 701)
            val requestedSeatId = seedSeat(requestedFlightId, "2A")
            markSeatUnavailable(requestedSeatId)

            val response =
                client.submitForm(
                    url = "/profile/bookings/change",
                    formParameters =
                        parameters {
                            append("bookingId", booking.bookingId.toString())
                            append("segmentId", booking.segmentId.toString())
                            append("requestedFlightId", requestedFlightId.toString())
                            append("requestedSeatId", requestedSeatId.toString())
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals(
                "/profile/bookings/change?bookingId=${booking.bookingId}&error=Seat+is+not+available",
                response.headers[HttpHeaders.Location],
            )
            assertEquals(0, changeRequestCount())
        }

    /**
     * Users should not be able to submit change requests for another user's booking.
     */
    @Test
    fun changeRequestSubmitRejectsAnotherUsersBooking() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val otherUserId = seedUser("other@example.com", "Other", "User")
            val booking = seedChangeRequestBooking(userId = otherUserId)
            val requestedFlightId = seedFlight(booking.destinationAirportId, booking.originAirportId, 701)

            val response =
                client.submitForm(
                    url = "/profile/bookings/change",
                    formParameters =
                        parameters {
                            append("bookingId", booking.bookingId.toString())
                            append("segmentId", booking.segmentId.toString())
                            append("requestedFlightId", requestedFlightId.toString())
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals(
                "/profile/bookings/change?bookingId=${booking.bookingId}&error=Booking+not+found",
                response.headers[HttpHeaders.Location],
            )
            assertEquals(0, changeRequestCount())
        }

    /**
     * Verifies response redirects to /404
     * @param reponse
     */
    private fun assertMissingSubmitIdRedirectsToNotFound(response: io.ktor.client.statement.HttpResponse) {
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/404", response.headers[HttpHeaders.Location])
    }

    /**
     * Creates change request booking with test values
     * @param userId
     * @return the change request booking
     */
    private fun seedChangeRequestBooking(userId: Int = userIdByEmail()): SeededChangeRequestBooking {
        val originAirportId = seedOrGetAirport("LHR", "London Heathrow")
        val destinationAirportId = seedOrGetAirport("DXB", "Dubai International")
        val flightId = seedFlight(originAirportId, destinationAirportId)
        val fareClassId = seedOrGetFareClass()
        val fareId = seedFlightFare(flightId, fareClassId)
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

    /**
     * Find or create helper for airport
     * @param iataCode
     * @param name
     * @return the id of the airport
     */
    private fun seedOrGetAirport(
        iataCode: String,
        name: String,
    ): Int =
        transaction {
            AirportTable
                .select { AirportTable.iataCode eq iataCode }
                .limit(1)
                .firstOrNull()
                ?.get(AirportTable.id)
        } ?: seedAirport(iataCode, name)

    /**
     * Find or create helper for fare class
     * @return the id of the fare class
     */
    private fun seedOrGetFareClass(): Int =
        transaction {
            FareClassTable
                .select { FareClassTable.classCode eq "ECON" }
                .limit(1)
                .firstOrNull()
                ?.get(FareClassTable.id)
        } ?: seedFareClass()

    /**
     * Marks seat as occupied
     * @param seatId: to search
     */
    private fun markSeatUnavailable(seatId: Int) =
        transaction {
            SeatTable.update({ SeatTable.id eq seatId }) {
                it[status] = "occupied"
            }
        }

    /**
     * Data class definition for seaded change request booking
     */
    private data class SeededChangeRequestBooking(
        val bookingId: Int,
        val segmentId: Int,
        val flightId: Int,
        val originAirportId: Int,
        val destinationAirportId: Int,
    )
}
