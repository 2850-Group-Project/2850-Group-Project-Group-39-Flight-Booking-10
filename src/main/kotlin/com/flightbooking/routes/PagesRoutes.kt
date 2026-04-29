package com.flightbooking.routes

import com.flightbooking.access.AirportTableAccess
import com.flightbooking.access.FlightTableAccess
import com.flightbooking.models.BookingSession
import com.flightbooking.models.FlightSearch
import com.flightbooking.models.FlightWithFares
import com.flightbooking.models.UserSession
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.SeatAssignmentTable
import com.flightbooking.tables.UserTable
import io.ktor.http.HttpStatusCode
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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.LocalDate

/**
 * Page routes for user-facing pages (home, profile, profile sub-pages, bookings) and a shared 404 page.
 * Pages that are not implemented yet redirect to `/404`.
 */
fun Route.pagesRoutes() {
    get("/home") { handleGetHome(call) }
    get("/flights/search") { handleGetFlightSearch(call) }
    get("/flights/passengers") { handleGetFlightPassengers(call) }
    get("/profile") { handleGetProfile(call) }
    get("/profile/notifications") { handleGetNotifications(call) }
    get("/404") { handleNotFound(call) }
    get("/profile/bookings") {
        val session = call.sessions.get<UserSession>()
        if (session == null) {
            call.respondRedirect("/login")
            return@get
        }
        handleGetBookings(call)
    }
    post("/profile/bookings/cancel") {
        val session = call.sessions.get<UserSession>()
        if (session == null) {
            call.respondRedirect("/login")
            return@post
        }
        handlePostBookingsCancel(call)
    }
    post("/profile/bookings/delete") {
        val session = call.sessions.get<UserSession>()
        if (session == null) {
            call.respondRedirect("/login")
            return@post
        }
        handlePostBookingsDelete(call)
    }
}

// need to add check to make sure user is logged in before loading the home page
private suspend fun handleGetHome(call: ApplicationCall) {
    val session = call.sessions.get<UserSession>()

    if (session == null) {
        call.respondRedirect("/login")
        return
    }

    val airports = AirportTableAccess().getAll()

    call.respond(
        PebbleContent(
            "home.peb",
            mapOf(
                "userSession" to session,
                "airports" to airports,
            ),
        ),
    )
}

// need to add check to make sure user is logged in before loading the flight search page
// we also need to check that all the required data is provided
private suspend fun handleGetFlightSearch(call: ApplicationCall) {
    val session = call.sessions.get<UserSession>()

    if (session == null) {
        call.respondRedirect("/login")
        return
    }

    // packaging all search data into one class
    val search =
        FlightSearch(
            tripType = call.request.queryParameters["trip_type"],
            origin = AirportTableAccess().getCityByOrigin(call.request.queryParameters["origin"] ?: ""),
            destination = AirportTableAccess().getCityByOrigin(call.request.queryParameters["destination"] ?: ""),
            departureDate = call.request.queryParameters["departure_date"],
            returnDate = call.request.queryParameters["return_date"],
            adults = call.request.queryParameters["adults"],
            children = call.request.queryParameters["children"],
            infants = call.request.queryParameters["infants"],
        )

    println(search)

    if (search.origin == null || search.destination == null || search.adults == "0") {
        call.respondRedirect("/home")
        return
    }

    val airports = AirportTableAccess()

    // to do: properly handle error when the inputted airport is not found, just isnt great
    // this should be checked beforehand however, on the home page, since we should only ever pass error free
    // form inputs to stages further up
    val originAirportCode = airports.getAirportCodeByOrigin(search.origin) ?: ""
    val destinationAirportCode = airports.getAirportCodeByOrigin(search.destination) ?: ""

    println(originAirportCode)
    println(destinationAirportCode)

    // println("FLIGHT SEARCH DATA VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV")
    // println(search)
    // println("FLIGHT SEARCH DATA ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^")

    // get outbound flight data
    val flightTable = FlightTableAccess()
    val outboundFlights =
        flightTable.getFlightsAroundDate(
            originAirportCode,
            destinationAirportCode,
            LocalDate.parse(search.departureDate),
        )

    // get inbound flight data (for trip type = return)
    var inboundFlights: List<FlightWithFares> = emptyList()
    if (search.tripType == "return") {
        inboundFlights =
            flightTable.getFlightsAroundDate(
                destinationAirportCode,
                originAirportCode,
                LocalDate.parse(search.returnDate),
            )
        println("INBOUND FLIGHT DATA VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV")
        println(inboundFlights)
    }

    call.respond(
        PebbleContent(
            "flight_search.peb",
            mapOf(
                "userSession" to session,
                "isLoggedIn" to true,
                "search" to search,
                "outboundFlights" to outboundFlights,
                "returnFlights" to inboundFlights,
            ),
        ),
    )
}

