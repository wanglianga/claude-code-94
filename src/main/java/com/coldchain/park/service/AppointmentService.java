package com.coldchain.park.service;

import com.coldchain.park.config.ParkProps;
import com.coldchain.park.domain.Appointment;
import com.coldchain.park.domain.AppointmentCargo;
import com.coldchain.park.domain.ApptStatus;
import com.coldchain.park.domain.Carrier;
import com.coldchain.park.domain.Customer;
import com.coldchain.park.domain.Decision;
import com.coldchain.park.domain.Dock;
import com.coldchain.park.domain.Driver;
import com.coldchain.park.domain.ExceptionEvent;
import com.coldchain.park.domain.ExceptionStatus;
import com.coldchain.park.domain.ExceptionType;
import com.coldchain.park.domain.ParkState;
import com.coldchain.park.domain.QcResult;
import com.coldchain.park.domain.SignResult;
import com.coldchain.park.domain.Stage;
import com.coldchain.park.domain.TempZone;
import com.coldchain.park.domain.TemperatureReading;
import com.coldchain.park.repo.AppointmentRepository;
import com.coldchain.park.repo.CarrierRepository;
import com.coldchain.park.repo.CustomerRepository;
import com.coldchain.park.repo.DockRepository;
import com.coldchain.park.repo.DriverRepository;
import com.coldchain.park.repo.ParkStateRepository;
import com.coldchain.park.repo.TemperatureReadingRepository;
import com.coldchain.park.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 车次全流程编排：预约 -> 排窗 -> 闸口 -> 月台 -> 质检 -> 隔离/处置 -> 结算，五角色同一车次协同 */
@Service
public class AppointmentService {

    private static final DateTimeFormatter CODE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AppointmentRepository appts;
    private final CarrierRepository carriers;
    private final CustomerRepository customers;
    private final DriverRepository drivers;
    private final DockRepository dockRepo;
    private final ParkStateRepository parkStateRepo;
    private final TemperatureReadingRepository readings;
    private final SchedulingService scheduling;
    private final TemperatureService temperature;
    private final TimelineRecorder timeline;
    private final ParkProps props;

    public AppointmentService(AppointmentRepository appts, CarrierRepository carriers,
                              CustomerRepository customers, DriverRepository drivers,
                              DockRepository dockRepo, ParkStateRepository parkStateRepo,
                              TemperatureReadingRepository readings,
                              SchedulingService scheduling, TemperatureService temperature,
                              TimelineRecorder timeline, ParkProps props) {
        this.appts = appts;
        this.carriers = carriers;
        this.customers = customers;
        this.drivers = drivers;
        this.dockRepo = dockRepo;
        this.parkStateRepo = parkStateRepo;
        this.readings = readings;
        this.scheduling = scheduling;
        this.temperature = temperature;
        this.timeline = timeline;
        this.props = props;
    }

    // ================= 预约提交 =================

