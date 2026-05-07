package com.flightbooking

import com.flightbooking.tables.FlightTable
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaffFlightsRoutesTest : IntegrationTestSupport() {
    /**
     * Unauthenticated staff users should be sent to the staff login page.
     */
    @Test
    fun unauthenticatedStaffFlightsRedirectsToStaffLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/staff/flights")
            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/login", response.headers[HttpHeaders.Location])
        }

    /**
     * An authenticated staff user should be able to load the staff flights page.
     */
    @Test
    fun authenticatedStaffFlightsPageLoads() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()

            val response = client.get("/staff/flights")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("Flight Scheduler"))
        }

    /**
     * Staff flights search should filter the list by flight number.
     */
    @Test
    fun staffFlightsSearchFiltersByFlightNumber() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")
            val originAirportId = seedAirport("LHR", "London Heathrow")
            val destinationAirportId = seedAirport("DXB", "Dubai International")
            seedFlight(originAirportId, destinationAirportId, flightNumberValue = 801)
            seedFlight(originAirportId, destinationAirportId, flightNumberValue = 902)

            val response = client.get("/staff/flights?q=80")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains(">801<"))
            assertFalse(body.contains(">902<"))
        }

    /**
     * The edit query should load the selected flight into the edit form.
     */
    @Test
    fun editQueryLoadsSelectedFlightIntoEditForm() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")
            val originAirportId = seedAirport("LHR", "London Heathrow")
            val destinationAirportId = seedAirport("DXB", "Dubai International")
            val flightId = seedFlight(originAirportId, destinationAirportId, flightNumberValue = 808)

            val response = client.get("/staff/flights?edit=$flightId")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("Edit Flight #$flightId"))
            assertTrue(body.contains("value=\"808\""))
            assertTrue(body.contains("LHR London Heathrow"))
            assertTrue(body.contains("DXB Dubai International"))
        }

    /**
     * Staff should be able to create a flight from the management page.
     */
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

    /**
     * Flight creation should reject invalid route input and redirect with an error.
     */
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

    /**
     * Flight creation should reject missing route endpoints.
     */
    @Test
    fun createFlightRejectsMissingOriginOrDestination() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")

            val response =
                client.submitForm(
                    url = "/staff/flights/create",
                    formParameters =
                        parameters {
                            append("flightNumber", "111")
                            append("dep", "2026-04-01 09:00:00")
                            append("arr", "2026-04-01 11:00:00")
                            append("status", "scheduled")
                            append("capacity", "180")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals(
                "/staff/flights?error=Please select origin and destination",
                response.headers[HttpHeaders.Location],
            )
        }

    /**
     * Staff should be able to update an existing flight.
     */
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

    /**
     * Flight updates should reject requests with no flight id.
     */
    @Test
    fun updateFlightRejectsMissingFlightId() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")
            val originAirportId = seedAirport("LHR", "London Heathrow")
            val destinationAirportId = seedAirport("DXB", "Dubai International")

            val response =
                client.submitForm(
                    url = "/staff/flights/update",
                    formParameters =
                        parameters {
                            append("flightNumber", "909")
                            append("originId", originAirportId.toString())
                            append("destId", destinationAirportId.toString())
                            append("dep", "2026-04-09 09:00:00")
                            append("arr", "2026-04-09 11:00:00")
                            append("status", "scheduled")
                            append("capacity", "180")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/flights?error=Missing flight id", response.headers[HttpHeaders.Location])
        }

    /**
     * Flight updates should reject missing route endpoints.
     */
    @Test
    fun updateFlightRejectsMissingOriginOrDestination() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")
            val originAirportId = seedAirport("LHR", "London Heathrow")
            val destinationAirportId = seedAirport("DXB", "Dubai International")
            val flightId = seedFlight(originAirportId, destinationAirportId, flightNumberValue = 919)

            val response =
                client.submitForm(
                    url = "/staff/flights/update",
                    formParameters =
                        parameters {
                            append("id", flightId.toString())
                            append("flightNumber", "919")
                            append("originId", originAirportId.toString())
                            append("dep", "2026-04-09 09:00:00")
                            append("arr", "2026-04-09 11:00:00")
                            append("status", "scheduled")
                            append("capacity", "180")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals(
                "/staff/flights?error=Please select origin and destination&edit=$flightId",
                response.headers[HttpHeaders.Location],
            )
        }

    /**
     * Flight updates should reject routes with the same origin and destination.
     */
    @Test
    fun updateFlightRejectsSameOriginAndDestination() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")
            val airportId = seedAirport("LHR", "London Heathrow")
            val destinationAirportId = seedAirport("DXB", "Dubai International")
            val flightId = seedFlight(airportId, destinationAirportId, flightNumberValue = 929)

            val response =
                client.submitForm(
                    url = "/staff/flights/update",
                    formParameters =
                        parameters {
                            append("id", flightId.toString())
                            append("flightNumber", "929")
                            append("originId", airportId.toString())
                            append("destId", airportId.toString())
                            append("dep", "2026-04-09 09:00:00")
                            append("arr", "2026-04-09 11:00:00")
                            append("status", "scheduled")
                            append("capacity", "180")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals(
                "/staff/flights?error=Origin and destination cannot be the same&edit=$flightId",
                response.headers[HttpHeaders.Location],
            )
        }

    /**
     * Staff should be able to delete an existing flight.
     */
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

    /**
     * Flight deletion should reject requests with no flight id.
     */
    @Test
    fun deleteFlightRejectsMissingFlightId() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")

            val response = client.submitForm(url = "/staff/flights/delete")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/flights?error=Missing flight id", response.headers[HttpHeaders.Location])
        }

    /**
     * Flight deletion should remove the flight row.
     */
    @Test
    fun deleteFlightRemovesFlightRow() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            client.get("/__health")
            val originAirportId = seedAirport("LHR", "London Heathrow")
            val destinationAirportId = seedAirport("DXB", "Dubai International")
            val flightId = seedFlight(originAirportId, destinationAirportId, flightNumberValue = 515)

            val response =
                client.submitForm(
                    url = "/staff/flights/delete",
                    formParameters =
                        parameters {
                            append("id", flightId.toString())
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertFalse(flightExists(flightId))
        }

    /**
     * Creating a flight should automatically generate seats for that flight.
     */
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

    /**
     * Updating a flight with existing seats should not duplicate them.
     */
    @Test
    fun seatGenerationDoesNotDuplicateExistingSeats() =
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
                            append("flightNumber", "606")
                            append("originId", originAirportId.toString())
                            append("destId", destinationAirportId.toString())
                            append("dep", "2026-04-06 09:00:00")
                            append("arr", "2026-04-06 11:00:00")
                            append("status", "scheduled")
                            append("capacity", "6")
                        },
                )
            assertEquals(HttpStatusCode.Found, createResponse.status)

            val flightId = latestFlightId()
            assertEquals(6, seatCountForFlight(flightId))

            val updateResponse =
                client.submitForm(
                    url = "/staff/flights/update",
                    formParameters =
                        parameters {
                            append("id", flightId.toString())
                            append("flightNumber", "606")
                            append("originId", originAirportId.toString())
                            append("destId", destinationAirportId.toString())
                            append("dep", "2026-04-06 10:00:00")
                            append("arr", "2026-04-06 12:00:00")
                            append("status", "delayed")
                            append("capacity", "6")
                        },
                )

            assertEquals(HttpStatusCode.Found, updateResponse.status)
            assertEquals(6, seatCountForFlight(flightId))
        }

    /**
     * Check the flight is in table
     * @param flightId
     * @return true if it exists, false if not
     */
    private fun flightExists(flightId: Int): Boolean =
        transaction {
            FlightTable
                .select { FlightTable.id eq flightId }
                .any()
        }

    /**
     * Counts total number of seats in a flight
     * @param flightId
     * @return seat count
     */
    private fun seatCountForFlight(flightId: Int): Int =
        transaction {
            SeatTable
                .select { SeatTable.flightId eq flightId }
                .count()
                .toInt()
        }
}
