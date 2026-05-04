package com.flightbooking

import com.flightbooking.tables.PassengerTable
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookingRoutesTest : IntegrationTestSupport() {
    // Check that submitting passenger details without logging in redirects to login.
    @Test
    fun unauthenticatedPassengerSubmitRedirectsToLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response =
                client.submitForm(
                    url = "/flights/passengers/submit",
                    formParameters =
                        parameters {
                            append("passengers[0][firstName]", "Alex")
                            append("passengers[0][lastName]", "Student")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login", response.headers[HttpHeaders.Location])
        }

    // Check that submitting passenger details without a booking session redirects home.
    @Test
    fun passengerSubmitRedirectsHomeWhenBookingSessionMissing() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response =
                client.submitForm(
                    url = "/flights/passengers/submit",
                    formParameters =
                        parameters {
                            append("passengers[0][firstName]", "Alex")
                            append("passengers[0][lastName]", "Student")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/home", response.headers[HttpHeaders.Location])
        }

    // Check that submitting one passenger saves their details in the booking session.
    @Test
    fun passengerSubmitStoresPassengerDataInBookingSession() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            client.seedBookingSession(adults = "1")

            val response =
                client.submitForm(
                    url = "/flights/passengers/submit",
                    formParameters =
                        parameters {
                            append("passengers[0][type]", "adult")
                            append("passengers[0][title]", "Mr")
                            append("passengers[0][firstName]", "Alex")
                            append("passengers[0][lastName]", "Student")
                            append("passengers[0][dateOfBirth]", "2000-01-01")
                            append("passengers[0][gender]", "Male")
                            append("passengers[0][email]", "alex@example.com")
                            append("passengers[0][nationality]", "British")
                            append("passengers[0][documentType]", "Passport")
                            append("passengers[0][documentNumber]", "A1234567")
                            append("passengers[0][documentCountry]", "GB")
                            append("passengers[0][documentExpiry]", "2030-01-01")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/flights/seats", response.headers[HttpHeaders.Location])
            assertTrue(passengerExists("Alex", "Student", "A1234567"))
        }

    // Check that submitting multiple passengers saves all of their details in the booking session.
    @Test
    fun passengerSubmitHandlesMultiplePassengers() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            client.seedBookingSession(adults = "2")

            val response =
                client.submitForm(
                    url = "/flights/passengers/submit",
                    formParameters =
                        parameters {
                            append("passengers[0][type]", "adult")
                            append("passengers[0][title]", "Ms")
                            append("passengers[0][firstName]", "Alice")
                            append("passengers[0][lastName]", "Brown")
                            append("passengers[0][dateOfBirth]", "1995-03-10")
                            append("passengers[0][gender]", "Female")
                            append("passengers[0][email]", "alice@example.com")
                            append("passengers[0][nationality]", "Canadian")
                            append("passengers[0][documentType]", "Passport")
                            append("passengers[0][documentNumber]", "P1111111")
                            append("passengers[0][documentCountry]", "CA")
                            append("passengers[0][documentExpiry]", "2031-03-10")
                            append("passengers[1][type]", "adult")
                            append("passengers[1][title]", "Mr")
                            append("passengers[1][firstName]", "Ben")
                            append("passengers[1][lastName]", "Taylor")
                            append("passengers[1][dateOfBirth]", "1992-07-22")
                            append("passengers[1][gender]", "Male")
                            append("passengers[1][email]", "ben@example.com")
                            append("passengers[1][nationality]", "Irish")
                            append("passengers[1][documentType]", "Passport")
                            append("passengers[1][documentNumber]", "P2222222")
                            append("passengers[1][documentCountry]", "IE")
                            append("passengers[1][documentExpiry]", "2032-07-22")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/flights/seats", response.headers[HttpHeaders.Location])
            assertTrue(passengerExists("Alice", "Brown", "P1111111"))
            assertTrue(passengerExists("Ben", "Taylor", "P2222222"))
        }

    // Passenger submission should reject missing required passenger fields.
    @Test
    fun passengerSubmitRejectsMissingRequiredPassengerFields() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            client.seedBookingSession(adults = "1")

            val response =
                client.submitForm(
                    url = "/flights/passengers/submit",
                    formParameters =
                        parameters {
                            append("passengers[0][type]", "adult")
                            append("passengers[0][firstName]", "")
                            append("passengers[0][lastName]", "Student")
                            append("passengers[0][documentNumber]", "MISSING1")
                        },
                )

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(0, passengerCount())
        }

    // Passenger submission should save adults, children, and infants from the booking search.
    @Test
    fun passengerSubmitHandlesChildrenAndInfants() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            client.seedBookingSession(adults = "1", children = "1", infants = "1")

            val response =
                client.submitForm(
                    url = "/flights/passengers/submit",
                    formParameters =
                        parameters {
                            appendPassenger(0, "adult", "Alex", "Adult", "ADULT1")
                            appendPassenger(1, "child", "Casey", "Child", "CHILD1")
                            appendPassenger(2, "infant", "Ivy", "Infant", "INFANT1")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/flights/seats", response.headers[HttpHeaders.Location])
            assertEquals(3, passengerCount())
            assertTrue(passengerExists("Alex", "Adult", "ADULT1"))
            assertTrue(passengerExists("Casey", "Child", "CHILD1"))
            assertTrue(passengerExists("Ivy", "Infant", "INFANT1"))
        }

    // Extra passenger form rows should not be saved beyond the booking search count.
    @Test
    fun passengerSubmitDoesNotSaveExtraPassengerRows() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            client.seedBookingSession(adults = "1")

            val response =
                client.submitForm(
                    url = "/flights/passengers/submit",
                    formParameters =
                        parameters {
                            appendPassenger(0, "adult", "Alex", "Student", "ONLY1")
                            appendPassenger(1, "adult", "Extra", "Passenger", "EXTRA1")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/flights/seats", response.headers[HttpHeaders.Location])
            assertEquals(1, passengerCount())
            assertTrue(passengerExists("Alex", "Student", "ONLY1"))
            assertFalse(passengerExists("Extra", "Passenger", "EXTRA1"))
        }

    // Submitted passenger fields should be preserved exactly in the passenger row.
    @Test
    fun passengerSubmitPreservesDocumentFields() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            client.seedBookingSession(adults = "1")

            val response =
                client.submitForm(
                    url = "/flights/passengers/submit",
                    formParameters =
                        parameters {
                            append("passengers[0][type]", "adult")
                            append("passengers[0][title]", "Dr")
                            append("passengers[0][firstName]", "Morgan")
                            append("passengers[0][lastName]", "Stone")
                            append("passengers[0][dateOfBirth]", "1988-08-08")
                            append("passengers[0][gender]", "Non-binary")
                            append("passengers[0][email]", "morgan@example.com")
                            append("passengers[0][nationality]", "NZ")
                            append("passengers[0][documentType]", "Passport")
                            append("passengers[0][documentNumber]", "DOC12345")
                            append("passengers[0][documentCountry]", "NZ")
                            append("passengers[0][documentExpiry]", "2035-08-08")
                        },
                )

            assertEquals(HttpStatusCode.Found, response.status)
            val passenger = passengerByDocumentNumber("DOC12345")
            assertEquals("Dr", passenger.title)
            assertEquals("Morgan", passenger.firstName)
            assertEquals("Stone", passenger.lastName)
            assertEquals("1988-08-08", passenger.dateOfBirth)
            assertEquals("Non-binary", passenger.gender)
            assertEquals("morgan@example.com", passenger.email)
            assertEquals("NZ", passenger.nationality)
            assertEquals("Passport", passenger.documentType)
            assertEquals("NZ", passenger.documentCountry)
            assertEquals("2035-08-08", passenger.documentExpiry)
        }

    // Invalid passenger indexes should not create blank passenger records.
    @Test
    fun passengerSubmitRejectsInvalidPassengerIndexes() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            client.seedBookingSession(adults = "1")

            val response =
                client.submitForm(
                    url = "/flights/passengers/submit",
                    formParameters =
                        parameters {
                            appendPassenger(1, "adult", "Wrong", "Index", "INDEX1")
                        },
                )

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(0, passengerCount())
        }

    // Check whether a submitted passenger row was persisted to the database.
    private fun passengerExists(
        firstName: String,
        lastName: String,
        documentNumber: String,
    ): Boolean =
        transaction {
            PassengerTable
                .select {
                    (PassengerTable.firstName eq firstName) and
                        (PassengerTable.lastName eq lastName) and
                        (PassengerTable.documentNumber eq documentNumber)
                }
                .any()
        }

    private fun passengerCount(): Int =
        transaction {
            PassengerTable.selectAll().count().toInt()
        }

    private fun passengerByDocumentNumber(documentNumber: String): TestPassenger =
        transaction {
            PassengerTable
                .select { PassengerTable.documentNumber eq documentNumber }
                .limit(1)
                .first()
                .let {
                    TestPassenger(
                        title = it[PassengerTable.title],
                        firstName = it[PassengerTable.firstName],
                        lastName = it[PassengerTable.lastName],
                        dateOfBirth = it[PassengerTable.dateOfBirth],
                        gender = it[PassengerTable.gender],
                        email = it[PassengerTable.email],
                        nationality = it[PassengerTable.nationality],
                        documentType = it[PassengerTable.documentType],
                        documentCountry = it[PassengerTable.documentCountry],
                        documentExpiry = it[PassengerTable.documentExpiry],
                    )
                }
        }

    private suspend fun io.ktor.client.HttpClient.seedBookingSession(
        adults: String,
        children: String,
        infants: String,
    ) {
        val response =
            submitForm(
                url = "/flights/select",
                formParameters =
                    parameters {
                        append("flightId", "10")
                        append("fareId", "20")
                        append("leg", "outbound")
                        append("tripType", "oneway")
                        append("origin", "LHR")
                        append("destination", "DXB")
                        append("departureDate", "2026-04-01")
                        append("adults", adults)
                        append("children", children)
                        append("infants", infants)
                    },
            )

        assertEquals(HttpStatusCode.OK, response.status)
    }

    private fun io.ktor.http.ParametersBuilder.appendPassenger(
        index: Int,
        type: String,
        firstName: String,
        lastName: String,
        documentNumber: String,
    ) {
        append("passengers[$index][type]", type)
        append("passengers[$index][title]", "Mx")
        append("passengers[$index][firstName]", firstName)
        append("passengers[$index][lastName]", lastName)
        append("passengers[$index][dateOfBirth]", "2000-01-01")
        append("passengers[$index][gender]", "Female")
        append("passengers[$index][email]", "$documentNumber@example.com")
        append("passengers[$index][nationality]", "GB")
        append("passengers[$index][documentType]", "Passport")
        append("passengers[$index][documentNumber]", documentNumber)
        append("passengers[$index][documentCountry]", "GB")
        append("passengers[$index][documentExpiry]", "2030-01-01")
    }

    private data class TestPassenger(
        val title: String?,
        val firstName: String?,
        val lastName: String?,
        val dateOfBirth: String?,
        val gender: String?,
        val email: String?,
        val nationality: String?,
        val documentType: String?,
        val documentCountry: String?,
        val documentExpiry: String?,
    )
}
