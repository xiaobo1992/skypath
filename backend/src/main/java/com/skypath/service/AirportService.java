package com.skypath.service;

import com.skypath.dto.AirportResponse;

import java.util.List;

public interface AirportService {

    List<AirportResponse> listAirports();
}
