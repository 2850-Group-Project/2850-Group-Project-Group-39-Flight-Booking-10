package com.flightbooking.models

import org.jetbrains.exposed.sql.ResultRow
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
import com.flightbooking.tables.ChangeRequestTable
import com.flightbooking.tables.NotificationTable

// data class for storing user session data (ie, if they are logged in)
data class UserSession(
    val userEmail: String,
    val firstName: String?
)

// data class for flight searches, contains data about the flight search (from home page)
data class FlightSearch(
    val tripType: String?,
    val origin: String?,
    val destination: String?,
    val departureDate: String?,
    val returnDate: String?,
    val adults: String?,
    val children: String?,
    val infants: String?
)

// fare option for a specific flight
data class FareOption(
    val fareId: Int,
    val fareClassId: Int,
    val displayName: String?,
    val cabinClass: String?,
    val price: Double,
    val currency: String,
    val seatsAvailable: Int
)

// flight connection to fare options
data class FlightWithFares(
    val flightId: Int,
    val flightNumber: Int?,
    val departureDay: String?,
    val departureTime: String?,
    val arrivalTime: String?,
    val duration: String?,
    val status: String,
    val capacity: Int?,
    val originCode: String,
    val destinationCode: String,
    val fares: List<FareOption>
) {
    val cheapestFare: FareOption? get() = fares.minByOrNull { it.price }
}

// raw passenger data from the form, before it's saved to the DB
data class PassengerInput(
    val type: String,           // adult, child, infant
    val title: String?,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: String?,
    val gender: String?,
    val email: String?,
    val nationality: String?,
    val documentType: String?,
    val documentNumber: String?,
    val documentCountry: String?,
    val documentExpiry: String?
)

// booking session data class that is used to keep track of all data about a booking in progress
data class BookingSession(
    val bookingId: Int = 0,
    val outboundFlightId: Int? = null,
    val outboundFareId: Int? = null,
    val returnFlightId: Int? = null,
    val returnFareId: Int? = null,
    val search: FlightSearch? = null,
)

data class User(
    val id: Int,
    val email: String,
    val passwordHash: String?,
    val firstName: String?,
    val lastName: String?,
    val phoneNumber: String?,
    val dateOfBirth: String?,
    val createdAt: String,
    val accountStatus: String
)

data class Airport(
    val id: Int,
    val iataCode: String,
    val name: String?,
    val city: String?,
    val country: String?
)

data class Flight(
    val id: Int,
    val flightNumber: Int?,
    val originAirport: Int,
    val destinationAirport: Int,
    val scheduledDepartureTime: String?,
    val scheduledArrivalTime: String?,
    val status: String,
    val capacity: Int?
)

data class FareClass(
    val id: Int,
    val classCode: String,
    val cabinClass: String?,
    val displayName: String?,
    val refundable: Int,
    val cancelProtocol: String,
    val advanceSeatSelection: Int,
    val priorityCheckin: Int,
    val priorityBoarding: Int,
    val loungeAccess: Int,
    val carryOnAllowed: Int,
    val carryOnWeightKg: Int,
    val checkedBaggagePieces: Int,
    val checkedBaggageWeightKg: Int,
    val milesEarnRate: Double,
    val minimumMilesForBooking: Int?,
    val description: String?,
    val createdAt: String,
    val updatedAt: String
)

data class ChangeRequest(
    val id: Int,
    val userId: Int,
    val bookingId: Int,
    val bookingSegmentId: Int,
    val currentFlightId: Int?,
    val requestedFlightId: Int?,
    val requestedSeatId: Int?,
    val reason: String?,
    val status: String,
    val createdAt: String?,
    val updatedAt: String?
)

data class FlightFare(
    val id: Int,
    val flightId: Int,
    val fareClassId: Int,
    val price: Double,
    val currency: String,
    val seatsAvailable: Int,
    val saleStart: String?,
    val saleEnd: String?
)

data class Booking(
    val id: Int,
    val userId: Int?,
    val bookingReference: String,
    val paymentId: Int?,
    val createdAt: String,
    val bookingStatus: String,
    val cancelledAt: String?,
    val amendable: Int
)

data class Payment(
    val id: Int,
    val bookingId: Int,
    val amount: Double?,
    val paymentMethod: String?,
    val paymentStatus: String,
    val paidAt: String?,
    val providerReference: String?,
    val currency: String
)

data class Passenger(
    val id: Int,
    val bookingId: Int?,
    val email: String?,
    val checkedIn: Int,
    val title: String?,
    val firstName: String?,
    val lastName: String?,
    val dateOfBirth: String?,
    val gender: String?,
    val nationality: String?,
    val documentType: String?,
    val documentNumber: String?,
    val documentCountry: String?,
    val documentExpiry: String?
)

