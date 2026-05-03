package com.flightbooking

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StaffAuthRoutesTest : IntegrationTestSupport() {
    // A staff user should be able to register, then log in successfully.
    @Test
    fun registerThenLoginRedirectsToDashboardAndSetsSessionCookie() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val registerResponse = client.registerStaff()
            assertEquals(HttpStatusCode.Found, registerResponse.status)
            assertEquals("/staff/login", registerResponse.headers[HttpHeaders.Location])

            val loginResponse = client.loginStaff()
            assertEquals(HttpStatusCode.Found, loginResponse.status)
            assertEquals("/staff/dashboard", loginResponse.headers[HttpHeaders.Location])
            assertNotNull(
                loginResponse.headers.getAll(HttpHeaders.SetCookie)?.find { it.contains("STAFF_SESSION") },
            )
        }

    // Staff login should fail when the password is incorrect.
    @Test
    fun loginRejectsInvalidPassword() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            client.registerStaff()
            val response = client.loginStaff(password = "WrongPass123!")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("Invalid staff credentials"))
        }

    // Staff registration should fail when the invite code is invalid.
    @Test
    fun staffRegisterRejectsWrongInviteCode() =
        testApplication {
            configureApp()

            val response =
                client.post("/staff/register") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf(
                            "firstName" to "Alex",
                            "lastName" to "Admin",
                            "email" to "staff@example.com",
                            "password" to "StrongPass123!",
                            "confirmPassword" to "StrongPass123!",
                            "role" to "admin",
                            "inviteCode" to "WRONG-CODE",
                        ).formUrlEncode(),
                    )
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("Invalid invite code"))
        }

    // Staff registration should fail when the account already exists.
    @Test
    fun duplicateRegisterShowsAlreadyExistsError() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val firstResponse = client.registerStaff()
            assertEquals(HttpStatusCode.Found, firstResponse.status)
            assertEquals("/staff/login", firstResponse.headers[HttpHeaders.Location])

            val secondResponse = client.registerStaff()
            assertEquals(HttpStatusCode.OK, secondResponse.status)
            assertTrue(secondResponse.bodyAsText().contains("Staff already exists"))
        }

    // Staff logout should clear the session and redirect back to staff login.
    @Test
    fun logoutClearsSessionAndRedirectsToStaffLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            client.registerStaff()
            val loginResponse = client.loginStaff()
            assertEquals(HttpStatusCode.Found, loginResponse.status)
            assertEquals("/staff/dashboard", loginResponse.headers[HttpHeaders.Location])

            val logoutResponse = client.get("/staff/logout")
            assertEquals(HttpStatusCode.Found, logoutResponse.status)
            assertEquals("/staff/login", logoutResponse.headers[HttpHeaders.Location])

            val dashboardResponse = client.get("/staff/dashboard")
            assertEquals(HttpStatusCode.Found, dashboardResponse.status)
            assertEquals("/staff/login", dashboardResponse.headers[HttpHeaders.Location])
        }
}
