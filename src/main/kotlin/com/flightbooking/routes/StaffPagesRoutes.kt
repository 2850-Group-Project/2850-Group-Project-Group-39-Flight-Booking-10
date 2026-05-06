package com.flightbooking.routes

import com.flightbooking.models.StaffSession
import com.flightbooking.service.AuthService
import com.flightbooking.tables.FareClassTable
import com.flightbooking.tables.FlightFareTable
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
import org.jetbrains.exposed.sql.select
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
        val (_, _) = AuthService.requireStaff(call)
        handleGetStaffDashboard(call)
    }

    get("/staff/flights") { handleGetStaffFlights(call) }

    post("/staff/flights/create") {
        val (_, _) = AuthService.requireStaff(call)

        val params = call.receiveParameters()
        val capacityIntOrNull = params["capacity"]?.toIntOrNull()

        transaction {
            val stmt =
                FlightTable.insert {
                    it[FlightTable.flightNumber] = params["flightNumber"]?.toIntOrNull()
                    it[FlightTable.originAirport] = params["originId"]!!.toInt()
                    it[FlightTable.destinationAirport] = params["destId"]!!.toInt()
                    it[FlightTable.scheduledDepartureTime] = params["dep"]
                    it[FlightTable.scheduledArrivalTime] = params["arr"]
                    it[FlightTable.status] = params["status"] ?: "scheduled"
                    it[FlightTable.capacity] = capacityIntOrNull
                }

            val newFlightId =
                stmt.resultedValues?.firstOrNull()?.get(FlightTable.id)
                    ?: FlightTable.selectAll().orderBy(FlightTable.id, SortOrder.DESC).limit(1).first()[FlightTable.id]

            val fareClassIds = params.getAll("fareClassId")?.mapNotNull { it.toIntOrNull() } ?: emptyList()

            // Used Claude AI to generate fareAllocations mapping, lines 96-103
            val fareAllocations =
                fareClassIds.mapNotNull { fareClassId ->
                    val cabinClass =
                        FareClassTable
                            .select { FareClassTable.id eq fareClassId }
                            .firstOrNull()
                            ?.get(FareClassTable.cabinClass) ?: return@mapNotNull null
                    val seats = params["seats_$fareClassId"]?.toIntOrNull() ?: 0
                    cabinClass to seats
                }

            createSeatsForFlight(newFlightId, capacityIntOrNull, fareAllocations)

            fareClassIds.forEach { fareClassId ->
                val price = params["price_$fareClassId"]?.toDoubleOrNull() ?: 0.0
                val seats = params["seats_$fareClassId"]?.toIntOrNull() ?: 0
                FlightFareTable.insert {
                    it[FlightFareTable.flightId] = newFlightId
                    it[FlightFareTable.fareClassId] = fareClassId
                    it[FlightFareTable.price] = price
                    it[FlightFareTable.seatsAvailable] = seats
                    it[FlightFareTable.currency] = "GBP"
                }
            }
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

    val params = call.receiveParameters()
    val id =
        params["id"]?.toIntOrNull() ?: run {
            call.respondRedirect("/staff/flights?error=Invalid flight ID")
            return
        }

    transaction {
        FlightTable.update({ FlightTable.id eq id }) {
            it[flightNumber] = params["flightNumber"]?.toIntOrNull()
            it[originAirport] = params["originId"]!!.toInt()
            it[destinationAirport] = params["destId"]!!.toInt()
            it[scheduledDepartureTime] = params["dep"]
            it[scheduledArrivalTime] = params["arr"]
            it[status] = params["status"] ?: "scheduled"
            it[capacity] = params["capacity"]?.toIntOrNull()

            // Deletes all flight fares connected to that flight
            // Imporant since patching updates is hard
            // So just remove them all and replace them
            FlightFareTable.deleteWhere { FlightFareTable.flightId eq id }

            val fareClassIds = params.getAll("fareClassId")?.mapNotNull { it.toIntOrNull() } ?: emptyList()

            fareClassIds.forEach { fareClassId ->
                val price = params["price_$fareClassId"]?.toDoubleOrNull() ?: 0.0
                val seats = params["seats_$fareClassId"]?.toIntOrNull() ?: 0
                FlightFareTable.insert {
                    it[FlightFareTable.flightId] = id
                    it[FlightFareTable.fareClassId] = fareClassId
                    it[FlightFareTable.price] = price
                    it[FlightFareTable.seatsAvailable] = seats
                    it[FlightFareTable.currency] = "GBP"
                }
            }
        }
    }

    call.respondRedirect("/staff/flights?ok=Flight updated")
}
