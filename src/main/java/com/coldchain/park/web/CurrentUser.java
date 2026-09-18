package com.coldchain.park.web;

import com.coldchain.park.domain.UserAccount;

/** 请求线程内的当前登录账号（由 AuthFilter 写入） */
public final class CurrentUser {

    private static final ThreadLocal<UserAccount> HOLDER = new ThreadLocal<>();

    private CurrentUser() {}

    public static void set(UserAccount user) { HOLDER.set(user); }
    public static UserAccount get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }
}
