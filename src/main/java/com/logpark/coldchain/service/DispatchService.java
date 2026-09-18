package com.logpark.coldchain.service;

import com.logpark.coldchain.repo.Db;
import com.logpark.coldchain.support.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 园区调度协同动作：异常闭环定责、货主临时改仓、园区限电开关、排队队列。
 * 所有动作进入车次共享时间线，承运商/调度/质检/客服/结算可见同一车次进展。
 */
@Service
public class DispatchService {

    private final Db db;
    private final GateService gate;
    private final AppointmentService appointments;

    public DispatchService(Db db, GateService gate, AppointmentService appointments) {
        this.db = db;
        this.gate = gate;
        this.appointments = appointments;
    }

    /** 调度/质检对异常进行处置闭环并判定责任。 */
    @Transactional
    public Map<String, Object> resolveIncident(String code, long incidentId, String responsibility,
                                               String resolution, String actor) {
        long tripId = db.tripId(code);
        List<Map<String, Object>> rows = db.jdbc().queryForList(
                "SELECT * FROM incident WHERE id = ? AND trip_id = ?", incidentId, tripId);
        if (rows.isEmpty()) {
            throw ApiException.notFound("异常不属于本车次或不存在: " + incidentId);
        }
        if (!List.of("CARRIER", "PARK", "OWNER").contains(responsibility)) {
            throw ApiException.badRequest("责任方必须为 CARRIER/PARK/OWNER");
        }
        db.jdbc().update("""
                UPDATE incident SET status = 'RESOLVED', responsibility = ?, resolution = ?,
                                   resolved_by = ?, resolved_at = ?
                WHERE id = ?
                """, responsibility, resolution, actor, Timestamp.valueOf(db.now()), incidentId);
        db.event(tripId, "INCIDENT_RESOLVED", "DISPATCH", actor,
                "异常#" + incidentId + "[" + rows.get(0).get("type") + "]已闭环，责任=" + responsibility
                        + "，处理意见=" + resolution);
        return rows.get(0);
    }

    /** 货主临时改仓：调度更新该货主在本车的目标仓并登记异常（可能引起改约）。 */
    @Transactional
    public Map<String, Object> changeWarehouse(String code, long ownerId, String newWarehouse, String actor) {
        long tripId = db.tripId(code);
        int n = db.jdbc().update(
                "UPDATE trip_cargo SET target_warehouse = ? WHERE trip_id = ? AND owner_id = ?",
                newWarehouse, tripId, ownerId);
        if (n == 0) {
            throw ApiException.badRequest("本车次不含该货主货物");
        }
        db.raiseIncident(tripId, "WAREHOUSE_CHANGE", "MED",
                "货主临时改仓：货主#" + ownerId + " 货物改送 " + newWarehouse + "，可能影响月台安排",
                "OWNER", "DISPATCH");
        db.event(tripId, "WAREHOUSE_CHANGED", "DISPATCH", actor,
                "货主#" + ownerId + " 目标仓变更为 " + newWarehouse + "，已同步质检/客服/结算");
        return appointments.tripView(code);
    }

    /** 园区限电开关：开启后仅自备发电月台可排窗，已排到无备电月台的车需改约。 */
    @Transactional
    public Map<String, Object> setPowerLimit(boolean limited, String actor) {
        db.setSetting("POWER_LIMIT", String.valueOf(limited));
        if (limited) {
            db.jdbc().queryForList("""
                    SELECT t.id, t.code FROM trip t JOIN dock d ON d.id = t.dock_id
                    WHERE t.status IN ('BOOKED','AT_GATE','QUEUED_AT_GATE') AND d.backup_power = FALSE
                    """).forEach(r -> {
                long id = ((Number) r.get("id")).longValue();
                boolean exists = db.jdbc().queryForObject(
                        "SELECT COUNT(*) FROM incident WHERE trip_id=? AND type='POWER_LIMIT' AND status='OPEN'",
                        Integer.class, id) > 0;
                if (!exists) {
                    db.raiseIncident(id, "POWER_LIMIT", "HIGH",
                            "园区限电，当前月台无自备发电，需调度改约至备电月台", "PARK", "DISPATCH");
                    db.event(id, "POWER_LIMIT_ON", "DISPATCH", actor, "园区限电启动，本车需改约至备电月台");
                }
            });
        }
        return Map.of("powerLimit", limited, "at", db.now().toString());
    }

    @Transactional
    public Map<String, Object> rebook(String code, String requestedAt, Boolean urgent, String actor) {
        return appointments.rebook(code, LocalDateTime.parse(requestedAt), urgent, actor);
    }

    @Transactional
    public Map<String, Object> admitFromQueue(String code, String actor) {
        return gate.admitFromQueue(code, actor);
    }

    public List<Map<String, Object>> queue() {
        return gate.queue();
    }
}
