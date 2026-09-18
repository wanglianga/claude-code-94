package com.logpark.coldchain.service;

import com.logpark.coldchain.repo.Db;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 客户客服视角：把温控/封签/晚到等专业异常翻译成客户可理解的
 * “发生了什么 → 责任在谁 → 货损多少 → 预计放行时间”。
 */
@Service
public class CustomerService {

    private final Db db;

    public CustomerService(Db db) {
        this.db = db;
    }

    public Map<String, Object> explain(String code) {
        Map<String, Object> trip = db.getTripByCode(code);
        long tripId = ((Number) trip.get("id")).longValue();
        List<Map<String, Object>> incidents = db.incidentsOfTrip(tripId);
        List<Map<String, Object>> lines = db.jdbc().queryForList("""
                SELECT tc.*, o.name AS owner_name FROM trip_cargo tc
                JOIN cargo_owner o ON o.id = tc.owner_id WHERE tc.trip_id = ?
                """, tripId);

        List<Map<String, Object>> plainIncidents = new ArrayList<>();
        for (Map<String, Object> inc : incidents) {
            String type = (String) inc.get("type");
            plainIncidents.add(Map.of(
                    "type", type,
                    "customerFacing", customerText(type),
                    "severity", inc.get("severity"),
                    "status", inc.get("status"),
                    "responsibility", humanParty((String) inc.get("responsibility"))
            ));
        }

        // 货损摘要
        int damaged = lines.stream().mapToInt(c -> ((Number) c.get("damaged_pieces")).intValue()).sum();
        int thawed = lines.stream().mapToInt(c -> ((Number) c.get("sample_thawed")).intValue()).sum();
        String disposition = (String) trip.get("disposition");
        String liability = (String) trip.get("liability_party");

        // 预计放行时间
        String eta = estimateRelease(trip, incidents);
        String stageCn = stageText((String) trip.get("current_stage"), (String) trip.get("status"));

        String summary = String.format(
                "您的货物当前在【%s】。%s预计放行时间：%s。",
                stageCn, buildDamageSentence(disposition, damaged, thawed, liability), eta);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("tripCode", code);
        result.put("status", trip.get("status"));
        result.put("currentStage", stageCn);
        result.put("plate", trip.get("vehicle_plate"));
        result.put("mixedLoading", trip.get("mixed_loading"));
        result.put("incidents", plainIncidents);
        result.put("damagedPieces", damaged);
        result.put("thawedSamples", thawed);
        result.put("disposition", disposition == null ? "待定" : dispositionText(disposition));
        result.put("liability", liability == null ? "责任判定中" : humanParty(liability));
        result.put("penalty", trip.get("penalty_amount"));
        result.put("compensation", trip.get("compensation_amount"));
        result.put("estimatedRelease", eta);
        result.put("customerSummary", summary);
        return result;
    }

    private String buildDamageSentence(String disposition, int damaged, int thawed, String liability) {
        if (disposition == null && damaged == 0 && thawed == 0) {
            return "目前没有发现货损。";
        }
        StringBuilder sb = new StringBuilder();
        if (thawed > 0) sb.append("质检抽样发现化冻 ").append(thawed).append(" 件，");
        if (damaged > 0) sb.append("卸货发现破损 ").append(damaged).append(" 件，");
        if (disposition != null) sb.append("处置结果：").append(dispositionText(disposition)).append("。");
        if (liability != null) sb.append("责任判定：").append(humanParty(liability)).append("，将据此赔付/扣罚。");
        return sb.toString();
    }

    private String estimateRelease(Map<String, Object> trip, List<Map<String, Object>> incidents) {
        String status = (String) trip.get("status");
        LocalDateTime now = db.now();
        long openHigh = incidents.stream()
                .filter(i -> "OPEN".equals(i.get("status")) && "HIGH".equals(i.get("severity"))).count();
        return switch (status) {
            case "DONE" -> "已完成结算并放行" + (trip.get("signed_at") != null ? "，客户已签收" : "，等待客户签收");
            case "SETTLING" -> "责任与金额核算中，预计 " + now.plusHours(1).getHour() + " 时前完成放行结算";
            case "QUARANTINED" -> "货物隔离待处置，存在 " + openHigh + " 个高优先级异常，预计 "
                    + now.toLocalDate() + " 当日给出放行/降级/拒收结论";
            case "IN_QC" -> "质检抽样中，正常约 30~60 分钟出结论";
            case "AT_DOCK" -> "月台卸货中，正常约 30~60 分钟完成卸货质检";
            case "QUEUED_AT_GATE" -> "车辆在闸口排队（月台拥堵/晚到/异常待处理），调度处置后优先放行高优先级客户";
            case "AT_GATE" -> "车辆已入园，等待月台接车";
            default -> "已预约，窗口 " + ((Timestamp) trip.get("window_start")).toLocalDateTime();
        };
    }

    static String humanParty(String p) {
        if (p == null) return "待定";
        return switch (p) {
            case "CARRIER" -> "承运商（运输方）";
            case "PARK" -> "园区（月台/调度）";
            case "OWNER" -> "货主/客户侧";
            case "SHARED" -> "多方共同承担";
            case "PENDING" -> "责任判定中";
            default -> p;
        };
    }

    static String customerText(String type) {
        return switch (type) {
            case "LATE_ARRIVAL" -> "车辆晚于预约时间到场，可能延迟卸货";
            case "TEMP_GAP" -> "运输途中冷藏设备有一段时间没有回传温度，温度链不完整，需核查";
            case "SEAL_ABNORMAL" -> "车厢电子封签与发货时不一致，途中有被开启风险，已在闸口拦下核验";
            case "WAREHOUSE_CHANGE" -> "您临时通知更改入库仓库，月台安排随之调整";
            case "DOCK_CONGESTION" -> "园区月台繁忙，车辆短时排队等待";
            case "THAW_FOUND" -> "质检发现部分商品化冻，已隔离，等待放行/降级/拒收决定";
            case "DOCS_EXPIRED" -> "司机证件过期，车辆暂不能入场";
            case "POWER_LIMIT" -> "园区临时限电，仅启用自备发电月台，入场时间可能调整";
            case "URGENT_INSERT" -> "您申请的加急订单，已优先安排入场窗口";
            case "TEMP_EXCURSION" -> "运输途中车厢温度超过商品安全温度，存在化冻风险";
            default -> type;
        };
    }

    static String dispositionText(String d) {
        return switch (d) {
            case "RELEASED" -> "正常放行入库";
            case "QUARANTINED" -> "隔离待查";
            case "DOWNGRADED" -> "降级入库（折价接收）";
            case "REJECTED" -> "拒收退回";
            default -> d;
        };
    }

    static String stageText(String stage, String status) {
        if (status == null) return "未知";
        return switch (status) {
            case "BOOKED" -> "已预约，未到场";
            case "AT_GATE" -> "闸口已核验，待月台";
            case "QUEUED_AT_GATE" -> "闸口排队/等待处理";
            case "AT_DOCK" -> "月台卸货";
            case "IN_QC" -> "质检抽样";
            case "QUARANTINED" -> "隔离区，待处置";
            case "AWAIT_DISPOSITION" -> "质检完成，待处置决定";
            case "SETTLING" -> "责任结算";
            case "DONE" -> "已完成";
            case "CANCELLED" -> "已取消";
            default -> stage;
        };
    }
}
