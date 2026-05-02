package com.flightbooking.routes

import com.flightbooking.access.AirportTableAccess
import com.flightbooking.access.FlightTableAccess
import com.flightbooking.access.PointsTableAccess
import com.flightbooking.models.BookingSession
import com.flightbooking.models.FlightSearch
import com.flightbooking.models.FlightWithFares
import com.flightbooking.models.UserSession
import com.flightbooking.service.AuthService
import com.flightbooking.service.PointsService
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.PassengerTable
import com.flightbooking.tables.SeatAssignmentTable
import com.flightbooking.tables.SeatTable
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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
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
 *
 * Routes:
 *  - GET /home
 *  - GET /flight/search
 *  - GET /flights/passengers
 *  - GET /profile
 *  - GET /profile/notifications
 *  - GET /404
 *  - GET /profile/bookings
 *  - POST /profile/bookings/cancel
 *  - POST /profile/bookings/delete
 *
 * @receiver Route Ktor route builder
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

/**
 * Handler function for rendering the home page
 * @param call application call
 */
private suspend fun handleGetHome(call: ApplicationCall) {
    val (userSession, _) = AuthService.requireAuth(call, requireUser = true, requireBooking = false) ?: return
    val airports = AirportTableAccess().getAll()

    println(userSession)

    call.respond(
        PebbleContent(
            "home.peb",
            mapOf(
                "userSession" to userSession!!,
                "airports" to airports,
            ),
        ),
    )
}

/**
 * Handler function to render flight search page, displaying available flights based on inputted parameters
 * @param call application call
 */
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

    val flightTable = FlightTableAccess()
    val outboundFlights =
        flightTable.getFlightsAroundDate(
            originAirportCode,
            destinationAirportCode,
            LocalDate.parse(search.departureDate),
        )

    var inboundFlights: List<FlightWithFares> = emptyList()
    if (search.tripType == "return") {
        inboundFlights =
            flightTable.getFlightsAroundDate(
                destinationAirportCode,
                originAirportCode,
                LocalDate.parse(search.returnDate),
            )
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

/**
 * GET handler function to display page where passengers info are inputted
 * @param call application call
 */
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
 * @param call application call
 */
private suspend fun handleGetProfile(call: ApplicationCall) {
    val userSession = call.sessions.get<UserSession>()
    if (userSession == null) {
        call.respondRedirect("/login")
        return
    }

    val userId =
        fetchUserId(userSession) ?: run {
            call.respondRedirect("/login")
            return
        }

    val pointsBalance = PointsService.getBalance(userId)
    val pointsTable = PointsTableAccess()
    val pointsTransactions = pointsTable.getTransactions(userId)
    println(pointsTransactions)

    call.respond(
        PebbleContent(
            "my_profile.peb",
            mapOf(
                "userSession" to userSession,
                "pointsBalance" to pointsBalance,
                "pointsTransactions" to pointsTransactions,
            ),
        ),
    )
}

/**
 * Placeholder for notifications page (not implemented yet).
 *
 * GET /profile/notifications
 * - Always redirects to /404 (until implemented).
 *
 * @param call application call
 */
private suspend fun handleGetNotifications(call: ApplicationCall) {
    // Placeholder for notifications page (not implemented yet)
    call.respondRedirect("/404")
}

/**
 * Central 404 route used by pages that are not implemented yet.
 *
 * @param call application call
 */
private suspend fun handleNotFound(call: ApplicationCall) {
    call.respond(
        HttpStatusCode.NotFound,
        PebbleContent("404.peb", mapOf<String, Any>()),
    )
}

/**
 * Renders the "My Bookings" page for the logged-in user.
 * @param call application call
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
 * Cancels a booking for the logged-in user
 *
 * @param call application call
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

            val passengers =
                PassengerTable
                    .select { PassengerTable.bookingId eq bookingId }
                    .map { it[PassengerTable.id] }

            val seatIdsToFree =
                SeatAssignmentTable
                    .select { SeatAssignmentTable.passengerId inList passengers }
                    .mapNotNull { it[SeatAssignmentTable.seatId] }

            SeatTable.update({ SeatTable.id inList seatIdsToFree }) {
                it[status] = "available"
            }

            if (passengers.isNotEmpty()) {
                SeatAssignmentTable.deleteWhere {
                    SeatAssignmentTable.passengerId inList passengers
                }
            }

            BookingTable.update({ (BookingTable.id eq bookingId) and (BookingTable.userId eq userId) }) {
                it[bookingStatus] = "cancelled"
                it[cancelledAt] = Instant.now().toString()
            }

            true
        }

    if (!ok) {
        call.respondRedirect("/404")
        return
    }

    call.respondRedirect("/profile/bookings")
}

/**
 * Deletes a booking for the logged-in user
 *
 * @param call application call
 */
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
