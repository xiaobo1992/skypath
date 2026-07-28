package com.skypath.repository;

import com.skypath.entity.Itinerary;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ItineraryRepository extends CrudRepository<Itinerary, Long> {

    List<Itinerary> findByOriginCodeAndDestinationCodeAndDepartureDateOrderByTotalDurationMinutesAsc(
            String originCode, String destinationCode, LocalDate departureDate);
}
