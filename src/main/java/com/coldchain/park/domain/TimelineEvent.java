package com.coldchain.park.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;

/** 车次时间线：每个角色的关键动作与协同评论都落到这里，形成"同一车次处理"的完整轨迹 */
@Entity
public class TimelineEvent extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private Appointment appointment;

    private LocalDateTime time;
    private String actor;             // 操作角色/账号
    private String action;            // 动作简述
    @Lob
    private String detail;            // 详细说明（可含客服解释口径）
    private String category;          // SYSTEM/GATE/DOCK/QC/DISPATCH/CS/SETTLEMENT/CARRIER

    public TimelineEvent() {}

    public TimelineEvent(LocalDateTime time, String actor, String action, String detail, String category) {
        this.time = time;
        this.actor = actor;
        this.action = action;
        this.detail = detail;
        this.category = category;
    }

    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
    public LocalDateTime getTime() { return time; }
    public void setTime(LocalDateTime time) { this.time = time; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
