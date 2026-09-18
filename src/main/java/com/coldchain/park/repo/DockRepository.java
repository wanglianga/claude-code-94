package com.coldchain.park.repo;

import com.coldchain.park.domain.Dock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DockRepository extends JpaRepository<Dock, Long> {
    List<Dock> findByEnabledTrue();
}
