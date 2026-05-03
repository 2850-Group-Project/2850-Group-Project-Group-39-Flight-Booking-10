package com.flightbooking

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class StaffBookingsRoutesTest : IntegrationTestSupport() {
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

    private suspend fun HttpClient.updateBooking(vararg params: Pair<String, String>) =
        submitForm(
            url = "/staff/bookings/update",
            formParameters = parameters { params.forEach { (k, v) -> append(k, v) } },
        )
}
