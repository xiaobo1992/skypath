package com.skypath.dto;

import com.skypath.entity.Airport;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AirportResponse(
        String code,
        String name,
        String city,
        String country,
        String timezone
) {

    public static AirportResponse fromEntity(Airport airport) {
        return new AirportResponse(
                airport.getCode(),
                airport.getName(),
                airport.getCity(),
                airport.getCountry(),
                airport.getTimezone()
        );
    }
}
