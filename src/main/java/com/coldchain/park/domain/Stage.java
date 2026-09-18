package com.coldchain.park.domain;

/** 车次当前所处的大环节（看板用：闸口 / 月台 / 质检 / 隔离 / 结算） */
public enum Stage {
    SCHEDULED("预约排窗"),
    GATE("闸口核验"),
    DOCK("月台接车"),
    QC("质检抽样"),
    QUARANTINE("隔离判定"),
    SETTLEMENT("结算放行"),
    DONE("完成离场");

    public final String label;

    Stage(String label) {
        this.label = label;
    }
}
