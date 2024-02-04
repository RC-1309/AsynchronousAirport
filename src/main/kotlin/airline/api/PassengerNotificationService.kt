package airline.api

import airline.service.EmailService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class PassengerNotificationService(private val emailService: EmailService) {
    private val channel = Channel<Pair<Flight, (Ticket) -> String>>(1024)

    suspend fun receive() {
        for ((flight, function) in channel) {
            coroutineScope {
                flight.tickets.values.forEach {
                    launch {
                        emailService.send(it.passengerEmail, function(it))
                    }
                }
            }
        }
    }

    suspend fun massiveMailing(flight: Flight, creationMessage: (Ticket) -> String) {
        channel.send(flight to creationMessage)
    }
}
