Use London => New York for flights (August 12 - August 20)


















comments are in AirportTableAccess.kt, omitted for the rest
UserTableAccess.kt is incomplete/unchanged

Example for the functions (is tested and works in Application.kt)

import access.AirportTableAccess // just "import access" is what we'll use
import com.flightbooking.tables.AirportTable // similarly "import com.flightbooking.tables"

// AirportTableAccess connection
val airporttableaccess = AirportTableAccess()
val airports = airporttableaccess.getAll()
println("Found ${airports.size} airports in the database")

// insert
try {
    val inserted = airporttableaccess.addAirport(
        iataCode = "TST", 
        name = "Test Airport", 
        city = "TestTown", 
        country = null
    )
    println("Inserted airport with ID: ${inserted.id}")
} catch (e: Throwable) {
    println("FAILED TO INSERT, ALREADY EXISTS: ${e.message}")
}

// update
val updated = airporttableaccess.updateRecordByAttribute(
    id = 7, 
    column = AirportTable.name, 
    value = "Updated Test Airport"
)
println("Update successful on record with ID 7: $updated")
println("New name ID 7: ${airporttableaccess.getByAttribute(AirportTable.id, 7).firstOrNull()?.name}")

//deleting the test record
airporttableaccess.deleteByID(7)



import com.flightbooking.access.PassengerTableAccess
import com.flightbooking.access.BookingSegmentTableAccess
import com.flightbooking.access.SeatAssignmentTableAccess
import com.flightbooking.access.SeatTableAccess

val activeFlights = listOf(
        2080, 4229, 4238, 4684, 4912,
        4924, 4955, 5754, 5791, 7388,
        8073, 8071, 7201, 8981, 4930
    )
    val passengers = PassengerTableAccess().generatePassengers()
    val segments = BookingSegmentTableAccess().generateBookingSegments(
        activeFlights = activeFlights,
        passengersByBooking = passengers
    )
    SeatAssignmentTableAccess().generateSeatAssignments(
        passengersByBooking = passengers,
        segmentsByBooking = segments
    )
    SeatTableAccess().generateUKDomesticSeats(
        activeFlights = activeFlights
    )
    SeatAssignmentTableAccess().assignSeats()