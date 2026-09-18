package com.coldchain.park.repo;

import com.coldchain.park.domain.Appointment;
import com.coldchain.park.domain.ApptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByCode(String code);

    List<Appointment> findByCarrierIdOrderByCreatedAtDesc(Long carrierId);

    /** 货主维度：混装货物行包含该货主的车次 */
    @Query("select distinct a from Appointment a join a.cargoLines c where c.customer.id = :customerId order by a.createdAt desc")
    List<Appointment> findByCustomerId(@Param("customerId") Long customerId);

    List<Appointment> findByStatusInOrderByWindowStartAsc(List<ApptStatus> statuses);

    List<Appointment> findAllByOrderByCreatedAtDesc();

    /** 占用某月台、时间窗与给定区间重叠、且已排窗未关单的车次 */
    @Query("""
            select a from Appointment a
            where a.assignedDock.id = :dockId
              and a.status in :statuses
              and a.windowStart < :end
              and a.windowEnd > :start
            order by a.windowStart asc
            """)
    List<Appointment> findOverlapping(@Param("dockId") Long dockId,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end,
                                      @Param("statuses") List<ApptStatus> statuses);

    long countByCodeStartingWith(String prefix);
}
