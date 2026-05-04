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
import kotlin.test.assertFalse
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
            assertTrue(body.contains("Request type"))
        }

    // Success query should show the submitted confirmation message.
    @Test
    fun complaintsPageShowsSuccessMessageFromQuery() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response = client.get("/complaints?success=true")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("Your complaint has been submitted"))
        }

    // Error queries should show the matching validation or server error message.
    @Test
    fun complaintsPageShowsErrorMessagesFromQuery() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val missingFieldsResponse = client.get("/complaints?error=missing_fields")
            val serverErrorResponse = client.get("/complaints?error=server_error")

            assertEquals(HttpStatusCode.OK, missingFieldsResponse.status)
            assertTrue(missingFieldsResponse.bodyAsText().contains("Please provide a full message"))
            assertEquals(HttpStatusCode.OK, serverErrorResponse.status)
            assertTrue(serverErrorResponse.bodyAsText().contains("Sorry, something went wrong"))
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

    // Complaint submissions should handle unusual but valid input safely.
    @Test
    fun complaintSubmitHandlesUnusualInputSafely() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response =
                client.submitForm(
                    url = "/complaints/submit",
                    formParameters =
                        parameters {
                            append("type", "other")
                            append("message", "Seat changed > twice & meal option was \"missing\"")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/complaints?success=true", response.headers[HttpHeaders.Location])
            assertEquals(1, complaintCount())
            val complaint = latestComplaint()
            assertEquals("other", complaint.type)
            assertEquals("Seat changed > twice & meal option was \"missing\"", complaint.message)
            assertEquals("open", complaint.status)
        }

    // The profile complaints page should show an empty state when the user has no complaints.
    @Test
    fun profileComplaintsShowsEmptyState() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response = client.get("/profile/complaints")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("No complaints yet"))
            assertTrue(body.contains("submit a complaint"))
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
            assertTrue(body.contains("My Requests"))
            assertTrue(body.contains("technical"))
            assertTrue(body.contains("The booking page froze"))
            assertTrue(body.contains("open"))
        }

    // Profile complaints should not include complaints owned by another user.
    @Test
    fun profileComplaintsOnlyShowsCurrentUsersComplaints() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val userId = userIdByEmail()
            val otherUserId = seedUser("other@example.com", "Other", "User")
            seedComplaint(userId, "service", "Current user complaint")
            seedComplaint(otherUserId, "technical", "Other user complaint")

            val response = client.get("/profile/complaints")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Current user complaint"))
            assertFalse(body.contains("Other user complaint"))
        }
}
