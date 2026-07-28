package com.skypath.controller;

import com.skypath.dto.ItineraryResponse;
import com.skypath.service.ItinerarySearchService;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;

import java.time.LocalDate;
import java.util.List;

@Controller("/itineraries")
public class ItineraryController {

    private final ItinerarySearchService itinerarySearchService;

    public ItineraryController(ItinerarySearchService itinerarySearchService) {
        this.itinerarySearchService = itinerarySearchService;
    }

    @Get
    public List<ItineraryResponse> search(
            @QueryValue String origin,
            @QueryValue String destination,
            @QueryValue LocalDate date) {
        return itinerarySearchService.search(origin, destination, date);
    }
}
