package cn.sju.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 机器人在线状态（内存）
 * <p>
 * 机器人通过 PUT /api/robot/state 上报心跳与位姿，超过 robot.online-ttl-ms 未上报即视为离线。
 */
@Slf4j
@Service
public class RobotStateService {

    /** robotId -> 最近一次上报的状态 */
    private final Map<String, Map<String, Object>> states = new ConcurrentHashMap<>();

    @Value("${robot.online-ttl-ms:30000}")
    private long onlineTtlMs;

    /** 更新机器人状态，返回合并后的快照 */
    public Map<String, Object> update(String robotId, Map<String, Object> body) {
        Map<String, Object> state = new LinkedHashMap<>();
        if (body != null) {
            state.putAll(body);
        }
        state.put("robot_id", robotId);
        state.put("last_active_at", LocalDateTime.now());
        states.put(robotId, state);
        log.info("机器人状态上报: robotId={}, battery={}, waypoint={}",
                robotId, state.get("battery"), state.get("current_waypoint"));
        return snapshot(robotId);
    }

    /** 获取状态快照，附带 online 标记 */
    public Map<String, Object> snapshot(String robotId) {
        Map<String, Object> state = states.get(robotId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("robot_id", robotId);
        result.put("online", isOnline(robotId));
        if (state != null) {
            state.forEach((k, v) -> {
                if (!"robot_id".equals(k)) {
                    result.put(k, v);
                }
            });
        }
        return result;
    }

    public boolean isOnline(String robotId) {
        Map<String, Object> state = states.get(robotId);
        if (state == null) {
            return false;
        }
        Object lastActive = state.get("last_active_at");
        if (!(lastActive instanceof LocalDateTime last)) {
            return false;
        }
        return last.plusNanos(onlineTtlMs * 1_000_000L).isAfter(LocalDateTime.now());
    }

    /** 所有机器人状态快照 */
    public Map<String, Object> all() {
        Map<String, Object> result = new LinkedHashMap<>();
        states.keySet().forEach(robotId -> result.put(robotId, snapshot(robotId)));
        return result;
    }
}
