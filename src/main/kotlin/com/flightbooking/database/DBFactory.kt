package com.flightbooking.database

import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils

import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.FareClassTable
import com.flightbooking.tables.FlightFareTable
import com.flightbooking.tables.UserTable
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.PaymentTable
import com.flightbooking.tables.PassengerTable
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.SeatTable
import com.flightbooking.tables.SeatAssignmentTable
import com.flightbooking.tables.StaffTable
import com.flightbooking.tables.ComplaintTable
import com.flightbooking.tables.NotificationTable

/**
 * Class responsible for creating/mainting connection to database.
 * 
 * On init, connects to SQLite database and makes sure all required
 * tables exist, creating any that are missing via tables in Tables.kt
 */
object DBFactory {
    private const val DEFAULT_URL = "jdbc:sqlite:data/flight_booking_DB.db"
    private const val DEFAULT_DRIVER = "org.sqlite.JDBC"

    /**
     * Initialised connection to database and checks for/creates missing tables
     * 
     * @throws SQLException/ExposedSQLException if the database connection/table connect fails
     */

    fun init(
        url: String = DEFAULT_URL,
        driver: String = DEFAULT_DRIVER
    ) {
        Database.connect(
            url = url,
            driver = driver
        )
        println("Checking if all tables exist...")
        transaction {
            SchemaUtils.create(
                AirportTable,
                FlightTable,
                FareClassTable,
                FlightFareTable,
                UserTable,
                BookingTable,
                PaymentTable,
                PassengerTable,
                BookingSegmentTable,
                SeatTable,
                SeatAssignmentTable,
                StaffTable,
                ComplaintTable,
                NotificationTable
            )
        }
        println("All tables present")
    }
}
