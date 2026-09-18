package com.coldchain.park.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

/** 预约货物行：支持多货主混装 */
@Entity
public class AppointmentCargo extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;

    private String goodsName;

    @Enumerated(EnumType.STRING)
    private TempZone zone;

    private int pieces;

    /** 改仓：原目标仓 -> 新目标仓（货主临时改仓异常） */
    private String targetWarehouse;
    private String changedWarehouse;

    /** 本行处置结论（可按货主分别放行/降级/拒收） */
    @Enumerated(EnumType.STRING)
    private Decision lineDecision;

    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }
    public TempZone getZone() { return zone; }
    public void setZone(TempZone zone) { this.zone = zone; }
    public int getPieces() { return pieces; }
    public void setPieces(int pieces) { this.pieces = pieces; }
    public String getTargetWarehouse() { return targetWarehouse; }
    public void setTargetWarehouse(String targetWarehouse) { this.targetWarehouse = targetWarehouse; }
    public String getChangedWarehouse() { return changedWarehouse; }
    public void setChangedWarehouse(String changedWarehouse) { this.changedWarehouse = changedWarehouse; }
    public Decision getLineDecision() { return lineDecision; }
    public void setLineDecision(Decision lineDecision) { this.lineDecision = lineDecision; }
}
