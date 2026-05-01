package com.flightbooking

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class StaffNotificationsRoutesTest : IntegrationTestSupport() {
    // Unauthenticated staff users should be redirected to staff login from notifications.
    @Test
    fun unauthenticatedStaffNotificationsRedirectsToStaffLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/staff/notifications")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/login", response.headers[HttpHeaders.Location])
        }

    // Unauthenticated notification status updates should be redirected to staff login.
    @Test
    fun unauthenticatedStaffNotificationStatusRedirectsToStaffLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.submitForm(url = "/staff/notifications/status")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/login", response.headers[HttpHeaders.Location])
        }

    // Unauthenticated notification deletes should be redirected to staff login.
    @Test
    fun unauthenticatedStaffNotificationDeleteRedirectsToStaffLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.submitForm(url = "/staff/notifications/delete")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/login", response.headers[HttpHeaders.Location])
        }
}
