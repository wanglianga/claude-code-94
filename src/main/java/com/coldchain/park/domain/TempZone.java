package com.coldchain.park.domain;

/** 温区：label + 常规目标温度下限 + 报警温度上限（超过即可能化冻） */
public enum TempZone {
    FROZEN("冷冻(-18℃)", -25, -15),
    CHILLED("冷藏(0~4℃)", 0, 7),
    CONST("恒温(8~15℃)", 8, 15);

    public final String label;
    public final double targetMinC;
    public final double alarmMaxC;

    TempZone(String label, double targetMinC, double alarmMaxC) {
        this.label = label;
        this.targetMinC = targetMinC;
        this.alarmMaxC = alarmMaxC;
    }

    /** 混装时取最冷温区作为月台分配依据 */
    public TempZone coldest(TempZone other) {
        return this.ordinal() <= other.ordinal() ? this : other;
    }
}
