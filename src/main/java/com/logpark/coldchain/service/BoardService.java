package com.logpark.coldchain.service;

import com.logpark.coldchain.repo.Db;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 调度看板：让调度一眼看到每辆车卡在 闸口/月台/质检/隔离/结算 哪个环节，
 * 并汇总各环节在园车辆、闸口排队、高优先级加急与未闭环异常。
 */
@Service
public class BoardService {

    private final Db db;

    public BoardService(Db db) {
        this.db = db;
    }

    public Map<String, Object> board() {
        List<Map<String, Object>> vehicles = db.jdbc().queryForList("""
                SELECT t.code, t.vehicle_plate, t.status, t.current_stage, t.temp_zone, t.urgent,
                       t.priority_score, t.queued, t.late, t.window_start, t.window_end,
                       t.stage_entered_at, d.code AS dock_code, c.name AS carrier_name,
                       (SELECT COUNT(*) FROM incident i WHERE i.trip_id = t.id AND i.status='OPEN') AS open_incidents,
                       (SELECT STRING_AGG(i.type, ',') FROM incident i
                          WHERE i.trip_id = t.id AND i.status='OPEN') AS incident_types
                FROM trip t
                LEFT JOIN dock d ON d.id = t.dock_id
                LEFT JOIN carrier c ON c.id = t.carrier_id
                WHERE t.status NOT IN ('DONE','CANCELLED')
                ORDER BY t.urgent DESC, t.priority_score DESC,
                         CASE t.current_stage
                            WHEN 'GATE' THEN 1 WHEN 'DOCK' THEN 2
                            WHEN 'QC' THEN 3 WHEN 'QUARANTINE' THEN 4 ELSE 5 END,
                         t.window_start
                """);
        for (Map<String, Object> v : vehicles) {
            v.put("stageCn", CustomerService.stageText(
                    (String) v.get("current_stage"), (String) v.get("status")));
        }

        Map<String, Integer> byStage = new java.util.LinkedHashMap<>();
        for (String s : List.of("GATE", "DOCK", "QC", "QUARANTINE", "SETTLE")) {
            byStage.put(s, 0);
        }
        for (Map<String, Object> v : vehicles) {
            String st = (String) v.get("current_stage");
            if (st != null && byStage.containsKey(st)) {
                byStage.put(st, byStage.get(st) + 1);
            }
        }

        int queued = db.jdbc().queryForObject(
                "SELECT COUNT(*) FROM trip WHERE status='QUEUED_AT_GATE'", Integer.class);
        int urgent = db.jdbc().queryForObject(
                "SELECT COUNT(*) FROM trip WHERE urgent=TRUE AND status NOT IN ('DONE','CANCELLED')", Integer.class);
        int openIncidents = db.jdbc().queryForObject(
                "SELECT COUNT(*) FROM incident WHERE status='OPEN'", Integer.class);
        boolean powerLimit = db.settingBool("POWER_LIMIT", false);

        return Map.of(
                "vehicles", vehicles,
                "countByStage", byStage,
                "queuedAtGate", queued,
                "urgentInPark", urgent,
                "openIncidents", openIncidents,
                "powerLimit", powerLimit,
                "parkTime", db.now().toString()
        );
    }

    /** 全量异常处理台：跨车次查看未闭环异常。 */
    public List<Map<String, Object>> openIncidents() {
        return db.jdbc().queryForList("""
                SELECT i.id, i.trip_id, t.code AS trip_code, i.type, i.severity, i.description,
                       i.responsibility, i.created_at
                FROM incident i JOIN trip t ON t.id = i.trip_id
                WHERE i.status = 'OPEN'
                ORDER BY CASE i.severity WHEN 'HIGH' THEN 1 WHEN 'MED' THEN 2 ELSE 3 END, i.created_at
                """);
    }
}
