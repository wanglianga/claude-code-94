package com.coldchain.park.repo;

import com.coldchain.park.domain.TemperatureReading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemperatureReadingRepository extends JpaRepository<TemperatureReading, Long> {
    List<TemperatureReading> findByAppointmentIdOrderBySampleTimeAsc(Long appointmentId);
}
