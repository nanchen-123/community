package cn.sju.backend.websocket.message;

/**
 * WebSocket 事件类型
 */
public final class WsEventType {

    public static final String CONNECTED = "connected";
    public static final String ROBOT_STATUS = "robot_status";
    public static final String TASK_STATUS = "task_status";
    public static final String TASK_DISPATCH = "task_dispatch";
    public static final String TASK_PROGRESS = "task_progress";
    public static final String ALARM_NEW = "alarm_new";
    public static final String PLATE_NEW = "plate_new";
    public static final String PHOTO_REQUEST = "photo_request";
    public static final String PHOTO_COMMAND = "photo_command";
    public static final String RECOGNITION_RESULT = "recognition_result";

    private WsEventType() {
    }
}
