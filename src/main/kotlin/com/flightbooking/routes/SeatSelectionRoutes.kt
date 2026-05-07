package com.flightbooking.routes

import com.flightbooking.access.AirportTableAccess
import com.flightbooking.access.BookingTableAccess
import com.flightbooking.access.ComplaintResponseTableAccess
import com.flightbooking.access.FlightFareTableAccess
import com.flightbooking.access.FlightTableAccess
import com.flightbooking.access.SeatTableAccess
import com.flightbooking.models.BookingSession
import com.flightbooking.models.SeatSelectionSession
import com.flightbooking.models.SeatSelectionEntry
import com.flightbooking.models.Seat
import com.flightbooking.service.AuthService
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.FareClassTable
import com.flightbooking.tables.FlightFareTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.SeatAssignmentTable
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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

private const val PAYMENT_REDIRECT = "/payment?ok=Seats assigned successfully"
private const val SEARCH_REDIRECT = "/flights/search"

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
fun Route.seatSelectionRoutes() {
    get("/flights/seats") {
        handleGetSeats(call, leg = "outbound")
    }
    post("/flights/seats") {
        handlePostSeats(call, leg = "outbound")
    }
    // Saves outbound seats then redirects to the return seat selection page
    post("/flights/seats/outbound") {
        handlePostSeats(call, leg = "outbound", nextStep = "/flights/seats/return")
    }
    get("/flights/seats/return") {
        handleGetSeats(call, leg = "return")
    }
    post("/flights/seats/return") {
        handlePostSeats(call, leg = "return")
    }
}

/**
 * Resolves the flight and fare IDs for the given leg from the booking session.
 */
private fun resolveIds(
    bookingSession: BookingSession,
    leg: String,
): Pair<Int?, Int?> {
    val flightId = if (leg == "return") bookingSession.returnFlightId else bookingSession.outboundFlightId
    val fareId = if (leg == "return") bookingSession.returnFareId else bookingSession.outboundFareId
    return flightId to fareId
}

/**
 * Holds the resolved seat map data needed to build the seat selection page.
 */
private data class SeatMapData(
    val seatStatusByCode: Map<String, String>,
    val seatCabinMap: Map<String, String>,
    val cabinColourMap: Map<String, String>,
    val seatPriceMap: Map<String, Double>,
    val seats: List<Seat>,
)

/**
 * Builds and returns all seat rendering data required for the seat map UI.
 *
 * Collects:
 * - seat availability status
 * - seat-to-cabin mappings
 * - cabin class colours
 * - seat pricing
 * - raw seat records
 *
 * @param flightId ID of the flight whose seat data should be loaded.
 * @return Aggregated seat map data for rendering the seat selection UI.
 */
private fun buildSeatMapData(flightId: Int): SeatMapData {
    val seats = SeatTableAccess().getByAttribute(SeatTable.flightId, flightId)

    val seatStatusByCode =
        transaction {
            val flightSeatIds = seats.map { it.id }.toSet()
            val assignedSeatIds =
                SeatAssignmentTable
                    .select { SeatAssignmentTable.seatId.isNotNull() }
                    .mapNotNull { it[SeatAssignmentTable.seatId] }
                    .filter { it in flightSeatIds } // only check seats for this flight
                    .toSet()
            seats.associate { seat ->
                seat.seatCode to
                    when {
                        seat.id in assignedSeatIds -> "occupied"
                        seat.status == "occupied" -> "occupied"
                        else -> "available"
                    }
            }
        }

    val seatCabinMap = seats.associate { it.seatCode to (it.cabinClass ?: "") }
    val cabinColourMap = getCabinColourMap(flightId)
    val seatPriceMap = getSeatPriceMap(seats, flightId)

    return SeatMapData(seatStatusByCode, seatCabinMap, cabinColourMap, seatPriceMap, seats)
}

/**
 * Builds the model used to render the seat selection Pebble page.
 *
 * Loads:
 * - flight details
 * - origin/destination airports
 * - selected fare
 * - booking passengers
 * - seat layout/render data
 * - unread inquiry count
 *
 * @param params Parameters required to build the seat selection page model.
 * @return Complete Pebble model map, or null if the flight does not exist.
 */
