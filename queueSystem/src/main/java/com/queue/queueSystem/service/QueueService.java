package com.queue.queueSystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queue.queueSystem.security.UserDisconnectedEvent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class QueueService {

    private final RedisTemplate<String, String> redis;
    private final QueueNotifier notifier;

    private static final String ACTIVE  = "active_users";   // SET
    private static final String WAITING = "waiting_queue";  // ZSET
    private static final int    MAX_ACTIVE = 2;

    private final ObjectMapper om = new ObjectMapper();

    public QueueService(RedisTemplate<String, String> redis,
                        QueueNotifier notifier) {
        this.redis    = redis;
        this.notifier = notifier;
    }

    /* ======================= 입장 ======================= */
    public QueueResponse enter(String user) {
        if (redis.opsForSet().isMember(ACTIVE, user))
            return entered();

        if (activeSize() < MAX_ACTIVE) {
            redis.opsForSet().add(ACTIVE, user);
            broadcast();
            log.info("enter " + user);
            return entered();
        }
        redis.opsForZSet().add(WAITING, user, Instant.now().toEpochMilli());
        broadcast();
        log.info("queue " + user);
        return waiting(position(user));
    }

    /* ======================= 퇴장 ======================= */
    public void leave(String user) {
        if (user == null || user.isBlank()) return;
        boolean removed = redis.opsForSet().remove(ACTIVE, user) == 1;
        if (!removed && redis.opsForZSet().rank(WAITING, user) == null) return;
        redis.opsForZSet().remove(WAITING, user);
        promoteNextUser();
        broadcast();
        log.info("leave " + user);
    }

    /* =================== WS 연결 종료 =================== */
    @EventListener
    public void onWebsocketDisconnected(UserDisconnectedEvent ev) {
        if (ev.userId() != null) leave(ev.userId());
    }

    /* ====================== 상태 ======================= */
    public QueueStatus status() {
        return new QueueStatus(activeSize(), waitingSize());
    }

    public Map<String, Long> QueuePosition(String userId) {
        Long rank = redis.opsForZSet().rank("waiting_queue", userId);
        // ZRANK 는 0-based, UI 에선 1-based
        long pos = rank == null ? 0 : rank + 1;
        return Map.of("pos", pos);
    }


    /* ===================== 내부 로직 ==================== */
    private void promoteNextUser() {
        if (activeSize() >= MAX_ACTIVE) return;

        Set<String> next = redis.opsForZSet().range(WAITING, 0, 0);
        if (next == null || next.isEmpty()) return;

        String uid = next.iterator().next();
        redis.opsForZSet().remove(WAITING, uid);
        redis.opsForSet().add(ACTIVE, uid);
        notifier.sendToUser(uid, "{\"type\":\"ENTER\"}");
    }

    private void broadcast() {
        try {
            notifier.broadcast(om.writeValueAsString(status()));
        } catch (Exception e) {
            log.error("broadcast fail", e);
        }
    }

    /* ================== 보조 메서드 =================== */
    private long activeSize() {
        Long v = redis.opsForSet().size(ACTIVE);
        return v == null ? 0 : v;
    }
    private long waitingSize() {
        Long v = redis.opsForZSet().size(WAITING);
        return v == null ? 0 : v;
    }
    private long position(String user) {
        Long r = redis.opsForZSet().rank(WAITING, user);
        return r == null ? -1 : r + 1;
    }

    /* ===================== DTO ====================== */
    public record QueueStatus(long active, long waiting) {}
    public record QueueResponse(boolean entered, long position) {}
    private QueueResponse entered()            { return new QueueResponse(true, 0); }
    private QueueResponse waiting(long pos)    { return new QueueResponse(false, pos); }
}
