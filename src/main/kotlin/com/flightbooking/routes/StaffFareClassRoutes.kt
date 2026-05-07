package com.flightbooking.routes

import com.flightbooking.access.FareClassTableAccess
import com.flightbooking.models.FareClass
import com.flightbooking.models.StaffSession
import com.flightbooking.tables.FareClassTable
import com.flightbooking.tables.StaffTable
import io.ktor.http.Parameters
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
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

private const val DEFAULT_CARRY_ON_WEIGHT = 7
private const val DEFAULT_COLOUR = "#6366f1"
private const val DEFAULT_CANCEL_PROTOCOL = "free cancellation"

/**
 * Staff fare class management routes.
 *
 * Routes:
 * - GET  /staff/fare-class ->
 *   - Requires [StaffSession]; Retrieves all fare classes and staff info,
 *   - then renders the staff fare class management page
 * - POST /staff/fare-class/create ->
 *   - Requires [StaffSession]; Validates, creates new fare classes from POST form data, redirects
 * - POST /staff/fare-class/update ->
 *   - Requires [StaffSession]; Validates fareClassId from form, updates then redirects
 * - POST /staff/fare-class/delete ->
 *   - Requires [StaffSession]; Validates fareClassId, deletes and redirects
 */
fun Route.staffFareClassRoutes() {
    get("/staff/fare-class") { handleGetStaffFareClass(call) }
    post("/staff/fare-class/create") { handlePostFareClassCreate(call) }
    post("/staff/fare-class/update") { handlePostFareClassUpdate(call) }
    post("/staff/fare-class/delete") { handlePostFareClassDelete(call) }
}

/**
 * Handles GET requests for the staff fare class management page.
 * Redirects to staff login if no valid session is found.
 * Fetches all fare classes and maps them to a display-friendly format,
 * then gets logged-in staff member's details page
 *
 * @param call the application call
 */