data class BookingSegment(
    val id: Int,
    val bookingId: Int,
    val flightId: Int,
    val flightFareId: Int
)

data class Seat(
    val id: Int,
    val flightId: Int,
    val seatCode: String,
    val cabinClass: String?,
    val position: String?,
    val extraLegroom: Int,
    val exitRow: Int,
    val reducedMobility: Int,
    val status: String
)

data class SeatAssignment(
    val id: Int,
    val passengerId: Int,
    val bookingSegmentId: Int,
    val seatId: Int?
)

data class Staff(
    val id: Int,
    val email: String,
    val passwordHash: String?,
    val firstName: String?,
    val lastName: String?,
    val phoneNumber: String?,
    val role: String?,
    val createdAt: String
)

data class Complaint(
    val id: Int,
    val userId: Int?,
    val type: String?,
    val message: String?,
    val createdAt: String,
    val status: String,
    val handledByStaffId: Int?
)

data class Notification(
    val id: Int,
    val userId: Int?,
    val type: String?,
    val message: String?,
    val createdAt: String,
    val readAt: String?
)

data class StaffSession(
val staffEmail: String
)

// following functions transform rows returned from Exposed queries
// into data objects that we can treat as kotlin classes
// rather than having to parse the sql query return (which can be funky sometimes)
fun ResultRow.toAirport(): Airport = Airport(
    id = this[AirportTable.id],
    iataCode = this[AirportTable.iataCode],
    name = this[AirportTable.name],
    city = this[AirportTable.city],
    country = this[AirportTable.country]
)

fun ResultRow.toFlight(): Flight = Flight(
    id = this[FlightTable.id],
    flightNumber = this[FlightTable.flightNumber],
    originAirport = this[FlightTable.originAirport],
    destinationAirport = this[FlightTable.destinationAirport],
    scheduledDepartureTime = this[FlightTable.scheduledDepartureTime],
    scheduledArrivalTime = this[FlightTable.scheduledArrivalTime],
    status = this[FlightTable.status],
    capacity = this[FlightTable.capacity]
)

fun ResultRow.toFareClass(): FareClass = FareClass(
    id = this[FareClassTable.id],
    classCode = this[FareClassTable.classCode],
    cabinClass = this[FareClassTable.cabinClass],
    displayName = this[FareClassTable.displayName],
    refundable = this[FareClassTable.refundable],
    cancelProtocol = this[FareClassTable.cancelProtocol],
    advanceSeatSelection = this[FareClassTable.advanceSeatSelection],
    priorityCheckin = this[FareClassTable.priorityCheckin],
    priorityBoarding = this[FareClassTable.priorityBoarding],
    loungeAccess = this[FareClassTable.loungeAccess],
    carryOnAllowed = this[FareClassTable.carryOnAllowed],
    carryOnWeightKg = this[FareClassTable.carryOnWeightKg],
    checkedBaggagePieces = this[FareClassTable.checkedBaggagePieces],
    checkedBaggageWeightKg = this[FareClassTable.checkedBaggageWeightKg],
    milesEarnRate = this[FareClassTable.milesEarnRate],
    minimumMilesForBooking = this[FareClassTable.minimumMilesForBooking],
    description = this[FareClassTable.description],
    createdAt = this[FareClassTable.createdAt],
    updatedAt = this[FareClassTable.updatedAt]
)

fun ResultRow.toFlightFare(): FlightFare = FlightFare(
    id = this[FlightFareTable.id],
    flightId = this[FlightFareTable.flightId],
    fareClassId = this[FlightFareTable.fareClassId],
    price = this[FlightFareTable.price],
    currency = this[FlightFareTable.currency],
    seatsAvailable = this[FlightFareTable.seatsAvailable],
    saleStart = this[FlightFareTable.saleStart],
    saleEnd = this[FlightFareTable.saleEnd]
)

fun ResultRow.toUser(): User = User(
    id = this[UserTable.id],
    email = this[UserTable.email],
    passwordHash = this[UserTable.passwordHash],
    firstName = this[UserTable.firstName],
    lastName = this[UserTable.lastName],
    phoneNumber = this[UserTable.phoneNumber],
    dateOfBirth = this[UserTable.dateOfBirth],
    createdAt = this[UserTable.createdAt],
    accountStatus = this[UserTable.accountStatus]
)

