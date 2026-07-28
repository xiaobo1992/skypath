package com.skypath.controller;

import com.skypath.dto.AirportResponse;
import com.skypath.repository.AirportRepository;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.List;

@Controller("/airports")
public class AirportController {

    private final AirportRepository airportRepository;

    public AirportController(AirportRepository airportRepository) {
        this.airportRepository = airportRepository;
    }

    @Get
    public List<AirportResponse> listAirports() {
        return airportRepository.findAll().stream()
                .map(AirportResponse::fromEntity)
                .toList();
    }
}
