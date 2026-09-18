package com.coldchain.park.config;

import com.coldchain.park.domain.UserAccount;
import com.coldchain.park.service.AuthService;
import com.coldchain.park.web.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** token 鉴权：登录接口 / 健康检查 / 静态页面放行，其余 /api/** 必须携带有效 X-Auth-Token */
@Component
public class AuthFilter extends OncePerRequestFilter {

    private static final List<String> WHITE = List.of(
            "/api/auth/login",
            "/actuator/health",
            "/actuator/info",
            "/", "/index.html", "/app.js", "/styles.css", "/favicon.ico");

    private final AuthService auth;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuthFilter(AuthService auth) {
        this.auth = auth;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/")) return false;
        return true; // 非 /api 路径（静态页/actuator）不过滤
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        if (WHITE.contains(path)) {
            chain.doFilter(req, resp);
            return;
        }
        String token = req.getHeader(AuthService.TOKEN_HEADER);
        UserAccount user = auth.resolve(token).orElse(null);
        if (user == null) {
            resp.setStatus(401);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write(mapper.writeValueAsString(Map.of(
                    "status", 401, "error", "未登录或会话失效，请先 POST /api/auth/login")));
            return;
        }
        CurrentUser.set(user);
        try {
            chain.doFilter(req, resp);
        } finally {
            CurrentUser.clear();
        }
    }
}
