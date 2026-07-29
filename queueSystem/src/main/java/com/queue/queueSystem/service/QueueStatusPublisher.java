package com.queue.queueSystem.service;

import java.util.Set;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class QueueStatusPublisher {
    private final RedisTemplate<String, String> redis;
    private final QueueNotifier notifier;
    private final ObjectMapper objectMapper;

    public void publish(String group) {
        try {
            long runningVip = size(QueueKeys.running(group, "vip"));
            long runningMain = size(QueueKeys.running(group, "main"));
            long waitingVip = size(QueueKeys.waiting(group, "vip"));
            long waitingMain = size(QueueKeys.waiting(group, "main"));
            publishQueue(group, "vip", runningVip, runningMain, waitingVip, waitingMain);
            publishQueue(group, "main", runningVip, runningMain, waitingVip, waitingMain);
        } catch (Exception e) {
            log.error("Queue status publish failed for group={}", group, e);
        }
    }

    private void publishQueue(String group, String qid, long runningVip, long runningMain,
                              long waitingVip, long waitingMain) {
        Set<String> waiters = redis.opsForZSet().range(QueueKeys.waiting(group, qid), 0, -1);
        if (waiters == null) return;

        long index = 0;
        for (String uid : waiters) {
            long localRank = ++index;
            long position = "main".equals(qid) ? waitingVip + localRank : localRank;
            ObjectNode message = objectMapper.createObjectNode()
                .put("type", "STATUS")
                .put("group", group)
                .put("qid", qid)
                .put("running", runningVip + runningMain)
                .put("runningVip", runningVip)
                .put("runningMain", runningMain)
                .put("waitingVip", waitingVip)
                .put("waitingMain", waitingMain)
                .put("waiting", waitingVip + waitingMain)
                .put("pos", position);
            notifier.sendToUser(group, uid, message.toString());
        }
    }

    private long size(String key) {
        Long value = redis.opsForZSet().size(key);
        return value == null ? 0 : value;
    }
}
