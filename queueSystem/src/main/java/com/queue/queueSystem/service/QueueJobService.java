package com.queue.queueSystem.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.queue.queueSystem.security.QueueConfig;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueJobService {

    private final RedisTemplate<String, String> redis;
    private final QueueNotifier notifier;

    private static final String WAITING_PREFIX = "waiting:";
    private static final String RUNNING_PREFIX = "running:";
    private static final String FINISHED_PREFIX = "finished:";

    /** 1초마다 Waiting → Running 승격 */
    @Scheduled(fixedRate = 1_000)
    public void promote() {
        Set<String> keys = redis.keys(WAITING_PREFIX + "*");
        if (keys == null) return;

        for (String waitKey : keys) {
            String qid = waitKey.substring(WAITING_PREFIX.length());
            QueueConfig cfg = QueueConfig.from(redis.opsForHash().entries("config:" + qid));
            int tp = cfg.throughput();
            if (tp <= 0) continue;

            Set<String> batch = redis.opsForZSet().range(waitKey, 0, tp - 1);
            if (batch == null || batch.isEmpty()) continue;

            long now = System.currentTimeMillis();
            redis.executePipelined((RedisCallback<Void>) conn -> {
                for (String uid : batch) {
                    conn.zRem(waitKey.getBytes(), uid.getBytes());
                    conn.zAdd((RUNNING_PREFIX + qid).getBytes(), now, uid.getBytes());
                }
                return null;
            });

            // 개인 알림 + 전체 현황
            batch.forEach(uid -> notifier.sendToUser(uid, "{\"type\":\"ENTER\"}"));
            notifier.broadcast(statusJson(qid));
        }
    }

    /** 10초마다 Running → Finished (세션 TTL 만료) */
    @Scheduled(fixedRate = 10_000)
    public void expire() {
        Set<String> keys = redis.keys(RUNNING_PREFIX + "*");
        if (keys == null) return;

        for (String runKey : keys) {
            String qid = runKey.substring(RUNNING_PREFIX.length());
            long cutoff = System.currentTimeMillis()
                          - QueueConfig.from(redis.opsForHash().entries("config:" + qid))
                                       .sessionTtlMillis();

            Set<String> expired = redis.opsForZSet().rangeByScore(runKey, 0, cutoff);
            if (expired == null || expired.isEmpty()) continue;

            redis.executePipelined((RedisCallback<Void>) conn -> {
                for (String uid : expired) {
                    conn.zRem(runKey.getBytes(), uid.getBytes());
                    conn.zAdd((FINISHED_PREFIX + qid).getBytes(),
                              System.currentTimeMillis(), uid.getBytes());
                }
                return null;
            });

            // 전체 현황만 브로드캐스트 (개인 TIMEOUT 알림도 가능)
            notifier.broadcast(statusJson(qid));
        }
    }

    private String statusJson(String qid) {
        long w = redis.opsForZSet().size(WAITING_PREFIX + qid);
        long r = redis.opsForZSet().size(RUNNING_PREFIX + qid);
        return String.format("{\"queue\":\"%s\",\"waiting\":%d,\"running\":%d}", qid, w, r);
    }
}
