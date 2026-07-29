package com.queue.queueSystem.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.util.UriComponentsBuilder;

import com.queue.queueSystem.service.QueueNotifier;
import com.queue.queueSystem.service.QueueValidator;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class QueueWebSocketHandler extends TextWebSocketHandler implements QueueNotifier {

    private final ApplicationEventPublisher publisher;
    private final QueueValidator validator;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public QueueWebSocketHandler(ApplicationEventPublisher publisher, QueueValidator validator) {
        this.publisher = publisher;
        this.validator = validator;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        if (session.getUri() == null) {
            return;
        }

        var qs = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams();
        String group = qs.getFirst("group");
        String qid = qs.getFirst("qid");
        String userId = qs.getFirst("userId");
        if (!validator.isValid(group, qid, userId)) {
            try {
                session.close(CloseStatus.BAD_DATA.withReason("Invalid queue parameters"));
            } catch (Exception ex) {
                log.debug("Failed to close invalid WebSocket session {}", session.getId(), ex);
            }
            return;
        }

        String key = userKey(group, userId);
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(session, 10_000, 64 * 1024);
        WebSocketSession old = sessions.put(key, safeSession);
        if (old != null && old.isOpen()) {
            try {
                old.close(CloseStatus.NORMAL);
            } catch (Exception ignore) {
            }
        }
        log.debug("WS OPEN group={} qid={} userId={}", group, qid, userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String group = null;
        String qid = null;
        String userId = null;
        boolean removedCurrentSession = false;

        if (session.getUri() != null) {
            var qs = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams();
            group = qs.getFirst("group");
            qid = qs.getFirst("qid");
            userId = qs.getFirst("userId");
            WebSocketSession current = sessions.get(userKey(group, userId));
            if (current instanceof ConcurrentWebSocketSessionDecorator decorated
                && decorated.getDelegate().equals(session)) {
                removedCurrentSession = sessions.remove(userKey(group, userId), current);
            } else {
                removedCurrentSession = sessions.remove(userKey(group, userId), session);
            }
        } else {
            removedCurrentSession = sessions.entrySet().removeIf(entry -> entry.getValue().equals(session));
        }

        log.debug(
            "WS CLOSE group={} qid={} userId={} activeSessionRemoved={}",
            group,
            qid,
            userId,
            removedCurrentSession
        );

        // A socket close is transient. Redis membership is removed only by explicit
        // POST /queue/leave or heartbeat TTL expiry.
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (!"PING".equalsIgnoreCase(message.getPayload()) || session.getUri() == null) {
            return;
        }

        var qs = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams();
        String group = qs.getFirst("group");
        String userId = qs.getFirst("userId");
        String qid = qs.getFirst("qid");
        publisher.publishEvent(new UserPingEvent(group, qid, userId));
    }

    @Override
    public void broadcast(String msg) {
        sessions.values().forEach(session -> sendSilently(session, msg));
    }

    @Override
    public void sendToUser(String group, String userId, String msg) {
        WebSocketSession session = sessions.get(userKey(group, userId));
        if (session != null) {
            sendSilently(session, msg);
        }
    }

    private void sendSilently(WebSocketSession session, String message) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(message));
        } catch (Exception ex) {
            log.warn("WS send fail {}", session.getId(), ex);
        }
    }

    private static String userKey(String group, String userId) {
        return group + "|" + userId;
    }
}
