package com.logpark.coldchain.service;

import com.logpark.coldchain.repo.Db;
import com.logpark.coldchain.support.ApiException;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 入场窗口生成。约束：
 * 1) 月台容量：同一月台同一时段（WINDOW_MINUTES）只能接一辆车；
 * 2) 质检班次：窗口必须落在质检班次内，且班内预约数不超过当班人数；
 * 3) 客户优先级：P0/P1/P2 + 加急 + 承运商信用分共同决定 priorityScore（加急可插队）；
 * 4) 夜间排队：夜间时段只能分配 night_open 月台；
 * 5) 园区限电：限电时只能分配 backup_power 自备发电月台。
 */
@Service
public class SchedulingService {

    private final Db db;

    public SchedulingService(Db db) {
        this.db = db;
    }

    public record WindowPlan(LocalDateTime start, LocalDateTime end, long dockId,
                             String dockCode, int priorityScore, List<String> notes) {}

    private record DockRow(long id, String code, boolean nightOpen, boolean backupPower) {}

    /**
     * 计算可行窗口。
     * @param excludeTripId 改约时排除本车自身占用，传 null 表示新预约
     */
    public WindowPlan plan(LocalDateTime requestedAt, String tempZone, boolean urgent,
                           int estimatedPieces, int ownerPriorityRank, int carrierCredit,
                           Long excludeTripId) {
        int windowMin = db.settingInt("WINDOW_MINUTES", 30);
        boolean powerLimited = db.settingBool("POWER_LIMIT", false);

        if (requestedAt.isBefore(db.now().minusMinutes(5))) {
            throw ApiException.badRequest("预约时间不能早于当前时间");
        }

        int priorityScore = ownerPriorityRank * 100 + carrierCredit + (urgent ? 50 : 0);

        // 时间槽向上取整
        LocalDateTime slot = requestedAt
                .withMinute((requestedAt.getMinute() / windowMin) * windowMin)
                .withSecond(0).withNano(0);
        if (slot.isBefore(requestedAt)) {
            slot = slot.plusMinutes(windowMin);
        }

        List<DockRow> docks = loadDocks(tempZone);
        if (docks.isEmpty()) {
            throw ApiException.unprocessable("温区 " + tempZone + " 没有可用月台");
        }

        List<String> staticNotes = new ArrayList<>();
        if (powerLimited) staticNotes.add("园区限电中，仅分配自备发电月台");
        if (urgent) staticNotes.add("客户临时加急，已按最高优先级寻窗");

        LocalDateTime horizon = requestedAt.plusHours(36);
        while (!slot.isAfter(horizon)) {
            boolean night = isNight(slot);
            List<String> slotNotes = new ArrayList<>(staticNotes);
            if (night) slotNotes.add("夜间时段，仅开放夜间作业月台，车辆进入夜间排队序列");

            // 班次与当班人力（并发产能 = 当班人数，按同一时间窗计数）
            Integer headroom = qcHeadroom(slot, windowMin, excludeTripId);
            if (headroom == null || headroom <= 0) {
                slot = slot.plusMinutes(windowMin);
                continue;
            }

            for (DockRow d : docks) {
                if (night && !d.nightOpen) continue;
                if (powerLimited && !d.backupPower) continue;
                if (dockBusy(d.id, slot, slot.plusMinutes(windowMin), excludeTripId)) continue;
                LocalDateTime end = slot.plusMinutes(windowMin);
                slotNotes.add("分配月台 " + d.code);
                return new WindowPlan(slot, end, d.id, d.code, priorityScore, slotNotes);
            }
            slot = slot.plusMinutes(windowMin);
        }
        throw ApiException.unprocessable(
                "未来36小时内无满足月台容量/质检班次" +
                (powerLimited ? "/限电" : "") + "约束的入场窗口，请联系园区调度改期");
    }

    private List<DockRow> loadDocks(String tempZone) {
        return db.jdbc().queryForList(
                        "SELECT id, code, night_open, backup_power FROM dock " +
                        "WHERE active = TRUE AND temp_zone = ? ORDER BY code", tempZone)
                .stream()
                .map(m -> new DockRow(
                        ((Number) m.get("id")).longValue(),
                        (String) m.get("code"),
                        (Boolean) m.get("night_open"),
                        (Boolean) m.get("backup_power")))
                .sorted(Comparator.comparing(DockRow::code))
                .toList();
    }

