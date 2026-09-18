package com.logpark.coldchain.web;

import com.logpark.coldchain.repo.Db;
import com.logpark.coldchain.service.AppointmentService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AppointmentController {

    private final AppointmentService appointments;
    private final Db db;

    public AppointmentController(AppointmentService appointments, Db db) {
        this.appointments = appointments;
        this.db = db;
    }

    /** 承运商提交预约（含多货主混装明细），系统生成入场窗口。 */
    @PostMapping("/appointments")
    public Map<String, Object> book(@RequestBody Map<String, Object> body, Principal principal) {
        return appointments.book(body, principal.getName());
    }

    @GetMapping("/appointments/{code}")
    public Map<String, Object> get(@PathVariable String code) {
        return appointments.tripView(code);
    }

    /** 车次完整时间线（五类角色在同一车次协同）。 */
    @GetMapping("/incidents/trips/{code}")
    public Map<String, Object> timeline(@PathVariable String code) {
        Map<String, Object> trip = db.getTripByCode(code);
        long tripId = ((Number) trip.get("id")).longValue();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("tripCode", code);
        result.put("status", trip.get("status"));
        result.put("currentStage", trip.get("current_stage") == null ? "BOOKED" : trip.get("current_stage"));
        result.put("incidents", db.incidentsOfTrip(tripId));
        result.put("timeline", db.jdbc().queryForList(
                "SELECT event_type, actor_role, actor_name, detail, created_at " +
                "FROM trip_event WHERE trip_id = ? ORDER BY created_at, id", tripId));
        return result;
    }

    /** 我的车次：承运商看本公司，其余角色看全部（看板/客服使用）。 */
    @GetMapping("/my/trips")
    public List<Map<String, Object>> myTrips(Principal principal) {
        Map<String, Object> user = db.jdbc().queryForMap(
                "SELECT role, carrier_id FROM app_user WHERE username = ?", principal.getName());
        if ("CARRIER".equals(user.get("role"))) {
            return db.jdbc().queryForList("""
                    SELECT code, vehicle_plate, status, current_stage, temp_zone, window_start,
                           window_end, urgent, disposition, liability_party
                    FROM trip WHERE carrier_id = ? ORDER BY id DESC
                    """, ((Number) user.get("carrier_id")).longValue());
        }
        return db.jdbc().queryForList("""
                SELECT code, vehicle_plate, status, current_stage, temp_zone, window_start,
                       window_end, urgent, disposition, liability_party
                FROM trip ORDER BY id DESC
                """);
    }
}
