package com.coldchain.park.web.dto;

import com.coldchain.park.domain.Decision;
import com.coldchain.park.domain.Party;
import com.coldchain.park.domain.QcResult;
import com.coldchain.park.domain.SignResult;
import com.coldchain.park.domain.TempZone;

import java.time.LocalDateTime;
import java.util.List;

/** 全部请求 DTO（record 不可变对象） */
public final class Dtos {

    private Dtos() {}

    public record LoginReq(String username, String password) {}

    public record CargoLineReq(Long customerId, String goodsName, TempZone zone,
                               Integer pieces, String targetWarehouse) {}

    public record CreateApptReq(Long carrierId, Long driverId, String plateNo, String deviceNo,
                                String sealNo, String cargoTypeDesc, Integer estimatedPieces,
                                LocalDateTime requestedTime, Boolean urgent,
                                List<CargoLineReq> lines) {}

    public record GateCheckReq(String plateScan, String sealChecked, LocalDateTime arrivalTime) {}

    public record ReadingReq(LocalDateTime time, Double tempC, String phase) {}

    public record ReadingsReq(List<ReadingReq> readings) {}

    public record DockStartReq(LocalDateTime time) {}

    public record UnloadReq(Double preOpenTempC, String photos, Integer damagedPieces,
                            LocalDateTime endTime) {}

    public record QcReq(QcResult result, Integer sampledPieces, Integer thawedPieces, String note) {}

    public record LineDecisionReq(Long lineId, Decision decision) {}

    public record DecisionReq(Decision decision, List<LineDecisionReq> lines, String note) {}

    public record CommentReq(String detail) {}

    public record ExceptionHandleReq(String resolution, Party party, Boolean close) {}

    public record WarehouseChangeReq(Long lineId, String newWarehouse) {}

    public record ReplaceDriverReq(Long driverId) {}

    public record SignSettleReq(SignResult signResult, Integer signedPieces, String note) {}

    public record PowerReq(Boolean shedding, String note) {}

    public record RescheduleReq(LocalDateTime fromTime) {}
}
