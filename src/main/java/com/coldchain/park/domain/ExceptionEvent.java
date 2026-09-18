package com.coldchain.park.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;

/**
 * 异常事件单：晚到 / 温度断点 / 封签异常 / 改仓 / 拥堵 / 化冻 / 证件过期 / 限电 / 夜间排队。
 * 五类角色在同一车次的异常单上协同（评论+状态流转）。
 */
@Entity
public class ExceptionEvent extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    private ExceptionType type;

    @Enumerated(EnumType.STRING)
    private ExceptionStatus status = ExceptionStatus.OPEN;

    private String detail;
    private String raisedBy;           // 发现角色/系统
    private LocalDateTime raisedAt;
    private LocalDateTime closedAt;
    private String resolution = "";    // 处理结论

    /** 判定责任方（由调度/系统判定） */
    @Enumerated(EnumType.STRING)
    private Party responsibleParty;

    public ExceptionEvent() {}

    public ExceptionEvent(ExceptionType type, String detail, String raisedBy, LocalDateTime raisedAt) {
        this.type = type;
        this.detail = detail;
        this.raisedBy = raisedBy;
        this.raisedAt = raisedAt;
        this.responsibleParty = type.defaultParty;
    }

    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
    public ExceptionType getType() { return type; }
    public void setType(ExceptionType type) { this.type = type; }
    public ExceptionStatus getStatus() { return status; }
    public void setStatus(ExceptionStatus status) { this.status = status; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getRaisedBy() { return raisedBy; }
    public void setRaisedBy(String raisedBy) { this.raisedBy = raisedBy; }
    public LocalDateTime getRaisedAt() { return raisedAt; }
    public void setRaisedAt(LocalDateTime raisedAt) { this.raisedAt = raisedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public Party getResponsibleParty() { return responsibleParty; }
    public void setResponsibleParty(Party responsibleParty) { this.responsibleParty = responsibleParty; }
}
