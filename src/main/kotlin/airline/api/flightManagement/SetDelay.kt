package airline.api.flightManagement

import airline.api.Flight
import kotlinx.datetime.Instant

class SetDelay(flightId: String, departureTime: Instant, name: String, private val actualDepartureTime: Instant) :
    FlightChanger(flightId, departureTime, name) {
        override fun changeParameter(flight: Flight): Flight {
            return flight.copy(actualDepartureTime = actualDepartureTime)
        }

        override fun getNewValue() = actualDepartureTime.toString()

        override fun getOldValue(flight: Flight) = flight.departureTime.toString()
    }
