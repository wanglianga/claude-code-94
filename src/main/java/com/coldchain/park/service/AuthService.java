package com.coldchain.park.service;

import com.coldchain.park.domain.UserAccount;
import com.coldchain.park.repo.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 极简 token 会话：登录换发 UUID token，过滤器校验后取回账号 */
@Service
public class AuthService {

    public static final String TOKEN_HEADER = "X-Auth-Token";

    private final UserRepository users;
    private final ConcurrentHashMap<String, Long> sessions = new ConcurrentHashMap<>();

    public AuthService(UserRepository users) {
        this.users = users;
    }

    public String login(String username, String password) {
        UserAccount u = users.findByUsername(username)
                .orElseThrow(() -> new ApiException(401, "账号不存在"));
        if (!u.isEnabled() || !passwordEquals(u, password)) {
            throw new ApiException(401, "账号已停用或密码错误");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, u.getId());
        return token;
    }

    private boolean passwordEquals(UserAccount u, String raw) {
        return u.getPassword() != null && u.getPassword().equals(raw);
    }

    public Optional<UserAccount> resolve(String token) {
        if (token == null) return Optional.empty();
        Long id = sessions.get(token);
        return id == null ? Optional.empty() : users.findById(id);
    }

    public void logout(String token) {
        if (token != null) sessions.remove(token);
    }

    /** 业务异常 -> 全局处理器转 HTTP 状态码 */
    public static class ApiException extends RuntimeException {
        private final int status;

        public ApiException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int getStatus() { return status; }
    }
}
