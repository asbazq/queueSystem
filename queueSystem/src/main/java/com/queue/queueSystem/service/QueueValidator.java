package com.queue.queueSystem.service;

import org.springframework.stereotype.Component;

@Component
public class QueueValidator {
    public void validateGroup(String group) {
        if (!isValidGroup(group)) {
            throw new IllegalArgumentException("Unsupported group: " + group);
        }
    }

    public void validate(String group, String qid, String userId) {
        validateGroup(group);
        if (!isValidQid(qid)) {
            throw new IllegalArgumentException("Unsupported qid: " + qid);
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
    }

    public boolean isValid(String group, String qid, String userId) {
        return isValidGroup(group) && isValidQid(qid)
            && userId != null && !userId.isBlank();
    }

    private boolean isValidGroup(String group) {
        return "site".equals(group) || "predict".equals(group);
    }

    private boolean isValidQid(String qid) {
        return "vip".equals(qid) || "main".equals(qid);
    }
}
