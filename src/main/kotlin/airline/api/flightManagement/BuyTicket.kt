package airline.api.flightManagement

import airline.api.Flight
import airline.api.Ticket
import kotlinx.datetime.Instant

class BuyTicket(
    flightId: String,
    departureTime: Instant,
    val seatNo: String,
    val passengerId: String,
    val passengerName: String,
    val passengerEmail: String,
    name: String,
) : FlightChanger(flightId, departureTime, name) {
    override fun changeParameter(flight: Flight): Flight {
        return flight.copy(
            tickets = flight.tickets + Pair(
                seatNo,
                Ticket(flightId, departureTime, seatNo, passengerId, passengerName, passengerEmail),
            ),
        )
    }

    override fun getNewValue() = ""

    override fun getOldValue(flight: Flight) = ""
}
