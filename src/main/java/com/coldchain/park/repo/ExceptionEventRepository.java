package com.coldchain.park.repo;

import com.coldchain.park.domain.ExceptionEvent;
import com.coldchain.park.domain.ExceptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExceptionEventRepository extends JpaRepository<ExceptionEvent, Long> {
    List<ExceptionEvent> findByAppointmentIdOrderByRaisedAtAsc(Long appointmentId);
    List<ExceptionEvent> findByStatus(ExceptionStatus status);
}
