package com.flightbooking.routes

import com.flightbooking.models.StaffSession
import com.flightbooking.tables.FlightTable
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * Registers the staff page routes (dashboard + flight management UI).
 *
 * Routes:
 * - GET /staff/dashboard ->
 *      Requires [StaffSession]
 *      Loads staff info + flight/complaint metrics and renders `staff_dashboard.peb`
 * - GET /staff/flights -> 
 *      Requires [StaffSession]
 *      Supports optional query params:
 *   - edit (Int): loads a flight into the edit form
 *   - q (String): filters flights by flight number
 *   Renders `staff_flights.peb`
 * - POST /staff/flights/create -> 
 *      Requires [StaffSession]
 *      Creates a new flight and initialises seats for that flight, then redirects back to /staff/flights
 * - POST /staff/flights/update ->
 *      Requires [StaffSession]
 *      Updates an existing flight and ensures seats exist, then redirects back to /staff/flights
 * - POST /staff/flights/delete -> 
 *      Requires [StaffSession]
 *      Deletes a flight, then redirects back to /staff/flights
 * - GET /staff/logout ->
 *      Clears [StaffSession] and redirects to /staff/login
 */
fun Route.staffPagesRoutes() {
    get("/staff/dashboard") {
        val session = call.sessions.get<StaffSession>()
        if (session == null) {
            call.respondRedirect("/staff/login")
            return@get
        }
        handleGetStaffDashboard(call)
    }
    get("/staff/flights") { handleGetStaffFlights(call) }

    post("/staff/flights/create") {
        val session = call.sessions.get<StaffSession>()
        if (session == null) {
            call.respondRedirect("/staff/login")
            return@post
        }

        val p = call.receiveParameters()

        val flightNumberText = p["flightNumber"]?.trim().orEmpty()
        val flightNumberIntOrNull = flightNumberText.toIntOrNull()
        val originId = p["originId"]?.toIntOrNull()
        val destId = p["destId"]?.toIntOrNull()
        val dep = p["dep"]?.trim().orEmpty()
        val arr = p["arr"]?.trim().orEmpty()
        val status = p["status"]?.trim().orEmpty().ifBlank { "scheduled" }
        val capacityIntOrNull = p["capacity"]?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()

        if (originId == null || destId == null) {
            call.respondRedirect("/staff/flights?error=Please select origin and destination")
            return@post
        }
        if (originId == destId) {
            call.respondRedirect("/staff/flights?error=Origin and destination cannot be the same")
            return@post
        }

        transaction {
            val stmt =
                FlightTable.insert {
                    it[FlightTable.flightNumber] = flightNumberIntOrNull
                    it[FlightTable.originAirport] = originId
                    it[FlightTable.destinationAirport] = destId
                    it[FlightTable.scheduledDepartureTime] = if (dep.isBlank()) null else dep
                    it[FlightTable.scheduledArrivalTime] = if (arr.isBlank()) null else arr
                    it[FlightTable.status] = status
                    it[FlightTable.capacity] = capacityIntOrNull
                }

            val newFlightId =
                stmt.resultedValues?.firstOrNull()?.get(FlightTable.id)
                    ?: FlightTable.selectAll().orderBy(FlightTable.id, SortOrder.DESC).limit(1).first()[FlightTable.id]

            createSeatsForFlight(newFlightId, capacityIntOrNull)
        }

        call.respondRedirect("/staff/flights?ok=Flight created")
    }

    post("/staff/flights/update") { handlePostStaffFlightsUpdate(call) }

    post("/staff/flights/delete") { handlePostStaffFlightsDelete(call) }
    get("/staff/logout") {
        call.sessions.clear<StaffSession>()
        call.respondRedirect("/staff/login")
    }
}

/**
 * Function that handles getting/displaying staff dashboard
 * @param call request call
 */
private suspend fun handleGetStaffDashboard(call: ApplicationCall) {
    val session = call.sessions.get<StaffSession>()
    checkNotNull(session)

    val model = buildStaffDashboardModel(session.staffEmail)

    if (model.containsKey("error")) {
        call.respondText(model["error"].toString())
        return
    }

    call.respond(PebbleContent("staff_dashboard.peb", model))
}

/**
 * Function that handles getting/displaying staff flights page
 * @param call request call
 */
private suspend fun handleGetStaffFlights(call: ApplicationCall) {
    val session = call.sessions.get<StaffSession>()
    if (session == null) {
        call.respondRedirect("/staff/login")
        return
    }

    val model =
        buildStaffFlightsModel(
            q = call.request.queryParameters["q"]?.trim().orEmpty(),
            editId = call.request.queryParameters["edit"]?.toIntOrNull(),
            urlError = call.request.queryParameters["error"] ?: "",
            urlOk = call.request.queryParameters["ok"] ?: "",
        )

    call.respond(PebbleContent("staff_flights.peb", model))
}

/**
 * Function that handles submitting staff's flight deletion
 * @param call request call
 */
private suspend fun handlePostStaffFlightsDelete(call: ApplicationCall) {
    val session = call.sessions.get<StaffSession>()
    if (session == null) {
        call.respondRedirect("/staff/login")
        return
    }

    val p = call.receiveParameters()
    val id = p["id"]?.toIntOrNull()
    if (id == null) {
        call.respondRedirect("/staff/flights?error=Missing flight id")
        return
    }

    transaction {
        FlightTable.deleteWhere { FlightTable.id eq id }
    }

    call.respondRedirect("/staff/flights?ok=Flight deleted")
}

/**
 * Function that handles submitting and processing staff flight update information
 * @param call request call
 */
private suspend fun handlePostStaffFlightsUpdate(call: ApplicationCall) {
    val session = call.sessions.get<StaffSession>()
    if (session == null) {
        call.respondRedirect("/staff/login")
        return
    }

    val p = call.receiveParameters()

    val id = p["id"]?.toIntOrNull()
    val flightNumberText = p["flightNumber"]?.trim().orEmpty()
    val flightNumberIntOrNull = flightNumberText.toIntOrNull()
    val originId = p["originId"]?.toIntOrNull()
    val destId = p["destId"]?.toIntOrNull()
    val dep = p["dep"]?.trim().orEmpty()
    val arr = p["arr"]?.trim().orEmpty()
    val status = p["status"]?.trim().orEmpty().ifBlank { "scheduled" }
    val capacityIntOrNull = p["capacity"]?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()
    var redirect = ""
    if (id == null) {
        redirect = "/staff/flights?error=Missing flight id"
    }
    if (originId == null || destId == null) {
        redirect = "/staff/flights?error=Please select origin and destination&edit=$id"
    }
    if (originId == destId) {
        redirect = "/staff/flights?error=Origin and destination cannot be the same&edit=$id"
    }
    if (redirect.isNotEmpty()) {
        call.respondRedirect(redirect)
        return
    }
    checkNotNull(id)
    checkNotNull(originId)
    checkNotNull(destId)

    transaction {
        FlightTable.update({ FlightTable.id eq id }) {
            it[FlightTable.flightNumber] = flightNumberIntOrNull
            it[FlightTable.originAirport] = originId
            it[FlightTable.destinationAirport] = destId
            it[FlightTable.scheduledDepartureTime] = if (dep.isBlank()) null else dep
            it[FlightTable.scheduledArrivalTime] = if (arr.isBlank()) null else arr
            it[FlightTable.status] = status
            it[FlightTable.capacity] = capacityIntOrNull
        }

        createSeatsForFlight(id, capacityIntOrNull)
    }

    call.respondRedirect("/staff/flights?ok=Flight updated")
}
