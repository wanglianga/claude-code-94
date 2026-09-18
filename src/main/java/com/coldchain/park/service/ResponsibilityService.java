package com.coldchain.park.service;

import com.coldchain.park.domain.Appointment;
import com.coldchain.park.domain.ExceptionEvent;
import com.coldchain.park.domain.ExceptionType;
import com.coldchain.park.domain.Party;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 责任判定：把车次上的异常按"到园前/后 + 异常类型"折算成 承运商 / 园区 / 客户 三方权重。
 * - 运输途中（到园前）的失温、断点、晚到、封签 -> 承运商
 * - 月台拥堵、限电、夜间排队 -> 园区（夜间排队若承运商明知夜班仍约晚到，按比例分摊）
 * - 货主临时改仓 -> 客户
 * 人工已在异常单上改判的责任方以人工判定为准。
 */
@Service
public class ResponsibilityService {

    public record Share(Party party, double weight, List<String> reasons) {}

    public record Verdict(Map<Party, Double> weights, List<Share> shares, String summary) {}

    public Verdict judge(Appointment a) {
        List<ExceptionEvent> events = a.getExceptions().stream()
                .filter(e -> e.getStatus() != com.coldchain.park.domain.ExceptionStatus.CLOSED
                        || (e.getResolution() != null && !e.getResolution().isBlank()))
                .toList();

        // 初始权重按异常严重度计分
        Map<Party, Double> score = new LinkedHashMap<>();
        score.put(Party.CARRIER, 0d);
        score.put(Party.PARK, 0d);
        score.put(Party.CUSTOMER, 0d);
        Map<Party, List<String>> reasons = new LinkedHashMap<>();
        reasons.put(Party.CARRIER, new java.util.ArrayList<>());
        reasons.put(Party.PARK, new java.util.ArrayList<>());
        reasons.put(Party.CUSTOMER, new java.util.ArrayList<>());

        for (ExceptionEvent e : events) {
            // 环境性异常（拥堵/限电/夜间排队）一旦解除关闭即不再追责
            if (e.getStatus() == com.coldchain.park.domain.ExceptionStatus.CLOSED
                    && (e.getType() == ExceptionType.LOAD_SHEDDING
                    || e.getType() == ExceptionType.NIGHT_QUEUE
                    || e.getType() == ExceptionType.DOCK_CONGESTION)) {
                continue;
            }
            Party p = e.getResponsibleParty() == null ? e.getType().defaultParty : e.getResponsibleParty();
            double w = e.getType().severity;

            // 夜间排队 + 晚到同时存在（排队异常仍未关闭）：园区与承运商分摊
            if (e.getType() == ExceptionType.NIGHT_QUEUE) {
                boolean alsoLate = a.getExceptions().stream().anyMatch(x -> x.getType() == ExceptionType.LATE);
                if (alsoLate) {
                    score.merge(Party.CARRIER, w * 0.5, Double::sum);
                    score.merge(Party.PARK, w * 0.5, Double::sum);
                    reasons.get(Party.CARRIER).add("明知夜班人力受限仍晚到，承担夜间等待一半责任");
                    reasons.get(Party.PARK).add("夜班质检容量有限造成夜间排队");
                    continue;
                }
            }
            // 化冻且限电异常仍生效：承运商主责 70%，园区分摊 30%
            if (e.getType() == ExceptionType.THAW) {
                boolean power = a.getExceptions().stream()
                        .anyMatch(x -> x.getType() == ExceptionType.LOAD_SHEDDING
                                && x.getStatus() != com.coldchain.park.domain.ExceptionStatus.CLOSED);
                if (power) {
                    score.merge(Party.CARRIER, w * 0.7, Double::sum);
                    score.merge(Party.PARK, w * 0.3, Double::sum);
                    reasons.get(Party.PARK).add("限电影响冷库保障，对化冻承担 30% 责任");
                    reasons.get(Party.CARRIER).add("运输温度异常在先，化冻主责 70%");
                    continue;
                }
            }
            score.merge(p, w, Double::sum);
            reasons.get(p).add(e.getType().label + "：" + e.getDetail());
        }

        double total = score.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<Party, Double> weights = new LinkedHashMap<>();
        List<Share> shares = new java.util.ArrayList<>();
        if (total <= 0) {
            weights.put(Party.NONE, 1.0);
            shares.add(new Share(Party.NONE, 1.0, List.of("全程无有效异常，各方免责")));
        } else {
            for (Party p : List.of(Party.CARRIER, Party.PARK, Party.CUSTOMER)) {
                double w = Math.round(score.getOrDefault(p, 0d) / total * 100.0) / 100.0;
                if (w > 0) {
                    weights.put(p, w);
                    shares.add(new Share(p, w, reasons.get(p)));
                }
            }
        }

        String summary = shares.stream()
                .map(s -> s.party().label + Math.round(s.weight() * 100) + "%")
                .reduce((x, y) -> x + " / " + y)
                .orElse(Party.NONE.label);
        return new Verdict(weights, shares, summary);
    }
}
