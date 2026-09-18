package com.coldchain.park.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * 车次结算单：责任判断最终落到钱与后续权益。
 * 承运商扣罚（运费扣减/违约金）、客户赔付（货损赔付）、加/减项与本次优先级积分变动。
 */
@Entity
public class Settlement extends BaseEntity {

    private String code;

    /** 运费基数（多货主混装按货主合同汇总） */
    private double freightBase;

    /** 承运商违约金/扣罚合计 */
    private double carrierPenalty;
    /** 应支付给承运商的运费 */
    private double carrierPayable;

    /** 货损赔付给客户金额 */
    private double customerClaim;
    /** 园区自担补偿（月台拥堵/限电责任时） */
    private double parkCompensation;

    /** 本次责任结构摘要，如 承运商70% / 园区30% */
    private String responsibilitySummary;

    /** 承运商服务分变动（负数扣分），影响后续预约优先级 */
    private int scoreDelta;

    private String note = "";

    @Enumerated(EnumType.STRING)
    private Decision finalDecision;

    private boolean settled = false;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public double getFreightBase() { return freightBase; }
    public void setFreightBase(double freightBase) { this.freightBase = freightBase; }
    public double getCarrierPenalty() { return carrierPenalty; }
    public void setCarrierPenalty(double carrierPenalty) { this.carrierPenalty = carrierPenalty; }
    public double getCarrierPayable() { return carrierPayable; }
    public void setCarrierPayable(double carrierPayable) { this.carrierPayable = carrierPayable; }
    public double getCustomerClaim() { return customerClaim; }
    public void setCustomerClaim(double customerClaim) { this.customerClaim = customerClaim; }
    public double getParkCompensation() { return parkCompensation; }
    public void setParkCompensation(double parkCompensation) { this.parkCompensation = parkCompensation; }
    public String getResponsibilitySummary() { return responsibilitySummary; }
    public void setResponsibilitySummary(String responsibilitySummary) { this.responsibilitySummary = responsibilitySummary; }
    public int getScoreDelta() { return scoreDelta; }
    public void setScoreDelta(int scoreDelta) { this.scoreDelta = scoreDelta; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Decision getFinalDecision() { return finalDecision; }
    public void setFinalDecision(Decision finalDecision) { this.finalDecision = finalDecision; }
    public boolean isSettled() { return settled; }
    public void setSettled(boolean settled) { this.settled = settled; }
}