    @Transactional
    public Appointment create(Dtos.CreateApptReq req) {
        Carrier carrier = carriers.findById(req.carrierId())
                .orElseThrow(() -> new AuthService.ApiException(404, "承运商不存在"));
        Driver driver = drivers.findById(req.driverId())
                .orElseThrow(() -> new AuthService.ApiException(404, "司机不存在"));
        if (!driver.getCarrier().getId().equals(carrier.getId())) {
            throw new AuthService.ApiException(400, "司机不属于该承运商");
        }
        if (req.lines() == null || req.lines().isEmpty()) {
            throw new AuthService.ApiException(400, "至少填写一行货物（支持多货主混装）");
        }

        Appointment a = new Appointment();
        a.setCarrier(carrier);
        a.setDriver(driver);
        a.setPlateNo(req.plateNo().trim().toUpperCase());
        a.setDeviceNo(req.deviceNo());
        a.setSealNo(req.sealNo());
        a.setCargoTypeDesc(req.cargoTypeDesc());
        a.setRequestedTime(req.requestedTime());
        a.setUrgent(Boolean.TRUE.equals(req.urgent()));

        TempZone coldest = null;
        int totalPieces = 0;
        for (Dtos.CargoLineReq line : req.lines()) {
            Customer c = customers.findById(line.customerId())
                    .orElseThrow(() -> new AuthService.ApiException(404, "货主不存在: " + line.customerId()));
            AppointmentCargo l = new AppointmentCargo();
            l.setAppointment(a);
            l.setCustomer(c);
            l.setGoodsName(line.goodsName());
            l.setZone(line.zone());
            l.setPieces(line.pieces() == null ? 0 : line.pieces());
            l.setTargetWarehouse(line.targetWarehouse());
            a.getCargoLines().add(l);
            totalPieces += l.getPieces();
            coldest = coldest == null ? line.zone() : coldest.coldest(line.zone());
        }
        a.setRequiredZone(coldest);
        a.setEstimatedPieces(req.estimatedPieces() != null ? req.estimatedPieces() : totalPieces);
        a.setCode(generateCode());
        appts.save(a);

        timeline.record(a, "承运商 " + carrier.getName(), "提交预约",
                "车牌 " + a.getPlateNo() + "，设备 " + a.getDeviceNo() + "，封签 " + a.getSealNo()
                        + "，温区 " + coldest.label + "，" + a.getCargoLines().size() + " 个货主混装，共 "
                        + totalPieces + " 件，期望到园 " + req.requestedTime(), "CARRIER");

        // 司机证件校验
        String expired = driver.expiredDocument(LocalDate.now());
        if (!expired.isEmpty()) {
            a.setStatus(ApptStatus.LICENSE_HOLD);
            raise(a, ExceptionType.LICENSE_EXPIRED, expired + "，须更换有效司机后才可排窗入园", "系统校验");
            timeline.record(a, "系统", "证件校验拦截", expired, "SYSTEM");
            appts.save(a);
            return a;
        }
        scheduleWindow(a, a.getRequestedTime());
        return a;
    }

    private String generateCode() {
        String prefix = "APT-" + LocalDate.now().format(CODE_FMT) + "-";
        long n = appts.countByCodeStartingWith(prefix) + 1;
        return prefix + String.format("%04d", n);
    }

    // ================= 排窗 =================

    @Transactional
    public Appointment scheduleWindow(Appointment a, LocalDateTime desired) {
        SchedulingService.Slot slot = scheduling.findSlot(a, desired);
        a.setWindowStart(slot.start());
        a.setWindowEnd(slot.end());
        a.setAssignedDock(slot.dock());
        a.setStatus(ApptStatus.SCHEDULED);
        String note = String.format("月台 %s（%s），质检班次容量 %d 车/批",
                slot.dock().getCode(),
                slot.dock().getPreferredZone() == null ? "通用温区" : slot.dock().getPreferredZone().label,
                slot.qcCapacity());
        a.setScheduleNote(note + (slot.notes().isEmpty() ? "" : "；" + String.join("；", slot.notes())));
        timeline.record(a, "系统排程", "生成入场窗口",
                "入场窗口 " + slot.start() + " ~ " + slot.end() + "，" + a.getScheduleNote(), "SYSTEM");

        long waitMin = java.time.Duration.between(desired, slot.start()).toMinutes();
        if (waitMin > 60 && !a.isUrgent()) {
            raise(a, ExceptionType.DOCK_CONGESTION,
                    "月台拥堵：实际窗口较期望时间顺延 " + waitMin + " 分钟", "系统排程");
        }
        if (slot.night()) {
            raise(a, ExceptionType.NIGHT_QUEUE,
                    "窗口/到园落在夜间时段（" + props.getNightStartHour() + ":00-次日"
                            + props.getNightEndHour() + ":00），夜班质检与月台人力有限，到园后进入夜间排队",
                    "系统排程");
        }
        if (a.getCarrier().getServiceScore() < 60 && !a.isUrgent()) {
            raise(a, ExceptionType.DOCK_CONGESTION,
                    "承运商当前服务分 " + a.getCarrier().getServiceScore()
                            + "（低于60），后续预约排队优先级已下调", "系统排程");
        }
        appts.save(a);
        return a;
    }

    @Transactional
    public Appointment replaceDriver(Long id, Long driverId, String actor) {
        Appointment a = must(id);
        Driver d = drivers.findById(driverId).orElseThrow(() -> new AuthService.ApiException(404, "司机不存在"));
        if (!d.getCarrier().getId().equals(a.getCarrier().getId())) {
            throw new AuthService.ApiException(400, "替补司机不属于该承运商");
        }
        String expired = d.expiredDocument(LocalDate.now());
        if (!expired.isEmpty()) {
            throw new AuthService.ApiException(409, "替补司机证件仍异常：" + expired);
        }
        a.setDriver(d);
        closeOne(a, ExceptionType.LICENSE_EXPIRED, "已更换为有效司机 " + d.getName(), actor);
        timeline.record(a, actor, "更换司机", "新司机 " + d.getName() + " 证件核验有效", "DISPATCH");
        scheduleWindow(a, LocalDateTime.now());
        return a;
    }

