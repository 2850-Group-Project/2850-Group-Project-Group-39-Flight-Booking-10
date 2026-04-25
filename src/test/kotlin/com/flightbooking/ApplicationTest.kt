package com.flightbooking

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest : IntegrationTestSupport() {
    @Test
    // Smoke test to verify the login page renders successfully.
    fun loginPageLoads() = testApplication {
        configureApp()

        val response = client.get("/login")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Login"))
    }

    @Test
    // Unauthenticated users should be redirected to the login page from home.
    fun unauthenticatedHomeRedirectsToLogin() = testApplication {
        configureApp()
        val client = createClient { followRedirects = false }

        val response = client.get("/home")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/login", response.headers[HttpHeaders.Location])
    }
}
