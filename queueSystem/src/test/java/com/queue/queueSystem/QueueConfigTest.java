package com.queue.queueSystem;

import com.queue.queueSystem.security.QueueConfig;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class QueueConfigTest {
    @Test
    void defaultValuesWhenMapIsEmpty() {
        Map<Object,Object> site = new HashMap<>();
        Map<Object,Object> predict = new HashMap<>();
        QueueConfig cfg = QueueConfig.from(site, predict);
        assertEquals(10, cfg.throughputSite());
        assertEquals(30_000L, cfg.sessionTtlMillisSite());
        assertEquals(30, cfg.maxRunningSite());
        assertEquals(10, cfg.throughputPredict());
        assertEquals(30_000L, cfg.sessionTtlMillisPredict());
        assertEquals(30, cfg.maxRunningPredict());
    }

    @Test
    void valuesFromProvidedMap() {
        Map<Object,Object> site = new HashMap<>();
        site.put("throughput", "5");
        site.put("sessionTtlMillis", "60000");
        site.put("maxRunning", "3");
        Map<Object,Object> predict = new HashMap<>();
        predict.put("throughput", "8");
        predict.put("sessionTtlMillis", "45000");
        predict.put("maxRunning", "2");

        QueueConfig cfg = QueueConfig.from(site, predict);
        assertEquals(5, cfg.throughputSite());
        assertEquals(60_000L, cfg.sessionTtlMillisSite());
        assertEquals(3, cfg.maxRunningSite());
        assertEquals(8, cfg.throughputPredict());
        assertEquals(45_000L, cfg.sessionTtlMillisPredict());
        assertEquals(2, cfg.maxRunningPredict());
    }
}
