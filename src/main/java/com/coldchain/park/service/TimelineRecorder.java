package com.coldchain.park.service;

import com.coldchain.park.domain.Appointment;
import com.coldchain.park.domain.TimelineEvent;
import com.coldchain.park.repo.TimelineEventRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 统一写车次时间线，保证五类角色的动作都落在同一车次轨迹上 */
@Component
public class TimelineRecorder {

    private final TimelineEventRepository repo;

    public TimelineRecorder(TimelineEventRepository repo) {
        this.repo = repo;
    }

    public TimelineEvent record(Appointment a, String actor, String action, String detail, String category) {
        TimelineEvent e = new TimelineEvent(LocalDateTime.now(), actor, action, detail, category);
        e.setAppointment(a);
        a.getTimeline().add(e);
        return repo.save(e);
    }
}