fun ResultRow.toBooking(): Booking = Booking(
    id = this[BookingTable.id],
    userId = this[BookingTable.userId],
    bookingReference = this[BookingTable.bookingReference],
    paymentId = this[BookingTable.paymentId],
    createdAt = this[BookingTable.createdAt],
    bookingStatus = this[BookingTable.bookingStatus],
    cancelledAt = this[BookingTable.cancelledAt],
    amendable = this[BookingTable.amendable]
)

fun ResultRow.toPayment(): Payment = Payment(
    id = this[PaymentTable.id],
    bookingId = this[PaymentTable.bookingId],
    amount = this[PaymentTable.amount],
    paymentMethod = this[PaymentTable.paymentMethod],
    paymentStatus = this[PaymentTable.paymentStatus],
    paidAt = this[PaymentTable.paidAt],
    providerReference = this[PaymentTable.providerReference],
    currency = this[PaymentTable.currency]
)

fun ResultRow.toPassenger(): Passenger = Passenger(
    id = this[PassengerTable.id],
    bookingId = this[PassengerTable.bookingId],
    email = this[PassengerTable.email],
    checkedIn = this[PassengerTable.checkedIn],
    title = this[PassengerTable.title],
    firstName = this[PassengerTable.firstName],
    lastName = this[PassengerTable.lastName],
    dateOfBirth = this[PassengerTable.dateOfBirth],
    gender = this[PassengerTable.gender],
    nationality = this[PassengerTable.nationality],
    documentType = this[PassengerTable.documentType],
    documentNumber = this[PassengerTable.documentNumber],
    documentCountry = this[PassengerTable.documentCountry],
    documentExpiry = this[PassengerTable.documentExpiry]
)

fun ResultRow.toBookingSegment(): BookingSegment = BookingSegment(
    id = this[BookingSegmentTable.id],
    bookingId = this[BookingSegmentTable.bookingId],
    flightId = this[BookingSegmentTable.flightId],
    flightFareId = this[BookingSegmentTable.flightFareId]
)

fun ResultRow.toSeat(): Seat = Seat(
    id = this[SeatTable.id],
    flightId = this[SeatTable.flightId],
    seatCode = this[SeatTable.seatCode],
    cabinClass = this[SeatTable.cabinClass],
    position = this[SeatTable.position],
    extraLegroom = this[SeatTable.extraLegroom],
    exitRow = this[SeatTable.exitRow],
    reducedMobility = this[SeatTable.reducedMobility],
    status = this[SeatTable.status]
)

fun ResultRow.toSeatAssignment(): SeatAssignment = SeatAssignment(
    id = this[SeatAssignmentTable.id],
    passengerId = this[SeatAssignmentTable.passengerId],
    bookingSegmentId = this[SeatAssignmentTable.bookingSegmentId],
    seatId = this[SeatAssignmentTable.seatId]
)

fun ResultRow.toStaff(): Staff = Staff(
    id = this[StaffTable.id],
    email = this[StaffTable.email],
    passwordHash = this[StaffTable.passwordHash],
    firstName = this[StaffTable.firstName],
    lastName = this[StaffTable.lastName],
    phoneNumber = this[StaffTable.phoneNumber],
    role = this[StaffTable.role],
    createdAt = this[StaffTable.createdAt]
)

fun ResultRow.toComplaint(): Complaint = Complaint(
    id = this[ComplaintTable.id],
    userId = this[ComplaintTable.userId],
    type = this[ComplaintTable.type],
    message = this[ComplaintTable.message],
    createdAt = this[ComplaintTable.createdAt],
    status = this[ComplaintTable.status],
    handledByStaffId = this[ComplaintTable.handledByStaffId]
)

fun ResultRow.toNotification(): Notification = Notification(
    id = this[NotificationTable.id],
    userId = this[NotificationTable.userId],
    type = this[NotificationTable.type],
    message = this[NotificationTable.message],
    createdAt = this[NotificationTable.createdAt],
    readAt = this[NotificationTable.readAt]
)

fun ResultRow.toChangeRequest(): ChangeRequest = ChangeRequest(
    id = this[ChangeRequestTable.id],
    userId = this[ChangeRequestTable.userId],
    bookingId = this[ChangeRequestTable.bookingId],
    bookingSegmentId = this[ChangeRequestTable.bookingSegmentId],
    currentFlightId = this[ChangeRequestTable.currentFlightId],
    requestedFlightId = this[ChangeRequestTable.requestedFlightId],
    requestedSeatId = this[ChangeRequestTable.requestedSeatId],
    reason = this[ChangeRequestTable.reason],
    status = this[ChangeRequestTable.status],
    createdAt = this[ChangeRequestTable.createdAt],
    updatedAt = this[ChangeRequestTable.updatedAt]
)
