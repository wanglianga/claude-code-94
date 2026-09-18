package com.coldchain.park.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 温控设备原始采样点（完整保留，供客户追溯运输质量） */
@Entity
@Table(indexes = @Index(name = "idx_reading_time", columnList = "sampleTime"))
public class TemperatureReading extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private Appointment appointment;

    private LocalDateTime sampleTime;
    private double tempC;
    /** 采样来源：装车前 / 运输途中 / 到园 / 开门前 */
    private String phase;

    public TemperatureReading() {}

    public TemperatureReading(LocalDateTime sampleTime, double tempC, String phase) {
        this.sampleTime = sampleTime;
        this.tempC = tempC;
        this.phase = phase;
    }

    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
    public LocalDateTime getSampleTime() { return sampleTime; }
    public void setSampleTime(LocalDateTime sampleTime) { this.sampleTime = sampleTime; }
    public double getTempC() { return tempC; }
    public void setTempC(double tempC) { this.tempC = tempC; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
}
