package com.queue.queueSystem;

import com.queue.queueSystem.security.QueueConfig;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class QueueConfigTest {
    @Test
    void defaultValuesWhenMapIsEmpty() {
        Map<Object,Object> map = new HashMap<>();
        QueueConfig cfg = QueueConfig.from(map);
        assertEquals(10, cfg.throughput());
        assertEquals(30_000L, cfg.sessionTtlMillis());
    }

    @Test
    void valuesFromProvidedMap() {
        Map<Object,Object> map = new HashMap<>();
        map.put("throughput", "5");
        map.put("sessionTtlMillis", "60000");
        QueueConfig cfg = QueueConfig.from(map);
        assertEquals(5, cfg.throughput());
        assertEquals(60_000L, cfg.sessionTtlMillis());
    }
}