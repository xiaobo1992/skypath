package com.skypath.service;

import com.skypath.dto.ItineraryResponse;

import java.time.LocalDate;
import java.util.List;

public interface ItinerarySearchService {

    /** Validated itinerary search: same-airport returns empty, unknown codes throw IllegalArgumentException. */
    List<ItineraryResponse> search(String origin, String destination, LocalDate date);
}
