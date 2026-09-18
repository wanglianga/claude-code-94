package com.logpark.coldchain.service;

import com.logpark.coldchain.repo.Db;
import com.logpark.coldchain.support.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** 客户签收：登记签收结果与签收件数，作为追溯闭环。 */
@Service
public class SignoffService {

    private final Db db;

    public SignoffService(Db db) {
        this.db = db;
    }

    @Transactional
    public Map<String, Object> signoff(String code, Map<String, Object> body, String actor) {
        Map<String, Object> trip = db.getTripByCode(code);
        long tripId = ((Number) trip.get("id")).longValue();
        if (!"DONE".equals(trip.get("status"))) {
            throw ApiException.conflict("仅结算完成（DONE）车次可签收，当前=" + trip.get("status"));
        }
        String result = String.valueOf(require(body, "result")).toUpperCase();
        if (!List.of("SIGNED", "PARTIAL", "REJECTED").contains(result)) {
            throw ApiException.badRequest("签收结果必须为 SIGNED/PARTIAL/REJECTED");
        }
        int signedPieces = ((Number) require(body, "signedPieces")).intValue();
        String signedBy = String.valueOf(require(body, "signedBy"));
        String note = body.get("note") == null ? null : String.valueOf(body.get("note"));

        db.updateTrip(tripId, "signoff_result = ?, signed_pieces = ?, signed_by = ?, signoff_note = ?, signed_at = ?",
                result, signedPieces, signedBy, note, Timestamp.valueOf(db.now()));
        db.event(tripId, "SIGNED_OFF", "CS", actor,
                "客户签收：结果=" + result + "，签收件数=" + signedPieces + "，签收人=" + signedBy
                        + (note == null ? "" : "，备注=" + note));
        return db.getTripByCode(code);
    }

    private static Object require(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null || (v instanceof String s && s.isBlank())) {
            throw ApiException.badRequest("缺少必填字段: " + key);
        }
        return v;
    }
}
