package com.flightbooking

import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.FareClassTable
import com.flightbooking.tables.FlightTable
import io.ktor.client.HttpClient
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PagesRoutesTest : IntegrationTestSupport() {
    // Authenticated users should be able to open the home page when airports exist.
    @Test
    fun authenticatedHomePageLoadsWithAirports() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            seedAirportWithCity("LHR", "London Heathrow", "London")
            seedAirportWithCity("DXB", "Dubai International", "Dubai")

            val response = client.get("/home")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Where are we flying to"))
            assertTrue(body.contains("Search flights"))
        }

    // Valid one-way searches should render outbound flight options.
    @Test
    fun oneWayFlightSearchRendersOutboundFlights() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val departureDate = LocalDate.now().plusDays(7)
            seedSearchFlights(departureDate)

            val response =
                client.get(
                    "/flights/search?trip_type=oneway&origin=LHR&destination=DXB" +
                        "&departure_date=$departureDate&adults=1&children=0&infants=0",
                )

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Outbound"))
            assertTrue(body.contains("Flight #701"))
            assertTrue(body.contains("LHR"))
            assertTrue(body.contains("DXB"))
            assertFalse(body.contains("return-track"))
        }

    // Valid return searches should render outbound and return flight options.
    @Test
    fun returnFlightSearchRendersOutboundAndReturnFlights() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val departureDate = LocalDate.now().plusDays(7)
            val returnDate = LocalDate.now().plusDays(14)
            seedSearchFlights(departureDate, returnDate)

            val response =
                client.get(
                    "/flights/search?trip_type=return&origin=LHR&destination=DXB" +
                        "&departure_date=$departureDate&return_date=$returnDate&adults=1&children=0&infants=0",
                )

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Outbound"))
            assertTrue(body.contains("Return"))
            assertTrue(body.contains("Flight #701"))
            assertTrue(body.contains("Flight #702"))
        }

    // Invalid searches should redirect users back home.
    @Test
    fun invalidFlightSearchRedirectsHome() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response = client.get("/flights/search")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/home", response.headers[HttpHeaders.Location])
        }

    // Passenger details page should render rows for adult, child, and infant counts.
    @Test
    fun passengersPageRendersPassengerCounts() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            client.seedBookingSession(adults = "1", children = "1", infants = "1")

            val response = client.get("/flights/passengers")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Adult 1"))
            assertTrue(body.contains("Child 1"))
            assertTrue(body.contains("Infant 1"))
            assertTrue(body.contains("passengers[0][firstName]"))
            assertTrue(body.contains("passengers[1][firstName]"))
            assertTrue(body.contains("passengers[2][firstName]"))
        }

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

    // Booking status filters should only show matching bookings.
    @Test
    fun bookingsPageFiltersByConfirmedStatus() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            seedUserBooking(bookingReference = "CONF123", bookingStatus = "confirmed")
            seedUserBooking(bookingReference = "CANCEL123", bookingStatus = "cancelled")

            val response = client.get("/profile/bookings?status=confirmed")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("CONF123"))
            assertTrue(body.contains("Status: confirmed"))
            assertFalse(body.contains("CANCEL123"))
        }

    // Booking id filters should show only the matching booking.
    @Test
    fun bookingsPageFiltersByBookingId() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val target = seedUserBooking(bookingReference = "TARGET123")
            seedUserBooking(bookingReference = "OTHER123")

            val response = client.get("/profile/bookings?q=${target.bookingId}")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("TARGET123"))
            assertFalse(body.contains("OTHER123"))
        }

    // Text booking searches should render an empty bookings list.
    @Test
    fun bookingsPageTextSearchReturnsEmptyList() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            seedUserBooking(bookingReference = "TEST123")

            val response = client.get("/profile/bookings?q=missing")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("No bookings found."))
            assertFalse(body.contains("TEST123"))
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

    // Cancelling an unknown booking should redirect to the shared not found route.
    @Test
    fun cancelUnknownBookingRedirectsToNotFound() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response =
                client.submitForm(
                    url = "/profile/bookings/cancel",
                    formParameters =
                        parameters {
                            append("bookingId", "999")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/404", response.headers[HttpHeaders.Location])
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

    // Deleting an unknown booking should redirect to the shared not found route.
    @Test
    fun deleteUnknownBookingRedirectsToNotFound() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response =
                client.submitForm(
                    url = "/profile/bookings/delete",
                    formParameters =
                        parameters {
                            append("bookingId", "999")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/404", response.headers[HttpHeaders.Location])
        }

    // Users should not be able to cancel or delete bookings owned by another user.
    @Test
    fun userCannotCancelOrDeleteAnotherUsersBooking() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val otherUserId = seedUser("other@example.com", "Other", "User")
            val booking = seedUserBooking(userId = otherUserId, bookingReference = "OTHER123")

            val cancelResponse =
                client.submitForm(
                    url = "/profile/bookings/cancel",
                    formParameters =
                        parameters {
                            append("bookingId", booking.bookingId.toString())
                        },
                )
            val deleteResponse =
                client.submitForm(
                    url = "/profile/bookings/delete",
                    formParameters =
                        parameters {
                            append("bookingId", booking.bookingId.toString())
                        },
                )

            assertEquals(HttpStatusCode.Found, cancelResponse.status)
            assertEquals("/404", cancelResponse.headers[HttpHeaders.Location])
            assertEquals(HttpStatusCode.Found, deleteResponse.status)
            assertEquals("/404", deleteResponse.headers[HttpHeaders.Location])
            assertEquals("confirmed", bookingStatus(booking.bookingId))
            assertTrue(bookingExists(booking.bookingId))
        }

    private fun seedUserBooking(
        userId: Int = userIdByEmail(),
        bookingReference: String = "TEST123",
        bookingStatus: String = "confirmed",
    ): SeededUserBooking {
        val originAirportId = seedAirportWithCity("LHR", "London Heathrow", "London")
        val destinationAirportId = seedAirportWithCity("DXB", "Dubai International", "Dubai")
        val flightId = seedFlight(originAirportId, destinationAirportId)
        val fareClassId = seedOrGetFareClass()
        val fareId = seedFlightFare(flightId, fareClassId)
        val bookingId = seedBooking(userId, bookingReference, bookingStatus)
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

    private fun seedAirportWithCity(
        iataCode: String,
        name: String,
        city: String,
    ): Int {
        val existingAirportId =
            transaction {
                AirportTable
                    .select { AirportTable.iataCode eq iataCode }
                    .limit(1)
                    .firstOrNull()
                    ?.get(AirportTable.id)
            }
        val airportId = existingAirportId ?: seedAirport(iataCode, name)
        transaction {
            AirportTable.update({ AirportTable.id eq airportId }) {
                it[AirportTable.city] = city
                it[country] = "Test Country"
            }
        }
        return airportId
    }

    private fun seedSearchFlights(
        departureDate: LocalDate,
        returnDate: LocalDate? = null,
    ) {
        val originAirportId = seedAirportWithCity("LHR", "London Heathrow", "London")
        val destinationAirportId = seedAirportWithCity("DXB", "Dubai International", "Dubai")
        val fareClassId = seedOrGetFareClass()
        seedSearchFlight(originAirportId, destinationAirportId, departureDate, 701, fareClassId)
        if (returnDate != null) {
            seedSearchFlight(destinationAirportId, originAirportId, returnDate, 702, fareClassId)
        }
    }

    private fun seedSearchFlight(
        originAirportId: Int,
        destinationAirportId: Int,
        departureDate: LocalDate,
        flightNumber: Int,
        fareClassId: Int,
    ) {
        val flightId = seedFlight(originAirportId, destinationAirportId, flightNumberValue = flightNumber)
        val departureTime = departureDate.atTime(9, 0).format(flightDateTimeFormatter)
        val arrivalTime = departureDate.atTime(17, 0).format(flightDateTimeFormatter)
        transaction {
            FlightTable.update({ FlightTable.id eq flightId }) {
                it[scheduledDepartureTime] = departureTime
                it[scheduledArrivalTime] = arrivalTime
            }
        }
        seedFlightFare(flightId, fareClassId)
    }

    private fun seedOrGetFareClass(): Int =
        transaction {
            FareClassTable
                .select { FareClassTable.classCode eq "ECON" }
                .limit(1)
                .firstOrNull()
                ?.get(FareClassTable.id)
        } ?: seedFareClass()

    private suspend fun HttpClient.seedBookingSession(
        adults: String,
        children: String,
        infants: String,
    ) {
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
                        append("children", children)
                        append("infants", infants)
                    },
            )

        assertEquals(HttpStatusCode.OK, response.status)
    }

    private companion object {
        val flightDateTimeFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    private data class SeededUserBooking(
        val bookingId: Int,
        val segmentId: Int,
        val seatId: Int,
    )
}
