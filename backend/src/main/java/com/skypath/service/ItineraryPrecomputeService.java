package com.skypath.service;

/**
 * Builds the precomputed {@code itineraries} table (direct, 1-stop, 2-stop) from the
 * {@code flights} table by walking the flight graph and applying the layover/timezone rules,
 * so the search endpoint can do a plain indexed read instead of a live graph traversal.
 */
public interface ItineraryPrecomputeService {

    /** Truncates and rebuilds the entire itineraries table. */
    void precompute();
}
