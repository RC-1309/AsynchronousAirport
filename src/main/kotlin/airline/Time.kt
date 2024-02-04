package airline

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class Time {
    companion object {
        fun getCurrentTime(): Instant {
            return Clock.System.now()
        }
    }
}