    @Transactional
    public Appointment markUrgent(Long id, String actor) {
        Appointment a = must(id);
        if (a.isUrgent()) throw new AuthService.ApiException(400, "该车次已是加急");
        if (!List.of(ApptStatus.SCHEDULED, ApptStatus.LICENSE_HOLD, ApptStatus.PENDING).contains(a.getStatus())) {
            throw new AuthService.ApiException(409, "车辆已进入现场环节，无法再加急改约");
        }
        a.setUrgent(true);
        timeline.record(a, actor, "客户临时加急", "货主要求优先安排，按最高优先级重新排窗", "CS");
        scheduleWindow(a, LocalDateTime.now());
        return a;
    }

    @Transactional
    public Appointment reschedule(Long id, LocalDateTime fromTime, String actor) {
        Appointment a = must(id);
        if (!List.of(ApptStatus.SCHEDULED, ApptStatus.QUEUED_NIGHT).contains(a.getStatus())) {
            throw new AuthService.ApiException(409, "当前状态不允许改约：" + a.getStatus().label);
        }
        timeline.record(a, actor, "调度改约", "原窗口 " + a.getWindowStart() + "，重新排窗", "DISPATCH");
        scheduleWindow(a, fromTime == null ? LocalDateTime.now() : fromTime);
        return a;
    }

    // ================= 闸口核验 =================

    @Transactional
    public Appointment gateCheck(Long id, Dtos.GateCheckReq req) {
        Appointment a = must(id);
        if (a.getStatus() == ApptStatus.LICENSE_HOLD) {
            throw new AuthService.ApiException(409, "司机证件异常未处理，闸口不予放行，请先更换司机");
        }
        if (a.getStatus() == ApptStatus.GATE_REJECTED) {
            throw new AuthService.ApiException(409, "车辆已被闸口拦截，须调度复核后人工放行");
        }
        LocalDateTime now = req.arrivalTime() == null ? LocalDateTime.now() : req.arrivalTime();
        a.setGateArriveTime(now);
        a.setGateSealChecked(req.sealChecked());

        boolean plateOk = req.plateScan() != null
                && req.plateScan().trim().equalsIgnoreCase(a.getPlateNo());
        boolean sealOk = req.sealChecked() != null
                && req.sealChecked().trim().equalsIgnoreCase(a.getSealNo());
        a.setGateSealMatch(sealOk);

        if (!plateOk) {
            a.setStatus(ApptStatus.GATE_REJECTED);
            a.setGateNote("车牌不匹配：扫描 " + req.plateScan() + "，预约 " + a.getPlateNo());
            timeline.record(a, "闸口", "核验拦截", a.getGateNote(), "GATE");
            appts.save(a);
            return a;
        }
        if (!sealOk) {
            a.setStatus(ApptStatus.GATE_REJECTED);
            a.setGateNote("电子封签不匹配：核验 " + req.sealChecked() + "，预约 " + a.getSealNo());
            raise(a, ExceptionType.SEAL_MISMATCH, a.getGateNote(), "闸口");
            timeline.record(a, "闸口", "核验拦截", a.getGateNote(), "GATE");
            appts.save(a);
            return a;
        }

        // 晚到判定
        long lateMin = java.time.Duration.between(a.getWindowStart(), now).toMinutes();
        if (lateMin > props.getLateToleranceMinutes()) {
            raise(a, ExceptionType.LATE,
                    "较预约窗口晚到 " + lateMin + " 分钟（容忍 " + props.getLateToleranceMinutes() + " 分钟）", "闸口");
        }

        a.setGateNote("车牌、预约、封签核验一致");
        if (scheduling.isNight(now)) {
            a.setStatus(ApptStatus.QUEUED_NIGHT);
            timeline.record(a, "闸口", "核验通过·夜间排队",
                    "核验通过，当前夜间时段，车辆进入夜间排队区等待月台", "GATE");
        } else {
            a.setStatus(ApptStatus.IN_PARK);
            timeline.record(a, "闸口", "核验通过入园", "核验通过，引导至月台 "
                    + (a.getAssignedDock() == null ? "待分配" : a.getAssignedDock().getCode()), "GATE");
        }
        appts.save(a);
        return a;
    }