// need to add check to a booking/user session exists before loading the page
private suspend fun handleGetFlightPassengers(call: ApplicationCall) {
    val userSession = call.sessions.get<UserSession>()
    val bookingSession = call.sessions.get<BookingSession>()
    val redirect = resolveGetFlightRedirects(call, bookingSession)
    if (redirect != null) {
        call.respondRedirect(redirect)
        return
    }
    checkNotNull(bookingSession)
    checkNotNull(bookingSession.search)

    println(bookingSession)

    val adultsCount = bookingSession.search.adults?.toIntOrNull() ?: 0
    val childrenCount = bookingSession.search.children?.toIntOrNull() ?: 0
    val infantsCount = bookingSession.search.infants?.toIntOrNull() ?: 0

    val adultsList =
        (0 until adultsCount).map {
            mapOf("label" to it + 1, "idx" to it)
        }
    val childrenList =
        (0 until childrenCount).map {
            mapOf("label" to it + 1, "idx" to adultsCount + it)
        }
    val infantsList =
        (0 until infantsCount).map {
            mapOf("label" to it + 1, "idx" to adultsCount + childrenCount + it)
        }

    call.respond(
        PebbleContent(
            "flight_passengers.peb",
            mapOf<String, Any>(
                "userSession" to userSession!!,
                "bookingSession" to bookingSession,
                "search" to bookingSession.search,
                "adults" to adultsList,
                "children" to childrenList,
                "infants" to infantsList,
            ),
        ),
    )
}

/**
 * Renders the user's profile dashboard page (left navigation + profile info panel).
 *
 * GET /profile
 * - Requires UserSession.
 * - On success: renders "my_profile.peb" with userSession in the model.
 * - On failure: redirects to /login.
 */
private suspend fun handleGetProfile(call: ApplicationCall) {
    val session = call.sessions.get<UserSession>()
    if (session == null) {
        call.respondRedirect("/login")
        return
    }

    call.respond(
        PebbleContent(
            "my_profile.peb",
            mapOf("userSession" to session),
        ),
    )
}

/**
 * Placeholder for notifications page (not implemented yet).
 *
 * GET /profile/notifications
 * - Always redirects to /404 (until implemented).
 */
private suspend fun handleGetNotifications(call: ApplicationCall) {
    // Placeholder for notifications page (not implemented yet)
    call.respondRedirect("/404")
}

/**
 * Central 404 route used by pages that are not implemented yet.
 *
 * GET /404
 * - Renders "404.peb" with HTTP 404 status.
 */
private suspend fun handleNotFound(call: ApplicationCall) {
    call.respond(
        HttpStatusCode.NotFound,
        PebbleContent("404.peb", mapOf<String, Any>()),
    )
}

/**
 * Renders the "My Bookings" page for the logged-in user.
 *
 * GET "/profile/bookings"
 * Query params:
 * - q (optional): booking id filter (numeric)
 * - status (optional): booking status filter (pending/confirmed/cancelled)
 *
 * Behaviour:
 * - Requires UserSession. If missing -> redirect /login
 * - If user cannot be resolved -> redirect /404
 * - Loads bookings belonging to the user, including segment flight + airport + seat info
 * - Renders my_bookings.peb
 */
