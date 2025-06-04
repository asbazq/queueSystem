package com.queue.queueSystem;

import com.queue.queueSystem.service.QueueNotifier;
import com.queue.queueSystem.service.QueueService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import static org.junit.jupiter.api.Assertions.*;

class QueueServiceTest {
    @Test
    // 두 대기열 모두에서 실행 및 대기 횟수 집계
    void statusFromBothQueues() {
        RedisTemplate<String, String> redis = Mockito.mock(RedisTemplate.class);
        ZSetOperations<String, String> zset = Mockito.mock(ZSetOperations.class);
        Mockito.when(redis.opsForZSet()).thenReturn(zset);

        Mockito.when(zset.size("running:vip")).thenReturn(2L);
        Mockito.when(zset.size("running:main")).thenReturn(1L);
        Mockito.when(zset.size("waiting:vip")).thenReturn(3L);
        Mockito.when(zset.size("waiting:main")).thenReturn(4L);

        QueueNotifier notifier = Mockito.mock(QueueNotifier.class);
        QueueService svc = new QueueService(redis, notifier);

        QueueService.QueueStatus st = svc.status("main");
        assertEquals(3L, st.running());
        assertEquals(7L, st.waiting());
        assertEquals(3L, st.waitingVip());
        assertEquals(4L, st.waitingMain());
    }
}