    @Transactional
    public Appointment gateOverride(Long id, String actor, String reason) {
        Appointment a = must(id);
        if (a.getStatus() != ApptStatus.GATE_REJECTED) {
            throw new AuthService.ApiException(409, "仅闸口拦截状态可人工放行");
        }
        a.setStatus(scheduling.isNight(LocalDateTime.now()) ? ApptStatus.QUEUED_NIGHT : ApptStatus.IN_PARK);
        timeline.record(a, actor, "闸口人工放行", "调度复核封签/车牌异常后人工放行。原因：" + reason, "DISPATCH");
        appts.save(a);
        return a;
    }

    @Transactional
    public Appointment releaseNightQueue(Long id, String actor) {
        Appointment a = must(id);
        if (a.getStatus() != ApptStatus.QUEUED_NIGHT) {
            throw new AuthService.ApiException(409, "车辆不在夜间排队状态");
        }
        a.setStatus(ApptStatus.IN_PARK);
        closeOne(a, ExceptionType.NIGHT_QUEUE, "月台空闲，结束夜间排队靠台", actor);
        timeline.record(a, actor, "结束夜间排队", "车辆引导至月台靠台", "DISPATCH");
        appts.save(a);
        return a;
    }

    // ================= 温控原始数据 =================

    @Transactional
    public List<TemperatureReading> uploadReadings(Long id, Dtos.ReadingsReq req) {
        Appointment a = must(id);
        if (a.getStatus().ordinal() < ApptStatus.SCHEDULED.ordinal()) {
            throw new AuthService.ApiException(409, "车次尚未排窗");
        }
        List<TemperatureReading> saved = new ArrayList<>();
        for (Dtos.ReadingReq r : req.readings()) {
            TemperatureReading tr = new TemperatureReading(r.time(), r.tempC(), r.phase());
            tr.setAppointment(a);
            a.getReadings().add(tr);
            saved.add(readings.save(tr));
        }
        evaluateTemperature(a, "温控设备回传 " + saved.size() + " 个采样点");
        appts.save(a);
        return saved;
    }

    private void evaluateTemperature(Appointment a, String prefix) {
        TemperatureService.Analysis an = analysis(a);
        if (an.hasGap()) {
            String detail = prefix + "；发现 " + an.gaps().size() + " 处曲线断点，最大缺失 "
                    + an.gaps().stream().mapToLong(TemperatureService.Gap::minutes).max().orElse(0) + " 分钟";
            raise(a, ExceptionType.TEMP_GAP, detail, "温控系统");
        }
        if (an.overLimit()) {
            String detail = prefix + "；" + an.excursions().size() + " 个采样点超过温区报警线 "
                    + a.getRequiredZone().alarmMaxC + "℃，最高 " + an.maxC() + "℃，存在失温/化冻风险";
            raise(a, ExceptionType.TEMP_EXCURSION, detail, "温控系统");
        }
    }

    private TemperatureService.Analysis analysis(Appointment a) {
        return temperature.analyze(a.getReadings(), a.getRequiredZone(), props.getTempGapMinutes());
    }

    // ================= 月台接车 / 卸货 =================

    @Transactional
    public Appointment startDock(Long id, LocalDateTime time, String actor) {
        Appointment a = must(id);
        if (a.getStatus() != ApptStatus.IN_PARK) {
            throw new AuthService.ApiException(409, "车辆未入园，无法靠月台（当前：" + a.getStatus().label + "）");
        }
        LocalDateTime start = time == null ? LocalDateTime.now() : time;
        a.setStatus(ApptStatus.AT_DOCK);
        a.setDockStartTime(start);
        closeOne(a, ExceptionType.DOCK_CONGESTION, "车辆已靠台开始卸货，拥堵等待结束", actor);
        timeline.record(a, actor, "月台接车", "靠台 " + a.getAssignedDock().getCode() + "，开始卸货", "DOCK");
        appts.save(a);
        return a;
    }

