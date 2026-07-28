package com.skypath.service;

import com.skypath.dto.ItineraryResponse;
import com.skypath.repository.AirportRepository;
import com.skypath.repository.ItineraryRepository;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;

@Singleton
public class ItinerarySearchService {

    private final ItineraryRepository itineraryRepository;
    private final AirportRepository airportRepository;

    public ItinerarySearchService(ItineraryRepository itineraryRepository, AirportRepository airportRepository) {
        this.itineraryRepository = itineraryRepository;
        this.airportRepository = airportRepository;
    }

    public List<ItineraryResponse> search(String origin, String destination, LocalDate date) {
        // TODO: replace with proper validation exceptions + a global @Error handler
        // once error response shape is decided (test cases #4 same-airport, #5 invalid code).
        if (origin.equalsIgnoreCase(destination)) {
            return List.of();
        }
        if (!airportRepository.existsById(origin) || !airportRepository.existsById(destination)) {
            throw new IllegalArgumentException("Unknown airport code");
        }

        return itineraryRepository
                .findByOriginCodeAndDestinationCodeAndDepartureDateOrderByTotalDurationMinutesAsc(
                        origin, destination, date)
                .stream()
                .map(ItineraryResponse::fromEntity)
                .toList();
    }
}
