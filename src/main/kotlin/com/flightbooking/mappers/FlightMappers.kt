package com.flightbooking.mappers

import com.flightbooking.models.Airport
import com.flightbooking.models.Booking
import com.flightbooking.models.BookingSegment
import com.flightbooking.models.FareClass
import com.flightbooking.models.Flight
import com.flightbooking.models.FlightFare
import com.flightbooking.models.Seat
import com.flightbooking.models.SeatAssignment
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.FareClassTable
import com.flightbooking.tables.FlightFareTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.SeatAssignmentTable
import com.flightbooking.tables.SeatTable
import org.jetbrains.exposed.sql.ResultRow

/**
 * Maps row returned from Exposed queries into defined Kotlin Airport class
 */
fun ResultRow.toAirport(): Airport =
    Airport(
        id = this[AirportTable.id],
        iataCode = this[AirportTable.iataCode],
        name = this[AirportTable.name],
        city = this[AirportTable.city],
        country = this[AirportTable.country],
    )

/**
 * Maps row returned from Exposed queries into defined Kotlin Flight class
 */
fun ResultRow.toFlight(): Flight =
    Flight(
        id = this[FlightTable.id],
        flightNumber = this[FlightTable.flightNumber],
        originAirport = this[FlightTable.originAirport],
        destinationAirport = this[FlightTable.destinationAirport],
        scheduledDepartureTime = this[FlightTable.scheduledDepartureTime],
        scheduledArrivalTime = this[FlightTable.scheduledArrivalTime],
        status = this[FlightTable.status],
        capacity = this[FlightTable.capacity],
    )

/**
 * Maps row returned from Exposed queries into defined Kotlin FareClass class
 */
fun ResultRow.toFareClass(): FareClass =
    FareClass(
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
        updatedAt = this[FareClassTable.updatedAt],
        colour = this[FareClassTable.colour],
    )

/**
 * Maps row returned from Exposed queries into defined Kotlin FlightFare class
 */
fun ResultRow.toFlightFare(): FlightFare =
    FlightFare(
        id = this[FlightFareTable.id],
        flightId = this[FlightFareTable.flightId],
        fareClassId = this[FlightFareTable.fareClassId],
        price = this[FlightFareTable.price],
        currency = this[FlightFareTable.currency],
        seatsAvailable = this[FlightFareTable.seatsAvailable],
        saleStart = this[FlightFareTable.saleStart],
        saleEnd = this[FlightFareTable.saleEnd],
    )

/**
 * Maps row returned from Exposed queries into defined Kotlin Booking class
 */
fun ResultRow.toBooking(): Booking =
    Booking(
        id = this[BookingTable.id],
        userId = this[BookingTable.userId],
        bookingReference = this[BookingTable.bookingReference],
        paymentId = this[BookingTable.paymentId],
        createdAt = this[BookingTable.createdAt],
        bookingStatus = this[BookingTable.bookingStatus],
        cancelledAt = this[BookingTable.cancelledAt],
        amendable = this[BookingTable.amendable],
    )

/**
 * Maps row returned from Exposed queries into defined Kotlin BookingSegment class
 */
fun ResultRow.toBookingSegment(): BookingSegment =
    BookingSegment(
        id = this[BookingSegmentTable.id],
        bookingId = this[BookingSegmentTable.bookingId],
        flightId = this[BookingSegmentTable.flightId],
        flightFareId = this[BookingSegmentTable.flightFareId],
    )

/**
 * Maps row returned from Exposed queries into defined Kotlin Seat class
 */
fun ResultRow.toSeat(): Seat =
    Seat(
        id = this[SeatTable.id],
        flightId = this[SeatTable.flightId],
        seatCode = this[SeatTable.seatCode],
        cabinClass = this[SeatTable.cabinClass],
        position = this[SeatTable.position],
        extraLegroom = this[SeatTable.extraLegroom],
        exitRow = this[SeatTable.exitRow],
        reducedMobility = this[SeatTable.reducedMobility],
        status = this[SeatTable.status],
    )

/**
 * Maps row returned from Exposed queries into defined Kotlin SeatAssignment class
 */
fun ResultRow.toSeatAssignment(): SeatAssignment =
    SeatAssignment(
        id = this[SeatAssignmentTable.id],
        passengerId = this[SeatAssignmentTable.passengerId],
        bookingSegmentId = this[SeatAssignmentTable.bookingSegmentId],
        seatId = this[SeatAssignmentTable.seatId],
    )
