package com.coldchain.park.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** 装卸月台 */
@Entity
public class Dock extends BaseEntity {

    private String code;

    /** 该月台适合的温区（null=通用），混装按最冷温区匹配 */
    @Enumerated(EnumType.STRING)
    private TempZone preferredZone;

    private boolean enabled = true;

    public Dock() {}

    public Dock(String code, TempZone preferredZone) {
        this.code = code;
        this.preferredZone = preferredZone;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public TempZone getPreferredZone() { return preferredZone; }
    public void setPreferredZone(TempZone preferredZone) { this.preferredZone = preferredZone; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
