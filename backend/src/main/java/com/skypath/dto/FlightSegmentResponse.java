package com.skypath.dto;

import com.skypath.entity.Flight;
import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Serdeable
public record FlightSegmentResponse(
        String flightNumber,
        String airline,
        String origin,
        String destination,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        BigDecimal price,
        String aircraft
) {

    public static FlightSegmentResponse fromEntity(Flight flight) {
        return new FlightSegmentResponse(
                flight.getFlightNumber(),
                flight.getAirline(),
                flight.getOrigin().getCode(),
                flight.getDestination().getCode(),
                flight.getDepartureTime(),
                flight.getArrivalTime(),
                flight.getPrice(),
                flight.getAircraft()
        );
    }
}
