package com.coldchain.park.service;

import com.coldchain.park.config.ParkProps;
import com.coldchain.park.domain.Appointment;
import com.coldchain.park.domain.ApptStatus;
import com.coldchain.park.domain.Dock;
import com.coldchain.park.domain.TempZone;
import com.coldchain.park.repo.AppointmentRepository;
import com.coldchain.park.repo.DockRepository;
import com.coldchain.park.repo.ParkStateRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 入场窗口排程：
 * 约束 = 月台容量（同月台窗口不重叠）+ 质检班次容量（白/夜班不同）
 *      + 温区匹配 + 园区限电（可用月台减半）+ 夜间排队（夜班质检容量低）
 * 优先级 = 加急/VIP 优先取最早空档，承运商服务分作为同档次序。
 */
@Service
public class SchedulingService {

    /** 占用月台的在途状态（已关单/结算的车次释放资源） */
    private static final List<ApptStatus> ACTIVE = List.of(
            ApptStatus.SCHEDULED, ApptStatus.QUEUED_NIGHT, ApptStatus.LICENSE_HOLD,
            ApptStatus.AT_GATE, ApptStatus.IN_PARK, ApptStatus.AT_DOCK);

    private final ParkProps props;
    private final DockRepository docks;
    private final AppointmentRepository appts;
    private final ParkStateRepository parkState;

    public SchedulingService(ParkProps props, DockRepository docks,
                             AppointmentRepository appts, ParkStateRepository parkState) {
        this.props = props;
        this.docks = docks;
        this.appts = appts;
        this.parkState = parkState;
    }

    public boolean isNight(LocalDateTime t) {
        int h = t.getHour();
        return h >= props.getNightStartHour() || h < props.getNightEndHour();
    }

    public int qcCapacityAt(LocalDateTime t) {
        return isNight(t) ? props.getQcNightCapacity() : props.getQcDayCapacity();
    }

    public boolean isPowerShedding() {
        return parkState.findById(1L).map(p -> p.isPowerShedding()).orElse(false);
    }

    /** 限电时可用月台减半（向下取整，至少保留 1 个月台） */
    public List<Dock> usableDocks() {
        List<Dock> all = docks.findByEnabledTrue();
        if (!isPowerShedding()) return all;
        int keep = Math.max(1, all.size() / 2);
        return all.subList(0, keep);
    }

    public record Slot(LocalDateTime start, LocalDateTime end, Dock dock,
                       boolean night, int qcCapacity, List<String> notes) {}

    /**
     * 为车次寻找最早可行窗口。
     * @param urgent 加急车辆可从"当前时刻"起寻找（提前插单），普通车辆从期望时间起排队
     */
    public Slot findSlot(Appointment a, LocalDateTime desired) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime searchFrom = desired.truncatedTo(ChronoUnit.MINUTES);
        if (a.isUrgent()) {
            searchFrom = now; // 临时加急：立刻找最早空档插单
        } else if (searchFrom.isBefore(now)) {
            searchFrom = now;
        }
        // 对齐到 15 分钟刻度
        int mod = searchFrom.getMinute() % 15;
        if (mod != 0) searchFrom = searchFrom.plusMinutes(15L - mod);

        int duration = props.getSlotMinutes();
        List<Dock> docksAvail = usableDocks();
        boolean shedding = isPowerShedding();
        List<String> notes = new ArrayList<>();
        if (shedding) notes.add("园区限电，可用月台减半");

        // 最多向后排 6 小时
        for (int step = 0; step <= 24 * 4; step++) {
            LocalDateTime start = searchFrom.plusMinutes(15L * step);
            LocalDateTime end = start.plusMinutes(duration);

            if (isNight(start)) {
                notes.add("窗口落在夜间排队时段，夜班质检容量 " + props.getQcNightCapacity() + " 车/批");
            }

            // 质检班次容量：质检预计发生在卸货窗口结束后的 45 分钟内
            LocalDateTime qcStart = end;
            LocalDateTime qcEnd = end.plusMinutes(45);
            int qcCap = qcCapacityAt(qcStart);
            int qcLoad = qcLoad(qcStart, qcEnd);
            if (qcLoad >= qcCap) {
                continue; // 该时段质检员排满，顺延
            }

            // 月台：温区精确匹配优先，通用月台其次
            List<Dock> ordered = orderedDocks(docksAvail, a.getRequiredZone());
            for (Dock d : ordered) {
                List<Appointment> clash = appts.findOverlapping(d.getId(), start, end, ACTIVE);
                if (clash.isEmpty()) {
                    long waitMin = ChronoUnit.MINUTES.between(desired.truncatedTo(ChronoUnit.MINUTES), start);
                    if (!a.isUrgent() && waitMin > 60) {
                        notes.add("月台拥堵，窗口较期望时间顺延 " + waitMin + " 分钟");
                    }
                    if (a.isUrgent()) notes.add("加急插单，按最高优先级安排");
                    return new Slot(start, end, d, isNight(start), qcCap, notes);
                }
            }
        }
        throw new AuthService.ApiException(409, "未来 6 小时内无可用月台/质检班次，请联系园区调度");
    }

    /** 某时段质检负荷：按计划窗口结束时刻估算 */
    private int qcLoad(LocalDateTime qcStart, LocalDateTime qcEnd) {
        int n = 0;
        for (Appointment x : appts.findAll()) {
            if (!ACTIVE.contains(x.getStatus()) || x.getWindowEnd() == null) continue;
            LocalDateTime s = x.getWindowEnd();
            LocalDateTime e = s.plusMinutes(45);
            if (s.isBefore(qcEnd) && e.isAfter(qcStart)) n++;
        }
        return n;
    }

    private List<Dock> orderedDocks(List<Dock> pool, TempZone need) {
        List<Dock> exact = new ArrayList<>();
        List<Dock> generic = new ArrayList<>();
        for (Dock d : pool) {
            if (d.getPreferredZone() == null) generic.add(d);
            else if (need != null && d.getPreferredZone() == need) exact.add(d);
        }
        List<Dock> out = new ArrayList<>(exact);
        out.addAll(generic);
        // 其它温区专用月台最后兜底
        pool.stream()
            .filter(d -> d.getPreferredZone() != null && (need == null || d.getPreferredZone() != need))
            .sorted(Comparator.comparing(Dock::getCode))
            .forEach(out::add);
        return out;
    }
}
