package com.flightbooking

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AuthRoutesTest : IntegrationTestSupport() {
    @Test
    // The register page should load successfully.
    fun registerPageLoads() = testApplication {
        configureApp()

        val response = client.get("/register")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(true, response.bodyAsText().contains("Create Account"))
    }

    @Test
    // The login page should load successfully.
    fun loginPageLoads() = testApplication {
        configureApp()

        val response = client.get("/login")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(true, response.bodyAsText().contains("Login"))
    }

    @Test
    // A user should be able to register, then log in and receive a session cookie.
    fun registerThenLoginRedirectsToHomeAndSetsSessionCookie() = testApplication {
        configureApp()
        val client = createClient { followRedirects = false }

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
        assertEquals("/login", registerResponse.headers[HttpHeaders.Location])

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
        assertEquals("/home", loginResponse.headers[HttpHeaders.Location])
        assertNotNull(loginResponse.headers.getAll(HttpHeaders.SetCookie)?.find { it.contains("USER_SESSION") })
    }

    @Test
    // Registration should fail when the passwords do not match.
    fun registerRejectsPasswordMismatch() = testApplication {
        configureApp()

        val response = client.post("/register") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                listOf(
                    "email" to "student@example.com",
                    "password" to "Password123!",
                    "confirmPassword" to "Mismatch123!",
                    "firstName" to "Student",
                    "lastName" to "Alex"
                ).formUrlEncode()
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(true, response.bodyAsText().contains("Passwords do not match"))
    }

    @Test
    // Registration should fail when the user already exists.
    fun registerRejectsDuplicateUser() = testApplication {
        configureApp()

        val firstResponse = client.post("/register") {
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
        assertEquals(HttpStatusCode.Found, firstResponse.status)
        assertEquals("/login", firstResponse.headers[HttpHeaders.Location])

        val secondResponse = client.post("/register") {
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

        assertEquals(HttpStatusCode.OK, secondResponse.status)
        assertEquals(true, secondResponse.bodyAsText().contains("User already exists"))
    }

    @Test
    // Login should fail when the credentials are invalid.
    fun loginRejectsInvalidCredentials() = testApplication {
        configureApp()

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

        val response = client.post("/login") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                listOf(
                    "email" to "student@example.com",
                    "password" to "WrongPass123!"
                ).formUrlEncode()
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(true, response.bodyAsText().contains("Invalid credentials"))
    }

    @Test
    // Logout should clear the user session and redirect to the landing page.
    fun logoutClearsSessionAndRedirectsToLandingPage() = testApplication {
        configureApp()
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
        assertEquals("/home", loginResponse.headers[HttpHeaders.Location])

        val logoutResponse = client.get("/logout")
        assertEquals(HttpStatusCode.Found, logoutResponse.status)
        assertEquals("/", logoutResponse.headers[HttpHeaders.Location])

        val homeResponse = client.get("/home")
        assertEquals(HttpStatusCode.Found, homeResponse.status)
        assertEquals("/login", homeResponse.headers[HttpHeaders.Location])
    }
}
