package com.coldchain.park.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 预约车次 —— 全流程主线聚合根。
 * 承运商预约 -> 排窗 -> 闸口 -> 月台 -> 质检 -> 隔离/处置 -> 结算，全部角色在同一车次上协同。
 */
@Entity
public class Appointment extends BaseEntity {

    /** 业务单号，如 APT-20260918-0001 */
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    private Carrier carrier;

    @ManyToOne(fetch = FetchType.LAZY)
    private Driver driver;

    // ---- 预约提交信息 ----
    private String plateNo;
    private String deviceNo;          // 车厢温度设备号
    private String sealNo;            // 电子封签号
    private String cargoTypeDesc;     // 货品类型描述（如 冻虾仁/冷鲜牛肉/进口奶酪）
    private int estimatedPieces;      // 预计件数

    /** 多货主混装：每行含货主/温区/件数 */
    @jakarta.persistence.OrderBy("id asc")
    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AppointmentCargo> cargoLines = new ArrayList<>();

    /** 排窗结果所需的最冷温区（多货主混装取最冷） */
    @Enumerated(EnumType.STRING)
    private TempZone requiredZone;

    private LocalDateTime requestedTime;   // 承运商期望到园时间
    private boolean urgent = false;        // 客户临时加急

    // ---- 排窗结果 ----
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    @ManyToOne(fetch = FetchType.LAZY)
    private Dock assignedDock;
    private String scheduleNote = "";

    // ---- 状态 ----
    @Enumerated(EnumType.STRING)
    private ApptStatus status = ApptStatus.PENDING;

    // ---- 闸口 ----
    private LocalDateTime gateArriveTime;
    private String gateSealChecked;
    private boolean gateSealMatch;
    private String gateNote = "";

    // ---- 月台 ----
    private LocalDateTime dockStartTime;
    private LocalDateTime dockEndTime;
    private Double preOpenTempC;      // 开门前温度
    private String unloadPhotos;      // 卸货照片（演示为 URL/占位文本，多个分号分隔）

    // ---- 质检 / 处置 ----
    @Enumerated(EnumType.STRING)
    private QcResult qcResult;
    @Enumerated(EnumType.STRING)
    private Decision decision;
    @Enumerated(EnumType.STRING)
    private SignResult signResult;
    private Integer damagedPieces;    // 破损件数
    private Integer signedPieces;     // 签收件数
    private Integer thawedPieces;     // 化冻件数
    private Integer sampledPieces;    // 抽样件数
    private LocalDateTime estimatedReleaseTime; // 给客户客服的预计放行时间

    // ---- 结算 ----
    @ManyToOne(fetch = FetchType.LAZY)
    private Settlement settlement;

    private boolean closed = false;
    private String remark = "";

