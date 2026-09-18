package com.coldchain.park.web;

import com.coldchain.park.domain.Appointment;
import com.coldchain.park.domain.ExceptionEvent;
import com.coldchain.park.domain.ParkState;
import com.coldchain.park.domain.UserAccount;
import com.coldchain.park.domain.UserRole;
import com.coldchain.park.service.AppointmentService;
import com.coldchain.park.service.ViewAssembler;
import com.coldchain.park.web.dto.Dtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 车次全流程接口：预约/闸口/温度/月台/质检/处置/异常协同，各角色在同一车次上操作 */
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService svc;

    public AppointmentController(AppointmentService svc) {
        this.svc = svc;
    }

    // ---------- 预约 ----------

    @PostMapping
    public Map<String, Object> create(@RequestBody Dtos.CreateApptReq req) {
        AuthController.requireAny(UserRole.CARRIER, UserRole.ADMIN, UserRole.DISPATCH);
        UserAccount u = CurrentUser.get();
        Dtos.CreateApptReq body = req;
        // 承运商账号只能给自己名下预约
        if (u.getRole() == UserRole.CARRIER) {
            if (req.carrierId() != null && !req.carrierId().equals(u.getBindCarrierId())) {
                throw new com.coldchain.park.service.AuthService.ApiException(403, "只能为自己的承运商公司提交预约");
            }
            body = new Dtos.CreateApptReq(u.getBindCarrierId(), req.driverId(), req.plateNo(),
                    req.deviceNo(), req.sealNo(), req.cargoTypeDesc(), req.estimatedPieces(),
                    req.requestedTime(), req.urgent(), req.lines());
        }
        Appointment a = svc.create(body);
        return svc.detailMap(a.getId());
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        UserAccount u = CurrentUser.get();
        Long carrierId = u.getRole() == UserRole.CARRIER ? u.getBindCarrierId() : null;
        Long customerId = u.getRole() == UserRole.CS ? u.getBindCustomerId() : null;
        return svc.listSummaries(carrierId, customerId);
    }

    @GetMapping("/board")
    public Map<String, Object> board() {
        return svc.board();
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        return svc.detailMap(id);
    }

    // ---------- 调度动作 ----------

    @PostMapping("/{id}/reschedule")
    public Map<String, Object> reschedule(@PathVariable Long id, @RequestBody Dtos.RescheduleReq req) {
        AuthController.requireAny(UserRole.DISPATCH);
        svc.reschedule(id, req == null ? null : req.fromTime(), AuthController.actor());
        return svc.detailMap(id);
    }

    @PostMapping("/{id}/replace-driver")
    public Map<String, Object> replaceDriver(@PathVariable Long id, @RequestBody Dtos.ReplaceDriverReq req) {
        AuthController.requireAny(UserRole.DISPATCH, UserRole.CARRIER);
        svc.replaceDriver(id, req.driverId(), AuthController.actor());
        return svc.detailMap(id);
    }

    @PostMapping("/{id}/urgent")
    public Map<String, Object> urgent(@PathVariable Long id) {
        AuthController.requireAny(UserRole.CS, UserRole.DISPATCH, UserRole.ADMIN);
        svc.markUrgent(id, AuthController.actor());
        return svc.detailMap(id);
    }

    // ---------- 闸口 ----------

    @PostMapping("/{id}/gate-check")
    public Map<String, Object> gateCheck(@PathVariable Long id, @RequestBody Dtos.GateCheckReq req) {
        AuthController.requireAny(UserRole.DISPATCH);
        svc.gateCheck(id, req);
        return svc.detailMap(id);
    }

    @PostMapping("/{id}/gate-override")
    public Map<String, Object> gateOverride(@PathVariable Long id, @RequestBody Dtos.CommentReq req) {
        AuthController.requireAny(UserRole.DISPATCH);
        svc.gateOverride(id, AuthController.actor(), req.detail());
        return svc.detailMap(id);
    }

    @PostMapping("/{id}/release-queue")
    public Map<String, Object> releaseQueue(@PathVariable Long id) {
        AuthController.requireAny(UserRole.DISPATCH);
        svc.releaseNightQueue(id, AuthController.actor());
        return svc.detailMap(id);
    }

    // ---------- 温控数据 ----------

    @PostMapping("/{id}/readings")
    public Map<String, Object> readings(@PathVariable Long id, @RequestBody Dtos.ReadingsReq req) {
        AuthController.requireAny(UserRole.CARRIER, UserRole.DISPATCH, UserRole.ADMIN);
        svc.uploadReadings(id, req);
        return svc.detailMap(id);
    }

    // ---------- 月台 ----------

    @PostMapping("/{id}/dock-start")
    public Map<String, Object> dockStart(@PathVariable Long id, @RequestBody(required = false) Dtos.DockStartReq req) {
        AuthController.requireAny(UserRole.DISPATCH);
        svc.startDock(id, req == null ? null : req.time(), AuthController.actor());
        return svc.detailMap(id);
    }

    @PostMapping("/{id}/unload")
    public Map<String, Object> unload(@PathVariable Long id, @RequestBody Dtos.UnloadReq req) {
        AuthController.requireAny(UserRole.DISPATCH);
        svc.unload(id, req, AuthController.actor());
        return svc.detailMap(id);
    }

    // ---------- 质检 ----------

    @PostMapping("/{id}/qc")
    public Map<String, Object> qc(@PathVariable Long id, @RequestBody Dtos.QcReq req) {
        AuthController.requireAny(UserRole.QC);
        svc.submitQc(id, req, AuthController.actor());
        return svc.detailMap(id);
    }

    // ---------- 隔离处置 / 改仓 ----------

    @PostMapping("/{id}/decision")
    public Map<String, Object> decision(@PathVariable Long id, @RequestBody Dtos.DecisionReq req) {
        AuthController.requireAny(UserRole.DISPATCH, UserRole.QC);
        svc.makeDecision(id, req, AuthController.actor());
        return svc.detailMap(id);
    }

    @PostMapping("/{id}/warehouse-change")
    public Map<String, Object> warehouseChange(@PathVariable Long id, @RequestBody Dtos.WarehouseChangeReq req) {
        AuthController.requireAny(UserRole.CS, UserRole.DISPATCH);
        svc.changeWarehouse(id, req, AuthController.actor());
        return svc.detailMap(id);
    }

    // ---------- 异常协同 ----------

    @PostMapping("/exceptions/{exId}/handle")
    public Map<String, Object> handleException(@PathVariable Long exId, @RequestBody Dtos.ExceptionHandleReq req) {
        AuthController.requireAny(UserRole.DISPATCH, UserRole.QC, UserRole.CS, UserRole.SETTLEMENT);
        ExceptionEvent e = svc.handleException(exId, req, AuthController.actor());
        return ViewAssembler.ex(e);
    }

    @PostMapping("/{id}/comments")
    public Map<String, Object> comment(@PathVariable Long id, @RequestBody Dtos.CommentReq req) {
        UserAccount u = CurrentUser.get();
        String category = switch (u.getRole()) {
            case CARRIER -> "CARRIER";
            case QC -> "QC";
            case CS -> "CS";
            case SETTLEMENT -> "SETTLEMENT";
            default -> "DISPATCH";
        };
        svc.comment(id, req.detail(), AuthController.actor(), category);
        return svc.detailMap(id);
    }

    // ---------- 限电（园区调度） ----------

    @PostMapping("/power-shedding")
    public Map<String, Object> power(@RequestBody Dtos.PowerReq req) {
        AuthController.requireAny(UserRole.DISPATCH, UserRole.ADMIN);
        ParkState ps = svc.setPowerShedding(Boolean.TRUE.equals(req.shedding()), req.note(), AuthController.actor());
        return Map.of("powerShedding", ps.isPowerShedding(), "note", ps.getPowerNote() == null ? "" : ps.getPowerNote());
    }
}
