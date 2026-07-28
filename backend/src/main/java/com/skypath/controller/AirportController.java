package com.skypath.controller;

import com.skypath.dto.AirportResponse;
import com.skypath.service.AirportService;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.List;

@Controller("/airports")
public class AirportController {

    private final AirportService airportService;

    public AirportController(AirportService airportService) {
        this.airportService = airportService;
    }

    @Get
    public List<AirportResponse> listAirports() {
        return airportService.listAirports();
    }
}