private suspend fun handleGetBookings(call: ApplicationCall) {
    val session = call.sessions.get<UserSession>()
    checkNotNull(session)
    val q = call.request.queryParameters["q"]?.trim().orEmpty()
    val qId = q.toIntOrNull()
    val statusFilter = call.request.queryParameters["status"]?.trim()?.lowercase().orEmpty()

    if (q.isNotBlank() && qId == null) {
        call.respond(
            PebbleContent(
                "my_bookings.peb",
                mapOf(
                    "userSession" to session,
                    "q" to q,
                    "statusFilter" to statusFilter,
                    "bookings" to emptyList<Map<String, Any>>(),
                ),
            ),
        )
        return
    }

    val origin = AirportTable.alias("origin")
    val dest = AirportTable.alias("dest")

    val bookings =
        transaction {
            val userId = fetchUserId(session) ?: return@transaction null
            val cond = buildBookingCondition(userId, q, qId, statusFilter)
            groupIntoBookings(fetchBookingRows(cond, origin, dest))
        }

    if (bookings == null) {
        call.respondRedirect("/404")
        return
    }

    call.respond(
        PebbleContent(
            "my_bookings.peb",
            mapOf(
                "userSession" to session,
                "q" to q,
                "statusFilter" to statusFilter,
                "bookings" to bookings,
            ),
        ),
    )
}

/**
 * Cancels a booking for the logged-in user.
 *
 * POST "/profile/bookings/cancel"
 * - Requires an existing [UserSession].
 * - Reads `bookingId` from form parameters.
 * - Verifies the booking belongs to the current user.
 * - Updates `booking_status` to "cancelled" and sets `cancelled_at` to now.
 * - Redirects back to `/profile/bookings`.
 * - If session is missing -> redirect to `/login`.
 * - If user or booking cannot be resolved -> redirect to `/404`.
 */
private suspend fun handlePostBookingsCancel(call: ApplicationCall) {
    val session = call.sessions.get<UserSession>()
    checkNotNull(session)

    val params = call.receiveParameters()
    val bookingId = params["bookingId"]?.toIntOrNull()
    if (bookingId == null) {
        call.respondRedirect("/404")
        return
    }

    val ok =
        transaction {
            val userRow =
                UserTable
                    .select { UserTable.email eq session.userEmail }
                    .limit(1)
                    .firstOrNull() ?: return@transaction false

            val userId = userRow[UserTable.id]

            val owned =
                BookingTable
                    .select { (BookingTable.id eq bookingId) and (BookingTable.userId eq userId) }
                    .limit(1)
                    .any()

            if (!owned) return@transaction false

            BookingTable.update({ (BookingTable.id eq bookingId) and (BookingTable.userId eq userId) }) {
                it[bookingStatus] = "cancelled"
                it[cancelledAt] = java.time.Instant.now().toString()
            }

            true
        }

    if (!ok) {
        call.respondRedirect("/404")
        return
    }

    call.respondRedirect("/profile/bookings")
}

private suspend fun handlePostBookingsDelete(call: ApplicationCall) {
    val session = call.sessions.get<UserSession>()
    checkNotNull(session)

    val params = call.receiveParameters()
    val bookingId = params["bookingId"]?.toIntOrNull()
    if (bookingId == null) {
        call.respondRedirect("/404")
        return
    }

    val ok =
        transaction {
            val userRow =
                UserTable
                    .select { UserTable.email eq session.userEmail }
                    .limit(1)
                    .firstOrNull() ?: return@transaction false

            val userId = userRow[UserTable.id]

            val owned =
                BookingTable
                    .select { (BookingTable.id eq bookingId) and (BookingTable.userId eq userId) }
                    .limit(1)
                    .any()

            if (!owned) {
                return@transaction false
            }

            val segmentIds =
                BookingSegmentTable
                    .select { BookingSegmentTable.bookingId eq bookingId }
                    .map { it[BookingSegmentTable.id] }

            segmentIds.forEach { segId ->
                SeatAssignmentTable.deleteWhere { SeatAssignmentTable.bookingSegmentId eq segId }
            }

            BookingSegmentTable.deleteWhere { BookingSegmentTable.bookingId eq bookingId }
            BookingTable.deleteWhere { (BookingTable.id eq bookingId) and (BookingTable.userId eq userId) }

            true
        }

    if (!ok) {
        call.respondRedirect("/404")
        return
    }

    call.respondRedirect("/profile/bookings")
}
