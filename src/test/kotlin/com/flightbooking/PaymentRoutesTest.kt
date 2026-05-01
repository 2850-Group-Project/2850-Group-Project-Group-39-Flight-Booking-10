package com.flightbooking

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentRoutesTest : IntegrationTestSupport() {
    // Users without a booking session should be redirected home from the payment page.
    @Test
    fun paymentPageRedirectsHomeWhenBookingSessionMissing() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/payment")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/home", response.headers[HttpHeaders.Location])
        }

    // Posting payment without a booking session should also redirect home.
    @Test
    fun paymentSubmitRedirectsHomeWhenBookingSessionMissing() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.submitForm(url = "/payment")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/home", response.headers[HttpHeaders.Location])
        }
}
