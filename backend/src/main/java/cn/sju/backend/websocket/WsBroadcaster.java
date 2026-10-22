package cn.sju.backend.websocket;

import cn.sju.backend.websocket.message.WsEventType;
import cn.sju.backend.websocket.message.WsMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * WebSocket 广播器 — 业务层统一调用入口
 * <p>
 * 提供 9 种事件的便捷广播方法，也可通过 {@link #broadcast(String, Object)} 发送自定义事件
 */
@Component
public class WsBroadcaster {

    @Autowired
    private WebSocketSessionManager sessionManager;

    /**
     * 通用广播
     */
    public void broadcast(String eventType, Object data) {
        sessionManager.broadcast(new WsMessage(eventType, data));
    }

    /**
     * 按角色广播
     */
    public void broadcastByRole(String eventType, Object data, String role) {
        sessionManager.broadcastByRole(role, new WsMessage(eventType, data));
    }

    /**
     * 向指定用户广播
     */
    public void sendToUser(String eventType, Object data, Long userId) {
        sessionManager.sendToUser(userId, new WsMessage(eventType, data));
    }

    // ====== 9 种事件的便捷方法 ======

    public void sendRobotStatus(Object data) {
        broadcast(WsEventType.ROBOT_STATUS, data);
    }

    public void sendTaskStatus(Object data) {
        broadcast(WsEventType.TASK_STATUS, data);
    }

    public void sendTaskProgress(Object data) {
        broadcast(WsEventType.TASK_PROGRESS, data);
    }

    public void sendAlarmNew(Object data) {
        broadcast(WsEventType.ALARM_NEW, data);
    }

    public void sendPlateNew(Object data) {
        broadcast(WsEventType.PLATE_NEW, data);
    }

    public void sendPhotoRequest(Object data) {
        broadcast(WsEventType.PHOTO_REQUEST, data);
    }

    public void sendPhotoCommand(Object data) {
        broadcast(WsEventType.PHOTO_COMMAND, data);
    }

    public void sendRecognitionResult(Object data) {
        broadcast(WsEventType.RECOGNITION_RESULT, data);
    }
}
