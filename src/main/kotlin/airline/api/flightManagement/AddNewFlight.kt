package airline.api.flightManagement

import airline.api.Plane
import kotlinx.datetime.Instant

class AddNewFlight(flightId: String, departureTime: Instant, val plane: Plane) : FlightManager(flightId, departureTime)
