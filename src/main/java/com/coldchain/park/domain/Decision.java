package com.coldchain.park.domain;

/** 货物处置结论 */
public enum Decision {
    RELEASE("正常放行"),
    QUARANTINE("隔离待查"),
    DOWNGRADE("降级入库"),
    REJECT("拒收退回");

    public final String label;

    Decision(String label) {
        this.label = label;
    }
}
