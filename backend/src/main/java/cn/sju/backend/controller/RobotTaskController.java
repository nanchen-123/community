package cn.sju.backend.controller;

import cn.sju.backend.entity.Task;
import cn.sju.backend.service.TaskService;
import cn.sju.backend.websocket.WsBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 机器人任务接口
 * <p>
 * 由 ROS 机器人使用 ROBOT_TOKEN 调用（经 {@link cn.sju.backend.filter.RobotTokenFilter} 校验），
 * 按项目约定不包装统一 Result&lt;T&gt;，直接返回裸 JSON。
 */
@Slf4j
@RestController
@RequestMapping("/api/robot/tasks")
public class RobotTaskController {

    /** 任务状态（与数据库、前端保持一致的中文值） */
    private static final String STATUS_PENDING = "待执行";
    private static final String STATUS_RUNNING = "执行中";
    private static final String STATUS_STOPPED = "已取消";
    private static final String STATUS_COMPLETED = "已完成";

    @Autowired
    private TaskService taskService;

    @Autowired
    private WsBroadcaster wsBroadcaster;

    /**
     * 拉取任务：status=active 时只返回一台机器人当前应执行的任务——
     * 已有「执行中」任务则返回该任务；否则领取最早的一个「待执行」任务并置为「执行中」。
     * 这样避免把所有待执行任务一次性置为执行中（前端看到多个任务同时执行，实际只有一台车在跑）。
     */
    @GetMapping
    public Map<String, Object> tasks(@RequestParam(defaultValue = "active") String status) {
        List<Task> tasks = new ArrayList<>();
        int upgraded = 0;

        if ("active".equalsIgnoreCase(status)) {
            List<Task> running = taskService.listByStatuses(List.of(STATUS_RUNNING));
            if (!running.isEmpty()) {
                // 优先继续执行已经下发的任务（机器人重启后也能续跑）
                tasks.add(running.get(0));
            } else {
                List<Task> pending = taskService.listByStatuses(List.of(STATUS_PENDING));
                if (!pending.isEmpty()) {
                    Task task = pending.get(0);
                    Task update = new Task();
                    update.setTaskId(task.getTaskId());
                    update.setTaskStatus(STATUS_RUNNING);
                    update.setStartTime(task.getStartTime() == null ? LocalDateTime.now() : task.getStartTime());
                    update.setProgress(task.getProgress() == null ? 0 : task.getProgress());
                    taskService.updateById(update);

                    task.setTaskStatus(STATUS_RUNNING);
                    task.setStartTime(update.getStartTime());
                    task.setProgress(update.getProgress());
                    upgraded = 1;
                    wsBroadcaster.sendTaskStatus(toRobotTask(task));
                    tasks.add(task);
                    log.info("机器人领取任务 #{}，待执行 → 执行中", task.getTaskId());
                }
            }
        } else {
            tasks = taskService.listByStatuses(List.of(toChineseStatus(status)));
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (Task task : tasks) {
            data.add(toRobotTask(task));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", data.size());
        result.put("status", status);
        result.put("upgraded", upgraded);
        result.put("data", data);
        return result;
    }

    /**
     * 回传进度：progress &gt;= 100 自动置为已完成并写入结束时间；
     * 任务已被用户停止时返回 cancelled=true，机器人应立即终止巡逻。
     */
    @PutMapping("/{id}/progress")
    public Map<String, Object> progress(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Task task = requireTask(id);
        String currentWaypoint = body.get("current_waypoint") == null
                ? null : String.valueOf(body.get("current_waypoint"));

        // 任务已被停止或已完成：不再改写状态，直接告知机器人
        if (STATUS_STOPPED.equals(task.getTaskStatus()) || STATUS_COMPLETED.equals(task.getTaskStatus())) {
            return buildProgressResult(task, currentWaypoint);
        }

        int progress = parseProgress(body.get("progress"));
        Task update = new Task();
        update.setTaskId(id);
        update.setProgress(progress);
        if (progress >= 100) {
            update.setTaskStatus(STATUS_COMPLETED);
            update.setProgress(100);
            update.setEndTime(LocalDateTime.now());
        } else {
            update.setTaskStatus(STATUS_RUNNING);
        }
        taskService.updateById(update);

        task.setProgress(update.getProgress());
        task.setTaskStatus(update.getTaskStatus());
        if (update.getEndTime() != null) {
            task.setEndTime(update.getEndTime());
        }

        log.info("机器人回传任务进度: taskId={}, progress={}, status={}", id, task.getProgress(), task.getTaskStatus());
        wsBroadcaster.sendTaskProgress(toRobotTask(task));
        if (STATUS_COMPLETED.equals(task.getTaskStatus())) {
            wsBroadcaster.sendTaskStatus(toRobotTask(task));
        }
        return buildProgressResult(task, currentWaypoint);
    }

    /**
     * 查询任务状态：机器人在阻塞移动过程中轮询是否被用户停止。
     */
    @GetMapping("/{id}/status")
    public Map<String, Object> taskStatus(@PathVariable Long id) {
        Task task = requireTask(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task_id", task.getTaskId());
        result.put("task_status", task.getTaskStatus());
        result.put("status", toCodeStatus(task.getTaskStatus()));
        result.put("cancelled", STATUS_STOPPED.equals(task.getTaskStatus()));
        result.put("progress", task.getProgress() == null ? 0 : task.getProgress());
        return result;
    }

    private Task requireTask(Long id) {
        Task task = taskService.getById(id);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + id);
        }
        return task;
    }

    private Map<String, Object> buildProgressResult(Task task, String currentWaypoint) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task_id", task.getTaskId());
        result.put("task_status", task.getTaskStatus());
        result.put("status", toCodeStatus(task.getTaskStatus()));
        result.put("cancelled", STATUS_STOPPED.equals(task.getTaskStatus())
                || STATUS_COMPLETED.equals(task.getTaskStatus()));
        result.put("progress", task.getProgress() == null ? 0 : task.getProgress());
        result.put("current_waypoint", currentWaypoint);
        return result;
    }

