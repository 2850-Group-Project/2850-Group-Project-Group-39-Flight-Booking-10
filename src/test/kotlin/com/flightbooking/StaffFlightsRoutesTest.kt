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

class StaffFlightsRoutesTest : IntegrationTestSupport() {
    // Unauthenticated staff users should be sent to the staff login page.
    @Test
    fun unauthenticatedStaffFlightsRedirectsToStaffLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/staff/flights")
            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/login", response.headers[HttpHeaders.Location])
        }

    // An authenticated staff user should be able to load the staff flights page.
    @Test
    fun authenticatedStaffFlightsPageLoads() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()

            val response = client.get("/staff/flights")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("Flight Scheduler"))
        }

    // Staff should be able to create a flight from the management page.
    @Test
    fun createFlightRedirectsWithSuccessMessage() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")
            val originAirportId = seedAirport("LHR", "London Heathrow")
            val destinationAirportId = seedAirport("DXB", "Dubai International")

            val response =
                client.submitForm(
                    url = "/staff/flights/create",
                    formParameters =
                        parameters {
                            append("flightNumber", "101")
                            append("originId", originAirportId.toString())
                            append("destId", destinationAirportId.toString())
                            append("dep", "2026-04-01 09:00:00")
                            append("arr", "2026-04-01 11:00:00")
                            append("status", "scheduled")
                            append("capacity", "180")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/flights?ok=Flight created", response.headers[HttpHeaders.Location])
        }

    // Flight creation should reject invalid route input and redirect with an error.
    @Test
    fun createFlightRejectsInvalidRouteData() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")
            val airportId = seedAirport("LHR", "London Heathrow")

            val response =
                client.submitForm(
                    url = "/staff/flights/create",
                    formParameters =
                        parameters {
                            append("flightNumber", "101")
                            append("originId", airportId.toString())
                            append("destId", airportId.toString())
                            append("dep", "2026-04-01 09:00:00")
                            append("arr", "2026-04-01 11:00:00")
                            append("status", "scheduled")
                            append("capacity", "180")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals(
                "/staff/flights?error=Origin and destination cannot be the same",
                response.headers[HttpHeaders.Location],
            )
        }

    // Staff should be able to update an existing flight.
    @Test
    fun updateFlightRedirectsWithSuccessMessage() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")
            val originAirportId = seedAirport("LHR", "London Heathrow")
            val destinationAirportId = seedAirport("DXB", "Dubai International")

            val createResponse =
                client.submitForm(
                    url = "/staff/flights/create",
                    formParameters =
                        parameters {
                            append("flightNumber", "303")
                            append("originId", originAirportId.toString())
                            append("destId", destinationAirportId.toString())
                            append("dep", "2026-04-03 09:00:00")
                            append("arr", "2026-04-03 11:00:00")
                            append("status", "scheduled")
                            append("capacity", "12")
                        },
                )
            assertEquals(HttpStatusCode.Found, createResponse.status)

            val flightId = latestFlightId()
            val response =
                client.submitForm(
                    url = "/staff/flights/update",
                    formParameters =
                        parameters {
                            append("id", flightId.toString())
                            append("flightNumber", "404")
                            append("originId", destinationAirportId.toString())
                            append("destId", originAirportId.toString())
                            append("dep", "2026-04-04 13:30:00")
                            append("arr", "2026-04-04 20:45:00")
                            append("status", "delayed")
                            append("capacity", "16")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/flights?ok=Flight updated", response.headers[HttpHeaders.Location])
        }

    // Staff should be able to delete an existing flight.
    @Test
    fun deleteFlightRedirectsWithSuccessMessage() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")
            val originAirportId = seedAirport("LHR", "London Heathrow")
            val destinationAirportId = seedAirport("DXB", "Dubai International")

            val createResponse =
                client.submitForm(
                    url = "/staff/flights/create",
                    formParameters =
                        parameters {
                            append("flightNumber", "505")
                            append("originId", originAirportId.toString())
                            append("destId", destinationAirportId.toString())
                            append("dep", "2026-04-05 09:00:00")
                            append("arr", "2026-04-05 11:00:00")
                            append("status", "scheduled")
                            append("capacity", "20")
                        },
                )
            assertEquals(HttpStatusCode.Found, createResponse.status)

            val flightId = latestFlightId()
            val response =
                client.submitForm(
                    url = "/staff/flights/delete",
                    formParameters =
                        parameters {
                            append("id", flightId.toString())
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/flights?ok=Flight deleted", response.headers[HttpHeaders.Location])
        }

    // Creating a flight should automatically generate seats for that flight.
    @Test
    fun createFlightAutoGeneratesSeats() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")
            val originAirportId = seedAirport("LHR", "London Heathrow")
            val destinationAirportId = seedAirport("DXB", "Dubai International")

            val response =
                client.submitForm(
                    url = "/staff/flights/create",
                    formParameters =
                        parameters {
                            append("flightNumber", "202")
                            append("originId", originAirportId.toString())
                            append("destId", destinationAirportId.toString())
                            append("dep", "2026-04-02 09:00:00")
                            append("arr", "2026-04-02 11:00:00")
                            append("status", "scheduled")
                            append("capacity", "8")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)

            val newestFlightId = latestFlightId()
            val seatCodes = seatCodesForFlight(newestFlightId)

            assertEquals(8, seatCodes.size)
            assertEquals("1A", seatCodes.first())
            assertEquals("2B", seatCodes.last())
        }
}
