package com.skypath.service;

import com.skypath.dto.AirportResponse;
import com.skypath.repository.AirportRepository;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class AirportServiceImpl implements AirportService {

    private final AirportRepository airportRepository;

    public AirportServiceImpl(AirportRepository airportRepository) {
        this.airportRepository = airportRepository;
    }

    @Override
    public List<AirportResponse> listAirports() {
        return airportRepository.findAll().stream()
                .map(AirportResponse::fromEntity)
                .toList();
    }
}
