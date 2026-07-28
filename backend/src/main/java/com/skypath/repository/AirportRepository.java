package com.skypath.repository;

import com.skypath.entity.Airport;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

@Repository
public interface AirportRepository extends CrudRepository<Airport, String> {
}
