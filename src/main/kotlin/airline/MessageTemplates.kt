package airline

import airline.api.Ticket

class MessageTemplates {
    companion object {
        fun createMessageForChangeParameter(ticket: Ticket, parameter: String, oldValue: String, newValue: String) =
            if (parameter == "cancel") {
                "Dear ${ticket.passengerName}, your flight ${ticket.flightId} is cancelled"
            } else {
                "Dear ${ticket.passengerName}, your flight ${ticket.flightId} " +
                    "$parameter changed from $oldValue to $newValue"
            }
    }
}
