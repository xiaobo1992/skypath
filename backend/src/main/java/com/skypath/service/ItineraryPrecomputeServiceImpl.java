package com.skypath.service;

import com.skypath.entity.Airport;
import com.skypath.entity.Flight;
import com.skypath.entity.Itinerary;
import com.skypath.repository.FlightRepository;
import com.skypath.repository.ItineraryRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class ItineraryPrecomputeServiceImpl implements ItineraryPrecomputeService {

    private static final Logger LOG = LoggerFactory.getLogger(ItineraryPrecomputeServiceImpl.class);

    private static final int MIN_LAYOVER_DOMESTIC_MINUTES = 45;
    private static final int MIN_LAYOVER_INTERNATIONAL_MINUTES = 90;
    private static final int MAX_LAYOVER_MINUTES = 6 * 60;

    private final FlightRepository flightRepository;
    private final ItineraryRepository itineraryRepository;

    public ItineraryPrecomputeServiceImpl(FlightRepository flightRepository, ItineraryRepository itineraryRepository) {
        this.flightRepository = flightRepository;
        this.itineraryRepository = itineraryRepository;
    }

    @Override
    @Transactional
    public void precompute() {
        List<Flight> flights = new ArrayList<>();
        flightRepository.findAll().forEach(flights::add);

        Map<String, List<Flight>> flightsByOrigin = flights.stream()
                .collect(Collectors.groupingBy(f -> f.getOrigin().getCode()));

        List<Itinerary> itineraries = new ArrayList<>();
        for (Flight first : flights) {
            itineraries.add(buildItinerary(List.of(first), null, null));

            for (Flight second : flightsByOrigin.getOrDefault(first.getDestination().getCode(), List.of())) {
                if (second.getDestination().getCode().equals(first.getOrigin().getCode())) {
                    continue; // don't fly back to the origin airport as the connection
                }
                Integer layover1 = layoverMinutes(first, second);
                if (layover1 == null) {
                    continue;
                }
                itineraries.add(buildItinerary(List.of(first, second), layover1, null));

                Set<String> visited = new HashSet<>(List.of(
                        first.getOrigin().getCode(),
                        second.getOrigin().getCode(),
                        second.getDestination().getCode()));

                for (Flight third : flightsByOrigin.getOrDefault(second.getDestination().getCode(), List.of())) {
                    if (visited.contains(third.getDestination().getCode())) {
                        continue; // don't route back through an airport already on the itinerary
                    }
                    Integer layover2 = layoverMinutes(second, third);
                    if (layover2 == null) {
                        continue;
                    }
                    itineraries.add(buildItinerary(List.of(first, second, third), layover1, layover2));
                }
            }
        }

        itineraryRepository.deleteAll();
        itineraryRepository.saveAll(itineraries);
        LOG.info("Precomputed {} itineraries from {} flights", itineraries.size(), flights.size());
    }

    /** Layover minutes between two connecting flights, or null if the connection breaks a rule. */
    private Integer layoverMinutes(Flight arriving, Flight departing) {
        if (arriving.getId().equals(departing.getId())) {
            return null;
        }
        long minutes = Duration.between(
                toInstant(arriving.getArrivalTime(), arriving.getDestination()),
                toInstant(departing.getDepartureTime(), departing.getOrigin())
        ).toMinutes();
        if (minutes < minLayoverMinutes(arriving, departing) || minutes > MAX_LAYOVER_MINUTES) {
            return null;
        }
        return (int) minutes;
    }

    /**
     * Domestic if the arriving flight's origin and the departing flight's destination are both
     * in the connecting airport's country (i.e. the whole arriving-airport-departing chain stays
     * in one country) - per instructions.md's "JFK-ORD-LAX = domestic, JFK-LHR-CDG = international".
     */
    private int minLayoverMinutes(Flight arriving, Flight departing) {
        String connectingCountry = arriving.getDestination().getCountry();
        boolean domestic = arriving.getOrigin().getCountry().equals(connectingCountry)
                && departing.getDestination().getCountry().equals(connectingCountry);
        return domestic ? MIN_LAYOVER_DOMESTIC_MINUTES : MIN_LAYOVER_INTERNATIONAL_MINUTES;
    }

    private Itinerary buildItinerary(List<Flight> legs, Integer layover1Minutes, Integer layover2Minutes) {
        Flight first = legs.get(0);
        Flight last = legs.get(legs.size() - 1);

        Instant departureInstant = toInstant(first.getDepartureTime(), first.getOrigin());
        Instant arrivalInstant = toInstant(last.getArrivalTime(), last.getDestination());

        Itinerary itinerary = new Itinerary();
        itinerary.setOrigin(first.getOrigin());
        itinerary.setDestination(last.getDestination());
        itinerary.setDepartureDate(first.getDepartureTime().toLocalDate());
        itinerary.setStopCount((short) (legs.size() - 1));
        itinerary.setFlight1(first);
        itinerary.setFlight2(legs.size() > 1 ? legs.get(1) : null);
        itinerary.setFlight3(legs.size() > 2 ? legs.get(2) : null);
        itinerary.setLayover1Minutes(layover1Minutes);
        itinerary.setLayover2Minutes(layover2Minutes);
        itinerary.setDepartureTime(first.getDepartureTime());
        itinerary.setArrivalTime(last.getArrivalTime());
        itinerary.setTotalDurationMinutes((int) Duration.between(departureInstant, arrivalInstant).toMinutes());
        itinerary.setTotalPrice(legs.stream().map(Flight::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add));
        itinerary.setCreatedAt(LocalDateTime.now());
        return itinerary;
    }

    private Instant toInstant(LocalDateTime localTime, Airport airport) {
        return localTime.atZone(ZoneId.of(airport.getTimezone())).toInstant();
    }
}
