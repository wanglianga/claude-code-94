package com.coldchain.park.domain;

import jakarta.persistence.Entity;

/** 承运商（车队） */
@Entity
public class Carrier extends BaseEntity {

    private String code;
    private String name;
    private String contact;
    private String phone;

    /** 承运商服务分（基础 100，扣罚后下降），影响后续预约排队 */
    private int serviceScore = 100;

    /** 近 90 天累计扣罚金额，供调度参考 */
    private double totalPenalty = 0;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public int getServiceScore() { return serviceScore; }
    public void setServiceScore(int serviceScore) { this.serviceScore = serviceScore; }
    public double getTotalPenalty() { return totalPenalty; }
    public void setTotalPenalty(double totalPenalty) { this.totalPenalty = totalPenalty; }
}