    @Transactional
    public Appointment unload(Long id, Dtos.UnloadReq req, String actor) {
        Appointment a = must(id);
        if (a.getStatus() != ApptStatus.AT_DOCK) {
            throw new AuthService.ApiException(409, "车辆未在月台卸货中");
        }
        LocalDateTime end = req.endTime() == null ? LocalDateTime.now() : req.endTime();
        a.setPreOpenTempC(req.preOpenTempC());
        a.setUnloadPhotos(req.photos());
        a.setDamagedPieces(req.damagedPieces() == null ? 0 : req.damagedPieces());
        a.setDockEndTime(end);
        a.setStatus(ApptStatus.UNLOADED);

        // 开门前温度并入曲线分析
        TemperatureReading pre = new TemperatureReading(end, req.preOpenTempC(), "开门前");
        pre.setAppointment(a);
        a.getReadings().add(pre);
        readings.save(pre);
        evaluateTemperature(a, "月台上传开门前温度 " + req.preOpenTempC() + "℃");

        long mins = java.time.Duration.between(a.getDockStartTime(), end).toMinutes();
        a.setEstimatedReleaseTime(end.plusMinutes(90));
        timeline.record(a, actor, "卸货完成",
                "开门前温度 " + req.preOpenTempC() + "℃，卸货时长 " + mins + " 分钟，破损 "
                        + a.getDamagedPieces() + " 件，卸货照片 " + req.photos()
                        + "；待质检抽样，预计放行 " + a.getEstimatedReleaseTime(), "DOCK");
        appts.save(a);
        return a;
    }

    // ================= 质检 =================

    @Transactional
    public Appointment submitQc(Long id, Dtos.QcReq req, String actor) {
        Appointment a = must(id);
        if (a.getStatus() != ApptStatus.UNLOADED) {
            throw new AuthService.ApiException(409, "仅卸货完成车次可提交质检（当前：" + a.getStatus().label + "）");
        }
        a.setStatus(ApptStatus.IN_QC);
        a.setQcResult(req.result());
        a.setSampledPieces(req.sampledPieces());
        a.setThawedPieces(req.thawedPieces() == null ? 0 : req.thawedPieces());

        switch (req.result()) {
            case PASS -> {
                a.setDecision(Decision.RELEASE);
                a.getCargoLines().forEach(l -> l.setLineDecision(Decision.RELEASE));
                a.setStatus(ApptStatus.RELEASED);
                a.setEstimatedReleaseTime(LocalDateTime.now().plusMinutes(30));
                timeline.record(a, actor, "质检合格放行",
                        "抽样 " + req.sampledPieces() + " 件合格，预计 " + a.getEstimatedReleaseTime()
                                + " 放行，转结算", "QC");
            }
            case THAW -> {
                a.setDecision(Decision.QUARANTINE);
                a.setStatus(ApptStatus.QUARANTINED);
                a.setEstimatedReleaseTime(LocalDateTime.now().plusHours(4));
                raise(a, ExceptionType.THAW,
                        "质检抽样 " + req.sampledPieces() + " 件中发现化冻 " + a.getThawedPieces()
                                + " 件，整批转隔离待处置", actor);
                timeline.record(a, actor, "质检发现化冻",
                        "化冻 " + a.getThawedPieces() + " 件，货物隔离，调度/客服/结算协同判定，预计 "
                                + a.getEstimatedReleaseTime() + " 有结论", "QC");
            }
            case REJECT -> {
                a.setDecision(Decision.REJECT);
                a.getCargoLines().forEach(l -> l.setLineDecision(Decision.REJECT));
                a.setStatus(ApptStatus.REJECTED);
                a.setEstimatedReleaseTime(null);
                raise(a, ExceptionType.THAW, "质检严重不合格，建议整车拒收：" + req.note(), actor);
                timeline.record(a, actor, "质检拒收建议", req.note(), "QC");
            }
        }
        appts.save(a);
        return a;
    }

    // ================= 隔离处置 / 改仓 =================

