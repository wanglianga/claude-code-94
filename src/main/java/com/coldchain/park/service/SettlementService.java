package com.coldchain.park.service;

import com.coldchain.park.domain.Appointment;
import com.coldchain.park.domain.ApptStatus;
import com.coldchain.park.domain.Customer;
import com.coldchain.park.domain.Decision;
import com.coldchain.park.domain.ExceptionEvent;
import com.coldchain.park.domain.ExceptionStatus;
import com.coldchain.park.domain.ExceptionType;
import com.coldchain.park.domain.Party;
import com.coldchain.park.domain.Settlement;
import com.coldchain.park.domain.SignResult;
import com.coldchain.park.repo.AppointmentRepository;
import com.coldchain.park.repo.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 结算：责任判定 -> 承运商扣罚 / 客户赔付 / 园区补偿 -> 服务分（影响后续预约优先级）。
 * 计算口径固定透明，便于客服向客户解释。
 */
@Service
public class SettlementService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AppointmentRepository appts;
    private final SettlementRepository settlements;
    private final ResponsibilityService responsibility;
    private final TimelineRecorder timeline;

    public SettlementService(AppointmentRepository appts, SettlementRepository settlements,
                             ResponsibilityService responsibility, TimelineRecorder timeline) {
        this.appts = appts;
        this.settlements = settlements;
        this.responsibility = responsibility;
        this.timeline = timeline;
    }

    @Transactional
    public Settlement settle(Long apptId, SignResult signResult, Integer signedPieces,
                             String note, String actor) {
        Appointment a = appts.findById(apptId)
                .orElseThrow(() -> new AuthService.ApiException(404, "车次不存在"));
        if (a.getStatus() != ApptStatus.RELEASED
                && a.getStatus() != ApptStatus.DOWNGRADED
                && a.getStatus() != ApptStatus.REJECTED) {
            throw new AuthService.ApiException(409, "须先完成处置（放行/降级/拒收）才能结算");
        }
        a.setSignResult(signResult);
        a.setSignedPieces(signedPieces);

        ResponsibilityService.Verdict verdict = responsibility.judge(a);
        double carrierW = verdict.weights().getOrDefault(Party.CARRIER, 0d);
        double parkW = verdict.weights().getOrDefault(Party.PARK, 0d);
        double customerW = verdict.weights().getOrDefault(Party.CUSTOMER, 0d);

        // 运费基数：多货主按各自合同运费累加
        double freightBase = a.getCargoLines().stream()
                .map(l -> l.getCustomer())
                .distinct()
                .mapToDouble(Customer::getContractFreight)
                .sum();

        // 货损：化冻件按货主赔付单价全损，破损件按 50% 定损
        double claimPrice = a.getCargoLines().stream()
                .map(l -> l.getCustomer())
                .mapToDouble(Customer::getClaimPricePerPiece)
                .average().orElse(50);
        int thawed = a.getThawedPieces() == null ? 0 : a.getThawedPieces();
        int damaged = a.getDamagedPieces() == null ? 0 : a.getDamagedPieces();
        double cargoLoss = ViewAssembler.round2(thawed * claimPrice + damaged * claimPrice * 0.5);

        // 承运商固定违约金（按判责给承运商的异常）
        double exceptionFine = 0;
        int scoreDelta = 0;
        for (ExceptionEvent e : a.getExceptions()) {
            if (e.getResponsibleParty() != Party.CARRIER) continue;
            exceptionFine += switch (e.getType()) {
                case LATE, LICENSE_EXPIRED -> 300;
                case TEMP_GAP, DOCK_CONGESTION -> 500;
                case TEMP_EXCURSION -> 800;
                case SEAL_MISMATCH, THAW -> 1000;
                default -> 0;
            };
            scoreDelta -= e.getType().severity * 2;
        }

        Decision d = a.getDecision();
        double decisionPenalty = 0;
        if (d == Decision.REJECT) {
            // 拒收：承运商按责任比例承担运费损失 + 化冻全损
            decisionPenalty = ViewAssembler.round2(freightBase * 0.5 * carrierW + cargoLoss * carrierW);
            scoreDelta -= 10;
        } else if (d == Decision.DOWNGRADE) {
            // 降级：差价损失按责任分摊
            decisionPenalty = ViewAssembler.round2(cargoLoss * carrierW);
            scoreDelta -= 5;
        } else {
            // 放行：货损仍按责任赔付
            decisionPenalty = ViewAssembler.round2(cargoLoss * carrierW);
        }

        double carrierPenalty = ViewAssembler.round2(exceptionFine + decisionPenalty);
        double carrierPayable = ViewAssembler.round2(Math.max(0, freightBase - carrierPenalty));

        // 客户赔付：货损按非客户责任比例赔给客户（客户自身改仓等原因自担）
        double customerClaim = ViewAssembler.round2(cargoLoss * (carrierW + parkW));
        // 园区补偿：园区责任货损 + 园区责任异常每次 100 元服务抵扣（已关闭/免责的不计）
        long parkFaults = a.getExceptions().stream()
                .filter(e -> e.getResponsibleParty() == Party.PARK
                        && e.getStatus() != ExceptionStatus.CLOSED).count();
        double parkCompensation = ViewAssembler.round2(cargoLoss * parkW + parkFaults * 100);

        Settlement s = new Settlement();
        s.setCode("STL-" + LocalDate.now().format(FMT) + "-" + String.format("%04d", settlements.count() + 1));
        s.setFreightBase(freightBase);
        s.setCarrierPenalty(carrierPenalty);
        s.setCarrierPayable(carrierPayable);
        s.setCustomerClaim(customerClaim);
        s.setParkCompensation(parkCompensation);
        s.setResponsibilitySummary(verdict.summary());
        s.setScoreDelta(scoreDelta);
        s.setFinalDecision(d);
        s.setSettled(true);
        StringBuilder sn = new StringBuilder();
        sn.append("货损口径：化冻 ").append(thawed).append(" 件全损 + 破损 ").append(damaged)
          .append(" 件半损，单价 ").append(claimPrice).append(" 元/件；");
        if (customerW > 0) sn.append("客户自身责任比例 ").append(Math.round(customerW * 100))
          .append("% 部分不予赔付；");
        if (note != null) sn.append(note);
        s.setNote(sn.toString());
        settlements.save(s);

        a.setSettlement(s);
        a.setStatus(ApptStatus.SETTLED);
        a.setClosed(true);

        // 关闭仍开放的异常单
        a.getExceptions().stream()
                .filter(e -> e.getStatus() != ExceptionStatus.CLOSED)
                .forEach(e -> {
                    e.setStatus(ExceptionStatus.CLOSED);
                    e.setClosedAt(java.time.LocalDateTime.now());
                    e.setResolution("已随结算单 " + s.getCode() + " 结案");
                });

        // 承运商服务分与累计扣罚（影响后续预约优先级）
        int newScore = Math.max(0, Math.min(100, a.getCarrier().getServiceScore() + scoreDelta));
        a.getCarrier().setServiceScore(newScore);
        a.getCarrier().setTotalPenalty(ViewAssembler.round2(a.getCarrier().getTotalPenalty() + carrierPenalty));

        timeline.record(a, actor, "结算完成",
                "结算单 " + s.getCode() + "｜责任 " + verdict.summary()
                        + "｜运费基数 " + freightBase + "，承运商扣罚 " + carrierPenalty
                        + "，实付运费 " + carrierPayable + "，客户赔付 " + customerClaim
                        + "，园区补偿 " + parkCompensation + "｜服务分 " + scoreDelta
                        + "（当前 " + newScore + "）｜签收：" + signResult.label
                        + " " + (signedPieces == null ? "" : signedPieces + " 件"),
                "SETTLEMENT");
        appts.save(a);
        return s;
    }
}
