package com.flightbooking.routes

import com.flightbooking.access.AirportTableAccess
import com.flightbooking.access.ComplaintResponseTableAccess
import com.flightbooking.access.FlightFareTableAccess
import com.flightbooking.access.FlightTableAccess
import com.flightbooking.access.FareClassTableAccess
import com.flightbooking.access.SeatTableAccess
import com.flightbooking.models.BookingSession
import com.flightbooking.models.Seat
import com.flightbooking.models.FareClass
import com.flightbooking.models.StaffSession
import com.flightbooking.service.AuthService
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.FareClassTable
import com.flightbooking.tables.FlightFareTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.StaffTable
import com.flightbooking.tables.PassengerTable
import com.flightbooking.tables.SeatTable
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Seat selection routes (booking flow).
 *
 * This version allows users to select seats for multiple passengers without page reloads:
 * - GET  /flights/seats ->
 *   renders `seat_selection.peb` for the **selected outbound flight** stored in [BookingSession].
 *   Seat map is generated from flight capacity (3 aircraft categories).
 *   If seat rows exist in DB for that flight, their status is used.
 *
 * - POST /flights/seats ->
 *   Accepts a JSON payload with all seat selections at once (selectedSeats: { passengerId: seatCode })
 *   Validates all seats are available, creates booking segment + seat assignments,
 *   then redirects to the next step (typically payment).
 */
fun Route.staffFareClassRoutes() {
    get("/staff/fare-class") {
        handleGetStaffFareClass(call)
    }
    post("/staff/fare-class/create") {
        handlePostFareClassCreate(call)
    }
    post("/staff/fare-class/update") {
        handlePostFareClassUpdate(call)
    }
    post("/staff/fare-class/delete") {
        handlePostFareClassDelete(call)
    }
}

private suspend fun handleGetStaffFareClass(call: ApplicationCall) {
    val staffSession = call.sessions.get<StaffSession>()
    if (staffSession == null) {
        call.respondRedirect("/staff/login")
        return
    }

    val access = FareClassTableAccess()

    val fareClasses = access.getAll().map { fc ->
        mapOf(
            "id" to fc.id,
            "classCode" to fc.classCode,
            "cabinClass" to (fc.cabinClass?.lowercase()?.replace(" ", "_") ?: ""),
            "displayName" to (fc.displayName ?: ""),
            "refundable" to fc.refundable,
            "cancelProtocol" to fc.cancelProtocol,
            "advanceSeatSelection" to fc.advanceSeatSelection,
            "priorityCheckin" to fc.priorityCheckin,
            "priorityBoarding" to fc.priorityBoarding,
            "loungeAccess" to fc.loungeAccess,
            "carryOnAllowed" to fc.carryOnAllowed,
            "carryOnWeightKg" to fc.carryOnWeightKg,
            "checkedBaggagePieces" to fc.checkedBaggagePieces,
            "checkedBaggageWeightKg" to fc.checkedBaggageWeightKg,
            "milesEarnRate" to fc.milesEarnRate,
            "minimumMilesForBooking" to (fc.minimumMilesForBooking ?: ""),
            "description" to (fc.description ?: ""),
            "colour" to fc.colour,
        )
    }

    val staffInfo = transaction {
        StaffTable.select { StaffTable.email eq staffSession.staffEmail }.firstOrNull()
    }

    val model = mapOf(
        "fareClasses" to fareClasses,
        "staffName" to listOfNotNull(
            staffInfo?.get(StaffTable.firstName),
            staffInfo?.get(StaffTable.lastName)
        ).joinToString(" ").ifBlank { "Staff" },
        "staffRole" to (staffInfo?.get(StaffTable.role) ?: "Staff"),
        "staffEmail" to staffSession.staffEmail,
        "error" to (call.request.queryParameters["error"] ?: ""),
        "ok" to (call.request.queryParameters["ok"] ?: ""),
        "editId" to (call.request.queryParameters["edit"]?.toIntOrNull() ?: 0),
    )

    call.respond(PebbleContent("staff_fare_class.peb", model))
}

private suspend fun handlePostFareClassCreate(call: ApplicationCall) {
    val session = call.sessions.get<StaffSession>() ?: run {
        call.respondRedirect("/staff/login")
        return
    }

    val p = call.receiveParameters()

    val classCode = p["classCode"]?.trim()
    if (classCode.isNullOrBlank()) {
        call.respondRedirect("/staff/fare-class?error=Class+code+is+required")
        return
    }

    val now = Instant.now().toString()
    
    val fareClass = FareClass(
        id = 0,
        classCode = classCode,
        cabinClass = p["cabinClass"]?.trim()?.ifBlank { null },
        displayName = p["displayName"]?.trim()?.ifBlank { null },
        refundable = if (p["refundable"] == "1") 1 else 0,
        cancelProtocol = p["cancelProtocol"]?.trim()?.ifBlank { "free cancellation" } ?: "free cancellation",
        advanceSeatSelection = if (p["advanceSeatSelection"] == "1") 1 else 0,
        priorityCheckin = if (p["priorityCheckin"] == "1") 1 else 0,
        priorityBoarding = if (p["priorityBoarding"] == "1") 1 else 0,
        loungeAccess = if (p["loungeAccess"] == "1") 1 else 0,
        carryOnAllowed = if (p["carryOnAllowed"] == "1") 1 else 0,
        carryOnWeightKg = p["carryOnWeightKg"]?.toIntOrNull() ?: 7,
        checkedBaggagePieces = p["checkedBaggagePieces"]?.toIntOrNull() ?: 0,
        checkedBaggageWeightKg = p["checkedBaggageWeightKg"]?.toIntOrNull() ?: 0,
        milesEarnRate = p["milesEarnRate"]?.toDoubleOrNull() ?: 1.0,
        minimumMilesForBooking = p["minimumMilesForBooking"]?.toIntOrNull(),
        description = p["description"]?.trim()?.ifBlank { null },
        colour = p["colour"]?.trim()?.ifBlank { "#6366f1" } ?: "#6366f1",
        createdAt = now,
        updatedAt = now,
    )

    FareClassTableAccess().createFareClass(fareClass)
 
    call.respondRedirect("/staff/fare-class?ok=Fare+class+created")
}

