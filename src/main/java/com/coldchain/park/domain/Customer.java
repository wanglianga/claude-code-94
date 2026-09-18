package com.coldchain.park.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** 货主（客户） */
@Entity
public class Customer extends BaseEntity {

    private String code;
    private String name;
    private String contact;
    private String phone;

    @Enumerated(EnumType.STRING)
    private Priority priority = Priority.STANDARD;

    /** 协议基础运费（元/车），用于赔付/扣罚计算演示 */
    private double contractFreight = 3000;

    /** 货损赔付单价（元/件） */
    private double claimPricePerPiece = 50;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public double getContractFreight() { return contractFreight; }
    public void setContractFreight(double contractFreight) { this.contractFreight = contractFreight; }
    public double getClaimPricePerPiece() { return claimPricePerPiece; }
    public void setClaimPricePerPiece(double claimPricePerPiece) { this.claimPricePerPiece = claimPricePerPiece; }
}
