package com.coldchain.park.repo;

import com.coldchain.park.domain.ParkState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkStateRepository extends JpaRepository<ParkState, Long> {
}
