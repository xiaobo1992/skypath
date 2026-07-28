package com.skypath.service;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Singleton;

/** Rebuilds the precomputed itineraries table once on every application startup. */
@Singleton
public class ItineraryPrecomputeStartupListener implements ApplicationEventListener<StartupEvent> {

    private final ItineraryPrecomputeService itineraryPrecomputeService;

    public ItineraryPrecomputeStartupListener(ItineraryPrecomputeService itineraryPrecomputeService) {
        this.itineraryPrecomputeService = itineraryPrecomputeService;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        itineraryPrecomputeService.precompute();
    }
}
