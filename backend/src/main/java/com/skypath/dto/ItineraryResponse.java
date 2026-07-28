package com.skypath.dto;

import com.skypath.entity.Itinerary;
import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Serdeable
public record ItineraryResponse(
        String origin,
        String destination,
        List<FlightSegmentResponse> segments,
        List<Integer> layoverMinutes,
        int totalDurationMinutes,
        BigDecimal totalPrice
) {

    public static ItineraryResponse fromEntity(Itinerary itinerary) {
        List<FlightSegmentResponse> segments = new ArrayList<>();
        segments.add(FlightSegmentResponse.fromEntity(itinerary.getFlight1()));
        if (itinerary.getFlight2() != null) {
            segments.add(FlightSegmentResponse.fromEntity(itinerary.getFlight2()));
        }
        if (itinerary.getFlight3() != null) {
            segments.add(FlightSegmentResponse.fromEntity(itinerary.getFlight3()));
        }

        List<Integer> layovers = new ArrayList<>();
        if (itinerary.getLayover1Minutes() != null) {
            layovers.add(itinerary.getLayover1Minutes());
        }
        if (itinerary.getLayover2Minutes() != null) {
            layovers.add(itinerary.getLayover2Minutes());
        }

        return new ItineraryResponse(
                itinerary.getOrigin().getCode(),
                itinerary.getDestination().getCode(),
                segments,
                layovers,
                itinerary.getTotalDurationMinutes(),
                itinerary.getTotalPrice()
        );
    }
}
