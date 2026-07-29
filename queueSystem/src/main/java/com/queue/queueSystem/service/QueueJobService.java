package com.queue.queueSystem.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.queue.queueSystem.security.QueueConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Profile("!crawler")
@RequiredArgsConstructor
public class QueueJobService {

    private static final String WAITING_PREFIX = "waiting:";
    private static final String RUNNING_PREFIX = "running:";
    private static final int VIP_STREAK_LIMIT = 3;

    private final RedisTemplate<String, String> redis;
    private final QueueNotifier notifier;
    private final QueueStatusPublisher statusPublisher;

    private static final String LUA_EXPIRE_AND_PROMOTE_WITH_CAP = """
    -- KEYS: 1 vipRunKey, 2 mainRunKey, 3 targetRunKey, 4 vipWaitKey, 5 mainWaitKey, 6 configKey
    -- ARGV: 1 nowMs, 2 ttlMillis, 3 fallbackMaxRunning, 4 vipStreakLimit

    local vipRunKey    = KEYS[1]
    local mainRunKey   = KEYS[2]
    local targetRunKey = KEYS[3]
    local vipWaitKey   = KEYS[4]
    local mainWaitKey  = KEYS[5]
    local configKey    = KEYS[6]

    local nowMs  = tonumber(ARGV[1])
    local ttlMs  = tonumber(ARGV[2])
    local fmax   = tonumber(ARGV[3]) or 30
    local limit  = tonumber(ARGV[4]) or 3
    local cutoff = nowMs - ttlMs

    local expired = redis.call('ZRANGEBYSCORE', targetRunKey, 0, cutoff)
    local expiredCount = #expired
    if expiredCount > 0 then
      redis.call('ZREMRANGEBYSCORE', targetRunKey, 0, cutoff)
    end

    local function maxRunning()
      local m = redis.call('HGET', configKey, 'maxRunning')
      local v = tonumber(m) or fmax
      if not v or v < 1 then v = 1 end
      return v
    end

    local function totalRunning()
      return (redis.call('ZCARD', vipRunKey) or 0) + (redis.call('ZCARD', mainRunKey) or 0)
    end

    local promoted = {}

    for i = 1, expiredCount do
      if totalRunning() >= maxRunning() then
        break
      end

      local vipStreak = tonumber(redis.call('HGET', configKey, 'vip_streak')) or 0
      local primaryWait   = (targetRunKey == vipRunKey) and vipWaitKey  or mainWaitKey
      local secondaryWait = (targetRunKey == vipRunKey) and mainWaitKey or vipWaitKey

      local waitMain = redis.call('ZCARD', mainWaitKey) or 0
      if (targetRunKey == vipRunKey) and (vipStreak >= limit) and (waitMain > 0) then
        primaryWait, secondaryWait = mainWaitKey, vipWaitKey
      end

      local selectedWait = primaryWait
      local popped = redis.call('ZPOPMIN', selectedWait, 1)
      if (not popped) or (#popped == 0) then
        selectedWait = secondaryWait
        popped = redis.call('ZPOPMIN', selectedWait, 1)
      end

      if not popped or #popped == 0 then
        break
      end

      local uid = popped[1]
      table.insert(promoted, uid)
      local destinationRunKey = (selectedWait == vipWaitKey) and vipRunKey or mainRunKey
      redis.call('ZADD', destinationRunKey, nowMs, uid)

      if targetRunKey == vipRunKey then
        if selectedWait == vipWaitKey then
          redis.call('HINCRBY', configKey, 'vip_streak', 1)
        else
          redis.call('HSET', configKey, 'vip_streak', 0)
        end
      else
        redis.call('HSET', configKey, 'vip_streak', 0)
      end
    end

    local res = {}
    table.insert(res, tostring(expiredCount))
    for i = 1, expiredCount do
      table.insert(res, expired[i])
    end
    table.insert(res, tostring(#promoted))
    for i = 1, #promoted do
      table.insert(res, promoted[i])
    end
    return res
    """;

    @SuppressWarnings("rawtypes")
    private final RedisScript<List> expireAndPromoteScript =
        new DefaultRedisScript<>(LUA_EXPIRE_AND_PROMOTE_WITH_CAP, List.class);

    @Scheduled(fixedRate = 10_000, initialDelayString = "20000")
    public void expire() {
        long now = System.currentTimeMillis();
        var siteMap = redis.opsForHash().entries("config:{site}");
        var predictMap = redis.opsForHash().entries("config:{predict}");
        QueueConfig config = QueueConfig.from(siteMap, predictMap);

        processOne(now, "site", "vip", config);
        processOne(now, "site", "main", config);
        statusPublisher.publish("site");

        processOne(now, "predict", "vip", config);
        processOne(now, "predict", "main", config);
        statusPublisher.publish("predict");
    }

    private void processOne(long now, String group, String qid, QueueConfig config) {
        long ttl = "site".equals(group) ? config.sessionTtlMillisSite() : config.sessionTtlMillisPredict();
        if (ttl <= 0) {
            return;
        }

        int fallbackMax = "site".equals(group) ? config.maxRunningSite() : config.maxRunningPredict();
        if (fallbackMax < 1) {
            fallbackMax = 1;
        }

        String vipRun = RUNNING_PREFIX + "{" + group + "}:vip";
        String mainRun = RUNNING_PREFIX + "{" + group + "}:main";
        String runKey = RUNNING_PREFIX + "{" + group + "}:" + qid;
        String vipWait = WAITING_PREFIX + "{" + group + "}:vip";
        String mainWait = WAITING_PREFIX + "{" + group + "}:main";
        String cfgKey = "config:{" + group + "}";

        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) redis.execute(
            expireAndPromoteScript,
            List.of(vipRun, mainRun, runKey, vipWait, mainWait, cfgKey),
            String.valueOf(now),
            String.valueOf(ttl),
            String.valueOf(fallbackMax),
            String.valueOf(VIP_STREAK_LIMIT)
        );

        if (result == null || result.isEmpty()) {
            return;
        }

        int idx = 0;
        int expiredCount = Integer.parseInt((String) result.get(idx++));
        List<String> expired = new ArrayList<>(expiredCount);
        for (int i = 0; i < expiredCount; i++) {
            expired.add((String) result.get(idx++));
        }

        int promotedCount = Integer.parseInt((String) result.get(idx++));
        List<String> promoted = new ArrayList<>(promotedCount);
        for (int i = 0; i < promotedCount; i++) {
            promoted.add((String) result.get(idx++));
        }

        for (String uid : expired) {
            notifier.sendToUser(group, uid, "{\"type\":\"TIMEOUT\"}");
        }
        for (String uid : promoted) {
            notifier.sendToUser(group, uid, "{\"type\":\"ENTER\"}");
        }
    }

    private long size(String key) {
        Long v = redis.opsForZSet().size(key);
        return v == null ? 0L : v;
    }
}
