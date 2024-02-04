package airline.api.flightManagement

import kotlinx.datetime.Instant

sealed class FlightManager(val flightId: String, val departureTime: Instant)
