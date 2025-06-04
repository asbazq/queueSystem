package com.queue.queueSystem;

import com.queue.queueSystem.service.QueueJobService;
import com.queue.queueSystem.service.QueueNotifier;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class QueueJobServiceTest {
    @Test
    // redis가 null일 때 처리
    void sizeReturnsZeroWhenRedisSizeNull() {
        RedisTemplate<String, String> redis = Mockito.mock(RedisTemplate.class);
        ZSetOperations<String, String> zset = Mockito.mock(ZSetOperations.class);
        Mockito.when(redis.opsForZSet()).thenReturn(zset);
        Mockito.when(zset.size("running:test")).thenReturn(null);

        QueueNotifier notifier = Mockito.mock(QueueNotifier.class);
        QueueJobService job = new QueueJobService(redis, notifier);

        long res = ReflectionTestUtils.invokeMethod(job, "size", "running:test");
        assertEquals(0L, res);
    }
}