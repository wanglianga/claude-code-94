package com.coldchain.park.domain;

/** 责任承担方 */
public enum Party {
    CARRIER("承运商"),
    PARK("物流园"),
    CUSTOMER("货主/客户"),
    NONE("无责/免责");

    public final String label;

    Party(String label) {
        this.label = label;
    }
}