    // ---- 关联流水 ----
    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TemperatureReading> readings = new ArrayList<>();

    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExceptionEvent> exceptions = new ArrayList<>();

    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TimelineEvent> timeline = new ArrayList<>();

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Carrier getCarrier() { return carrier; }
    public void setCarrier(Carrier carrier) { this.carrier = carrier; }
    public Driver getDriver() { return driver; }
    public void setDriver(Driver driver) { this.driver = driver; }
    public String getPlateNo() { return plateNo; }
    public void setPlateNo(String plateNo) { this.plateNo = plateNo; }
    public String getDeviceNo() { return deviceNo; }
    public void setDeviceNo(String deviceNo) { this.deviceNo = deviceNo; }
    public String getSealNo() { return sealNo; }
    public void setSealNo(String sealNo) { this.sealNo = sealNo; }
    public String getCargoTypeDesc() { return cargoTypeDesc; }
    public void setCargoTypeDesc(String cargoTypeDesc) { this.cargoTypeDesc = cargoTypeDesc; }
    public int getEstimatedPieces() { return estimatedPieces; }
    public void setEstimatedPieces(int estimatedPieces) { this.estimatedPieces = estimatedPieces; }
    public List<AppointmentCargo> getCargoLines() { return cargoLines; }
    public void setCargoLines(List<AppointmentCargo> cargoLines) { this.cargoLines = cargoLines; }
    public TempZone getRequiredZone() { return requiredZone; }
    public void setRequiredZone(TempZone requiredZone) { this.requiredZone = requiredZone; }
    public LocalDateTime getRequestedTime() { return requestedTime; }
    public void setRequestedTime(LocalDateTime requestedTime) { this.requestedTime = requestedTime; }
    public boolean isUrgent() { return urgent; }
    public void setUrgent(boolean urgent) { this.urgent = urgent; }
    public LocalDateTime getWindowStart() { return windowStart; }
    public void setWindowStart(LocalDateTime windowStart) { this.windowStart = windowStart; }
    public LocalDateTime getWindowEnd() { return windowEnd; }
    public void setWindowEnd(LocalDateTime windowEnd) { this.windowEnd = windowEnd; }
    public Dock getAssignedDock() { return assignedDock; }
    public void setAssignedDock(Dock assignedDock) { this.assignedDock = assignedDock; }
    public String getScheduleNote() { return scheduleNote; }
    public void setScheduleNote(String scheduleNote) { this.scheduleNote = scheduleNote; }
    public ApptStatus getStatus() { return status; }
    public void setStatus(ApptStatus status) { this.status = status; }
    public LocalDateTime getGateArriveTime() { return gateArriveTime; }
    public void setGateArriveTime(LocalDateTime gateArriveTime) { this.gateArriveTime = gateArriveTime; }
    public String getGateSealChecked() { return gateSealChecked; }
    public void setGateSealChecked(String gateSealChecked) { this.gateSealChecked = gateSealChecked; }
    public boolean isGateSealMatch() { return gateSealMatch; }
    public void setGateSealMatch(boolean gateSealMatch) { this.gateSealMatch = gateSealMatch; }
    public String getGateNote() { return gateNote; }
    public void setGateNote(String gateNote) { this.gateNote = gateNote; }
    public LocalDateTime getDockStartTime() { return dockStartTime; }
    public void setDockStartTime(LocalDateTime dockStartTime) { this.dockStartTime = dockStartTime; }
    public LocalDateTime getDockEndTime() { return dockEndTime; }
    public void setDockEndTime(LocalDateTime dockEndTime) { this.dockEndTime = dockEndTime; }
    public Double getPreOpenTempC() { return preOpenTempC; }
    public void setPreOpenTempC(Double preOpenTempC) { this.preOpenTempC = preOpenTempC; }
    public String getUnloadPhotos() { return unloadPhotos; }
    public void setUnloadPhotos(String unloadPhotos) { this.unloadPhotos = unloadPhotos; }
    public QcResult getQcResult() { return qcResult; }
    public void setQcResult(QcResult qcResult) { this.qcResult = qcResult; }
    public Decision getDecision() { return decision; }
    public void setDecision(Decision decision) { this.decision = decision; }
    public SignResult getSignResult() { return signResult; }
    public void setSignResult(SignResult signResult) { this.signResult = signResult; }
    public Integer getDamagedPieces() { return damagedPieces; }
    public void setDamagedPieces(Integer damagedPieces) { this.damagedPieces = damagedPieces; }
    public Integer getSignedPieces() { return signedPieces; }
    public void setSignedPieces(Integer signedPieces) { this.signedPieces = signedPieces; }
    public Integer getThawedPieces() { return thawedPieces; }
    public void setThawedPieces(Integer thawedPieces) { this.thawedPieces = thawedPieces; }
    public Integer getSampledPieces() { return sampledPieces; }
    public void setSampledPieces(Integer sampledPieces) { this.sampledPieces = sampledPieces; }
    public LocalDateTime getEstimatedReleaseTime() { return estimatedReleaseTime; }
    public void setEstimatedReleaseTime(LocalDateTime estimatedReleaseTime) { this.estimatedReleaseTime = estimatedReleaseTime; }
    public Settlement getSettlement() { return settlement; }
    public void setSettlement(Settlement settlement) { this.settlement = settlement; }
    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public List<TemperatureReading> getReadings() { return readings; }
    public void setReadings(List<TemperatureReading> readings) { this.readings = readings; }
    public List<ExceptionEvent> getExceptions() { return exceptions; }
    public void setExceptions(List<ExceptionEvent> exceptions) { this.exceptions = exceptions; }
    public List<TimelineEvent> getTimeline() { return timeline; }
    public void setTimeline(List<TimelineEvent> timeline) { this.timeline = timeline; }

    /** 看板环节由状态派生 */
    public Stage stage() {
        return status.stage;
    }
}
