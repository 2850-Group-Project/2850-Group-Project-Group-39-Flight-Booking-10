package com.flightbooking

import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest

abstract class IntegrationTestSupport {
    private val tempDbFiles = mutableListOf<Path>()

    @AfterTest
    fun cleanupTempDatabases() {
        tempDbFiles.forEach { it.deleteIfExists() }
        tempDbFiles.clear()
    }

    // Boot the app against a fresh temporary SQLite database for each test.
    protected fun ApplicationTestBuilder.configureApp() {
        val dbFile = Files.createTempFile("flight-booking-test-", ".db")
        tempDbFiles.add(dbFile)

        environment { config = MapApplicationConfig() }
        application { testModule("jdbc:sqlite:${dbFile.toAbsolutePath()}") }
    }
}