private suspend fun handleGetStaffFareClass(call: ApplicationCall) {
    val staffSession =
        call.sessions.get<StaffSession>() ?: run {
            call.respondRedirect("/staff/login")
            return
        }

    val fareClasses =
        FareClassTableAccess().getAll().map { fc ->
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

    val staffInfo =
        transaction {
            StaffTable.select { StaffTable.email eq staffSession.staffEmail }.firstOrNull()
        }

    val model =
        mapOf(
            "fareClasses" to fareClasses,
            "staffName" to
                listOfNotNull(
                    staffInfo?.get(StaffTable.firstName),
                    staffInfo?.get(StaffTable.lastName),
                ).joinToString(" ").ifBlank { "Staff" },
            "staffRole" to (staffInfo?.get(StaffTable.role) ?: "Staff"),
            "staffEmail" to staffSession.staffEmail,
            "error" to (call.request.queryParameters["error"] ?: ""),
            "ok" to (call.request.queryParameters["ok"] ?: ""),
            "editId" to (call.request.queryParameters["edit"]?.toIntOrNull() ?: 0),
        )

    call.respond(PebbleContent("staff_fare_class.peb", model))
}

/**
 * Handler function for POST to create a new fare class.
 * Redirects to staff login if no valid session is found.
 * Validates that a class code is provided, then maps the submitted
 * form parameters to a new [FareClass] object with sensible defaults
 * for any missing optional fields, persists it to the database,
 * and redirects back to the fare class management page.
 *
 * @param call the application call
 */
private suspend fun handlePostFareClassCreate(call: ApplicationCall) {
    call.sessions.get<StaffSession>() ?: run {
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
    FareClassTableAccess().createFareClass(
        FareClass(
            id = 0,
            classCode = classCode,
            cabinClass = p["cabinClass"]?.trim()?.ifBlank { null },
            displayName = p["displayName"]?.trim()?.ifBlank { null },
            refundable = flag(p, "refundable"),
            cancelProtocol =
                p["cancelProtocol"]
                    ?.trim()
                    ?.ifBlank { DEFAULT_CANCEL_PROTOCOL }
                    ?: DEFAULT_CANCEL_PROTOCOL,
            advanceSeatSelection = flag(p, "advanceSeatSelection"),
            priorityCheckin = flag(p, "priorityCheckin"),
            priorityBoarding = flag(p, "priorityBoarding"),
            loungeAccess = flag(p, "loungeAccess"),
            carryOnAllowed = flag(p, "carryOnAllowed"),
            carryOnWeightKg = p["carryOnWeightKg"]?.toIntOrNull() ?: DEFAULT_CARRY_ON_WEIGHT,
            checkedBaggagePieces = p["checkedBaggagePieces"]?.toIntOrNull() ?: 0,
            checkedBaggageWeightKg = p["checkedBaggageWeightKg"]?.toIntOrNull() ?: 0,
            milesEarnRate = p["milesEarnRate"]?.toDoubleOrNull() ?: 1.0,
            minimumMilesForBooking = p["minimumMilesForBooking"]?.toIntOrNull(),
            description = p["description"]?.trim()?.ifBlank { null },
            colour = p["colour"]?.trim()?.ifBlank { DEFAULT_COLOUR } ?: DEFAULT_COLOUR,
            createdAt = now,
            updatedAt = now,
        ),
    )

    call.respondRedirect("/staff/fare-class?ok=Fare+class+created")
}

/**
 * Handler function for POST to update an existing fare class.
 * Redirects to staff login if no valid session
 * Validates a fare class ID is present
 * Applies the updates via [applyFareClassUpdates]
 * Redirects back to the fare class management page with a success or error
 *
 * @param call the application call
 */
private suspend fun handlePostFareClassUpdate(call: ApplicationCall) {
    call.sessions.get<StaffSession>() ?: run {
        call.respondRedirect("/staff/login")
        return
    }

    val p = call.receiveParameters()
    val id =
        p["id"]?.toIntOrNull() ?: run {
            call.respondRedirect("/staff/fare-class?error=Missing+fare+class+ID")
            return
        }

    applyFareClassUpdates(id, p)
    call.respondRedirect("/staff/fare-class?ok=Fare+class+updated")
}

/**
 * Mapper for form parameters to corresponding fare class fields
 * using [updateNullable] for optional fields that can be cleared
 * [updateRecordByAttribute] for required fields with defaults
 * Persists updates across DB
 * @param id
 * @param p: form parameters
 */
private fun applyFareClassUpdates(
    id: Int,
    p: Parameters,
) {
    val access = FareClassTableAccess()
    val now = Instant.now().toString()
    with(access) {
        updateNullable(id, FareClassTable.cabinClass, p["cabinClass"]?.trim()?.ifBlank { null })
        updateNullable(id, FareClassTable.displayName, p["displayName"]?.trim()?.ifBlank { null })
        updateNullable(id, FareClassTable.description, p["description"]?.trim()?.ifBlank { null })
        updateNullable(id, FareClassTable.minimumMilesForBooking, p["minimumMilesForBooking"]?.toIntOrNull())

        updateRecordByAttribute(id, FareClassTable.refundable, flag(p, "refundable"))
        updateRecordByAttribute(
            id,
            FareClassTable.cancelProtocol,
            p["cancelProtocol"]?.trim()?.ifBlank {
                DEFAULT_CANCEL_PROTOCOL
            } ?: DEFAULT_CANCEL_PROTOCOL,
        )
        updateRecordByAttribute(id, FareClassTable.advanceSeatSelection, flag(p, "advanceSeatSelection"))
        updateRecordByAttribute(id, FareClassTable.priorityCheckin, flag(p, "priorityCheckin"))
        updateRecordByAttribute(id, FareClassTable.priorityBoarding, flag(p, "priorityBoarding"))
        updateRecordByAttribute(id, FareClassTable.loungeAccess, flag(p, "loungeAccess"))
        updateRecordByAttribute(id, FareClassTable.carryOnAllowed, flag(p, "carryOnAllowed"))
        updateRecordByAttribute(
            id,
            FareClassTable.carryOnWeightKg,
            p["carryOnWeightKg"]
                ?.toIntOrNull()
                ?: DEFAULT_CARRY_ON_WEIGHT,
        )
        updateRecordByAttribute(id, FareClassTable.checkedBaggagePieces, p["checkedBaggagePieces"]?.toIntOrNull() ?: 0)
        updateRecordByAttribute(
            id,
            FareClassTable.checkedBaggageWeightKg,
            p["checkedBaggageWeightKg"]
                ?.toIntOrNull()
                ?: 0,
        )
        updateRecordByAttribute(id, FareClassTable.milesEarnRate, p["milesEarnRate"]?.toDoubleOrNull() ?: 1.0)
        updateRecordByAttribute(
            id,
            FareClassTable.colour,
            p["colour"]
                ?.trim()
                ?.ifBlank { DEFAULT_COLOUR }
                ?: DEFAULT_COLOUR,
        )
        updateRecordByAttribute(id, FareClassTable.updatedAt, now)
    }
}

/**
 * Handler function for POST to delete a fare class in DB
 * Redirects to staff login if no valid session
 * Validates fare class ID is present in the form parameters
 * Deletes the record via [FareClassTableAccess.deleteByID]
 * Redirects to fare class management page with a success or error
 *
 * @param call the application call
 */
private suspend fun handlePostFareClassDelete(call: ApplicationCall) {
    call.sessions.get<StaffSession>() ?: run {
        call.respondRedirect("/staff/login")
        return
    }

    val p = call.receiveParameters()
    val id =
        p["id"]?.toIntOrNull() ?: run {
            call.respondRedirect("/staff/fare-class?error=Missing+fare+class+ID")
            return
        }

    FareClassTableAccess().deleteByID(id)
    call.respondRedirect("/staff/fare-class?ok=Fare+class+deleted")
}

/**
 * Converts form parameter into flag
 * @param p: the parameters
 * @param key: parameter key to check by
 */
private fun flag(
    p: Parameters,
    key: String,
): Int = if (p[key] == "1") 1 else 0

/**
 * Wrapper around updateRecordByAttribute for nullables
 * @param id: fare class to update
 * @param col
 * @param value
 */
private fun <T> FareClassTableAccess.updateNullable(
    id: Int,
    col: Column<T?>,
    value: T?,
) = updateRecordByAttribute(id, col, value)
