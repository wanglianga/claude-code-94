package com.coldchain.park.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "park")
public class ParkProps {

    private int slotMinutes = 30;
    private int dockCount = 6;
    private int qcDayCapacity = 3;
    private int qcNightCapacity = 1;
    private int lateToleranceMinutes = 30;
    private int tempGapMinutes = 20;
    private boolean strictGate = true;
    private int nightStartHour = 22;
    private int nightEndHour = 6;

    public int getSlotMinutes() { return slotMinutes; }
    public void setSlotMinutes(int slotMinutes) { this.slotMinutes = slotMinutes; }
    public int getDockCount() { return dockCount; }
    public void setDockCount(int dockCount) { this.dockCount = dockCount; }
    public int getQcDayCapacity() { return qcDayCapacity; }
    public void setQcDayCapacity(int qcDayCapacity) { this.qcDayCapacity = qcDayCapacity; }
    public int getQcNightCapacity() { return qcNightCapacity; }
    public void setQcNightCapacity(int qcNightCapacity) { this.qcNightCapacity = qcNightCapacity; }
    public int getLateToleranceMinutes() { return lateToleranceMinutes; }
    public void setLateToleranceMinutes(int lateToleranceMinutes) { this.lateToleranceMinutes = lateToleranceMinutes; }
    public int getTempGapMinutes() { return tempGapMinutes; }
    public void setTempGapMinutes(int tempGapMinutes) { this.tempGapMinutes = tempGapMinutes; }
    public boolean isStrictGate() { return strictGate; }
    public void setStrictGate(boolean strictGate) { this.strictGate = strictGate; }
    public int getNightStartHour() { return nightStartHour; }
    public void setNightStartHour(int nightStartHour) { this.nightStartHour = nightStartHour; }
    public int getNightEndHour() { return nightEndHour; }
    public void setNightEndHour(int nightEndHour) { this.nightEndHour = nightEndHour; }
}
