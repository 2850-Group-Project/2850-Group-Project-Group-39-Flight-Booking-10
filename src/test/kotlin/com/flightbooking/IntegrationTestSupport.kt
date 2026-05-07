package com.flightbooking

import com.flightbooking.tables.PassengerTable
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest

abstract class IntegrationTestSupport {
    private val tempDbFiles = mutableListOf<Path>()

    /**
     * Cleans the temp databases
     */
    @AfterTest
    fun cleanupTempDatabases() {
        tempDbFiles.forEach { it.deleteIfExists() }
        tempDbFiles.clear()
    }

    /**
     * Boot the app against a fresh temporary SQLite database for each test.
     */
    protected fun ApplicationTestBuilder.configureApp() {
        val dbFile = Files.createTempFile("flight-booking-test-", ".db")
        tempDbFiles.add(dbFile)

        environment { config = MapApplicationConfig() }
        application { testModule("jdbc:sqlite:${dbFile.toAbsolutePath()}") }
    }

    /**
     * Retrieves the IDs of all passengers associated with the most recently created booking.
     *
     * Useful in tests where passengers need to be referenced after a booking has been
     * created implicitly (e.g. via [selectOneWayTrip]), without needing to track the booking ID manually.
     *
     * @return A list of passenger IDs belonging to the latest booking.
     */
    protected fun getPassengerIdsForLatestBooking(): List<Int> =
        transaction {
            PassengerTable
                .select { PassengerTable.bookingId eq latestBookingId() }
                .map { it[PassengerTable.id] }
        }
}
