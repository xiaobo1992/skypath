package com.skypath.service;

import com.skypath.dto.ItineraryResponse;
import com.skypath.repository.AirportRepository;
import com.skypath.repository.ItineraryRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

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

    // Keeps the Hibernate session open through the entity->DTO mapping below, since
    // ItineraryResponse.fromEntity touches lazy Flight/Airport associations on Itinerary.
    @Transactional
    public List<ItineraryResponse> search(String origin, String destination, LocalDate date) {
        String originCode = origin.toUpperCase();
        String destinationCode = destination.toUpperCase();

        if (originCode.equals(destinationCode)) {
            return List.of();
        }
        if (!airportRepository.existsById(originCode)) {
            throw new IllegalArgumentException("Unknown origin airport code: " + originCode);
        }
        if (!airportRepository.existsById(destinationCode)) {
            throw new IllegalArgumentException("Unknown destination airport code: " + destinationCode);
        }

        return itineraryRepository
                .findByOriginCodeAndDestinationCodeAndDepartureDateOrderByTotalDurationMinutesAsc(
                        originCode, destinationCode, date)
                .stream()
                .map(ItineraryResponse::fromEntity)
                .toList();
    }
}
