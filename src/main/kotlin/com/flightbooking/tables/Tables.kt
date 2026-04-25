package com.flightbooking.tables

import org.jetbrains.exposed.sql.Table
import com.flightbooking.constants.IATA_CODE_LENGTH
import com.flightbooking.constants.STANDARD_FIELD_LENGTH
import com.flightbooking.constants.SHORT_FIELD_LENGTH
import com.flightbooking.constants.DEFAULT_CARRY_ON_WEIGHT_ALLOWED
import com.flightbooking.constants.CLASS_CODE_LENGTH
import com.flightbooking.constants.CURRENCY_LENGTH
import com.flightbooking.constants.DOB_LENGTH
import com.flightbooking.constants.PASSENGER_TITLE_LENGTH
import com.flightbooking.constants.GENDER_LENGTH
import com.flightbooking.constants.SHORTEST_FIELD_LENGTH
import com.flightbooking.constants.DOCUMENT_NUMBER_LENGTH
import com.flightbooking.constants.DOCUMENT_COUNTRY_LENGTH
import com.flightbooking.constants.DOCUMENT_EXPIRY_LENGTH
import com.flightbooking.constants.SHORTER_FIELD_LENGTH

/**
 * Exposed table definition for the `airport` table.
 */
object AirportTable : Table("airport") {
    val id = integer("airport_id").autoIncrement()
    val iataCode = varchar("iata_code", IATA_CODE_LENGTH).uniqueIndex()
    val name = varchar("name", STANDARD_FIELD_LENGTH).nullable()
    val city = varchar("city", STANDARD_FIELD_LENGTH).nullable()
    val country = varchar("country", STANDARD_FIELD_LENGTH).nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Exposed table definition for the `flight` table.
 */
object FlightTable : Table("flight") {
    val id = integer("flight_id").autoIncrement()
    val flightNumber = integer("flight_number").nullable()
    val originAirport = integer("origin_airport").references(AirportTable.id)
    val destinationAirport = integer("destination_airport").references(AirportTable.id)
    val scheduledDepartureTime = varchar("scheduled_departure_time", STANDARD_FIELD_LENGTH).nullable()
    val scheduledArrivalTime = varchar("scheduled_arrival_time", STANDARD_FIELD_LENGTH).nullable()
    val status = varchar("status", SHORT_FIELD_LENGTH).default("scheduled")
    val capacity = integer("capacity").nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Exposed table definition for the `fare_class` table.
 */
object FareClassTable : Table("fare_class") {
    val id = integer("fare_class_id").autoIncrement()
    val classCode = varchar("class_code", CLASS_CODE_LENGTH).uniqueIndex()
    val cabinClass = varchar("cabin_class", SHORT_FIELD_LENGTH).nullable()
    val displayName = varchar("display_name", STANDARD_FIELD_LENGTH).nullable()
    val refundable = integer("refundable").default(0)
    val cancelProtocol = varchar("cancel_protocol", STANDARD_FIELD_LENGTH).default("free cancellation")
    val advanceSeatSelection = integer("advance_seat_selection").default(0)
    val priorityCheckin = integer("priority_checkin").default(0)
    val priorityBoarding = integer("priority_boarding").default(0)
    val loungeAccess = integer("lounge_access").default(0)
    val carryOnAllowed = integer("carry_on_allowed").default(1)
    val carryOnWeightKg = integer("carry_on_weight_kg").default(DEFAULT_CARRY_ON_WEIGHT_ALLOWED)
    val checkedBaggagePieces = integer("checked_baggage_pieces").default(0)
    val checkedBaggageWeightKg = integer("checked_baggage_weight_kg").default(0)
    val milesEarnRate = double("miles_earn_rate").default(1.0)
    val minimumMilesForBooking = integer("minimum_miles_for_booking").nullable()
    val description = text("description").nullable()
    val createdAt = varchar("created_at", STANDARD_FIELD_LENGTH)
    val updatedAt = varchar("updated_at", STANDARD_FIELD_LENGTH)

