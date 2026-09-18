package com.coldchain.park.domain;

public enum ExceptionStatus {
    OPEN("待处理"),
    ACK("处理中"),
    CLOSED("已关闭");

    public final String label;

    ExceptionStatus(String label) {
        this.label = label;
    }
}
