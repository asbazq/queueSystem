package com.queue.queueSystem.security;

// import java.util.Map;

// public record QueueConfig(int throughput, long sessionTtlMillis) {

//     /* Redis HASH → 객체 변환 (기본값: 10 req/s, TTL 2 min) */
//     public static QueueConfig from(Map<Object, Object> m) {
//         int tp   = m.getOrDefault("throughput", "10")        instanceof String s ? Integer.parseInt(s) : 10;
//         long ttl = m.getOrDefault("sessionTtlMillis", "120000") instanceof String s ? Long.parseLong(s) : 120_000L;
//         return new QueueConfig(tp, ttl);
//     }
// }

import java.util.Map;

public record QueueConfig(int throughput, long sessionTtlMillis) {

    /** Redis HASH → QueueConfig 변환 (기본값: throughput=10, TTL=120_000ms) */
    public static QueueConfig from(Map<Object,Object> m) {
        int tp = 10;
        long ttl = 120_000L;
        if (m.containsKey("throughput")) {
            tp = Integer.parseInt((String)m.get("throughput"));
        }
        if (m.containsKey("sessionTtlMillis")) {
            ttl = Long.parseLong((String)m.get("sessionTtlMillis"));
        }
        return new QueueConfig(tp, ttl);
    }
}
