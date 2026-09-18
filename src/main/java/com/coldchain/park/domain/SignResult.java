package com.coldchain.park.domain;

public enum SignResult {
    FULL("全数签收"),
    PARTIAL("短少/部分签收"),
    REFUSED("拒签");

    public final String label;

    SignResult(String label) {
        this.label = label;
    }
}
