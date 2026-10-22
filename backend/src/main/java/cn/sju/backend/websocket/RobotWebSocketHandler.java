package cn.sju.backend.websocket;

import cn.sju.backend.util.JwtUtil;
import cn.sju.backend.websocket.message.ConnectedMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * WebSocket 统一处理器
 * <p>
 * 端点: /ws?token=xxx
 * 鉴权: 从 query param 的 token 解析 JWT
 * 心跳: 客户端每 30s 发送 ping，服务端回复 pong
 */
@Slf4j
@Component
public class RobotWebSocketHandler extends TextWebSocketHandler {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_ROLE = "role";

    @Autowired
    private WebSocketSessionManager sessionManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = null;
        String role = null;
        try {
            String token = extractToken(session.getUri());
            if (token == null || !jwtUtil.validateToken(token)) {
                log.warn("WebSocket 鉴权失败，关闭连接: sessionId={}", session.getId());
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid token"));
                return;
            }
            userId = jwtUtil.getUserId(token);
            role = jwtUtil.getRole(token);
        } catch (Exception e) {
            log.warn("WebSocket 鉴权异常: sessionId={}", session.getId(), e);
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("auth error"));
            return;
        }

        session.getAttributes().put(ATTR_USER_ID, userId);
        session.getAttributes().put(ATTR_ROLE, role);
        sessionManager.addSession(userId, role, session);

        // 发送 connected 事件
        ConnectedMessage connected = new ConnectedMessage(userId, session.getId(), role);
        session.sendMessage(new TextMessage(
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(connected)
        ));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if ("ping".equalsIgnoreCase(payload)) {
            session.sendMessage(new TextMessage("pong"));
        }
        // 其他消息目前忽略
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.removeSession(session);
    }

    private String extractToken(URI uri) {
        if (uri == null) {
            return null;
        }
        Map<String, String> params = UriComponentsBuilder.fromUri(uri).build().getQueryParams().toSingleValueMap();
        return params.get("token");
    }
}