    @Transactional
    public Appointment makeDecision(Long id, Dtos.DecisionReq req, String actor) {
        Appointment a = must(id);
        if (a.getStatus() != ApptStatus.QUARANTINED) {
            throw new AuthService.ApiException(409, "仅隔离待处置车次可下处置结论");
        }
        a.setDecision(req.decision());
        if (req.lines() != null) {
            for (Dtos.LineDecisionReq ld : req.lines()) {
                a.getCargoLines().stream()
                        .filter(l -> l.getId().equals(ld.lineId()))
                        .findFirst()
                        .ifPresent(l -> l.setLineDecision(ld.decision()));
            }
        }
        // 未逐行指定的货物行按整车结论处理
        a.getCargoLines().stream()
                .filter(l -> l.getLineDecision() == null)
                .forEach(l -> l.setLineDecision(req.decision()));

        LocalDateTime now = LocalDateTime.now();
        switch (req.decision()) {
            case RELEASE -> {
                a.setStatus(ApptStatus.RELEASED);
                a.setEstimatedReleaseTime(now.plusMinutes(30));
            }
            case DOWNGRADE -> {
                a.setStatus(ApptStatus.DOWNGRADED);
                a.setEstimatedReleaseTime(now.plusHours(2));
            }
            case REJECT -> {
                a.setStatus(ApptStatus.REJECTED);
                a.setEstimatedReleaseTime(null);
            }
            case QUARANTINE -> {
                a.setStatus(ApptStatus.QUARANTINED);
                a.setEstimatedReleaseTime(now.plusHours(4));
            }
        }
        timeline.record(a, actor, "处置结论：" + req.decision().label,
                (req.note() == null ? "" : req.note() + "；")
                        + "逐货主处置：" + a.getCargoLines().stream()
                        .map(l -> l.getCustomer().getName() + "=" + l.getLineDecision().label)
                        .collect(Collectors.joining("，"))
                        + (a.getEstimatedReleaseTime() == null ? "，整车拒收无放行时间"
                                                               : "，预计放行 " + a.getEstimatedReleaseTime()),
                "DISPATCH");
        appts.save(a);
        return a;
    }

    @Transactional
    public Appointment changeWarehouse(Long id, Dtos.WarehouseChangeReq req, String actor) {
        Appointment a = must(id);
        AppointmentCargo line = a.getCargoLines().stream()
                .filter(l -> l.getId().equals(req.lineId()))
                .findFirst()
                .orElseThrow(() -> new AuthService.ApiException(404, "货物行不存在"));
        line.setChangedWarehouse(req.newWarehouse());
        raise(a, ExceptionType.WAREHOUSE_CHANGE,
                "货主 " + line.getCustomer().getName() + " 临时改仓：" + line.getTargetWarehouse()
                        + " -> " + req.newWarehouse(), actor);
        timeline.record(a, actor, "货主临时改仓",
                line.getCustomer().getName() + " 的 " + line.getGoodsName() + " 改送 " + req.newWarehouse()
                        + "，调度据此确认月台/库位", "CS");
        appts.save(a);
        return a;
    }

    // ================= 异常单协同 / 评论 =================

    @Transactional
    public ExceptionEvent raise(Appointment a, ExceptionType type, String detail, String by) {
        ExceptionEvent open = a.getExceptions().stream()
                .filter(e -> e.getType() == type && e.getStatus() != ExceptionStatus.CLOSED)
                .findFirst().orElse(null);
        if (open != null) {
            open.setDetail(open.getDetail() + " | 更新：" + detail);
            return open;
        }
        ExceptionEvent e = new ExceptionEvent(type, detail, by, LocalDateTime.now());
        e.setAppointment(a);
        a.getExceptions().add(e);
        return e;
    }

    @Transactional
    public void closeOne(Appointment a, ExceptionType type, String resolution, String by) {
        a.getExceptions().stream()
                .filter(e -> e.getType() == type && e.getStatus() != ExceptionStatus.CLOSED)
                .findFirst()
                .ifPresent(e -> {
                    e.setStatus(ExceptionStatus.CLOSED);
                    e.setClosedAt(LocalDateTime.now());
                    e.setResolution(resolution);
                    timeline.record(a, by, "关闭异常·" + type.label, resolution, "DISPATCH");
                });
    }

    @Transactional
    public ExceptionEvent handleException(Long exId, Dtos.ExceptionHandleReq req, String actor) {
        ExceptionEvent e = appts.findAll().stream()
                .flatMap(a -> a.getExceptions().stream())
                .filter(x -> x.getId().equals(exId))
                .findFirst()
                .orElseThrow(() -> new AuthService.ApiException(404, "异常单不存在"));
        if (req.resolution() != null) e.setResolution(req.resolution());
        if (req.party() != null) e.setResponsibleParty(req.party());
        if (Boolean.TRUE.equals(req.close())) {
            e.setStatus(ExceptionStatus.CLOSED);
            e.setClosedAt(LocalDateTime.now());
        } else if (e.getStatus() == ExceptionStatus.OPEN) {
            e.setStatus(ExceptionStatus.ACK);
        }
        timeline.record(e.getAppointment(), actor, "协同处理·" + e.getType().label,
                "责任建议=" + e.getResponsibleParty().label + "；" + req.resolution(), "DISPATCH");
        return e;
    }

