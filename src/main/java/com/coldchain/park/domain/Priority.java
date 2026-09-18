package com.coldchain.park.domain;

/** 客户优先级（货主等级），rank 越小越优先 */
public enum Priority {
    URGENT("临时加急", 0),
    VIP("VIP客户", 1),
    STANDARD("标准客户", 2),
    ECONOMY("经济客户", 3);

    public final String label;
    public final int rank;

    Priority(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }
}
