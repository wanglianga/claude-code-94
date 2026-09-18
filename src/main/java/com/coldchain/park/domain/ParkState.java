package com.coldchain.park.domain;

import jakarta.persistence.Entity;

/** 园区全局状态（单行 id=1）：限电等临时管控开关 */
@Entity
public class ParkState extends BaseEntity {

    /** 是否限电（限电时冷库月台减半可用、禁止恒温车长时间靠台） */
    private boolean powerShedding = false;
    private String powerNote = "";

    public boolean isPowerShedding() { return powerShedding; }
    public void setPowerShedding(boolean powerShedding) { this.powerShedding = powerShedding; }
    public String getPowerNote() { return powerNote; }
    public void setPowerNote(String powerNote) { this.powerNote = powerNote; }
}