private fun buildSeatPageModel(params: SeatPageParams): Map<String, Any>? {
    val flight =
        FlightTableAccess()
            .getByAttribute(FlightTable.id, params.flightId)
            .firstOrNull() ?: return null

    val airportAccess = AirportTableAccess()
    val origin = airportAccess.getByAttribute(AirportTable.id, flight.originAirport).firstOrNull()
    val dest = airportAccess.getByAttribute(AirportTable.id, flight.destinationAirport).firstOrNull()

    val flightFare =
        params.fareId?.let {
            FlightFareTableAccess().getByAttribute(FlightFareTable.id, it).firstOrNull()
        }

    val passengers = BookingTableAccess().getPassengersForBooking(params.bookingSession.bookingId)

    val capacity = (flight.capacity ?: SMALL_AIRCRAFT_CAP_THRESHOLD).coerceAtLeast(1)
    val seatMapData = buildSeatMapData(flight.id)

    val seatRows =
        buildSeatRows(
            capacity,
            getLayout(capacity),
            SeatRenderContext(
                seatStatusByCode = seatMapData.seatStatusByCode,
                seatPriceMap = seatMapData.seatPriceMap,
                cabinColourMap = seatMapData.cabinColourMap,
                seatCabinMap = seatMapData.seatCabinMap,
            ),
        )

    val unreadCount = ComplaintResponseTableAccess().getUnreadResponsesCountForUser(params.userId)

    val base =
        buildSeatsModel(
            SeatsModelParams(
                flight = flight,
                origin = origin,
                dest = dest,
                passengers = passengers,
                seatRows = seatRows,
                error = params.call.request.queryParameters["error"] ?: "",
                ok = params.call.request.queryParameters["ok"] ?: "",
                unreadCount = unreadCount,
                farePrice = flightFare?.price ?: 0.0,
                fareCurrency = flightFare?.currency ?: "GBP",
                passengerCount = passengers.size,
                bookingTotal = 0.0,
            ),
        )

    return base.toMutableMap().apply {
        put("hasReturnFlight", params.bookingSession.returnFlightId != null)
        put("leg", params.leg)
        put(
            "cabinColours",
            seatMapData.cabinColourMap.map { (name, colour) ->
                mapOf("name" to formatCabinName(name), "colour" to colour)
            },
        )
    }
}

/**
 * Formats cabin class name
 * @param name
 * @return the name fixed
 */
private fun formatCabinName(name: String): String =
    name.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar(Char::titlecase) }

/**
 * Retrieves the display colour associated with each cabin class for a flight.
 *
 * Joins fare classes with flight fares and maps cabin class names to their
 * configured display colours.
 *
 * @param flightId ID of the flight whose cabin colours should be loaded.
 * @return Map of cabin class names to colour values.
 */
private fun getCabinColourMap(flightId: Int): Map<String, String> =
    transaction {
        FlightFareTable
            .join(FareClassTable, JoinType.INNER, additionalConstraint = {
                FlightFareTable.fareClassId eq FareClassTable.id
            })
            .select { FlightFareTable.flightId eq flightId }
            .mapNotNull { row ->
                val cabin = row[FareClassTable.cabinClass] ?: return@mapNotNull null
                cabin to row[FareClassTable.colour]
            }
            .toMap()
    }

/**
 * Builds a map of seat codes to their prices based on the cabin class of each seat.
 *
 * Looks up the fare price for each seat by joining [FlightFareTable] with [FareClassTable]
 * on the fare class, filtering by the given [flightId] and the seat's cabin class.
 *
 * Seats with a null cabin class, or with no matching fare, are excluded from the result.
 *
 * @param seats The list of seats to build the price map from.
 * @param flightId The ID of the flight to look up fares for.
 * @return A map of seat codes to their corresponding fare prices, e.g. `{"1A" to 49.99}`.
 */
