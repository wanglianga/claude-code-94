package com.logpark.coldchain.web;

import com.logpark.coldchain.repo.Db;
import com.logpark.coldchain.support.ParkClock;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class CommonController {

    private final ParkClock clock;
    private final Db db;

    public CommonController(ParkClock clock, Db db) {
        this.clock = clock;
        this.db = db;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "UP");
        m.put("parkTime", clock.now().toString());
        m.put("powerLimit", db.settingBool("POWER_LIMIT", false));
        return m;
    }

    @GetMapping("/api/whoami")
    public Map<String, Object> whoami(Authentication auth) {
        Map<String, Object> user = db.jdbc().queryForMap(
                "SELECT username, display_name, role, carrier_id, owner_id FROM app_user WHERE username = ?",
                auth.getName());
        user.put("authorities", auth.getAuthorities().stream().map(String::valueOf).toList());
        return user;
    }

    @GetMapping("/api/clock")
    public Map<String, Object> parkClock() {
        return Map.of("parkTime", clock.now().toString());
    }
}
