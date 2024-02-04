package airline.api.flightManagement

import airline.MessageTemplates
import airline.api.Flight
import airline.api.Ticket
import kotlinx.datetime.Instant

abstract class FlightChanger(flightId: String, departureTime: Instant, private val name: String = "") :
    FlightManager(flightId, departureTime) {
        abstract fun changeParameter(flight: Flight): Flight

        private fun getName(): String = name

        protected abstract fun getNewValue(): String

        protected abstract fun getOldValue(flight: Flight): String

        open fun messageForMail(ticket: Ticket, flight: Flight): String {
            return MessageTemplates.createMessageForChangeParameter(
                ticket,
                getName(),
                getOldValue(flight),
                getNewValue(),
            )
        }
    }