fun getSeatPriceMap(
    seats: List<Seat>,
    flightId: Int,
): Map<String, Double> =
    transaction {
        seats.mapNotNull { seat ->
            if (seat.cabinClass == null) return@mapNotNull null
            val price =
                FlightFareTable
                    .join(FareClassTable, JoinType.INNER, additionalConstraint = {
                        FlightFareTable.fareClassId eq FareClassTable.id
                    })
                    .select {
                        (FlightFareTable.flightId eq flightId) and
                            (FareClassTable.cabinClass eq seat.cabinClass)
                    }
                    .firstOrNull()
                    ?.get(FlightFareTable.price) ?: return@mapNotNull null
            seat.seatCode to price
        }.toMap()
    }

/**
 * Renders the seat selection page for the current booking session.
 *
 * GET /flights/seats
 * @param call request call
 */
private suspend fun handleGetSeats(
    call: ApplicationCall,
    leg: String = "outbound",
) {
    val (_, userId) = AuthService.requireUser(call) ?: return
    val bookingSession = AuthService.requireBooking(call) ?: return
    val (flightId, fareId) = resolveIds(bookingSession, leg)

    val model =
        flightId?.let {
            buildSeatPageModel(SeatPageParams(call, bookingSession, leg, it, fareId, userId))
        }

    if (model != null) {
        call.respond(PebbleContent("seat_selection.peb", model))
    } else {
        call.respondRedirect(SEARCH_REDIRECT)
    }
}

/**
 * Handles batch seat selection submit.
 *
 * POST /flights/seats
 * Form params:
 * - selectedSeats (JSON string): { passengerId: seatCode, ... }
 * @param call request call
 * @param leg leg
 * @param nextStep next link to go to
 */
private suspend fun handlePostSeats(
    call: ApplicationCall,
    leg: String = "outbound",
    nextStep: String? = null,
) {
    if (AuthService.requireUser(call) == null) return
    val bookingSession = AuthService.requireBooking(call) ?: return
    val (flightId, _) = resolveIds(bookingSession, leg)

    flightId?.also { id ->
        submitSeatSelection(call, bookingSession, id, leg, redirectTo = nextStep ?: PAYMENT_REDIRECT)
    } ?: call.respondRedirect(SEARCH_REDIRECT)
}

/**
 * Validates submitted seat selections, creates a booking segment, and assigns the selected seats.
 * @param call request call
 * @param bookingSession active booking session
 * @param flightId selected outbound flight id
 */
private suspend fun submitSeatSelection(
    call: ApplicationCall,
    bookingSession: BookingSession,
    flightId: Int,
    leg: String,
    redirectTo: String = PAYMENT_REDIRECT,
) {
    val params = call.receiveParameters()
    val selectedSeatsJson = params["selectedSeats"]?.trim().orEmpty()
    val selectedSeats = parseSelectedSeats(call, selectedSeatsJson) ?: return
    val bookingTotal = params["bookingTotal"]?.toDoubleOrNull() ?: 0.0

    val seatAccess = SeatTableAccess()
    val seatRows = seatAccess.getByAttribute(SeatTable.flightId, flightId)
    val seatMap = seatRows.associateBy { it.seatCode }
    val validateSeatsError = validateSeats(selectedSeats, seatMap)

    if (validateSeatsError != null) {
        call.respondRedirect("/flights/seats?error=$validateSeatsError")
        return
    }

    val bookingSegmentId = createBookingSegment(bookingSession, flightId)
    val seatEntries = assignSeats(selectedSeats, seatMap, bookingSegmentId, leg)

    val existingSession = call.sessions.get<SeatSelectionSession>() ?: SeatSelectionSession()
    call.sessions.set(SeatSelectionSession(seats = existingSession.seats + seatEntries))

    val currentSession = call.sessions.get<BookingSession>() ?: bookingSession
    val updatedSession =
        if (redirectTo.contains("return")) {
            currentSession.copy(outboundTotal = bookingTotal, totalPrice = bookingTotal)
        } else {
            currentSession.copy(returnTotal = bookingTotal, totalPrice = currentSession.outboundTotal + bookingTotal)
        }

    call.sessions.set(updatedSession)
    call.respondRedirect(redirectTo)
}

/**
 * Parameters for building the seat page model.
 */
data class SeatPageParams(
    val call: ApplicationCall,
    val bookingSession: BookingSession,
    val leg: String,
    val flightId: Int,
    val fareId: Int?,
    val userId: Int,
)
