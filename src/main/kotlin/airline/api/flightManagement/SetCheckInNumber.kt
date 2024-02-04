package airline.api.flightManagement

import airline.api.Flight
import kotlinx.datetime.Instant

class SetCheckInNumber(flightId: String, departureTime: Instant, name: String, private val checkInNumber: String) :
    FlightChanger(flightId, departureTime, name) {
        override fun changeParameter(flight: Flight): Flight {
            return flight.copy(checkInNumber = checkInNumber)
        }

        override fun getNewValue() = checkInNumber

        override fun getOldValue(flight: Flight) = flight.checkInNumber.toString()
    }
