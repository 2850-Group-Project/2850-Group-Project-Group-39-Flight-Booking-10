package com.flightbooking

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.post
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.setBody
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

class BookingRoutesTest : IntegrationTestSupport() {
    // Check that submitting passenger details without logging in redirects to login.
    @Test
    fun unauthenticatedPassengerSubmitRedirectsToLogin() = testApplication {
        configureApp()
        val client = createClient { followRedirects = false }

        val response = client.submitForm(
            url = "/flights/passengers/submit",
            formParameters = parameters {
                append("passengers[0][firstName]", "Alex")
                append("passengers[0][lastName]", "Student")
            }
        )

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/login", response.headers[HttpHeaders.Location])
    }

    // Check that submitting passenger details without a booking session redirects home.
    @Test
    fun passengerSubmitRedirectsHomeWhenBookingSessionMissing() = testApplication {
        configureApp()
        val client = createAuthenticatedUserClient()

        val response = client.submitForm(
            url = "/flights/passengers/submit",
            formParameters = parameters {
                append("passengers[0][firstName]", "Alex")
                append("passengers[0][lastName]", "Student")
            }
        )

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/home", response.headers[HttpHeaders.Location])
    }

    // Check that submitting one passenger saves their details in the booking session.
    @Test
    fun passengerSubmitStoresPassengerDataInBookingSession() = testApplication {
        configureApp()
        val client = createAuthenticatedUserClient()
        client.seedBookingSession(adults = "1")

        val response = client.submitForm(
            url = "/flights/passengers/submit",
            formParameters = parameters {
                append("passengers[0][type]", "adult")
                append("passengers[0][title]", "Mr")
                append("passengers[0][firstName]", "Alex")
                append("passengers[0][lastName]", "Student")
                append("passengers[0][dateOfBirth]", "2000-01-01")
                append("passengers[0][gender]", "Male")
                append("passengers[0][email]", "alex@example.com")
                append("passengers[0][nationality]", "British")
                append("passengers[0][documentType]", "Passport")
                append("passengers[0][documentNumber]", "A1234567")
                append("passengers[0][documentCountry]", "GB")
                append("passengers[0][documentExpiry]", "2030-01-01")
            }
        )

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/flights/seat_selection", response.headers[HttpHeaders.Location])

        val bookingCookie = client.cookies(Url("http://localhost/")).firstOrNull { it.name == "BOOKING_SESSION" }
        assertNotNull(bookingCookie)
        assertTrue(bookingCookie.value.contains("Alex"))
        assertTrue(bookingCookie.value.contains("Student"))
        assertTrue(bookingCookie.value.contains("A1234567"))
    }

    // Check that submitting multiple passengers saves all of their details in the booking session.
    @Test
    fun passengerSubmitHandlesMultiplePassengers() = testApplication {
        configureApp()
        val client = createAuthenticatedUserClient()
        client.seedBookingSession(adults = "2")

        val response = client.submitForm(
            url = "/flights/passengers/submit",
            formParameters = parameters {
                append("passengers[0][type]", "adult")
                append("passengers[0][title]", "Ms")
                append("passengers[0][firstName]", "Alice")
                append("passengers[0][lastName]", "Brown")
                append("passengers[0][dateOfBirth]", "1995-03-10")
                append("passengers[0][gender]", "Female")
                append("passengers[0][email]", "alice@example.com")
                append("passengers[0][nationality]", "Canadian")
                append("passengers[0][documentType]", "Passport")
                append("passengers[0][documentNumber]", "P1111111")
                append("passengers[0][documentCountry]", "CA")
                append("passengers[0][documentExpiry]", "2031-03-10")
                append("passengers[1][type]", "adult")
                append("passengers[1][title]", "Mr")
                append("passengers[1][firstName]", "Ben")
                append("passengers[1][lastName]", "Taylor")
                append("passengers[1][dateOfBirth]", "1992-07-22")
                append("passengers[1][gender]", "Male")
                append("passengers[1][email]", "ben@example.com")
                append("passengers[1][nationality]", "Irish")
                append("passengers[1][documentType]", "Passport")
                append("passengers[1][documentNumber]", "P2222222")
                append("passengers[1][documentCountry]", "IE")
                append("passengers[1][documentExpiry]", "2032-07-22")
            }
        )

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/flights/seat_selection", response.headers[HttpHeaders.Location])

        val bookingCookie = client.cookies(Url("http://localhost/")).firstOrNull { it.name == "BOOKING_SESSION" }
        assertNotNull(bookingCookie)
        assertTrue(bookingCookie.value.contains("Alice"))
        assertTrue(bookingCookie.value.contains("Brown"))
        assertTrue(bookingCookie.value.contains("Ben"))
        assertTrue(bookingCookie.value.contains("Taylor"))
    }

    // Create a logged-in user client for booking route tests.
    private suspend fun ApplicationTestBuilder.createAuthenticatedUserClient(): HttpClient {
        val client = createClient {
            followRedirects = false
            install(HttpCookies)
        }

        val registerResponse = client.post("/register") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                listOf(
                    "email" to "student@example.com",
                    "password" to "Password123!",
                    "confirmPassword" to "Password123!",
                    "firstName" to "Student",
                    "lastName" to "Alex"
                ).formUrlEncode()
            )
        }
        assertEquals(HttpStatusCode.Found, registerResponse.status)

        val loginResponse = client.post("/login") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                listOf(
                    "email" to "student@example.com",
                    "password" to "Password123!"
                ).formUrlEncode()
            )
        }
        assertEquals(HttpStatusCode.Found, loginResponse.status)

        return client
    }

    // Seed a booking session through the flight selection route before passenger submission.
    private suspend fun HttpClient.seedBookingSession(adults: String) {
        val response = submitForm(
            url = "/flights/select",
            formParameters = parameters {
                append("flightId", "10")
                append("fareId", "20")
                append("leg", "outbound")
                append("tripType", "oneway")
                append("origin", "LHR")
                append("destination", "DXB")
                append("departureDate", "2026-04-01")
                append("adults", adults)
            }
        )

        assertEquals(HttpStatusCode.OK, response.status)
    }
}