    override val primaryKey = PrimaryKey(id)
}

/**
 * Exposed table definition for the `flight_fare` table.
 */
object FlightFareTable : Table("flight_fare") {
    val id = integer("flight_fare_id").autoIncrement()
    val flightId = integer("flight_id").references(FlightTable.id)
    val fareClassId = integer("fare_class_id").references(FareClassTable.id)
    val price = double("price")
    val currency = varchar("currency", CURRENCY_LENGTH).default("GBP")
    val seatsAvailable = integer("seats_available")
    val saleStart = varchar("sale_start", STANDARD_FIELD_LENGTH).nullable()
    val saleEnd = varchar("sale_end", STANDARD_FIELD_LENGTH).nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Exposed table definition for the `user` table.
 */
object UserTable : Table("user") {
    val id = integer("user_id").autoIncrement()
    val email = varchar("email", STANDARD_FIELD_LENGTH).uniqueIndex()
    val passwordHash = varchar("password_hash", STANDARD_FIELD_LENGTH).nullable()
    val firstName = varchar("first_name", STANDARD_FIELD_LENGTH).nullable()
    val lastName = varchar("last_name", STANDARD_FIELD_LENGTH).nullable()
    val phoneNumber = varchar("phone_number", SHORT_FIELD_LENGTH).nullable()
    val dateOfBirth = varchar("date_of_birth", DOB_LENGTH).nullable()
    val createdAt = varchar("created_at", STANDARD_FIELD_LENGTH)
    val accountStatus = varchar("account_status", SHORT_FIELD_LENGTH).default("active")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Exposed table definition for the `booking` table.
 */
object BookingTable : Table("booking") {
    val id = integer("booking_id").autoIncrement()
    val userId = integer("user_id").references(UserTable.id).nullable()
    val bookingReference = varchar("booking_reference", SHORT_FIELD_LENGTH).uniqueIndex()
    val paymentId = integer("payment_id").uniqueIndex().nullable()
    val createdAt = varchar("created_at", STANDARD_FIELD_LENGTH)
    val bookingStatus = varchar("booking_status", SHORT_FIELD_LENGTH).default("pending")
    val cancelledAt = varchar("cancelled_at", STANDARD_FIELD_LENGTH).nullable()
    val amendable = integer("amendable").default(1)

    override val primaryKey = PrimaryKey(id)
}

/**
 * Exposed table definition for the `payment` table.
 */
object PaymentTable : Table("payment") {
    val id = integer("payment_id").autoIncrement()
    val bookingId = integer("booking_id").references(BookingTable.id).uniqueIndex()
    val amount = double("amount").nullable()
    val paymentMethod = varchar("payment_method", SHORT_FIELD_LENGTH).nullable()
    val paymentStatus = varchar("payment_status", SHORT_FIELD_LENGTH).default("pending")
    val paidAt = varchar("paid_at", STANDARD_FIELD_LENGTH).nullable()
    val providerReference = varchar("provider_reference", STANDARD_FIELD_LENGTH).nullable()
    val currency = varchar("currency", CURRENCY_LENGTH).default("GBP")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Exposed table definition for the `passenger` table.
 */
object PassengerTable : Table("passenger") {
    val id = integer("passenger_id").autoIncrement()
    val bookingId = integer("booking_id").references(BookingTable.id).nullable()
    val email = varchar("email", STANDARD_FIELD_LENGTH).nullable()
    val checkedIn = integer("checked_in").default(0)
    val title = varchar("title", PASSENGER_TITLE_LENGTH).nullable()
    val firstName = varchar("first_name", STANDARD_FIELD_LENGTH).nullable()
    val lastName = varchar("last_name", STANDARD_FIELD_LENGTH).nullable()
    val dateOfBirth = varchar("date_of_birth", DOB_LENGTH).nullable()
    val gender = varchar("gender", GENDER_LENGTH).nullable()
    val nationality = varchar("nationality", SHORTEST_FIELD_LENGTH).nullable()
    val documentType = varchar("document_type", SHORT_FIELD_LENGTH).nullable()
    val documentNumber = varchar("document_number", DOCUMENT_NUMBER_LENGTH).nullable()
    val documentCountry = varchar("document_country", DOCUMENT_COUNTRY_LENGTH).nullable()
    val documentExpiry = varchar("document_expiry", DOCUMENT_EXPIRY_LENGTH).nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Exposed table definition for the `booking_segment` table.
 */
object BookingSegmentTable : Table("booking_segment") {
    val id = integer("booking_segment_id").autoIncrement()
    val bookingId = integer("booking_id").references(BookingTable.id)
    val flightId = integer("flight_id").references(FlightTable.id)
    val flightFareId = integer("flight_fare_id").references(FlightFareTable.id)

