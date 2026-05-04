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

class ConfirmationRoutesTest : IntegrationTestSupport() {
    // Unauthenticated users should be redirected to login from confirmation.
    @Test
    fun unauthenticatedConfirmationRedirectsToLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/confirmation")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login", response.headers[HttpHeaders.Location])
        }

    // Users without a booking session should be redirected home from confirmation.
    @Test
    fun confirmationRedirectsHomeWhenBookingSessionMissing() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response = client.get("/confirmation")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/home", response.headers[HttpHeaders.Location])
        }

    // Confirmation should render the one-way booking session summary.
    @Test
    fun confirmationPageShowsOneWayBookingSummary() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            client.seedBookingSession()

            val response = client.get("/confirmation")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Booking Confirmed"))
            assertTrue(body.contains("Trip Type"))
            assertTrue(body.contains("One Way"))
            assertTrue(body.contains("1 Adult"))
            assertTrue(body.contains("LHR"))
            assertTrue(body.contains("DXB"))
            assertTrue(body.contains("Outbound Flight"))
            assertTrue(body.contains("Ticket Price"))
            assertTrue(body.contains("Loyalty Points Earned"))
        }

    // Confirmation should include return details when the booking session has a return leg.
    @Test
    fun confirmationPageShowsReturnBookingSummary() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            client.selectReturnTrip()

            val response = client.get("/confirmation")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Booking Confirmed"))
            assertTrue(body.contains("Return"))
            assertTrue(body.contains("Return Flight"))
            assertTrue(body.contains("Return Date"))
            assertTrue(body.contains("2026-04-10"))
            assertTrue(body.contains("30"))
        }

    private suspend fun io.ktor.client.HttpClient.selectReturnTrip() {
        submitForm(
            url = "/flights/select",
            formParameters =
                parameters {
                    append("flightId", "10")
                    append("fareId", "20")
                    append("leg", "outbound")
                    append("tripType", "return")
                    append("origin", "LHR")
                    append("destination", "DXB")
                    append("departureDate", "2026-04-01")
                    append("returnDate", "2026-04-10")
                    append("adults", "1")
                },
        )

        submitForm(
            url = "/flights/select",
            formParameters =
                parameters {
                    append("flightId", "30")
                    append("fareId", "40")
                    append("leg", "return")
                    append("tripType", "return")
                    append("origin", "LHR")
                    append("destination", "DXB")
                    append("departureDate", "2026-04-01")
                    append("returnDate", "2026-04-10")
                    append("adults", "1")
                },
        )
    }
}
