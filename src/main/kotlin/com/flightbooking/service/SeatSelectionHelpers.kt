package com.flightbooking.service

import com.flightbooking.models.Airport
import com.flightbooking.models.BookingSession
import com.flightbooking.models.Flight
import com.flightbooking.models.Seat
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.SeatAssignmentTable
import com.flightbooking.tables.SeatTable
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.get
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.math.ceil

private const val SMALL_AISLE_GAP_INDEX = 3
private const val MEDIUM_FIRST_AISLE_GAP_INDEX = 2
private const val MEDIUM_SECOND_AISLE_GAP_INDEX = 6
private const val LARGE_FIRST_AISLE_GAP_INDEX = 3
private const val LARGE_SECOND_AISLE_GAP_INDEX = 7
private const val SMALL_SEATS_PER_ROW = 6
private const val MEDIUM_SEATS_PER_ROW = 8
const val SMALL_AIRCRAFT_CAP_THRESHOLD = 180
private const val MEDIUM_AIRCRAFT_CAP_THRESHOLD = 350

data class SeatsModelParams(
    val flight: Flight,
    val origin: Airport?,
    val dest: Airport?,
    val passengers: List<Map<String, Any?>>,
    val seatRows: List<Map<String, Any>>,
    val error: String,
    val ok: String,
)

data class SeatLayout(
    val seatsPerRow: Int,
    val letters: List<String>,
    val aisleGapsAfterIndex: Set<Int>,
) {
    fun positionFor(index: Int): String {
        return when {
            index == 0 -> "window"
            index == letters.size - 1 -> "window"
            else -> {
                val leftEdge = index
                val rightEdge = index + 1
                if (
                    aisleGapsAfterIndex.contains(leftEdge) ||
                    aisleGapsAfterIndex.contains(rightEdge)
                ) {
                    "aisle"
                } else {
                    "middle"
                }
            }
        }
    }
}

fun getLayout(capacity: Int): SeatLayout {
    val layout =
        when {
            capacity <= SMALL_AIRCRAFT_CAP_THRESHOLD ->
                SeatLayout(
                    seatsPerRow = SMALL_SEATS_PER_ROW,
                    letters = listOf("A", "B", "C", "D", "E", "F"),
                    aisleGapsAfterIndex = setOf(SMALL_AISLE_GAP_INDEX),
                )

            capacity <= MEDIUM_AIRCRAFT_CAP_THRESHOLD ->
                SeatLayout(
                    seatsPerRow = MEDIUM_SEATS_PER_ROW,
                    letters = listOf("A", "B", "C", "D", "E", "F", "G", "H"),
                    aisleGapsAfterIndex =
                        setOf(
                            MEDIUM_FIRST_AISLE_GAP_INDEX,
                            MEDIUM_SECOND_AISLE_GAP_INDEX,
                        ),
                )

            else ->
                SeatLayout(
                    seatsPerRow = 10,
                    letters = listOf("A", "B", "C", "D", "E", "F", "G", "H", "J", "K"),
                    aisleGapsAfterIndex =
                        setOf(
                            LARGE_FIRST_AISLE_GAP_INDEX,
                            LARGE_SECOND_AISLE_GAP_INDEX,
                        ),
                )
        }
    return layout
}

