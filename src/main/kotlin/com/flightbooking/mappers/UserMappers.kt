package com.flightbooking.mappers

import com.flightbooking.models.ChangeRequest
import com.flightbooking.models.Complaint
import com.flightbooking.models.Notification
import com.flightbooking.models.Passenger
import com.flightbooking.models.Payment
import com.flightbooking.models.Staff
import com.flightbooking.models.User
import com.flightbooking.tables.ChangeRequestTable
import com.flightbooking.tables.ComplaintTable
import com.flightbooking.tables.NotificationTable
import com.flightbooking.tables.PassengerTable
import com.flightbooking.tables.PaymentTable
import com.flightbooking.tables.StaffTable
import com.flightbooking.tables.UserTable
import org.jetbrains.exposed.sql.ResultRow

// following functions transform rows returned from Exposed queries
// into data objects that we can treat as kotlin classes
// rather than having to parse the sql query return (which can be funky sometimes)
fun ResultRow.toUser(): User =
    User(
        id = this[UserTable.id],
        email = this[UserTable.email],
        passwordHash = this[UserTable.passwordHash],
        firstName = this[UserTable.firstName],
        lastName = this[UserTable.lastName],
        phoneNumber = this[UserTable.phoneNumber],
        dateOfBirth = this[UserTable.dateOfBirth],
        createdAt = this[UserTable.createdAt],
        accountStatus = this[UserTable.accountStatus],
    )

fun ResultRow.toPayment(): Payment =
    Payment(
        id = this[PaymentTable.id],
        bookingId = this[PaymentTable.bookingId],
        amount = this[PaymentTable.amount],
        paymentMethod = this[PaymentTable.paymentMethod],
        paymentStatus = this[PaymentTable.paymentStatus],
        paidAt = this[PaymentTable.paidAt],
        providerReference = this[PaymentTable.providerReference],
        currency = this[PaymentTable.currency],
    )

fun ResultRow.toPassenger(): Passenger =
    Passenger(
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
        documentExpiry = this[PassengerTable.documentExpiry],
    )

fun ResultRow.toStaff(): Staff =
    Staff(
        id = this[StaffTable.id],
        email = this[StaffTable.email],
        passwordHash = this[StaffTable.passwordHash],
        firstName = this[StaffTable.firstName],
        lastName = this[StaffTable.lastName],
        phoneNumber = this[StaffTable.phoneNumber],
        role = this[StaffTable.role],
        createdAt = this[StaffTable.createdAt],
    )

fun ResultRow.toComplaint(): Complaint =
    Complaint(
        id = this[ComplaintTable.id],
        userId = this[ComplaintTable.userId],
        type = this[ComplaintTable.type],
        message = this[ComplaintTable.message],
        createdAt = this[ComplaintTable.createdAt],
        status = this[ComplaintTable.status],
        handledByStaffId = this[ComplaintTable.handledByStaffId],
    )

fun ResultRow.toNotification(): Notification =
    Notification(
        id = this[NotificationTable.id],
        userId = this[NotificationTable.userId],
        type = this[NotificationTable.type],
        message = this[NotificationTable.message],
        createdAt = this[NotificationTable.createdAt],
        readAt = this[NotificationTable.readAt],
    )

fun ResultRow.toChangeRequest(): ChangeRequest =
    ChangeRequest(
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
        updatedAt = this[ChangeRequestTable.updatedAt],
    )
