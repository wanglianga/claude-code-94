package com.coldchain.park.service;

import com.coldchain.park.domain.Appointment;
import com.coldchain.park.domain.AppointmentCargo;
import com.coldchain.park.domain.ExceptionEvent;
import com.coldchain.park.domain.TemperatureReading;
import com.coldchain.park.domain.TimelineEvent;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 把实体组装成接口返回的 Map 视图（在事务内访问 LAZY 关联） */
public final class ViewAssembler {

    private ViewAssembler() {}

    public static Map<String, Object> summary(Appointment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("code", a.getCode());
        m.put("plateNo", a.getPlateNo());
        m.put("sealNo", a.getSealNo());
        m.put("deviceNo", a.getDeviceNo());
        m.put("cargoTypeDesc", a.getCargoTypeDesc());
        m.put("estimatedPieces", a.getEstimatedPieces());
        m.put("requiredZone", zone(a));
        m.put("urgent", a.isUrgent());
        m.put("status", a.getStatus().name());
        m.put("statusLabel", a.getStatus().label);
        m.put("stage", a.stage().name());
        m.put("stageLabel", a.stage().label);
        m.put("carrierId", a.getCarrier() == null ? null : a.getCarrier().getId());
        m.put("carrierName", a.getCarrier() == null ? null : a.getCarrier().getName());
        m.put("driverName", a.getDriver() == null ? null : a.getDriver().getName());
        m.put("dockCode", a.getAssignedDock() == null ? null : a.getAssignedDock().getCode());
        m.put("requestedTime", a.getRequestedTime());
        m.put("windowStart", a.getWindowStart());
        m.put("windowEnd", a.getWindowEnd());
        m.put("gateArriveTime", a.getGateArriveTime());
        m.put("dockStartTime", a.getDockStartTime());
        m.put("dockEndTime", a.getDockEndTime());
        m.put("decision", a.getDecision() == null ? null : a.getDecision().name());
        m.put("decisionLabel", a.getDecision() == null ? null : a.getDecision().label);
        m.put("estimatedReleaseTime", a.getEstimatedReleaseTime());
        m.put("openExceptionCount", a.getExceptions().stream().filter(e -> e.getStatus() != com.coldchain.park.domain.ExceptionStatus.CLOSED).count());
        m.put("customers", a.getCargoLines().stream().map(l -> l.getCustomer() == null ? "?" : l.getCustomer().getName()).distinct().toList());
        return m;
    }

    private static String zone(Appointment a) {
        return a.getRequiredZone() == null ? null : a.getRequiredZone().name();
    }

    public static Map<String, Object> cargoLine(AppointmentCargo l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("customerId", l.getCustomer() == null ? null : l.getCustomer().getId());
        m.put("customerName", l.getCustomer() == null ? null : l.getCustomer().getName());
        m.put("goodsName", l.getGoodsName());
        m.put("zone", l.getZone() == null ? null : l.getZone().name());
        m.put("zoneLabel", l.getZone() == null ? null : l.getZone().label);
        m.put("pieces", l.getPieces());
        m.put("targetWarehouse", l.getTargetWarehouse());
        m.put("changedWarehouse", l.getChangedWarehouse());
        m.put("lineDecision", l.getLineDecision() == null ? null : l.getLineDecision().name());
        m.put("lineDecisionLabel", l.getLineDecision() == null ? null : l.getLineDecision().label);
        return m;
    }

    public static Map<String, Object> reading(TemperatureReading r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("time", r.getSampleTime());
        m.put("tempC", r.getTempC());
        m.put("phase", r.getPhase());
        return m;
    }

    public static Map<String, Object> ex(ExceptionEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("type", e.getType().name());
        m.put("typeLabel", e.getType().label);
        m.put("severity", e.getType().severity);
        m.put("status", e.getStatus().name());
        m.put("statusLabel", e.getStatus().label);
        m.put("detail", e.getDetail());
        m.put("raisedBy", e.getRaisedBy());
        m.put("raisedAt", e.getRaisedAt());
        m.put("closedAt", e.getClosedAt());
        m.put("resolution", e.getResolution());
        m.put("responsibleParty", e.getResponsibleParty() == null ? null : e.getResponsibleParty().name());
        m.put("responsiblePartyLabel", e.getResponsibleParty() == null ? null : e.getResponsibleParty().label);
        return m;
    }

    public static Map<String, Object> timeline(TimelineEvent t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("time", t.getTime());
        m.put("actor", t.getActor());
        m.put("action", t.getAction());
        m.put("detail", t.getDetail());
        m.put("category", t.getCategory());
        return m;
    }

    public static Map<String, Object> detail(Appointment a,
                                             List<AppointmentCargo> lines,
                                             List<TemperatureReading> readings,
                                             List<ExceptionEvent> exceptions,
                                             List<TimelineEvent> timeline) {
        Map<String, Object> m = summary(a);
        m.put("cargoLines", lines.stream().map(ViewAssembler::cargoLine).toList());
        m.put("readings", readings.stream().map(ViewAssembler::reading).toList());
        m.put("exceptions", exceptions.stream().map(ViewAssembler::ex).toList());
        m.put("timeline", timeline.stream().map(ViewAssembler::timeline).toList());
        m.put("preOpenTempC", a.getPreOpenTempC());
        m.put("unloadPhotos", a.getUnloadPhotos());
        m.put("damagedPieces", a.getDamagedPieces());
        m.put("sampledPieces", a.getSampledPieces());
        m.put("thawedPieces", a.getThawedPieces());
        m.put("qcResult", a.getQcResult() == null ? null : a.getQcResult().name());
        m.put("qcResultLabel", a.getQcResult() == null ? null : a.getQcResult().label);
        m.put("signResult", a.getSignResult() == null ? null : a.getSignResult().name());
        m.put("signedPieces", a.getSignedPieces());
        m.put("scheduleNote", a.getScheduleNote());
        m.put("gateNote", a.getGateNote());
        m.put("gateSealMatch", a.isGateSealMatch());
        m.put("remark", a.getRemark());
        m.put("serviceScore", a.getCarrier() == null ? null : a.getCarrier().getServiceScore());
        if (a.getSettlement() != null) {
            var s = a.getSettlement();
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("code", s.getCode());
            sm.put("freightBase", round2(s.getFreightBase()));
            sm.put("carrierPenalty", round2(s.getCarrierPenalty()));
            sm.put("carrierPayable", round2(s.getCarrierPayable()));
            sm.put("customerClaim", round2(s.getCustomerClaim()));
            sm.put("parkCompensation", round2(s.getParkCompensation()));
            sm.put("responsibilitySummary", s.getResponsibilitySummary());
            sm.put("scoreDelta", s.getScoreDelta());
            sm.put("note", s.getNote());
            sm.put("settled", s.isSettled());
            m.put("settlement", sm);
        }
        if (a.getDockStartTime() != null && a.getDockEndTime() != null) {
            m.put("unloadDurationMinutes", Duration.between(a.getDockStartTime(), a.getDockEndTime()).toMinutes());
        }
        return m;
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
