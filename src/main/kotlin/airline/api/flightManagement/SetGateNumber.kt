package airline.api.flightManagement

import airline.api.Flight
import kotlinx.datetime.Instant

class SetGateNumber(flightId: String, departureTime: Instant, name: String, private val gateNumber: String) :
    FlightChanger(flightId, departureTime, name) {
        override fun changeParameter(flight: Flight): Flight {
            return flight.copy(gateNumber = gateNumber)
        }

        override fun getNewValue() = gateNumber

        override fun getOldValue(flight: Flight) = flight.gateNumber.toString()
    }