    boolean isNight(LocalDateTime t) {
        int ns = db.settingInt("NIGHT_START_MINUTE", 1320);
        int ne = db.settingInt("NIGHT_END_MINUTE", 360);
        int m = t.getHour() * 60 + t.getMinute();
        if (ns > ne) { // 跨夜，如 22:00 - 06:00
            return m >= ns || m < ne;
        }
        return m >= ns && m < ne;
    }

    /**
     * 返回该时间窗内质检班次剩余并发人力；不在任何班次内返回 null。
     * 同一 30 分钟窗内在班车次不超过当班人数。
     */
    Integer qcHeadroom(LocalDateTime t, int windowMin, Long excludeTripId) {
        int dow = t.getDayOfWeek().getValue();
        List<Map<String, Object>> shifts = db.jdbc().queryForList(
                "SELECT day_of_week, start_minute, end_minute, headcount FROM qc_shift");

        LocalDateTime winStart = t;
        LocalDateTime winEnd = t.plusMinutes(windowMin);

        for (Map<String, Object> s : shifts) {
            int sDow = ((Number) s.get("day_of_week")).intValue();
            int sStart = ((Number) s.get("start_minute")).intValue();
            int sEnd = ((Number) s.get("end_minute")).intValue();
            int headcount = ((Number) s.get("headcount")).intValue();

            LocalDate anchor = t.toLocalDate()
                    .minusDays((t.getDayOfWeek().getValue() - sDow + 7) % 7);
            LocalDateTime intervalStart = anchor.atStartOfDay().plusMinutes(sStart);
            LocalDateTime intervalEnd = intervalStart.plusMinutes(sEnd - sStart);

            // 跨夜区间（end_minute>1440）自然延伸到次日凌晨
            boolean covered = !winStart.isBefore(intervalStart) && !winEnd.isAfter(intervalEnd);
            if (covered) {
                Integer used = db.jdbc().queryForObject("""
                        SELECT COUNT(*) FROM trip
                        WHERE status <> 'CANCELLED'
                          AND window_start < ? AND window_end > ?
                          AND (CAST(? AS bigint) IS NULL OR id <> CAST(? AS bigint))
                        """, Integer.class, Timestamp.valueOf(winEnd), Timestamp.valueOf(winStart),
                        excludeTripId, excludeTripId);
                return headcount - (used == null ? 0 : used);
            }
        }
        return null;
    }

    boolean dockBusy(long dockId, LocalDateTime start, LocalDateTime end, Long excludeTripId) {
        Integer n = db.jdbc().queryForObject("""
                SELECT COUNT(*) FROM trip
                WHERE dock_id = ? AND status <> 'CANCELLED'
                  AND window_start < ? AND window_end > ?
                  AND (CAST(? AS bigint) IS NULL OR id <> CAST(? AS bigint))
                """, Integer.class, dockId, Timestamp.valueOf(end), Timestamp.valueOf(start),
                excludeTripId, excludeTripId);
        return n != null && n > 0;
    }

    /** 判断月台此刻是否有在园车辆占用（闸口排队用）；车辆转入隔离区即视为释放月台。 */
    public boolean dockOccupiedNow(long dockId, Long excludeTripId) {
        Integer n = db.jdbc().queryForObject("""
                SELECT COUNT(*) FROM trip
                WHERE dock_id = ? AND status IN ('AT_GATE','QUEUED_AT_GATE','AT_DOCK','IN_QC')
                  AND (CAST(? AS bigint) IS NULL OR id <> CAST(? AS bigint))
                """, Integer.class, dockId, excludeTripId, excludeTripId);
        return n != null && n > 0;
    }

    /** 客户优先级档位 → 排序分值基数。 */
    public static int ownerRank(String level) {
        return switch (level) {
            case "P0" -> 3;
            case "P1" -> 2;
            default -> 1;
        };
    }
}