    override val primaryKey = PrimaryKey(id)
}

/**
 * Exposed table definition for the `seat` table.
 */
object SeatTable : Table("seat") {
    val id = integer("seat_id").autoIncrement()
    val flightId = integer("flight_id").references(FlightTable.id)
    val seatCode = varchar("seat_code", SHORTEST_FIELD_LENGTH)
    val cabinClass = varchar("cabin_class", SHORT_FIELD_LENGTH).nullable()
    val position = varchar("position", SHORTER_FIELD_LENGTH).nullable()
    val extraLegroom = integer("extra_legroom").default(0)
    val exitRow = integer("exit_row").default(0)
    val reducedMobility = integer("reduced_mobility").default(0)
    val status = varchar("status", SHORTER_FIELD_LENGTH).default("available")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Exposed table definition for the `seat_assignment` table.
 */
object SeatAssignmentTable : Table("seat_assignment") {
    val id = integer("seat_assignment_id").autoIncrement()
    val passengerId = integer("passenger_id").references(PassengerTable.id).uniqueIndex()
    val bookingSegmentId = integer("booking_segment_id").references(BookingSegmentTable.id)
    val seatId = integer("seat_id").references(SeatTable.id).nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Exposed table definition for the `staff` table.
 */
object StaffTable : Table("staff") {
    val id = integer("staff_id").autoIncrement()
    val email = varchar("email", STANDARD_FIELD_LENGTH).uniqueIndex()
    val passwordHash = varchar("password_hash", STANDARD_FIELD_LENGTH).nullable()
    val firstName = varchar("first_name", STANDARD_FIELD_LENGTH).nullable()
    val lastName = varchar("last_name", STANDARD_FIELD_LENGTH).nullable()
    val phoneNumber = varchar("phone_number", SHORT_FIELD_LENGTH).nullable()
    val role = varchar("role", SHORT_FIELD_LENGTH).nullable()
    val createdAt = varchar("created_at", STANDARD_FIELD_LENGTH)

    override val primaryKey = PrimaryKey(id)
}

/**
 * Exposed table definition for the `complaint` table.
 */
object ComplaintTable : Table("complaint") {
    val id = integer("complaint_id").autoIncrement()
    val userId = integer("user_id").references(UserTable.id).nullable()
    val type = varchar("type", SHORT_FIELD_LENGTH).nullable()
    val message = text("message").nullable()
    val createdAt = varchar("created_at", STANDARD_FIELD_LENGTH)
    val status = varchar("status", SHORT_FIELD_LENGTH).default("open")
    val handledByStaffId = integer("handled_by_staff_id").references(StaffTable.id).nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Exposed table definition for the `notification` table.
 */
object NotificationTable : Table("notification") {
    val id = integer("notification_id").autoIncrement()
    val userId = integer("user_id").references(UserTable.id).nullable()
    val type = varchar("type", SHORT_FIELD_LENGTH).nullable()
    val message = text("message").nullable()
    val createdAt = varchar("created_at", STANDARD_FIELD_LENGTH)
    val readAt = varchar("read_at", STANDARD_FIELD_LENGTH).nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Exposed table definition for the `change_request` table.
 */

object ChangeRequestTable : Table("change_request") {
    val id = integer("change_request_id").autoIncrement()
    override val primaryKey = PrimaryKey(id)

    val userId = integer("user_id")
    val bookingId = integer("booking_id")
    val bookingSegmentId = integer("booking_segment_id")

    val currentFlightId = integer("current_flight_id").nullable()
    val requestedFlightId = integer("requested_flight_id").nullable()
    val requestedSeatId = integer("requested_seat_id").nullable()

    val reason = text("reason").nullable()
    val status = varchar("status", SHORT_FIELD_LENGTH).default("pending") // pending/approved/rejected/cancelled

    val createdAt = varchar("created_at", STANDARD_FIELD_LENGTH).nullable()
    val updatedAt = varchar("updated_at", STANDARD_FIELD_LENGTH).nullable()
}