    @Transactional
    public void comment(Long id, String detail, String actor, String category) {
        Appointment a = must(id);
        timeline.record(a, actor, "协同沟通", detail, category);
    }

    // ================= 限电 =================

    @Transactional
    public ParkState setPowerShedding(boolean shedding, String note, String actor) {
        ParkState ps = parkStateRepo.findById(1L).orElseGet(ParkState::new);
        boolean changed = ps.isPowerShedding() != shedding;
        ps.setPowerShedding(shedding);
        ps.setPowerNote(note);
        parkStateRepo.save(ps);

        if (changed) {
            if (shedding) {
                for (Appointment a : appts.findAll()) {
                    if (List.of(ApptStatus.SCHEDULED, ApptStatus.AT_GATE, ApptStatus.IN_PARK,
                            ApptStatus.QUEUED_NIGHT).contains(a.getStatus())) {
                        raise(a, ExceptionType.LOAD_SHEDDING,
                                "园区临时限电：冷库月台减半运行，月台与质检节奏调整：" + note, actor);
                        timeline.record(a, actor, "园区限电通知", note, "DISPATCH");
                    }
                }
            } else {
                for (Appointment a : appts.findAll()) {
                    closeOne(a, ExceptionType.LOAD_SHEDDING, "限电解除，月台恢复全部运行", actor);
                }
            }
        }
        return ps;
    }

    // ================= 查询 =================

    @Transactional(readOnly = true)
    public Appointment must(Long id) {
        return appts.findById(id).orElseThrow(() -> new AuthService.ApiException(404, "车次不存在"));
    }

    @Transactional(readOnly = true)
    public List<Appointment> listForUser(Long carrierId, Long customerId) {
        if (carrierId != null) return appts.findByCarrierIdOrderByCreatedAtDesc(carrierId);
        if (customerId != null) return appts.findByCustomerId(customerId);
        return appts.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSummaries(Long carrierId, Long customerId) {
        return listForUser(carrierId, customerId).stream().map(ViewAssembler::summary).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detailMap(Long id) {
        Appointment a = must(id);
        return ViewAssembler.detail(
                a,
                a.getCargoLines(),
                readings.findByAppointmentIdOrderBySampleTimeAsc(id),
                a.getExceptions().stream()
                        .sorted(Comparator.comparing(e -> e.getRaisedAt() == null ? java.time.LocalDateTime.MIN : e.getRaisedAt()))
                        .toList(),
                a.getTimeline().stream()
                        .sorted(Comparator.comparing(t -> t.getTime() == null ? java.time.LocalDateTime.MIN : t.getTime()))
                        .toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> board() {
        List<Appointment> all = appts.findAllByOrderByCreatedAtDesc();
        Map<String, Object> board = new LinkedHashMap<>();
        ParkState ps = parkStateRepo.findById(1L).orElse(null);
        board.put("powerShedding", ps != null && ps.isPowerShedding());
        board.put("powerNote", ps == null ? "" : ps.getPowerNote());
        board.put("usableDockCount", scheduling.usableDocks().size());
        board.put("totalDockCount", dockRepo.findByEnabledTrue().size());
        board.put("nightNow", scheduling.isNight(LocalDateTime.now()));

        Map<String, Long> stageCounts = new LinkedHashMap<>();
        for (Stage s : Stage.values()) stageCounts.put(s.name(), 0L);
        for (Appointment a : all) {
            stageCounts.merge(a.stage().name(), 1L, Long::sum);
        }
        board.put("stageCounts", stageCounts);

        List<Map<String, Object>> active = all.stream()
                .filter(a -> a.getStatus() != ApptStatus.SETTLED && a.getStatus() != ApptStatus.CLOSED
                        && a.getStatus() != ApptStatus.GATE_REJECTED)
                .sorted(Comparator.comparing(Appointment::getCreatedAt).reversed())
                .map(ViewAssembler::summary)
                .toList();
        board.put("vehicles", active);
        return board;
    }
}