private suspend fun handlePostFareClassUpdate(call: ApplicationCall) {
    val session = call.sessions.get<StaffSession>() ?: run {
        call.respondRedirect("/staff/login")
        return
    }

    val p = call.receiveParameters()
    val id = p["id"]?.toIntOrNull() ?: run {
        call.respondRedirect("/staff/fare-class?error=Missing+fare+class+ID")
        return
    }
 
    val access = FareClassTableAccess()
    val now = Instant.now().toString()

    // Used Claude AI to write update clause, lines 179-213
    with(access) {
        p["cabinClass"]?.trim()?.ifBlank { null }
            .let { updateRecordByAttribute(id, com.flightbooking.tables.FareClassTable.cabinClass, it) }
        p["displayName"]?.trim()?.ifBlank { null }
            .let { updateRecordByAttribute(id, com.flightbooking.tables.FareClassTable.displayName, it) }
        updateRecordByAttribute(id, com.flightbooking.tables.FareClassTable.refundable,
            if (p["refundable"] == "1") 1 else 0)
        updateRecordByAttribute(id, com.flightbooking.tables.FareClassTable.cancelProtocol,
            p["cancelProtocol"]?.trim()?.ifBlank { "free cancellation" } ?: "free cancellation")
        updateRecordByAttribute(id, com.flightbooking.tables.FareClassTable.advanceSeatSelection,
            if (p["advanceSeatSelection"] == "1") 1 else 0)
        updateRecordByAttribute(id, com.flightbooking.tables.FareClassTable.priorityCheckin,
            if (p["priorityCheckin"] == "1") 1 else 0)
        updateRecordByAttribute(id, com.flightbooking.tables.FareClassTable.priorityBoarding,
            if (p["priorityBoarding"] == "1") 1 else 0)
        updateRecordByAttribute(id, com.flightbooking.tables.FareClassTable.loungeAccess,
            if (p["loungeAccess"] == "1") 1 else 0)
        updateRecordByAttribute(id, com.flightbooking.tables.FareClassTable.carryOnAllowed,
            if (p["carryOnAllowed"] == "1") 1 else 0)
        updateRecordByAttribute(id, com.flightbooking.tables.FareClassTable.carryOnWeightKg,
            p["carryOnWeightKg"]?.toIntOrNull() ?: 7)
        updateRecordByAttribute(id, com.flightbooking.tables.FareClassTable.checkedBaggagePieces,
            p["checkedBaggagePieces"]?.toIntOrNull() ?: 0)
        updateRecordByAttribute(id, com.flightbooking.tables.FareClassTable.checkedBaggageWeightKg,
            p["checkedBaggageWeightKg"]?.toIntOrNull() ?: 0)
        updateRecordByAttribute(id, com.flightbooking.tables.FareClassTable.milesEarnRate,
            p["milesEarnRate"]?.toDoubleOrNull() ?: 1.0)
        p["minimumMilesForBooking"]?.toIntOrNull()
            .let { updateRecordByAttribute(id, com.flightbooking.tables.FareClassTable.minimumMilesForBooking, it) }
        p["description"]?.trim()?.ifBlank { null }
            .let { updateRecordByAttribute(id, com.flightbooking.tables.FareClassTable.description, it) }
        updateRecordByAttribute(id, com.flightbooking.tables.FareClassTable.colour,
            p["colour"]?.trim()?.ifBlank { "#6366f1" } ?: "#6366f1")
        updateRecordByAttribute(id, com.flightbooking.tables.FareClassTable.updatedAt, now)
    }

    call.respondRedirect("/staff/fare-class?ok=Fare+class+updated")
}

private suspend fun handlePostFareClassDelete(call: ApplicationCall) {
    val session = call.sessions.get<StaffSession>() ?: run {
        call.respondRedirect("/staff/login")
        return
    }

    val p = call.receiveParameters()
    val id = p["id"]?.toIntOrNull() ?: run {
        call.respondRedirect("/staff/fare-class?error=Missing+fare+class+ID")
        return
    }
 
    FareClassTableAccess().deleteByID(id)

    call.respondRedirect("/staff/fare-class?ok=Fare+class+deleted")
}