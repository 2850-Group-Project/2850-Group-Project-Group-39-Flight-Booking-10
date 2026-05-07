package com.flightbooking

import com.flightbooking.tables.ChangeRequestTable
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaffNotificationsRoutesTest : IntegrationTestSupport() {
    /**
     * Unauthenticated staff users should be redirected to staff login from notifications.
     */
    @Test
    fun unauthenticatedStaffNotificationsRedirectsToStaffLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/staff/notifications")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/login", response.headers[HttpHeaders.Location])
        }

    /**
     * Unauthenticated notification status updates should be redirected to staff login.
     */
    @Test
    fun unauthenticatedStaffNotificationStatusRedirectsToStaffLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.submitForm(url = "/staff/notifications/status")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/login", response.headers[HttpHeaders.Location])
        }

    /**
     * Unauthenticated notification deletes should be redirected to staff login.
     */
    @Test
    fun unauthenticatedStaffNotificationDeleteRedirectsToStaffLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.submitForm(url = "/staff/notifications/delete")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/login", response.headers[HttpHeaders.Location])
        }

    /**
     * Authenticated staff users should be able to load the notifications page.
     */
    @Test
    fun authenticatedStaffNotificationsPageLoads() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()

            val response = client.get("/staff/notifications")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("Notifications"))
            assertTrue(body.contains("Review and manage user change requests."))
        }

    /**
     * The notifications page should list seeded change request details.
     */
    @Test
    fun notificationsPageListsSeededChangeRequests() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            val request = seedStaffChangeRequest()

            val response = client.get("/staff/notifications")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("CR-${request.requestId}"))
            assertTrue(body.contains(request.email))
            assertTrue(body.contains("Booking #${request.bookingId}"))
            assertTrue(body.contains("Segment #${request.segmentId}"))
            assertTrue(body.contains("Flight 700"))
            assertTrue(body.contains("Flight 701"))
            assertTrue(body.contains("Seat: 2A"))
            assertTrue(body.contains("Reason: Earlier flight preferred"))
        }

    /**
     * Numeric search should filter notifications by change request id.
     */
    @Test
    fun notificationsSearchByRequestIdFiltersList() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            val request =
                seedStaffChangeRequest(
                    StaffChangeRequestSeedOptions().apply {
                        reason = "First request"
                    },
                )
            val otherRequest =
                seedStaffChangeRequest(
                    StaffChangeRequestSeedOptions().apply {
                        email = "other@example.com"
                        reason = "Second request"
                        originCode = "CDG"
                        destinationCode = "JFK"
                        flightNumber = 710
                        requestedFlightNumber = 711
                        seatCode = "3A"
                        fareClassId = request.fareClassId
                    },
                )

            val response = client.get("/staff/notifications?q=${request.requestId}")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("First request"))
            assertFalse(body.contains("Second request"))
            assertFalse(body.contains("CR-${otherRequest.requestId}"))
        }

    /**
     * Non-numeric search cannot match request ids and should return an empty list.
     */
    @Test
    fun notificationsSearchWithTextReturnsEmptyList() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            seedStaffChangeRequest(
                StaffChangeRequestSeedOptions().apply {
                    reason = "Visible request"
                },
            )

            val response = client.get("/staff/notifications?q=not-a-number")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("No change requests found."))
            assertFalse(body.contains("Visible request"))
        }

    /**
     * Staff status updates should persist each supported status value.
     */
    @Test
    fun notificationStatusUpdateChangesRequestStatus() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            val request = seedStaffChangeRequest()

            listOf("approved", "rejected", "cancelled", "complete").forEach { status ->
                val response =
                    client.submitForm(
                        url = "/staff/notifications/status",
                        formParameters =
                            parameters {
                                append("requestId", request.requestId.toString())
                                append("status", status)
                            },
                    )

                assertEquals(HttpStatusCode.Found, response.status)
                assertEquals("/staff/notifications?ok=Request updated", response.headers[HttpHeaders.Location])
                assertEquals(status, changeRequestStatus(request.requestId))
            }
        }

    /**
     * Invalid statuses should redirect with an error and leave the request unchanged.
     */
    @Test
    fun notificationStatusUpdateRejectsInvalidStatus() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            val request = seedStaffChangeRequest()

            val response =
                client.submitForm(
                    url = "/staff/notifications/status",
                    formParameters =
                        parameters {
                            append("requestId", request.requestId.toString())
                            append("status", "archived")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/notifications?error=Invalid status", response.headers[HttpHeaders.Location])
            assertEquals("pending", changeRequestStatus(request.requestId))
        }

    /**
     * Missing request id or status should redirect with a validation error.
     */
    @Test
    fun notificationStatusUpdateRejectsMissingRequestIdOrStatus() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()

            val missingIdResponse =
                client.submitForm(
                    url = "/staff/notifications/status",
                    formParameters =
                        parameters {
                            append("status", "approved")
                        },
                )
            val missingStatusResponse =
                client.submitForm(
                    url = "/staff/notifications/status",
                    formParameters =
                        parameters {
                            append("requestId", "1")
                        },
                )

            assertEquals(HttpStatusCode.Found, missingIdResponse.status)
            assertEquals(
                "/staff/notifications?error=Missing requestId/status",
                missingIdResponse.headers[HttpHeaders.Location],
            )
            assertEquals(HttpStatusCode.Found, missingStatusResponse.status)
            assertEquals(
                "/staff/notifications?error=Missing requestId/status",
                missingStatusResponse.headers[HttpHeaders.Location],
            )
        }

    /**
     * Deleting a notification should remove the matching change request row.
     */
    @Test
    fun notificationDeleteRemovesChangeRequestRow() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            val request = seedStaffChangeRequest()

            val response =
                client.submitForm(
                    url = "/staff/notifications/delete",
                    formParameters =
                        parameters {
                            append("requestId", request.requestId.toString())
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/notifications?ok=Request deleted", response.headers[HttpHeaders.Location])
            assertFalse(changeRequestExists(request.requestId))
        }

    /**
     * Missing or unknown delete ids should redirect with an error.
     */
    @Test
    fun notificationDeleteRejectsMissingOrUnknownRequestId() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()

            val missingResponse = client.submitForm(url = "/staff/notifications/delete")
            val unknownResponse =
                client.submitForm(
                    url = "/staff/notifications/delete",
                    formParameters =
                        parameters {
                            append("requestId", "9999")
                        },
                )

            assertEquals(HttpStatusCode.Found, missingResponse.status)
            assertEquals("/staff/notifications?error=Missing requestId", missingResponse.headers[HttpHeaders.Location])
            assertEquals(HttpStatusCode.Found, unknownResponse.status)
            assertEquals("/staff/notifications?error=Delete failed", unknownResponse.headers[HttpHeaders.Location])
        }

    /**
     * Seeds a staff change request
     * @param options: seed values
     * @return the seeded change request
     */
    private fun seedStaffChangeRequest(
        options: StaffChangeRequestSeedOptions = StaffChangeRequestSeedOptions(),
    ): SeededStaffChangeRequest {
        val email = options.email
        val reason = options.reason
        val originCode = options.originCode
        val destinationCode = options.destinationCode
        val flightNumber = options.flightNumber
        val requestedFlightNumber = options.requestedFlightNumber
        val seatCode = options.seatCode
        val fareClassId = options.fareClassId

        val originAirportId = seedAirport(originCode, "$originCode Airport")
        val destinationAirportId = seedAirport(destinationCode, "$destinationCode Airport")
        val currentFlightId =
            seedFlight(
                originAirportId,
                destinationAirportId,
                flightNumberValue = flightNumber,
            )
        val requestedFlightId =
            seedFlight(
                originAirportId,
                destinationAirportId,
                flightNumberValue = requestedFlightNumber,
            )
        val actualFareClassId = fareClassId ?: seedFareClass()
        val flightFareId = seedFlightFare(currentFlightId, actualFareClassId)
        val userId = seedUser(email, "Pat", "Smith")
        val bookingId = seedBooking(userId, bookingReference = "NOTIFY$flightNumber")
        val passengerId = seedPassenger(bookingId)
        val segmentId = seedBookingSegment(bookingId, currentFlightId, flightFareId)
        seedSeatAssignment(passengerId, segmentId, seatId = null)
        val requestedSeatId = seedSeat(requestedFlightId, seatCode)
        val requestId =
            transaction {
                ChangeRequestTable.insert {
                    it[ChangeRequestTable.userId] = userId
                    it[ChangeRequestTable.bookingId] = bookingId
                    it[ChangeRequestTable.bookingSegmentId] = segmentId
                    it[ChangeRequestTable.currentFlightId] = currentFlightId
                    it[ChangeRequestTable.requestedFlightId] = requestedFlightId
                    it[ChangeRequestTable.requestedSeatId] = requestedSeatId
                    it[ChangeRequestTable.reason] = reason
                    it[ChangeRequestTable.status] = "pending"
                    it[ChangeRequestTable.createdAt] = "2026-04-01T00:00:00Z"
                    it[ChangeRequestTable.updatedAt] = "2026-04-01T00:00:00Z"
                }.resultedValues!!.first()[ChangeRequestTable.id]
            }

        return SeededStaffChangeRequest(
            requestId = requestId,
            email = email,
            bookingId = bookingId,
            segmentId = segmentId,
            fareClassId = actualFareClassId,
        )
    }

    /**
     * Data class definition for seeded change request parameters
     */
    private class StaffChangeRequestSeedOptions {
        var email: String = "passenger@example.com"
        var reason: String = "Earlier flight preferred"
        var originCode: String = "LHR"
        var destinationCode: String = "DXB"
        var flightNumber: Int = 700
        var requestedFlightNumber: Int = 701
        var seatCode: String = "2A"
        var fareClassId: Int? = null
    }

    /**
     * Searches for a change requests status with id
     * @param requestId
     * @return the status found
     */
    private fun changeRequestStatus(requestId: Int): String =
        transaction {
            ChangeRequestTable
                .select { ChangeRequestTable.id eq requestId }
                .limit(1)
                .first()[ChangeRequestTable.status]
        }

    /**
     * Check a change request is in table
     * @param requestId
     * @return true if it does, false otherwise
     */
    private fun changeRequestExists(requestId: Int): Boolean =
        transaction {
            ChangeRequestTable
                .select { ChangeRequestTable.id eq requestId }
                .any()
        }

    /**
     * Data class definition for the seeded change request
     */
    private data class SeededStaffChangeRequest(
        val requestId: Int,
        val email: String,
        val bookingId: Int,
        val segmentId: Int,
        val fareClassId: Int,
    )
}
