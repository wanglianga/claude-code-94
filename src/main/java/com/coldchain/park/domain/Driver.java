package com.coldchain.park.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

import java.time.LocalDate;

/** 司机及其证件（驾驶证/从业资格证有效期） */
@Entity
public class Driver extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Carrier carrier;

    private String name;
    private String phone;
    private String idNo;

    /** 驾驶证到期日 */
    private LocalDate licenseExpiry;
    /** 从业资格证到期日 */
    private LocalDate qualificationExpiry;

    public Carrier getCarrier() { return carrier; }
    public void setCarrier(Carrier carrier) { this.carrier = carrier; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getIdNo() { return idNo; }
    public void setIdNo(String idNo) { this.idNo = idNo; }
    public LocalDate getLicenseExpiry() { return licenseExpiry; }
    public void setLicenseExpiry(LocalDate licenseExpiry) { this.licenseExpiry = licenseExpiry; }
    public LocalDate getQualificationExpiry() { return qualificationExpiry; }
    public void setQualificationExpiry(LocalDate qualificationExpiry) { this.qualificationExpiry = qualificationExpiry; }

    /** 返回已过期的证件名称；空串=均有效 */
    public String expiredDocument(LocalDate today) {
        if (licenseExpiry != null && !licenseExpiry.isAfter(today)) {
            return "驾驶证已于 " + licenseExpiry + " 到期";
        }
        if (qualificationExpiry != null && !qualificationExpiry.isAfter(today)) {
            return "从业资格证已于 " + qualificationExpiry + " 到期";
        }
        return "";
    }
}