    /** 机器人侧任务结构（snake_case，与 robot_client.py 保持一致） */
    private Map<String, Object> toRobotTask(Task task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", task.getTaskId());
        map.put("task_id", task.getTaskId());
        map.put("task_name", task.getTaskName());
        map.put("task_status", task.getTaskStatus());
        map.put("status", toCodeStatus(task.getTaskStatus()));
        map.put("progress", task.getProgress() == null ? 0 : task.getProgress());
        map.put("loops", task.getLoops());
        map.put("community_id", task.getCommunityId());
        map.put("device_id", task.getDeviceId());
        map.put("user_id", task.getUserId());
        map.put("start_time", task.getStartTime());
        map.put("end_time", task.getEndTime());
        map.put("create_time", task.getCreateTime());
        map.put("waypoints", task.getWaypoints() == null ? List.of() : task.getWaypoints());
        return map;
    }

    private int parseProgress(Object raw) {
        int value;
        try {
            value = raw == null ? 0 : Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("progress 必须为 0-100 的整数");
        }
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("progress 必须为 0-100 的整数");
        }
        return value;
    }

    private String toChineseStatus(String status) {
        switch (status == null ? "" : status.toLowerCase()) {
            case "pending":
                return STATUS_PENDING;
            case "running":
                return STATUS_RUNNING;
            case "stopped":
                return STATUS_STOPPED;
            case "completed":
                return STATUS_COMPLETED;
            default:
                throw new IllegalArgumentException("不支持的任务状态: " + status);
        }
    }

    private String toCodeStatus(String chineseStatus) {
        if (STATUS_PENDING.equals(chineseStatus)) {
            return "pending";
        }
        if (STATUS_RUNNING.equals(chineseStatus)) {
            return "running";
        }
        if (STATUS_STOPPED.equals(chineseStatus)) {
            return "stopped";
        }
        if (STATUS_COMPLETED.equals(chineseStatus)) {
            return "completed";
        }
        return "unknown";
    }
}
