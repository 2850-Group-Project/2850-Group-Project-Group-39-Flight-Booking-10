package com.flightbooking.routes

import com.flightbooking.models.StaffSession
import com.flightbooking.tables.*
import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.request.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate

/**
 * Registers the staff page routes (dashboard + flight management UI).
 *
 * Routes included:
 * - **GET `/staff/dashboard`**: Requires [StaffSession]. :
 *      Loads staff info + flight/complaint metrics and renders `staff_dashboard.peb`.
 * - **GET `/staff/flights`**: Requires [StaffSession]. Supports optional query params:
 *   - `edit` (Int): loads a flight into the edit form
 *   - `q` (String): filters flights by flight number
 *   Renders `staff_flights.peb`.
 * - **POST `/staff/flights/create`**: Requires [StaffSession]. :
 * Creates a new flight and initialises seats for that flight, then redirects back to `/staff/flights`.
 * - **POST `/staff/flights/update`**: Requires [StaffSession]. :
 *      Updates an existing flight and ensures seats exist, then redirects back to `/staff/flights`.
 * - **POST `/staff/flights/delete`**: Requires [StaffSession]. :
 *      Deletes a flight, then redirects back to `/staff/flights`.
 * - **GET `/staff/logout`**: Clears [StaffSession] and redirects to `/staff/login`.
 */
