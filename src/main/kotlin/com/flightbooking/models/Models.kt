package com.flightbooking.models

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
