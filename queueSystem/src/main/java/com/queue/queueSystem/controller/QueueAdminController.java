package com.queue.queueSystem.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import com.queue.queueSystem.security.QueueConfig;

import java.util.Map;

@RestController
@RequestMapping("/admin/queue")
@RequiredArgsConstructor
public class QueueAdminController {

    private final RedisTemplate<String, String> redis;

    /** 현재 설정 조회 */
    @GetMapping
    public QueueConfig getConfig() {
        Map<Object,Object> site = redis.opsForHash().entries("config:{site}");
        Map<Object,Object> predict = redis.opsForHash().entries("config:{predict}");
        return QueueConfig.from(site, predict);
    }

    /** throughput, sessionTtlMillis 동시 갱신 */
    @PostMapping("/{group}")
    public void updateConfig(@PathVariable String group,
                             @RequestParam(required = false) Integer throughput,
                             @RequestParam(required = false) Long sessionTtlMillis,
                             @RequestParam(required = false) Integer maxRunning) {
        if (!"site".equals(group) && !"predict".equals(group)) {
            throw new IllegalArgumentException("Unsupported group: " + group);
        }
        String key = "config:{" + group + "}";
        if (throughput != null) {
            redis.opsForHash().put(key, "throughput", throughput.toString());
        }
        if (sessionTtlMillis != null) {
            redis.opsForHash().put(key, "sessionTtlMillis", sessionTtlMillis.toString());
        }
        if (maxRunning != null) {
            redis.opsForHash().put(key, "maxRunning", maxRunning.toString());
        }
    }
}
