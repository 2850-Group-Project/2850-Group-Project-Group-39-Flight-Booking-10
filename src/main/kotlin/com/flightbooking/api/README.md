Temporary functions that were used to import from API in Application.kt

Application.kt imports needed:

// API client imports
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import com.flightbooking.api.AviationStackClient
import com.flightbooking.access.AirportTableAccess
import com.flightbooking.service.AirportImportService
import com.flightbooking.tables.AirportTable

    val httpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(
                kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
            }
            )
        }
    }
    val aviationStackClient = AviationStackClient(httpClient)
    val airportAccess = AirportTableAccess()
    val importer = AirportImportService(aviationStackClient, airportAccess)

    environment.monitor.subscribe(ApplicationStarted) {
        launch {
            println("Starting airport import...")

            importer.importAllAirports()

            println("Airport import complete.")
            println("Total airports in DB: ${airportAccess.getAll().size}")
        }
    }


into build.gradle.kts:

kotlin("plugin.serialization") version "1.9.0"
// Client-side ContentNegotiation
implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
implementation("io.ktor:ktor-client-json:2.3.7")
implementation("io.ktor:ktor-client-serialization:2.3.7")


used countries.csv and airports.csv to fill in missing database information
still 5 with unknown country and 320 with unknown city / ~6000

functions used in Application.kt:

fun importCityNames() {
    val inputFile = {}::class.java.getResourceAsStream("/airports.csv")
    val allLines = inputFile.bufferedReader().readLines()
    val airportTableAccess = AirportTableAccess()
    allLines.drop(1).forEach { line ->
        val cols = line.split(",")
        val iata = cols[13].replace("\"", "")
        val city = cols[10].replace("\"", "")
        if (iata.isNotBlank()) {
            val accessMatches = airportTableAccess.getByAttribute(AirportTable.iataCode, iata)
            if (accessMatches.isNotEmpty()) {
                val currentRecord = accessMatches.first()
                if (currentRecord.city == "Unknown City") {
                    airportTableAccess.updateRecordByAttribute(
                        id = currentRecord.id!!,
                        column = AirportTable.city,
                        value = city
                    )
                }
            } else {
                println("no records returned from getByAttribute for iata code $iata") 
            }
        }
    }
}
fun importCountryNames() {
    val countriesInputFile = {}::class.java.getResourceAsStream("/countries.csv")
    val airportsInputFile = {}::class.java.getResourceAsStream("/airports.csv")
    val countryLines = countriesInputFile.bufferedReader().readLines()
    val airportLines = airportsInputFile.bufferedReader().readLines()
    val airportTableAccess = AirportTableAccess()

    val isoToCountryMap = countryLines.drop(1).associate { line ->
        val cols = line.split(",")
        val isoCode = cols[1].replace("\"", "")
        val countryName = cols[2].replace("\"", "")
        isoCode to countryName
    }

    airportLines.drop(1).forEach { line ->
        val cols = line.split(",")
        val iata = cols[13].replace("\"", "")
        val airport_iso_code = cols[8].replace("\"", "")
        if (iata.isNotBlank() && airport_iso_code.isNotBlank()) {
            val accessMatches = airportTableAccess.getByAttribute(AirportTable.iataCode, iata)
            if (accessMatches.isNotEmpty()) {
                val currentRecord = accessMatches.first()
                if (currentRecord.country == "Unknown Country") {
                    val country_name = isoToCountryMap[airport_iso_code]
                            airportTableAccess.updateRecordByAttribute(
                                id = currentRecord.id!!,
                                column = AirportTable.country,
                                value = country_name
                            )
                }
            }
        }
    }
}

-----------------------------------------------------------
FLIGHT IMPORT
in Application.kt:
import com.flightbooking.service.FlightImportService
import com.flightbooking.access.AirportTableAccess
import com.flightbooking.access.FlightTableAccess
import com.flightbooking.api.AviationStackClient
import kotlinx.coroutines.launch
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

launch {
            val httpClient = HttpClient {
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                    json(
                        kotlinx.serialization.json.Json {
                            ignoreUnknownKeys = true
                        }
                    )

                }
            }
            val airportAccess = AirportTableAccess()
            val flightAccess = FlightTableAccess()
            val client = AviationStackClient(httpClient)

            val importer = FlightImportService(client, airportAccess, flightAccess)
            importer.importAllFlights()
        }