package com.coldchain.park.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** 演示账号（无密码体系，登录即换发简单 token） */
@Entity
@Table(name = "users")
public class UserAccount extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String username;

    private String password;
    private String displayName;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    /** 角色绑定的业务主体 id：承运商 / 货主（客服），其它角色为 null */
    private Long bindCarrierId;
    private Long bindCustomerId;

    private boolean enabled = true;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public Long getBindCarrierId() { return bindCarrierId; }
    public void setBindCarrierId(Long bindCarrierId) { this.bindCarrierId = bindCarrierId; }
    public Long getBindCustomerId() { return bindCustomerId; }
    public void setBindCustomerId(Long bindCustomerId) { this.bindCustomerId = bindCustomerId; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
