package airline.api.flightManagement

import airline.api.Flight
import airline.api.Ticket
import kotlinx.datetime.Instant

class CancelFlight(flightId: String, departureTime: Instant) : FlightChanger(flightId, departureTime) {
    override fun changeParameter(flight: Flight): Flight {
        return flight.copy(isCancelled = true)
    }

    override fun getNewValue() = "true"

    override fun getOldValue(flight: Flight) = flight.isCancelled.toString()

    override fun messageForMail(ticket: Ticket, flight: Flight): String {
        return "Dear ${ticket.passengerName}, your flight ${ticket.flightId} is cancelled"
    }
}
