package cn.sju.backend.websocket.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * connected 事件消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectedMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type = WsEventType.CONNECTED;

    private Long userId;

    private String sessionId;

    private String role;

    private Long timestamp = System.currentTimeMillis();

    public ConnectedMessage(Long userId, String sessionId, String role) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.role = role;
    }
}
