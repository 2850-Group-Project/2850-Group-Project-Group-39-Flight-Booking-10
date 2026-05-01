package com.flightbooking

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FlightRoutesTest : IntegrationTestSupport() {
    // Unauthenticated users should be redirected to login when selecting a flight.
    @Test
    fun unauthenticatedFlightSelectRedirectsToLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response =
                client.submitForm(
                    url = "/flights/select",
                    formParameters =
                        parameters {
                            append("flightId", "1")
                            append("fareId", "1")
                            append("leg", "outbound")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login", response.headers[HttpHeaders.Location])
        }

    // Flight selection should reject requests with no flight id.
    @Test
    fun flightSelectRejectsMissingFlightId() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response =
                client.submitForm(
                    url = "/flights/select",
                    formParameters =
                        parameters {
                            append("fareId", "1")
                            append("leg", "outbound")
                        },
                )

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("Missing flightId", response.bodyAsText())
        }

    // Flight selection should reject requests with no fare id.
    @Test
    fun flightSelectRejectsMissingFareId() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response =
                client.submitForm(
                    url = "/flights/select",
                    formParameters =
                        parameters {
                            append("flightId", "1")
                            append("leg", "outbound")
                        },
                )

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("Missing fareId", response.bodyAsText())
        }

    // Flight selection should reject requests with no leg value.
    @Test
    fun flightSelectRejectsMissingLeg() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response =
                client.submitForm(
                    url = "/flights/select",
                    formParameters =
                        parameters {
                            append("flightId", "1")
                            append("fareId", "1")
                        },
                )

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("Missing leg", response.bodyAsText())
        }

    // Flight selection should reject requests with an invalid leg value.
    @Test
    fun flightSelectRejectsInvalidLeg() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response =
                client.submitForm(
                    url = "/flights/select",
                    formParameters =
                        parameters {
                            append("flightId", "1")
                            append("fareId", "1")
                            append("leg", "sideways")
                        },
                )

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("Invalid leg", response.bodyAsText())
        }

    // Selecting an outbound flight should store the outbound booking session values.
    @Test
    fun flightSelectStoresOutboundSelectionInBookingSession() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response =
                client.submitForm(
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
                            append("adults", "1")
                        },
                )

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("ok", response.bodyAsText())

            val bookingCookie = client.cookies(Url("http://localhost/")).firstOrNull { it.name == "BOOKING_SESSION" }
            assertNotNull(bookingCookie)
            assertTrue(bookingCookie.value.isNotBlank())
        }

    // Selecting a return flight should store the return booking session values.
    @Test
    fun flightSelectStoresReturnSelectionInBookingSession() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            client.submitForm(
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

            val response =
                client.submitForm(
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

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("ok", response.bodyAsText())

            val bookingCookie = client.cookies(Url("http://localhost/")).firstOrNull { it.name == "BOOKING_SESSION" }
            assertNotNull(bookingCookie)
            assertTrue(bookingCookie.value.isNotBlank())
        }

    // Create a logged-in user client for flight route tests.
    private suspend fun ApplicationTestBuilder.createAuthenticatedUserClient(): HttpClient {
        val client =
            createClient {
                followRedirects = false
                install(HttpCookies)
            }

        val registerResponse =
            client.post("/register") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf(
                        "email" to "student@example.com",
                        "password" to "Password123!",
                        "confirmPassword" to "Password123!",
                        "firstName" to "Student",
                        "lastName" to "Alex",
                    ).formUrlEncode(),
                )
            }
        assertEquals(HttpStatusCode.Found, registerResponse.status)

        val loginResponse =
            client.post("/login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf(
                        "email" to "student@example.com",
                        "password" to "Password123!",
                    ).formUrlEncode(),
                )
            }
        assertEquals(HttpStatusCode.Found, loginResponse.status)

        return client
    }
}
