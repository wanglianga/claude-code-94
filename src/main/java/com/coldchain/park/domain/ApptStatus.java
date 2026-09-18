package com.coldchain.park.domain;

/** 车次业务状态，携带所属看板环节 */
public enum ApptStatus {
    PENDING("待排窗", Stage.SCHEDULED),
    SCHEDULED("已排窗", Stage.SCHEDULED),
    LICENSE_HOLD("证件异常待换司机", Stage.SCHEDULED),
    QUEUED_NIGHT("夜间排队", Stage.GATE),
    GATE_REJECTED("闸口拦截", Stage.GATE),
    AT_GATE("到园核验", Stage.GATE),
    IN_PARK("入园等待月台", Stage.DOCK),
    AT_DOCK("月台卸货中", Stage.DOCK),
    UNLOADED("卸货完成待质检", Stage.QC),
    IN_QC("质检抽样中", Stage.QC),
    QUARANTINED("隔离待处置", Stage.QUARANTINE),
    RELEASED("放行待结算", Stage.SETTLEMENT),
    DOWNGRADED("降级入库待结算", Stage.SETTLEMENT),
    REJECTED("拒收待结算", Stage.SETTLEMENT),
    SETTLED("已结算", Stage.DONE),
    CLOSED("已关单", Stage.DONE);

    public final String label;
    public final Stage stage;

    ApptStatus(String label, Stage stage) {
        this.label = label;
        this.stage = stage;
    }
}
