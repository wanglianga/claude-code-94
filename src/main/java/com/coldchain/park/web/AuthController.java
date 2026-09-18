package com.coldchain.park.web;

import com.coldchain.park.domain.UserAccount;
import com.coldchain.park.domain.UserRole;
import com.coldchain.park.service.AuthService;
import com.coldchain.park.web.dto.Dtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Dtos.LoginReq req) {
        String token = auth.login(req.username(), req.password());
        UserAccount u = auth.resolve(token).orElseThrow();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", token);
        m.put("username", u.getUsername());
        m.put("displayName", u.getDisplayName());
        m.put("role", u.getRole().name());
        m.put("roleLabel", u.getRole().label);
        m.put("bindCarrierId", u.getBindCarrierId());
        m.put("bindCustomerId", u.getBindCustomerId());
        return m;
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        UserAccount u = CurrentUser.get();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", u.getUsername());
        m.put("displayName", u.getDisplayName());
        m.put("role", u.getRole().name());
        m.put("roleLabel", u.getRole().label);
        m.put("bindCarrierId", u.getBindCarrierId());
        m.put("bindCustomerId", u.getBindCustomerId());
        return m;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestHeader(value = AuthService.TOKEN_HEADER, required = false) String token) {
        auth.logout(token);
        return Map.of("ok", true);
    }

    /** 角色校验 */
    public static void requireAny(UserRole... roles) {
        UserAccount u = CurrentUser.get();
        for (UserRole r : roles) {
            if (u.getRole() == r || u.getRole() == UserRole.ADMIN) return;
        }
        throw new AuthService.ApiException(403, "当前角色[" + u.getRole().label
                + "]无权执行该操作，需要角色：" + java.util.Arrays.toString(roles));
    }

    public static String actor() {
        UserAccount u = CurrentUser.get();
        return u.getDisplayName() + "(" + u.getRole().label + ")";
    }
}
