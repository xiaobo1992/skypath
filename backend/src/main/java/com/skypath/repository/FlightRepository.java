package com.skypath.repository;

import com.skypath.entity.Flight;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

@Repository
public interface FlightRepository extends CrudRepository<Flight, Long> {
}
