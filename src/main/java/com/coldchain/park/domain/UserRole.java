package com.coldchain.park.domain;

/** 系统角色：承运商 / 园区调度 / 质检 / 客户客服 / 结算 / 管理员 */
public enum UserRole {
    ADMIN("系统管理员"),
    CARRIER("承运商"),
    DISPATCH("园区调度"),
    QC("质检员"),
    CS("客户客服"),
    SETTLEMENT("结算员");

    public final String label;

    UserRole(String label) {
        this.label = label;
    }
}
