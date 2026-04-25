package com.flightbooking.routes

import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.*
import io.ktor.server.pebble.*
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.sessions.*

import com.flightbooking.access.FlightTableAccess
import com.flightbooking.access.AirportTableAccess

import com.flightbooking.tables.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import com.flightbooking.models.UserSession
import com.flightbooking.models.BookingSession
import com.flightbooking.models.FlightSearch
import com.flightbooking.models.FlightWithFares

import com.flightbooking.routes.authRoutes

import java.time.LocalDate
import org.jetbrains.exposed.sql.compoundAnd

/**
 * Page routes for user-facing pages (home, profile, profile sub-pages, bookings) and a shared 404 page.
 * Pages that are not implemented yet redirect to `/404`.
 */
fun Route.pagesRoutes() {
    get("/home") {
        // need to add check to make sure user is logged in before loading the home page
        val session = call.sessions.get<UserSession>()
        // println(session)

        if (session == null) {
            call.respondRedirect("/login")
            return@get
        }

        val airports = AirportTableAccess().getAll()

        call.respond(PebbleContent("home.peb", mapOf(
            "userSession" to session,
            "airports" to airports,
        )))
    }

    get("/flights/search") {
        // need to add check to make sure user is logged in before loading the flight search page
        // we also need to check that all the required data is provided
        val session = call.sessions.get<UserSession>()
        
        // println("SESSION DATA VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV")
        // println(session)
        // println("SESSION DATA ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^")
        
        if (session == null) {
            call.respondRedirect("/login")
            return@get
        }
        
        // packaging all search data into one class
        val search = FlightSearch(
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
            return@get
        }

        val airports = AirportTableAccess()

        // TODO: properly handle error when the inputted airport is not found, just isnt great
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
        val outboundFlights = flightTable.getFlightsAroundDate(originAirportCode, destinationAirportCode, LocalDate.parse(search.departureDate))

        // get inbound flight data (for trip type = return)
        var inboundFlights: List<FlightWithFares> = emptyList()
        if (search.tripType == "return") {
            inboundFlights = flightTable.getFlightsAroundDate(destinationAirportCode, originAirportCode, LocalDate.parse(search.returnDate))
            println("INBOUND FLIGHT DATA VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV")
            println(inboundFlights)
        }

        call.respond(PebbleContent("flight_search.peb", mapOf(
            "userSession" to session,
            "isLoggedIn" to true,
            "search" to search,
            "outboundFlights" to outboundFlights,
            "returnFlights" to inboundFlights,
        )))
    }

    get("/flights/passengers") {
        // need to add check to a booking/user session exists before loading the page
        val userSession = call.sessions.get<UserSession>()
        val bookingSession = call.sessions.get<BookingSession>()

        println(bookingSession)

        if (userSession == null) {
            call.respondRedirect("/login")
            return@get
        }

        if (bookingSession == null) {
            call.respondRedirect("/home")
            return@get
        }

        if (bookingSession.search == null) {
            call.respondRedirect("/home")
            return@get
        }

        val adultsCount = bookingSession.search?.adults?.toIntOrNull() ?: 0
        val childrenCount = bookingSession.search?.children?.toIntOrNull() ?: 0
        val infantsCount = bookingSession.search?.infants?.toIntOrNull() ?: 0

        val adultsList = (0 until adultsCount).map { mapOf("label" to it + 1, "idx" to it) }
        val childrenList = (0 until childrenCount).map { mapOf("label" to it + 1, "idx" to adultsCount + it) }
        val infantsList = (0 until infantsCount).map { mapOf("label" to it + 1, "idx" to adultsCount + childrenCount + it) }


        call.respond(PebbleContent("flight_passengers.peb", mapOf<String, Any>(
            "userSession" to userSession,
            "bookingSession" to bookingSession,
            "search" to (bookingSession.search ?: ""),
            "adults" to adultsList,
            "children" to childrenList,
            "infants" to infantsList,
        )))
    }

    /**
     * Renders the user's profile dashboard page (left navigation + profile info panel).
     *
     * GET /profile
     * - Requires UserSession.
     * - On success: renders "my_profile.peb" with userSession in the model.
     * - On failure: redirects to /login.
     */
    get("/profile") {
        val session = call.sessions.get<UserSession>()
        if (session == null) {
            call.respondRedirect("/login")
            return@get
        }

        call.respond(
            PebbleContent(
                "my_profile.peb",
                mapOf("userSession" to session)
            )
        )
    }

    /**
     * Placeholder for complaints page (not implemented yet).
     *
     * GET /profile/complaints
     * - Always redirects to /404 (until implemented).
     */
    get("/profile/complaints") {
        call.respondRedirect("/404")
    }

    /**
     * Placeholder for notifications page (not implemented yet).
     *
     * GET /profile/notifications
     * - Always redirects to /404 (until implemented).
     */
    get("/profile/notifications") {
        call.respondRedirect("/404")
    }

    /**
     * Central 404 route used by pages that are not implemented yet.
     *
     * GET /404
     * - Renders "404.peb" with HTTP 404 status.
     */
    get("/404") {
        call.respond(
            HttpStatusCode.NotFound,
            PebbleContent("404.peb", mapOf<String, Any>())
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
    get("/profile/bookings") {
        val session = call.sessions.get<UserSession>()
        if (session == null) {
            call.respondRedirect("/login")
            return@get
        }

        val q = call.request.queryParameters["q"]?.trim().orEmpty()
        val qId = q.toIntOrNull()
        val statusFilter = call.request.queryParameters["status"]?.trim()?.lowercase().orEmpty()

        val model = transaction {
            val userRow = UserTable
                .select { UserTable.email eq session.userEmail }
                .limit(1)
                .firstOrNull()

            if (userRow == null) {
                return@transaction mapOf<String, Any>("__notFound" to true)
            }

            val userId = userRow[UserTable.id]

            val origin = AirportTable.alias("origin")
            val dest = AirportTable.alias("dest")
            var cond: Op<Boolean> = (BookingTable.userId eq userId)

            if (statusFilter.isNotBlank()) {
                cond = cond and (BookingTable.bookingStatus.lowerCase() eq statusFilter)
            }

            if (q.isNotBlank()) {
                if (qId == null) {
                    val emptyBookings = emptyList<Map<String, Any>>()
                    return@transaction mapOf(
                        "userSession" to session,
                        "q" to q,
                        "statusFilter" to statusFilter,
                        "bookings" to emptyBookings
                    )
                }
                cond = cond and (BookingTable.id eq qId)
            }

            val rows = (BookingTable
                .join(BookingSegmentTable, JoinType.INNER, additionalConstraint = { BookingSegmentTable.bookingId eq BookingTable.id })
                .join(FlightTable, JoinType.LEFT, additionalConstraint = { FlightTable.id eq BookingSegmentTable.flightId })
                .join(origin, JoinType.LEFT, additionalConstraint = { FlightTable.originAirport eq origin[AirportTable.id] })
                .join(dest, JoinType.LEFT, additionalConstraint = { FlightTable.destinationAirport eq dest[AirportTable.id] })
                .join(SeatAssignmentTable, JoinType.LEFT, additionalConstraint = { SeatAssignmentTable.bookingSegmentId eq BookingSegmentTable.id })
                .join(SeatTable, JoinType.LEFT, additionalConstraint = { SeatTable.id eq SeatAssignmentTable.seatId })
                .slice(
                    BookingTable.id,
                    BookingTable.bookingReference,
                    BookingTable.bookingStatus,
                    BookingTable.createdAt,

                    BookingSegmentTable.id,

                    FlightTable.id,
                    FlightTable.flightNumber,
                    FlightTable.status,
                    FlightTable.scheduledDepartureTime,
                    FlightTable.scheduledArrivalTime,

                    origin[AirportTable.iataCode],
                    origin[AirportTable.name],
                    dest[AirportTable.iataCode],
                    dest[AirportTable.name],

                    SeatTable.seatCode
                )
                .select { cond }
                .orderBy(BookingTable.id, SortOrder.DESC)
                .map { r ->
                    mapOf(
                        "bookingId" to r[BookingTable.id],
                        "bookingReference" to r[BookingTable.bookingReference],
                        "bookingStatus" to r[BookingTable.bookingStatus],
                        "createdAt" to r[BookingTable.createdAt],

                        "segmentId" to r[BookingSegmentTable.id],
   
                        "flightNumber" to (r.getOrNull(FlightTable.flightNumber)?.toString() ?: ""),
                        "flightStatus" to (r.getOrNull(FlightTable.status) ?: ""),
                        "dep" to (r.getOrNull(FlightTable.scheduledDepartureTime) ?: ""),
                        "arr" to (r.getOrNull(FlightTable.scheduledArrivalTime) ?: ""),

                        "originIata" to r.getOrNull(origin[AirportTable.iataCode]),
                        "originName" to r.getOrNull(origin[AirportTable.name]),
                        "destIata" to r.getOrNull(dest[AirportTable.iataCode]),
                        "destName" to r.getOrNull(dest[AirportTable.name]),

                        "seatCode" to r.getOrNull(SeatTable.seatCode)
                    )              
                }
            )

            val grouped = rows.groupBy { it["bookingId"] as Int }
   
            val bookings = grouped.map { (bid, items) ->
                val first = items.first()
 
                val segments = items.map { itRow ->
                    mapOf(
                        "segmentId" to itRow["segmentId"],
                        "flightNumber" to (itRow["flightNumber"] ?: ""),
                        "flightStatus" to (itRow["flightStatus"] ?: ""),
                        "dep" to (itRow["dep"] ?: ""),
                        "arr" to (itRow["arr"] ?: ""),
                        "originIata" to itRow["originIata"],
                        "originName" to itRow["originName"],
                        "destIata" to itRow["destIata"],
                        "destName" to itRow["destName"],
                        "seatCode" to itRow["seatCode"]
                    )
                }

                mapOf(
                    "bookingId" to bid,
                    "bookingReference" to (first["bookingReference"] ?: ""),
                    "bookingStatus" to (first["bookingStatus"] ?: ""),
                    "createdAt" to (first["createdAt"] ?: ""),
                    "segments" to segments
                )
            }

            mapOf(
                "userSession" to session,
                "q" to q,
                "statusFilter" to statusFilter,
                "bookings" to bookings
            )
        }

        if (model.containsKey("__notFound")) {
            call.respondRedirect("/404")
            return@get
        }

        call.respond(PebbleContent("my_bookings.peb", model))
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
    post("/profile/bookings/cancel") {
        val session = call.sessions.get<UserSession>()
        if (session == null) {
            call.respondRedirect("/login")
            return@post
        }

        val params = call.receiveParameters()
        val bookingId = params["bookingId"]?.toIntOrNull()
        if (bookingId == null) {
            call.respondRedirect("/404")
            return@post
        }

        val ok = transaction {
            val userRow = UserTable
                .select { UserTable.email eq session.userEmail }
                .limit(1)
                .firstOrNull() ?: return@transaction false

            val userId = userRow[UserTable.id]

            val owned = BookingTable
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
            return@post
        }

        call.respondRedirect("/profile/bookings")
    }
}