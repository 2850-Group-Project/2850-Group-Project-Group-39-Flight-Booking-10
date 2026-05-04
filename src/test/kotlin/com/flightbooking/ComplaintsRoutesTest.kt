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

    // Authenticated users should be able to open the complaints form.
    @Test
    fun complaintsPageLoadsForAuthenticatedUser() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response = client.get("/complaints")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Submit a"))
            assertTrue(body.contains("Complaint"))
            assertTrue(body.contains("Complaint type"))
        }

    // Submitted complaints should be saved for the logged-in user.
    @Test
    fun complaintSubmitCreatesComplaint() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val userId = userIdByEmail()

            val response =
                client.submitForm(
                    url = "/complaints/submit",
                    formParameters =
                        parameters {
                            append("type", "service")
                            append("message", "The check-in queue was too long")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/complaints?success=true", response.headers[HttpHeaders.Location])
            assertEquals(1, complaintCount())
            val complaint = latestComplaint()
            assertEquals(userId, complaint.userId)
            assertEquals("service", complaint.type)
            assertEquals("The check-in queue was too long", complaint.message)
            assertEquals("open", complaint.status)
        }

    // Short complaint submissions should redirect with a validation error.
    @Test
    fun complaintSubmitRejectsMissingMessage() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response =
                client.submitForm(
                    url = "/complaints/submit",
                    formParameters =
                        parameters {
                            append("type", "service")
                            append("message", "")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/complaints?error=missing_fields", response.headers[HttpHeaders.Location])
            assertEquals(0, complaintCount())
        }

    // The profile complaints page should list complaints for the logged-in user.
    @Test
    fun profileComplaintsShowsUserComplaints() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val userId = userIdByEmail()
            seedComplaint(userId, "technical", "The booking page froze")

            val response = client.get("/profile/complaints")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("My Complaints"))
            assertTrue(body.contains("technical"))
            assertTrue(body.contains("The booking page froze"))
            assertTrue(body.contains("open"))
        }
}
