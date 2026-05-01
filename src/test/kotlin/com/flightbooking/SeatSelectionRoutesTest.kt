package com.flightbooking

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class SeatSelectionRoutesTest : IntegrationTestSupport() {
    // Unauthenticated users should be redirected to login from seat selection.
    @Test
    fun unauthenticatedSeatSelectionRedirectsToLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/flights/seats")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login", response.headers[HttpHeaders.Location])
        }

    // Unauthenticated seat submissions should be redirected to login.
    @Test
    fun unauthenticatedSeatSubmitRedirectsToLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.submitForm(url = "/flights/seats")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login", response.headers[HttpHeaders.Location])
        }
}
