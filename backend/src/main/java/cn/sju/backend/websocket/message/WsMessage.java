package cn.sju.backend.websocket.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * WebSocket 消息基类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WsMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;

    private Object data;

    private Long timestamp = System.currentTimeMillis();

    public WsMessage(String type, Object data) {
        this.type = type;
        this.data = data;
    }
}