fun Route.staffPagesRoutes() {

    get("/staff/dashboard") {
        val session = call.sessions.get<StaffSession>()
        if (session == null) {
            call.respondRedirect("/staff/login")
            return@get
        }

        val staffEmail = session.staffEmail

        val model = transaction {
            val staffRow = StaffTable
                .select { StaffTable.email eq staffEmail }
                .limit(1)
                .firstOrNull()

            if (staffRow == null) {
                return@transaction mapOf<String, Any>("error" to "Staff not found, please login again.")
            }

            val staffName = listOfNotNull(
                staffRow[StaffTable.firstName],
                staffRow[StaffTable.lastName]
            ).joinToString(" ").ifBlank { "Staff" }

            val staffRole = staffRow[StaffTable.role] ?: "Staff"

            val todayPrefix = LocalDate.now().toString()

            val activeFlightsCount = FlightTable
                .select { FlightTable.status neq "cancelled" }
                .count()

            val departuresTodayCount = FlightTable
                .select { FlightTable.scheduledDepartureTime like "$todayPrefix%" }
                .count()

            val systemAlertsCount = ComplaintTable
                .select { ComplaintTable.status eq "open" }
                .count()

            val origin = AirportTable.alias("origin")
            val dest = AirportTable.alias("dest")

            val flightList = (FlightTable
                .join(origin, JoinType.INNER, additionalConstraint = { 
                    FlightTable.originAirport eq origin[AirportTable.id] })
                .join(dest, JoinType.INNER, additionalConstraint = { 
                    FlightTable.destinationAirport eq dest[AirportTable.id] })
                .slice(
                    FlightTable.id,
                    FlightTable.flightNumber,
                    FlightTable.scheduledDepartureTime,
                    FlightTable.status,
                    FlightTable.capacity,
                    origin[AirportTable.iataCode],
                    dest[AirportTable.iataCode]
                )
                .select { FlightTable.status neq "cancelled" }
                .orderBy(FlightTable.id, SortOrder.DESC)
                .limit(8)
                .map { row ->
                    val no = row[FlightTable.flightNumber]?.toString() ?: row[FlightTable.id].toString()
                    val destination = row[dest[AirportTable.iataCode]]
                    val departureTimeRaw = row[FlightTable.scheduledDepartureTime] ?: ""
                    val depTime = if  (departureTimeRaw.length >= 16) {
                        departureTimeRaw.substring(11, 16) 
                    } else { departureTimeRaw }
                    val status = row[FlightTable.status]
                    val cap = row[FlightTable.capacity]?.toString() ?: ""

                    mapOf(
                        "no" to no,
                        "dest" to destination,
                        "dep" to depTime,
                        "status" to status,
                        "capacity" to cap
                    )
                })

            mapOf(
                "staffEmail" to staffEmail,
                "staffName" to staffName,
                "staffRole" to staffRole,
                "activeFlights" to activeFlightsCount,
                "departuresToday" to departuresTodayCount,
                "systemAlerts" to systemAlertsCount,
                "flights" to flightList
            )
        }

        if (model.containsKey("error")) {
            call.respondText(model["error"].toString())
            return@get
        }

        call.respond(PebbleContent("staff_dashboard.peb", model))
    }

    get("/staff/flights") {
        val session = call.sessions.get<StaffSession>()
        if (session == null) {
            call.respondRedirect("/staff/login")
            return@get
        }

        val editId = call.request.queryParameters["edit"]?.toIntOrNull()
        val q = call.request.queryParameters["q"]?.trim().orEmpty()

        val model = transaction {
            val airports = AirportTable
                .selectAll()
                .orderBy(AirportTable.iataCode, SortOrder.ASC)
                .map {
                    mapOf(
                        "id" to it[AirportTable.id],
                        "iata" to it[AirportTable.iataCode],
                        "name" to (it[AirportTable.name] ?: "")
                    )
                }

            val origin = AirportTable.alias("origin")
            val dest = AirportTable.alias("dest")

            val flights = (FlightTable
                .join(origin, JoinType.INNER, additionalConstraint = { 
                    FlightTable.originAirport eq origin[AirportTable.id] })
                .join(dest, JoinType.INNER, additionalConstraint = { 
                    FlightTable.destinationAirport eq dest[AirportTable.id] })
                .slice(
                    FlightTable.id,
                    FlightTable.flightNumber,
                    FlightTable.originAirport,
                    FlightTable.destinationAirport,
                    FlightTable.scheduledDepartureTime,
                    FlightTable.scheduledArrivalTime,
                    FlightTable.status,
                    FlightTable.capacity,
                    origin[AirportTable.iataCode],
                    dest[AirportTable.iataCode]
                )
                .select {
                    if (q.isBlank()) Op.TRUE
                    else FlightTable.flightNumber.castTo<String>(VarCharColumnType()).like("%$q%")
                }
                .orderBy(FlightTable.id, SortOrder.DESC)
                .map { row ->
                    mapOf(
                        "id" to row[FlightTable.id],
                        "flightNumber" to (row[FlightTable.flightNumber]?.toString() ?: ""),
                        "originId" to row[FlightTable.originAirport],
                        "destId" to row[FlightTable.destinationAirport],
                        "originIata" to row[origin[AirportTable.iataCode]],
                        "destIata" to row[dest[AirportTable.iataCode]],
                        "dep" to (row[FlightTable.scheduledDepartureTime] ?: ""),
                        "arr" to (row[FlightTable.scheduledArrivalTime] ?: ""),
                        "status" to row[FlightTable.status],
                        "capacity" to (row[FlightTable.capacity]?.toString() ?: "")
                    )
                }
            )
            val editFlight = if (editId != null) flights.firstOrNull { (it["id"] as Int) == editId } else null

            mapOf(
                "airports" to airports,
                "flights" to flights,
                "q" to q,
                "editId" to (editId ?: 0),
                "editFlight" to (editFlight ?: mapOf<String, Any>()),
                "error" to (call.request.queryParameters["error"] ?: ""),
                "ok" to (call.request.queryParameters["ok"] ?: "")
            )
        }

        call.respond(PebbleContent("staff_flights.peb", model))
    }

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
            val stmt = FlightTable.insert {
                it[FlightTable.flightNumber] = flightNumberIntOrNull
                it[FlightTable.originAirport] = originId
                it[FlightTable.destinationAirport] = destId
                it[FlightTable.scheduledDepartureTime] = if (dep.isBlank()) null else dep
                it[FlightTable.scheduledArrivalTime] = if (arr.isBlank()) null else arr
                it[FlightTable.status] = status
                it[FlightTable.capacity] = capacityIntOrNull
            }

            val newFlightId = stmt.resultedValues?.firstOrNull()?.get(FlightTable.id)
                ?: FlightTable.selectAll().orderBy(FlightTable.id, SortOrder.DESC).limit(1).first()[FlightTable.id]

            createSeatsForFlight(newFlightId, capacityIntOrNull)
        }

        call.respondRedirect("/staff/flights?ok=Flight created")
    }

    post("/staff/flights/update") {
        val session = call.sessions.get<StaffSession>()
        if (session == null) {
            call.respondRedirect("/staff/login")
            return@post
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

        if (id == null) {
            call.respondRedirect("/staff/flights?error=Missing flight id")
            return@post
        }
        if (originId == null || destId == null) {
            call.respondRedirect("/staff/flights?error=Please select origin and destination&edit=$id")
            return@post
        }
        if (originId == destId) {
            call.respondRedirect("/staff/flights?error=Origin and destination cannot be the same&edit=$id")
            return@post
        }

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

    post("/staff/flights/delete") {
        val session = call.sessions.get<StaffSession>()
        if (session == null) {
            call.respondRedirect("/staff/login")
            return@post
        }

        val p = call.receiveParameters()
        val id = p["id"]?.toIntOrNull()
        if (id == null) {
            call.respondRedirect("/staff/flights?error=Missing flight id")
            return@post
        }

        transaction {
            FlightTable.deleteWhere { FlightTable.id eq id }
        }

        call.respondRedirect("/staff/flights?ok=Flight deleted")
    }

    get("/staff/logout") {
        call.sessions.clear<StaffSession>()
        call.respondRedirect("/staff/login")
    }
}

private fun createSeatsForFlight(flightId: Int, capacity: Int?) {
    val existing = SeatTable.select { SeatTable.flightId eq flightId }.count()
    if (existing > 0L) return

    val total = (capacity ?: 180).coerceAtLeast(1)
    val letters = listOf("A", "B", "C", "D", "E", "F")
    val seatMaps = ArrayList<Map<String, Any>>(total)

    for (i in 0 until total) {
        val row = (i / letters.size) + 1
        val col = letters[i % letters.size]
        val code = "$row$col"
        seatMaps.add(mapOf("flightId" to flightId, "seatCode" to code))
    }

    SeatTable.batchInsert(seatMaps) { s ->
        this[SeatTable.flightId] = s["flightId"] as Int
        this[SeatTable.seatCode] = s["seatCode"] as String
        this[SeatTable.cabinClass] = null
        this[SeatTable.position] = null
        this[SeatTable.extraLegroom] = 0
        this[SeatTable.exitRow] = 0
        this[SeatTable.reducedMobility] = 0
        this[SeatTable.status] = "available"
    }
}
