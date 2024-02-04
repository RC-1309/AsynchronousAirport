package airline

import airline.api.*
import airline.api.flightManagement.*
import airline.service.AirlineManagementService
import airline.service.BookingService
import airline.service.EmailService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant

class AirlineApplication(private val config: AirlineConfig, emailService: EmailService) {
    private val managementFlow = MutableSharedFlow<FlightManager>(extraBufferCapacity = 1000000)
    private val listOfFlights = MutableStateFlow(emptyList<Flight>())
    private val bufferedEmailService = BufferedEmailService(emailService)
    private val passengerNotificationService = PassengerNotificationService(emailService)

    companion object {
        private var timeForAudioAlerts: Duration = 3.minutes
    }

    data class MyPair(private val shift: Duration, val function: (Flight) -> AudioAlerts) {
        val predicate: (Instant, Instant) -> Boolean =
            { curT, t -> ((curT - (t - shift)) in 0.seconds..timeForAudioAlerts) }
    }

    private val rangesForAudioAlerts = listOf(
        MyPair(
            config.registrationOpeningTime,
        ) { x -> AudioAlerts.RegistrationOpen(x.flightId, x.checkInNumber.toString()) },
        MyPair(
            config.registrationClosingTime + timeForAudioAlerts,
        ) { x -> AudioAlerts.RegistrationClosing(x.flightId, x.checkInNumber.toString()) },
        MyPair(config.boardingOpeningTime) { x -> AudioAlerts.BoardingOpened(x.flightId, x.gateNumber.toString()) },
        MyPair(
            config.boardingClosingTime + timeForAudioAlerts,
        ) { x -> AudioAlerts.BoardingClosing(x.flightId, x.gateNumber.toString()) },
    )

    val bookingService: BookingService = object : BookingService {
        fun checkTimeAndCancel(flight: Flight): Boolean {
            return !flight.isCancelled && Time.getCurrentTime() < flight.actualDepartureTime - config.ticketSaleEndTime
        }

        override val flightSchedule: List<FlightInfo>
            get() {
                return listOfFlights.value
                    .filter { it.tickets.keys.size != it.plane.seats.size && checkTimeAndCancel(it) }
                    .map { flightToFlightInfo(it) }
            }

        override fun freeSeats(flightId: String, departureTime: Instant): Set<String> {
            val flight =
                listOfFlights.value.firstOrNull { it.flightId == flightId && it.departureTime == departureTime }
            return flight?.plane?.seats?.toSet()?.minus(flight.tickets.keys) ?: setOf()
        }

        override suspend fun buyTicket(
            flightId: String,
            departureTime: Instant,
            seatNo: String,
            passengerId: String,
            passengerName: String,
            passengerEmail: String,
        ) {
            managementFlow.emit(
                BuyTicket(
                    flightId,
                    departureTime,
                    seatNo,
                    passengerId,
                    passengerName,
                    passengerEmail,
                    "buy ticket",
                ),
            )
        }

    }

    val managementService: AirlineManagementService = object : AirlineManagementService {
        override suspend fun scheduleFlight(flightId: String, departureTime: Instant, plane: Plane) {
            managementFlow.emit(AddNewFlight(flightId, departureTime, plane))
        }

        override suspend fun delayFlight(flightId: String, departureTime: Instant, actualDepartureTime: Instant) {
            managementFlow.emit(SetDelay(flightId, departureTime, "departure time", actualDepartureTime))
        }

        override suspend fun cancelFlight(flightId: String, departureTime: Instant) {
            managementFlow.emit(CancelFlight(flightId, departureTime))
        }

        override suspend fun setCheckInNumber(flightId: String, departureTime: Instant, checkInNumber: String) {
            managementFlow.emit(SetCheckInNumber(flightId, departureTime, "check in number", checkInNumber))
        }

        override suspend fun setGateNumber(flightId: String, departureTime: Instant, gateNumber: String) {
            managementFlow.emit(SetGateNumber(flightId, departureTime, "gate number", gateNumber))
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

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun airportInformationDisplay(coroutineScope: CoroutineScope): StateFlow<InformationDisplay> =
        listOfFlights.mapLatest { list -> InformationDisplay(list.map { flightToFlightInfo(it) }) }
            .sample(config.displayUpdateInterval)
            .stateIn(coroutineScope, SharingStarted.Eagerly, InformationDisplay(emptyList()))

    private suspend fun checkFlightForAudioAlerts(flight: Flight, flow: FlowCollector<AudioAlerts>) {
        val curTime = Time.getCurrentTime()
        rangesForAudioAlerts.forEach {
            if (it.predicate(curTime, flight.actualDepartureTime)) {
                flow.emit(it.function(flight))
            }
        }
    }

    private suspend fun updateAudioAlerts(flow: FlowCollector<AudioAlerts>) {
        listOfFlights.value.forEach {
            if (!it.isCancelled) checkFlightForAudioAlerts(it, flow)
        }
    }

    val airportAudioAlerts: Flow<AudioAlerts>
        get() = flow {
            while (true) {
                delay(config.audioAlertsInterval)
                updateAudioAlerts(this)
            }
        }

    suspend fun run() {
        fun FlightManager.eq(flight: Flight) =
            flightId == flight.flightId && departureTime == flight.departureTime

        suspend fun changeParameter(
            predicate: (Flight) -> Boolean,
            flight: Flight,
            creationMessage: (Ticket) -> String = { "" },
            needsToNotifyAll: Boolean = false,
            function: (Flight) -> Flight,
        ) =
            if (predicate(flight) && !flight.isCancelled && Time.getCurrentTime() < flight.actualDepartureTime) {
                if (needsToNotifyAll) passengerNotificationService.massiveMailing(flight, creationMessage)
                function(flight)
            } else {
                flight
            }

        suspend fun change(changer: FlightChanger, needsToNotifyAll: Boolean = true) {
            listOfFlights.value = listOfFlights.value.map {
                changeParameter(
                    { x -> changer.eq(x) },
                    it,
                    { x -> changer.messageForMail(x, it) },
                    needsToNotifyAll,
                    { x -> changer.changeParameter(x) },
                )
            }
        }

        suspend fun managementFlowCollect() {
            managementFlow.collect {
                when (it) {
                    is AddNewFlight -> listOfFlights.value += Flight(it.flightId, it.departureTime, plane = it.plane)

                    is BuyTicket -> {
                        suspend fun sendEmail(status: String) {
                            bufferedEmailService.send(
                                it.passengerEmail,
                                "Dear ${it.passengerName}(id: ${it.passengerId}), you have $status " +
                                    "bought a ticket for seat ${it.seatNo} on flight: ${it.flightId}",
                            )
                        }

                        fun checkFlight(flightId: String, departureTime: Instant): Boolean {
                            return bookingService.flightSchedule.any { flight ->
                                flight.flightId == flightId && flight.departureTime == departureTime
                            }
                        }

                        if (checkFlight(it.flightId, it.departureTime) &&
                            bookingService.freeSeats(it.flightId, it.departureTime).contains(it.seatNo)
                        ) {
                            change(it, false)
                            sendEmail("Successfully")
                        } else {
                            sendEmail("Unsuccessfully")
                        }
                    }

                    is FlightChanger -> change(it)
                }
            }
        }

        coroutineScope {
            launch {
                bufferedEmailService.receive()
            }
            launch {
                passengerNotificationService.receive()
            }
            launch {
                managementFlowCollect()
            }
        }

    }
}
