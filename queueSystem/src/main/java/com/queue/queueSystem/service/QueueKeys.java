package com.queue.queueSystem.service;

public final class QueueKeys {
    private QueueKeys() {}

    public static String running(String group, String qid) {
        return "running:{" + group + "}:" + qid;
    }

    public static String waiting(String group, String qid) {
        return "waiting:{" + group + "}:" + qid;
    }

    public static String config(String group) {
        return "config:{" + group + "}";
    }

    public static String sequence(String group) {
        return "seq:{" + group + "}";
    }
}
