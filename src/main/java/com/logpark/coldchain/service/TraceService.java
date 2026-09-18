package com.logpark.coldchain.service;

import com.logpark.coldchain.repo.Db;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 客户质量追溯：汇总某车次（某批冷链商品）的
 * 温控设备原始数据、温度断点、开门前温度、卸货时长、质检抽样、
 * 处置、责任结算、签收结果，供客户完整追溯运输质量。
 */
@Service
public class TraceService {

    private final Db db;

    public TraceService(Db db) {
        this.db = db;
    }

    public Map<String, Object> trace(String code) {
        Map<String, Object> trip = db.getTripByCode(code);
        long tripId = ((Number) trip.get("id")).longValue();

        List<Map<String, Object>> readings = db.jdbc().queryForList(
                "SELECT device_no, recorded_at, celsius, gap_after FROM temp_reading " +
                "WHERE trip_id = ? ORDER BY recorded_at", tripId);
        List<Map<String, Object>> lines = db.jdbc().queryForList("""
                SELECT tc.cargo_type, tc.temp_zone, tc.max_temp_celsius, tc.pieces,
                       tc.door_open_temp, tc.damaged_pieces, tc.sample_total, tc.sample_thawed,
                       tc.qc_result, tc.disposition, tc.disposition_note, tc.target_warehouse,
                       o.code AS owner_code, o.name AS owner_name
                FROM trip_cargo tc JOIN cargo_owner o ON o.id = tc.owner_id
                WHERE tc.trip_id = ?
                """, tripId);
        List<Map<String, Object>> timeline = db.jdbc().queryForList(
                "SELECT event_type, actor_role, actor_name, detail, created_at " +
                "FROM trip_event WHERE trip_id = ? ORDER BY created_at, id", tripId);
        List<Map<String, Object>> incidents = db.incidentsOfTrip(tripId);

        long gapCount = readings.stream().filter(r -> Boolean.TRUE.equals(r.get("gap_after"))).count();
        Long unloadMinutes = null;
        Timestamp s = (Timestamp) trip.get("unloading_started_at");
        Timestamp f = (Timestamp) trip.get("unloading_finished_at");
        if (s != null && f != null) {
            unloadMinutes = Duration.between(s.toLocalDateTime(), f.toLocalDateTime()).toMinutes();
        }

        // LinkedHashMap 允许 value 为 null（车次早期处置/责任/签收字段尚未产生）
        Map<String, Object> tripView = new java.util.LinkedHashMap<>();
        tripView.put("code", trip.get("code"));
        tripView.put("plate", trip.get("vehicle_plate"));
        tripView.put("tempDeviceNo", trip.get("temp_device_no"));
        tripView.put("eSealNo", trip.get("e_seal_no"));
        tripView.put("windowStart", trip.get("window_start"));
        tripView.put("arrivedAt", trip.get("arrived_at"));
        tripView.put("status", trip.get("status"));
        tripView.put("disposition", trip.get("disposition"));
        tripView.put("liabilityParty", trip.get("liability_party"));
        tripView.put("penaltyAmount", trip.get("penalty_amount"));
        tripView.put("compensationAmount", trip.get("compensation_amount"));
        tripView.put("unloadMinutes", unloadMinutes);
        tripView.put("unloadPhotos", trip.get("unload_photos"));
        tripView.put("signoffResult", trip.get("signoff_result"));
        tripView.put("signedPieces", trip.get("signed_pieces"));
        tripView.put("signedBy", trip.get("signed_by"));
        tripView.put("signedAt", trip.get("signed_at"));

        Map<String, Object> tempView = new java.util.LinkedHashMap<>();
        tempView.put("deviceNo", trip.get("temp_device_no"));
        tempView.put("readingCount", readings.size());
        tempView.put("gapCount", gapCount);
        tempView.put("rawReadings", readings);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("trip", tripView);
        result.put("cargoLines", lines);
        result.put("temperature", tempView);
        result.put("incidents", incidents);
        result.put("timeline", timeline);
        return result;
    }

    /** 按货主查询其全部批次（车次）运输质量概览。 */
    public List<Map<String, Object>> byOwner(long ownerId) {
        return db.jdbc().queryForList("""
                SELECT DISTINCT t.code, t.status, t.window_start, t.disposition, t.liability_party,
                       t.penalty_amount, t.compensation_amount, t.signoff_result
                FROM trip t JOIN trip_cargo tc ON tc.trip_id = t.id
                WHERE tc.owner_id = ?
                ORDER BY t.window_start DESC
                """, ownerId);
    }
}
