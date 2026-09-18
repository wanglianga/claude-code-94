package com.coldchain.park.service;

import com.coldchain.park.domain.Appointment;
import com.coldchain.park.domain.AppointmentCargo;
import com.coldchain.park.domain.ExceptionType;
import com.coldchain.park.domain.Party;
import com.coldchain.park.domain.Stage;
import com.coldchain.park.repo.AppointmentRepository;
import com.coldchain.park.repo.CustomerRepository;
import com.coldchain.park.repo.TemperatureReadingRepository;
import com.coldchain.park.repo.TimelineEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户客服口径：把温控异常翻译成客户可理解的
 * 「卡在哪一环 + 谁的责任 + 货损多少 + 预计何时放行」；并提供批次级运输质量追溯。
 */
@Service
public class CustomerExplanationService {

    private final AppointmentRepository appts;
    private final CustomerRepository customers;
    private final TemperatureReadingRepository readings;
    private final TimelineEventRepository timeline;
    private final TemperatureService temperature;
    private final ResponsibilityService responsibility;
    private final com.coldchain.park.config.ParkProps props;

    public CustomerExplanationService(AppointmentRepository appts, CustomerRepository customers,
                                      TemperatureReadingRepository readings,
                                      TimelineEventRepository timeline,
                                      TemperatureService temperature,
                                      ResponsibilityService responsibility,
                                      com.coldchain.park.config.ParkProps props) {
        this.appts = appts;
        this.customers = customers;
        this.readings = readings;
        this.timeline = timeline;
        this.temperature = temperature;
        this.responsibility = responsibility;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> explain(Long apptId, Long customerId) {
        Appointment a = appts.findById(apptId).orElseThrow(() -> new AuthService.ApiException(404, "车次不存在"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", a.getCode());
        m.put("plateNo", a.getPlateNo());
        m.put("statusLabel", a.getStatus().label);
        m.put("estimatedReleaseTime", a.getEstimatedReleaseTime());

        Stage stage = a.stage();
        m.put("stage", stage.name());
        m.put("stageLabel", stage.label);
        m.put("stuckAt", stuckText(a, stage));

        // 客户视角：只解释本货主的货物
        List<AppointmentCargo> mine = a.getCargoLines().stream()
                .filter(l -> customerId == null || (l.getCustomer() != null && l.getCustomer().getId().equals(customerId)))
                .toList();
        m.put("myCargo", mine.stream().map(l -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("customerName", l.getCustomer().getName());
            x.put("goodsName", l.getGoodsName());
            x.put("pieces", l.getPieces());
            x.put("zoneLabel", l.getZone().label);
            x.put("lineDecisionLabel", l.getLineDecision() == null ? "待处置" : l.getLineDecision().label);
            x.put("changedWarehouse", l.getChangedWarehouse());
            return x;
        }).toList());

        // 责任大白话
        ResponsibilityService.Verdict v = responsibility.judge(a);
        m.put("responsibilitySummary", v.summary());
        List<Map<String, Object>> plain = new java.util.ArrayList<>();
        for (ResponsibilityService.Share s : v.shares()) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("partyLabel", s.party().label);
            sm.put("percent", Math.round(s.weight() * 100));
            sm.put("why", plainWhy(s.party(), s.reasons()));
            plain.add(sm);
        }
        m.put("responsibilityDetail", plain);

        // 货损估算
        int thawed = a.getThawedPieces() == null ? 0 : a.getThawedPieces();
        int damaged = a.getDamagedPieces() == null ? 0 : a.getDamagedPieces();
        double price = mine.stream().findFirst().map(l -> l.getCustomer().getClaimPricePerPiece()).orElse(50d);
        double loss = ViewAssembler.round2(thawed * price + damaged * price * 0.5);
        Map<String, Object> lossM = new LinkedHashMap<>();
        lossM.put("thawedPieces", thawed);
        lossM.put("damagedPieces", damaged);
        lossM.put("pricePerPiece", price);
        lossM.put("estimatedAmount", loss);
        m.put("cargoLoss", lossM);
        if (a.getSettlement() != null) {
            m.put("customerClaim", a.getSettlement().getCustomerClaim());
            m.put("parkCompensation", a.getSettlement().getParkCompensation());
        }

        // 预计放行时间的客户口径
        m.put("releaseText", releaseText(a));

        // 温控曲线摘要（保留原始数据入口）
        TemperatureService.Analysis an = temperature.analyze(
                readings.findByAppointmentIdOrderBySampleTimeAsc(a.getId()),
                a.getRequiredZone(), props.getTempGapMinutes());
        m.put("temperature", temperature.explain(an, a.getRequiredZone()));

        // 一段可直接念给客户听的话
        m.put("script", buildScript(a, mine, v, thawed, damaged, loss));
        return m;
    }

    private String stuckText(Appointment a, Stage stage) {
        return switch (stage) {
            case SCHEDULED -> "车辆尚未到园，已生成入场窗口 " + a.getWindowStart();
            case GATE -> switch (a.getStatus()) {
                case GATE_REJECTED -> "车辆卡在闸口：" + a.getGateNote();
                case QUEUED_NIGHT -> "车辆已到园，因夜间班次人力有限在门外排队";
                default -> "车辆正在闸口核验预约、车牌与电子封签";
            };
            case DOCK -> "车辆已入园，正在月台靠台/卸货";
            case QC -> "货物已卸完，正在质检抽样";
            case QUARANTINE -> "质检发现异常，货物被隔离，正在由调度、质检、客服、结算共同判定";
            case SETTLEMENT -> "处置结论已出（" + a.getDecision().label + "），正在结算责任与赔付";
            case DONE -> "车次已结算关单";
        };
    }

    private String releaseText(Appointment a) {
        if (a.getStatus().name().equals("REJECTED")) {
            return "整车已拒收，无入库放行时间；将原路退回并按责任结算";
        }
        if (a.getEstimatedReleaseTime() == null) {
            return "待隔离处置会后确定预计放行时间";
        }
        String prefix = switch (a.getStatus()) {
            case RELEASED -> "质检合格，预计 ";
            case DOWNGRADED -> "按降级入库处理，预计 ";
            case QUARANTINED -> "如复核通过，预计 ";
            default -> "当前预计 ";
        };
        return prefix + a.getEstimatedReleaseTime() + " 放行（异常处理进度变化时会更新）";
    }

    private List<String> plainWhy(Party p, List<String> raw) {
        if (p == Party.NONE) return List.of("本车次全程温控与交接正常，没有需要追责的问题");
        return raw.stream().limit(3).map(r -> switch (p) {
            case CARRIER -> "运输环节：" + r;
            case PARK -> "园区环节：" + r;
            case CUSTOMER -> "客户/货主环节：" + r;
            default -> r;
        }).toList();
    }

    private String buildScript(Appointment a, List<AppointmentCargo> mine,
                               ResponsibilityService.Verdict v, int thawed, int damaged, double loss) {
        String goods = mine.stream().map(AppointmentCargo::getGoodsName).reduce((x, y) -> x + "、" + y).orElse("货物");
        StringBuilder sb = new StringBuilder();
        sb.append("您好，您的 ").append(goods).append("（车次 ").append(a.getCode())
          .append("，车牌 ").append(a.getPlateNo()).append("）目前处于【").append(a.stage().label).append("】，")
          .append(stuckText(a, a.stage())).append("。");
        if (thawed + damaged > 0) {
            sb.append("目前登记化冻 ").append(thawed).append(" 件、破损 ").append(damaged)
              .append(" 件，预估货损 ").append(loss).append(" 元；");
        } else {
            sb.append("目前没有登记货损；");
        }
        sb.append("责任判定为：").append(v.summary()).append("。");
        sb.append(releaseText(a)).append("。温控设备原始数据、卸货时长与签收结果均已留存，可随时为您追溯。");
        return sb.toString();
    }

    /** 批次追溯：温控原始数据 + 卸货时长 + 签收结果 */
    @Transactional(readOnly = true)
    public Map<String, Object> trace(Long apptId, Long customerId) {
        Appointment a = appts.findById(apptId).orElseThrow(() -> new AuthService.ApiException(404, "车次不存在"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", a.getCode());
        m.put("plateNo", a.getPlateNo());
        m.put("carrierName", a.getCarrier().getName());
        m.put("deviceNo", a.getDeviceNo());
        m.put("sealNo", a.getSealNo());
        m.put("windowStart", a.getWindowStart());
        m.put("gateArriveTime", a.getGateArriveTime());
        m.put("dockStartTime", a.getDockStartTime());
        m.put("dockEndTime", a.getDockEndTime());
        if (a.getDockStartTime() != null && a.getDockEndTime() != null) {
            m.put("unloadDurationMinutes", Duration.between(a.getDockStartTime(), a.getDockEndTime()).toMinutes());
        }
        m.put("preOpenTempC", a.getPreOpenTempC());
        m.put("unloadPhotos", a.getUnloadPhotos());
        m.put("qcResultLabel", a.getQcResult() == null ? null : a.getQcResult().label);
        m.put("decisionLabel", a.getDecision() == null ? null : a.getDecision().label);
        m.put("signResult", a.getSignResult() == null ? null : a.getSignResult().name());
        m.put("signResultLabel", a.getSignResult() == null ? null : a.getSignResult().label);
        m.put("signedPieces", a.getSignedPieces());
        m.put("damagedPieces", a.getDamagedPieces());

        List<com.coldchain.park.domain.TemperatureReading> rs =
                readings.findByAppointmentIdOrderBySampleTimeAsc(apptId);
        m.put("readingCount", rs.size());
        m.put("rawReadings", rs.stream().map(ViewAssembler::reading).toList());

        TemperatureService.Analysis an = temperature.analyze(rs, a.getRequiredZone(), props.getTempGapMinutes());
        m.put("analysis", temperature.explain(an, a.getRequiredZone()));
        m.put("timeline", timeline.findByAppointmentIdOrderByTimeAsc(apptId).stream()
                .map(ViewAssembler::timeline).toList());
        m.put("cargoLines", a.getCargoLines().stream()
                .filter(l -> customerId == null || l.getCustomer().getId().equals(customerId))
                .map(ViewAssembler::cargoLine).toList());
        return m;
    }
}
