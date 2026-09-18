package com.coldchain.park.repo;

import com.coldchain.park.domain.TimelineEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimelineEventRepository extends JpaRepository<TimelineEvent, Long> {
    List<TimelineEvent> findByAppointmentIdOrderByTimeAsc(Long appointmentId);
}
