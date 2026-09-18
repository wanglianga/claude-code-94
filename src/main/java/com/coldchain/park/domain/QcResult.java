package com.coldchain.park.domain;

public enum QcResult {
    PASS("抽检合格"),
    THAW("发现化冻"),
    REJECT("严重不合格拒收");

    public final String label;

    QcResult(String label) {
        this.label = label;
    }
}
