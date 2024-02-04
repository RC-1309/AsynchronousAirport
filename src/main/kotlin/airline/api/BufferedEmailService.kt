package airline.api

import airline.service.EmailService
import kotlinx.coroutines.channels.Channel

class BufferedEmailService(private val emailService: EmailService) : EmailService {
    data class Email(val to: String, val text: String)
    private val channel = Channel<Email>(1024)

    suspend fun receive() {
        for (email in channel) {
            emailService.send(email.to, email.text)
        }
    }

    override suspend fun send(to: String, text: String) {
        channel.send(Email(to, text))
    }
}
