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
    /**
     * Health check should return ok for uptime checks.
     */
    @Test
    fun healthCheckReturnsOk() =
        testApplication {
            configureApp()

            val response = client.get("/__health")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("ok", response.bodyAsText())
        }

    /**
     * The landing route should send users to login.
     */
    @Test
    fun landingRouteRedirectsToLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login", response.headers[HttpHeaders.Location])
        }

    /**
     * Smoke test to verify the login page renders successfully.
     */
    @Test
    fun loginPageLoads() =
        testApplication {
            configureApp()

            val response = client.get("/login")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("Login"))
        }

    /**
     * Unauthenticated users should be redirected to the login page from home.
     */
    @Test
    fun unauthenticatedHomeRedirectsToLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/home")
            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login", response.headers[HttpHeaders.Location])
        }

    /**
     * Unknown routes should render the shared not found status.
     */
    @Test
    fun unknownRouteReturnsNotFound() =
        testApplication {
            configureApp()

            val response = client.get("/missing-page")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
}
