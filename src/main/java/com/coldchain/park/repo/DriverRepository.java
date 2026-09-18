package com.coldchain.park.repo;

import com.coldchain.park.domain.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findByIdNo(String idNo);
    List<Driver> findByCarrierId(Long carrierId);
}
