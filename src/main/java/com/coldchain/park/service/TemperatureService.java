package com.coldchain.park.service;

import com.coldchain.park.domain.TempZone;
import com.coldchain.park.domain.TemperatureReading;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 温控原始数据分析：曲线断点（采样间隔过大）、温度超标（失温/化冻风险）统计。
 * 原始数据始终保留，分析结论只作为异常/责任判定输入。
 */
@Service
public class TemperatureService {

    public record Gap(LocalDateTime from, LocalDateTime to, long minutes) {}

    public record Excursion(LocalDateTime time, double tempC, double limit) {}

    public record Analysis(int pointCount,
                           Double minC, Double maxC, Double lastC,
                           List<Gap> gaps,
                           List<Excursion> excursions,
                           double excursionRate,
                           boolean hasGap,
                           boolean overLimit) {}

    public Analysis analyze(List<TemperatureReading> readings, TempZone zone, int maxGapMinutes) {
        List<TemperatureReading> sorted = new ArrayList<>(readings);
        sorted.sort(Comparator.comparing(TemperatureReading::getSampleTime));

        List<Gap> gaps = new ArrayList<>();
        List<Excursion> excursions = new ArrayList<>();
        Double min = null, max = null, last = null;

        TemperatureReading prev = null;
        for (TemperatureReading r : sorted) {
            double t = r.getTempC();
            min = min == null ? t : Math.min(min, t);
            max = max == null ? t : Math.max(max, t);
            last = t;
            if (prev != null) {
                long mins = Duration.between(prev.getSampleTime(), r.getSampleTime()).toMinutes();
                if (mins > maxGapMinutes) {
                    gaps.add(new Gap(prev.getSampleTime(), r.getSampleTime(), mins));
                }
            }
            if (zone != null && t > zone.alarmMaxC) {
                excursions.add(new Excursion(r.getSampleTime(), t, zone.alarmMaxC));
            }
            prev = r;
        }
        double rate = sorted.isEmpty() ? 0 : (double) excursions.size() / sorted.size();
        return new Analysis(sorted.size(), min, max, last, gaps, excursions,
                Math.round(rate * 1000) / 1000.0, !gaps.isEmpty(), !excursions.isEmpty());
    }

    /** 客服可理解的温度曲线说明 */
    public Map<String, Object> explain(Analysis a, TempZone zone) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("points", a.pointCount());
        m.put("minC", a.minC());
        m.put("maxC", a.maxC());
        m.put("lastC", a.lastC());
        m.put("alarmMaxC", zone == null ? null : zone.alarmMaxC);
        m.put("gapCount", a.gaps().size());
        m.put("excursionCount", a.excursions().size());
        m.put("excursionRate", a.excursionRate());
        m.put("hasGap", a.hasGap());
        m.put("overLimit", a.overLimit());
        List<Map<String, Object>> gapList = new ArrayList<>();
        for (Gap g : a.gaps()) {
            Map<String, Object> gm = new LinkedHashMap<>();
            gm.put("from", g.from());
            gm.put("to", g.to());
            gm.put("missingMinutes", g.minutes());
            gapList.add(gm);
        }
        m.put("gaps", gapList);
        return m;
    }
}
