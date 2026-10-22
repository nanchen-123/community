package cn.sju.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话管理器
 * <p>
 * 维护所有已连接的会话，支持按 userId / role 分组广播
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    /** userId -> sessionId */
    private final Map<Long, Set<String>> userSessions = new ConcurrentHashMap<>();
    /** sessionId -> userId */
    private final Map<String, Long> sessionUsers = new ConcurrentHashMap<>();
    /** sessionId -> role */
    private final Map<String, String> sessionRoles = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void addSession(Long userId, String role, WebSocketSession session) {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        sessionUsers.put(sessionId, userId);
        sessionRoles.put(sessionId, role);
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        log.info("WebSocket 连接建立: sessionId={}, userId={}, role={}", sessionId, userId, role);
    }

    public void removeSession(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        Long userId = sessionUsers.remove(sessionId);
        sessionRoles.remove(sessionId);
        if (userId != null) {
            Set<String> ids = userSessions.get(userId);
            if (ids != null) {
                ids.remove(sessionId);
                if (ids.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
        }
        log.info("WebSocket 连接关闭: sessionId={}", sessionId);
    }

    public boolean isEmpty() {
        return sessions.isEmpty();
    }

    /**
     * 广播消息给所有在线会话
     */
    public void broadcast(Object message) {
        sendToOpenSessions(sessions.values(), message);
    }

    /**
     * 广播消息给指定角色的会话
     */
    public void broadcastByRole(String role, Object message) {
        java.util.List<WebSocketSession> targets = new java.util.ArrayList<>();
        sessions.forEach((sessionId, session) -> {
            if (role.equals(sessionRoles.get(sessionId))) {
                targets.add(session);
            }
        });
        sendToOpenSessions(targets, message);
    }

    /**
     * 向指定 userId 发送
     */
    public void sendToUser(Long userId, Object message) {
        Set<String> ids = userSessions.get(userId);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        java.util.List<WebSocketSession> targets = new java.util.ArrayList<>();
        ids.forEach(id -> {
            WebSocketSession session = sessions.get(id);
            if (session != null) {
                targets.add(session);
            }
        });
        sendToOpenSessions(targets, message);
    }

    private void sendToOpenSessions(java.util.Collection<WebSocketSession> targets, Object message) {
        if (targets.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            log.error("WebSocket 消息序列化失败", e);
            return;
        }
        TextMessage textMessage = new TextMessage(payload);
        targets.forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    log.warn("WebSocket 消息发送失败: sessionId={}", session.getId(), e);
                }
            }
        });
    }
}
