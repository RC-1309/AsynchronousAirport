package airline

import airline.api.*
import airline.service.BookingService
import airline.service.EmailService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class AdvancedTest {
    private val emailService = InChannelEmailService()
    private val config = AirlineConfig(
        audioAlertsInterval = 200.milliseconds,
        displayUpdateInterval = 100.milliseconds,
        registrationOpeningTime = 4.seconds + 500.milliseconds,
        registrationClosingTime = 1.seconds,
        boardingOpeningTime = 2.seconds,
        boardingClosingTime = 1.seconds,
        ticketSaleEndTime = 1.seconds,
    )
    private val timeBeforeAlert = 3.minutes
    private val listOfNames = listOf("Ada", "Bob", "Chuck", "David", "Elena", "Fil", "Grigory", "Henry", "Igor", "Jake")

    @Test
    fun manyFlight() {
        val numberOfFlights = 3
        val listOfFlights: List<Flight> = createFlights(numberOfFlights)
        val listOnPassengers = createPassengers(18)

        val airlineApplication = AirlineApplication(config, emailService)

        testAndCancel {
            launch { airlineApplication.run() }
            sleep()

            val booking = airlineApplication.bookingService
            val management = airlineApplication.managementService
            val display = airlineApplication.airportInformationDisplay(this)
            val passengerInFlights = LinkedHashMap<FlightInfo, ArrayList<Passenger>>()

            for (i in 1..numberOfFlights) {
                val flight = listOfFlights[i - 1]
                passengerInFlights[flightToFlightInfo(flight)] = ArrayList()
                management.scheduleFlight(flight.flightId, flight.departureTime, plane = flight.plane)
                sleep()

                Assertions.assertEquals(i, display.value.departing.size)
                Assertions.assertEquals(flight.flightId, display.value.departing[i - 1].flightId)

                Assertions.assertEquals(i, booking.flightSchedule.size)
                Assertions.assertEquals(flight.flightId, booking.flightSchedule[i - 1].flightId)
            }

            for ((idx, passenger) in listOnPassengers.withIndex()) {
                val flight = getElemFromList(booking.flightSchedule, idx)
                passenger.seatNo = getElemFromList(booking.freeSeats(flight.flightId, flight.departureTime).toList(), 0)

                buyTicket(booking, flight, passenger)
                checkMessageForBuyTicket("Successful", passenger, flight, emailService)
                passengerInFlights[flight]?.add(passenger)
            }

            for ((idx, flight) in passengerInFlights.keys.withIndex()) {
                val newTime = flight.departureTime + 1.hours
                management.delayFlight(flight.flightId, flight.departureTime, newTime)

                checkMessageForChangeParameter(
                    flight,
                    emailService,
                    passengerInFlights[flight] ?: ArrayList(),
                    "departure time",
                )

                sleep()

                Assertions.assertEquals(numberOfFlights, display.value.departing.size)
                Assertions.assertEquals(flight.flightId, display.value.departing[idx].flightId)
                Assertions.assertEquals(flight.departureTime, display.value.departing[idx].departureTime)
                Assertions.assertEquals(newTime, display.value.departing[idx].actualDepartureTime)
            }
        }
    }

    @Test
    fun testAudioAlerts() {
        val airlineApplication = AirlineApplication(config, emailService)
        val numberOfFlights = 5
        val listOfFlights = createFlights(numberOfFlights, 2.seconds).toMutableList()

        testAndCancel {
            launch { airlineApplication.run() }
            sleep()

            val booking = airlineApplication.bookingService
            val management = airlineApplication.managementService
            val audioAlerts = airlineApplication.airportAudioAlerts

            launch {
                audioAlerts.collect { alert ->
                    when (alert) {
                        is AudioAlerts.BoardingClosing -> {
                            checkAlert(
                                listOfFlights,
                                alert.flightNumber,
                                alert.gateNumber,
                                config.boardingClosingTime,
                            ) { x -> x.gateNumber.toString() }
                        }

                        is AudioAlerts.BoardingOpened -> {
                            checkAlert(
                                listOfFlights,
                                alert.flightNumber,
                                alert.gateNumber,
                                config.boardingOpeningTime,
                            ) { x -> x.gateNumber.toString() }
                        }

                        is AudioAlerts.RegistrationClosing -> {
                            checkAlert(
                                listOfFlights,
                                alert.flightNumber,
                                alert.checkInNumber,
                                config.registrationClosingTime,
                            ) { x -> x.checkInNumber.toString() }
                        }

                        is AudioAlerts.RegistrationOpen -> {
                            checkAlert(
                                listOfFlights,
                                alert.flightNumber,
                                alert.checkInNumber,
                                config.registrationOpeningTime,
                            ) { x -> x.checkInNumber.toString() }
                        }
                    }
                }
            }

            listOfFlights.forEach {
                management.scheduleFlight(it.flightId, it.departureTime, plane = it.plane)
                management.setGateNumber(it.flightId, it.departureTime, it.gateNumber.toString())
                management.setCheckInNumber(it.flightId, it.departureTime, it.checkInNumber.toString())
                management.delayFlight(it.flightId, it.departureTime, it.departureTime + timeBeforeAlert)
            }
            sleep()

            Assertions.assertEquals(numberOfFlights, booking.flightSchedule.size)

            delay(5.seconds)

            listOfFlights[4] = listOfFlights[4].copy(
                actualDepartureTime = listOfFlights[4].departureTime + timeBeforeAlert + 2.seconds,
                checkInNumber = "check5",
                gateNumber = "gate5",
            )

            val flight = listOfFlights[4]

            management.delayFlight(flight.flightId, flight.departureTime, flight.actualDepartureTime)
            management.setGateNumber(flight.flightId, flight.departureTime, flight.gateNumber.toString())
            management.setCheckInNumber(flight.flightId, flight.departureTime, flight.checkInNumber.toString())

            delay(5.seconds)
        }
    }

    private fun testAndCancel(block: suspend CoroutineScope.() -> Unit) {
        try {
            runBlocking {
                block()
                cancel()
            }
        } catch (ignore: CancellationException) {
        }
    }

    private suspend fun checkMessageForBuyTicket(
        status: String,
        passenger: Passenger,
        flight: FlightInfo,
        emailService: InChannelEmailService,
    ) {
        withTimeout(100.milliseconds) {
            val (email, text) = emailService.messages.receive()
            Assertions.assertEquals(passenger.email, email)
            Assertions.assertTrue(passenger.name in text)
            Assertions.assertTrue(passenger.id in text)
            Assertions.assertTrue(flight.flightId in text)
            Assertions.assertTrue(passenger.seatNo in text)
            Assertions.assertTrue(status in text)
        }
    }

    private fun createFlights(number: Int, diffTime: Duration = 1.hours): List<Flight> {
        val list = ArrayList<Flight>()
        val curTime = Time.getCurrentTime()
        for (i in 1..number) {
            list.add(Flight("$i", curTime + diffTime * i, plane = Plane("A3$i", createSeats(i, 4))))
        }
        return list
    }

    private fun createSeats(rows: Int, columns: Int = 2): Set<String> {
        val set = HashSet<String>()
        for (i in 1..rows) {
            for (j in 1..columns) {
                set.add("$i${(j + 64).toChar()}")
            }
        }
        return set
    }

    private fun createPassengers(number: Int = 10): List<Passenger> {
        val list = ArrayList<Passenger>()
        for (i in 1..number) {
            val name = getElemFromList(listOfNames, i - 1)
            list.add(Passenger("$name$i@gmail.com", name, "$i"))
        }
        return list
    }

    private suspend fun checkMessageForChangeParameter(
        flight: FlightInfo,
        emailService: InChannelEmailService,
        passengers: List<Passenger>,
        parameter: String,
    ) {
        val emails = ArrayList<Pair<String, String>>()
        for (i in 1..passengers.size) {
            emails.add(emailService.messages.receive())
        }
        var hasEmail = false
        for (passenger in passengers) {
            for ((email, text) in emails) {
                if (email == passenger.email) {
                    Assertions.assertEquals(false, hasEmail)
                    hasEmail = true
                    Assertions.assertTrue(flight.flightId in text)
                    Assertions.assertTrue(parameter in text)
                    Assertions.assertTrue(passenger.name in text)
                }
            }
            Assertions.assertEquals(true, hasEmail)
            hasEmail = false
        }
    }

    private fun flightToFlightInfo(flight: Flight) = FlightInfo(
        flight.flightId,
        flight.departureTime,
        flight.isCancelled,
        flight.actualDepartureTime,
        flight.checkInNumber,
        flight.gateNumber,
        flight.plane,
    )

    private suspend fun sleep() {
        delay(200.milliseconds)
    }

    private suspend fun buyTicket(booking: BookingService, flight: FlightInfo, passenger: Passenger) {
        booking.buyTicket(
            flight.flightId,
            flight.departureTime,
            passenger.seatNo,
            passenger.id,
            passenger.name,
            passenger.email,
        )
    }

    private fun <T> getElemFromList(list: List<T>, idx: Int = -1) =
        if (idx > -1) list[idx % list.size] else list.random()

    private fun checkAlert(
        list: List<Flight>,
        flightId: String,
        value: String,
        configTime: Duration,
        func: (Flight) -> String,
    ) {
        // added 50.milliseconds for spread
        val checkingTime: Duration = timeBeforeAlert + 50.milliseconds
        val flight = list.firstOrNull { it.flightId == flightId }
        Assertions.assertNotEquals(null, flight)
        if (flight != null) {
            val curTime = Time.getCurrentTime()
            val time = curTime - (flight.actualDepartureTime - configTime)
            Assertions.assertTrue(time in 0.seconds..checkingTime)
            Assertions.assertEquals(func(flight), value)
        }
    }

    private class Passenger(val email: String, val name: String, val id: String, var seatNo: String = "")

    private class InChannelEmailService : EmailService {
        val messages = Channel<Pair<String, String>>()

        override suspend fun send(to: String, text: String) {
            messages.send(to to text)
        }
    }
}
