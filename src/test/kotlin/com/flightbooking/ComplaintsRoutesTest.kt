package com.flightbooking

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ComplaintsRoutesTest : IntegrationTestSupport() {
    // Unauthenticated users should be redirected to login from the complaints page.
    @Test
    fun unauthenticatedComplaintsRedirectsToLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/complaints")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login", response.headers[HttpHeaders.Location])
        }

    // Unauthenticated complaint submissions should be redirected to login.
    @Test
    fun unauthenticatedComplaintSubmitRedirectsToLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.submitForm(url = "/complaints/submit")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login", response.headers[HttpHeaders.Location])
        }

    // Unauthenticated users should be redirected to login from profile complaints.
    @Test
    fun unauthenticatedProfileComplaintsRedirectsToLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/profile/complaints")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login", response.headers[HttpHeaders.Location])
        }
}
