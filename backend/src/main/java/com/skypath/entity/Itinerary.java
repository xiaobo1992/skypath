package com.skypath.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "itineraries")
public class Itinerary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_code", nullable = false)
    private Airport origin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_code", nullable = false)
    private Airport destination;

    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    @Column(name = "stop_count", nullable = false)
    private Short stopCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_1_id", nullable = false)
    private Flight flight1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_2_id")
    private Flight flight2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_3_id")
    private Flight flight3;

    @Column(name = "layover_1_minutes")
    private Integer layover1Minutes;

    @Column(name = "layover_2_minutes")
    private Integer layover2Minutes;

    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    @Column(name = "arrival_time", nullable = false)
    private LocalDateTime arrivalTime;

    @Column(name = "total_duration_minutes", nullable = false)
    private Integer totalDurationMinutes;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Itinerary() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Airport getOrigin() {
        return origin;
    }

    public void setOrigin(Airport origin) {
        this.origin = origin;
    }

    public Airport getDestination() {
        return destination;
    }

    public void setDestination(Airport destination) {
        this.destination = destination;
    }

    public LocalDate getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(LocalDate departureDate) {
        this.departureDate = departureDate;
    }

    public Short getStopCount() {
        return stopCount;
    }

    public void setStopCount(Short stopCount) {
        this.stopCount = stopCount;
    }

    public Flight getFlight1() {
        return flight1;
    }

    public void setFlight1(Flight flight1) {
        this.flight1 = flight1;
    }

    public Flight getFlight2() {
        return flight2;
    }

    public void setFlight2(Flight flight2) {
        this.flight2 = flight2;
    }

    public Flight getFlight3() {
        return flight3;
    }

    public void setFlight3(Flight flight3) {
        this.flight3 = flight3;
    }

    public Integer getLayover1Minutes() {
        return layover1Minutes;
    }

    public void setLayover1Minutes(Integer layover1Minutes) {
        this.layover1Minutes = layover1Minutes;
    }

    public Integer getLayover2Minutes() {
        return layover2Minutes;
    }

    public void setLayover2Minutes(Integer layover2Minutes) {
        this.layover2Minutes = layover2Minutes;
    }

    public LocalDateTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalDateTime departureTime) {
        this.departureTime = departureTime;
    }

    public LocalDateTime getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(LocalDateTime arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public Integer getTotalDurationMinutes() {
        return totalDurationMinutes;
    }

    public void setTotalDurationMinutes(Integer totalDurationMinutes) {
        this.totalDurationMinutes = totalDurationMinutes;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
