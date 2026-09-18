package com.coldchain.park.domain;

/**
 * 异常类型。severity 1~3（影响扣罚档位），defaultParty 为默认建议责任方，
 * 实际责任以到园前后数据等上下文由 ResponsibilityService 再判定。
 */
public enum ExceptionType {
    LATE(2, "车辆晚到", Party.CARRIER),
    TEMP_GAP(3, "温度曲线断点", Party.CARRIER),
    TEMP_EXCURSION(3, "温度超标/失温", Party.CARRIER),
    SEAL_MISMATCH(3, "电子封签异常", Party.CARRIER),
    WAREHOUSE_CHANGE(1, "货主临时改仓", Party.CUSTOMER),
    DOCK_CONGESTION(2, "月台拥堵", Party.PARK),
    THAW(3, "质检发现化冻", Party.CARRIER),
    LICENSE_EXPIRED(2, "司机证件过期", Party.CARRIER),
    LOAD_SHEDDING(2, "园区限电", Party.PARK),
    NIGHT_QUEUE(1, "夜间排队", Party.PARK);

    public final int severity;
    public final String label;
    public final Party defaultParty;

    ExceptionType(int severity, String label, Party defaultParty) {
        this.severity = severity;
        this.label = label;
        this.defaultParty = defaultParty;
    }
}
