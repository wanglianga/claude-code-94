package com.logpark.coldchain.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 园区运营时钟。设置 PARK_CLOCK 后，以该时刻为基准，按真实流逝速度推进，
 * 便于演示“晚到 / 夜间排队 / 加急”等时间相关场景。
 */
@Service
public class ParkClock {

    private final LocalDateTime base;
    private final long baseEpochMillis = System.currentTimeMillis();

    public ParkClock(@Value("${park.clock:}") String configured) {
        LocalDateTime b = null;
        if (configured != null && !configured.isBlank()) {
            try {
                b = LocalDateTime.parse(configured.trim());
            } catch (Exception ignored) {
                b = null;
            }
        }
        this.base = b;
    }

    public LocalDateTime now() {
        if (base == null) {
            return LocalDateTime.now();
        }
        long delta = System.currentTimeMillis() - baseEpochMillis;
        return base.plus(Duration.ofMillis(delta));
    }
}