// Create booking segment (if not exists)
fun createBookingSegment(
    bookingSession: BookingSession,
    flightId: Int,
): Int {
    val bookingSegmentId =
        transaction {
            val condition =
                (BookingSegmentTable.bookingId eq bookingSession.bookingId) and
                    (BookingSegmentTable.flightId eq flightId)

            val existing =
                BookingSegmentTable
                    .select { condition }
                    .firstOrNull()

            if (existing != null) {
                existing[BookingSegmentTable.id]
            } else {
                BookingSegmentTable.insert {
                    it[BookingSegmentTable.bookingId] = bookingSession.bookingId
                    it[BookingSegmentTable.flightId] = flightId
                    it[BookingSegmentTable.flightFareId] =
                        bookingSession.outboundFareId?.toInt() ?: 0
                }

                BookingSegmentTable
                    .selectAll()
                    .orderBy(BookingSegmentTable.id, SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()
                    ?.get(BookingSegmentTable.id) ?: 0
            }
        }
    return bookingSegmentId
}

// puts seat assignment in
// seat assigment and seat table
fun assignSeats(
    selectedSeats: Map<String, String>,
    seatMap: Map<String, Seat>,
    bookingSegmentId: Int,
) {
    transaction {
        selectedSeats.forEach { (passengerId, seatCode) ->
            val seatId = seatMap[seatCode]!!.id

            // create new seat assignment
            SeatAssignmentTable.insert {
                it[SeatAssignmentTable.passengerId] = passengerId.toInt()
                it[SeatAssignmentTable.seatId] = seatId
                it[SeatAssignmentTable.bookingSegmentId] = bookingSegmentId
            }

            // update seat status
            SeatTable.update({ SeatTable.id eq seatId }) {
                it[SeatTable.status] = "occupied"
            }
        }
    }
}

// parsing the JSON: { "1": "3A", "2": "3B", ... }
suspend fun parseSelectedSeats(
    call: ApplicationCall,
    json: String,
): Map<String, String>? {
    if (json.isBlank()) {
        call.respondRedirect("/flights/seats?error=No seats selected")
        return null
    }
    return try {
        Gson().fromJson(json, object : TypeToken<Map<String, String>>() {}.type)
    } catch (e: JsonSyntaxException) {
        call.application.log.error("Failed to parse seat selection JSON: ${e.message}", e)
        call.respondRedirect("/flights/seats?error=Invalid seat selection format")
        null
    }
}

// Validate all seats exist and are available
fun validateSeats(
    selectedSeats: Map<String, String>,
    seatMap: Map<String, Seat>,
): String? =
    selectedSeats.entries
        .firstNotNullOfOrNull { (_, seatCode) ->
            val seat = seatMap[seatCode] ?: return@firstNotNullOfOrNull "Seat $seatCode not found"
            "Seat $seatCode is already occupied".takeIf { seat.status != "available" }
        }

fun aircraftTyper(capacity: Int): String {
    return when {
        capacity <= SMALL_AIRCRAFT_CAP_THRESHOLD -> "Narrow-body"
        capacity <= MEDIUM_AIRCRAFT_CAP_THRESHOLD -> "Wide-body"
        else -> "Jumbo-jet"
    }
}

fun buildSeatsModel(params: SeatsModelParams): Map<String, Any> {
    val capacity = (params.flight.capacity ?: SMALL_AIRCRAFT_CAP_THRESHOLD).coerceAtLeast(1)
    val currentPassengerName =
        params.passengers.firstOrNull()
            ?.let { "${it["firstName"]} ${it["lastName"]}" }
            ?: "Select passenger"

    return mapOf(
        "flightId" to params.flight.id,
        "flightNumber" to (params.flight.flightNumber?.toString() ?: params.flight.id.toString()),
        "originIata" to (params.origin?.iataCode ?: "—"),
        "originName" to (params.origin?.name ?: "—"),
        "destIata" to (params.dest?.iataCode ?: "—"),
        "destName" to (params.dest?.name ?: "—"),
        "aircraftType" to aircraftTyper(capacity),
        "currentSeatCode" to "",
        "currentPassenger" to currentPassengerName,
        "seatRows" to params.seatRows,
        "passengers" to params.passengers,
        "error" to params.error,
        "ok" to params.ok,
    )
}

fun buildSeatRows(
    capacity: Int,
    layout: SeatLayout,
    seatStatusByCode: Map<String, String>,
): List<Map<String, Any>> {
    val totalRows = ceil(capacity / layout.seatsPerRow.toDouble()).toInt().coerceAtLeast(1)
    return (1..totalRows).map { buildSeatRow(it, layout, seatStatusByCode) }
}

fun buildSeatRow(
    rowNum: Int,
    layout: SeatLayout,
    seatStatusByCode: Map<String, String>,
): Map<String, Any> {
    val seats = mutableListOf<Map<String, Any>>()

    layout.letters.forEachIndexed { idx, letter ->
        seats.add(buildSeat(rowNum, letter, idx, layout, seatStatusByCode))

        val after = idx + 1
        if (layout.aisleGapsAfterIndex.contains(after)) {
            seats.add(mapOf("isAisleGap" to true))
        }
    }

    return mapOf(
        "rowNumber" to rowNum,
        "seats" to seats,
    )
}

fun buildSeat(
    rowNum: Int,
    letter: String,
    idx: Int,
    layout: SeatLayout,
    seatStatusByCode: Map<String, String>,
): Map<String, Any> {
    val code = "$rowNum$letter"
    return mapOf(
        "code" to code,
        "letter" to letter,
        "position" to layout.positionFor(idx),
        "status" to (seatStatusByCode[code] ?: "available"),
        "isAisleGap" to false,
    )
}
