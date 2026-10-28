package cn.sju.backend.controller;

import cn.sju.backend.common.Result;
import cn.sju.backend.entity.Task;
import cn.sju.backend.exception.BusinessException;
import cn.sju.backend.service.TaskService;
import cn.sju.backend.websocket.WsBroadcaster;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 任务管理接口
 */
@Tag(name = "任务管理")
@RestController
@RequestMapping("/api/task")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private WsBroadcaster wsBroadcaster;

    @Operation(summary = "下发巡检任务给机器人（启动 SLAM 巡逻）")
    @PostMapping("/{id}/dispatch")
    public Result<Task> dispatch(@PathVariable Long id) {
        return Result.success(startTask(id));
    }

    @Operation(summary = "手动开始任务")
    @PostMapping("/{id}/start")
    public Result<Task> start(@PathVariable Long id) {
        return Result.success(startTask(id));
    }

    @Operation(summary = "手动停止任务")
    @PostMapping("/{id}/stop")
    public Result<Task> stop(@PathVariable Long id) {
        Task task = taskService.getById(id);
        if (task == null) {
            throw new BusinessException(404, "巡检任务不存在");
        }
        if ("已完成".equals(task.getTaskStatus())) {
            throw new BusinessException(400, "该任务已完成，无法停止");
        }
        // 已停止则直接返回，保持幂等
        if (!"已取消".equals(task.getTaskStatus())) {
            Task update = new Task();
            update.setTaskId(id);
            update.setTaskStatus("已取消");
            update.setEndTime(LocalDateTime.now());
            taskService.updateById(update);
            task.setTaskStatus("已取消");
            task.setEndTime(update.getEndTime());
            // 通知前端刷新，机器人通过 /api/robot/tasks/{id}/status 轮询感知取消
            wsBroadcaster.sendTaskStatus(task);
        }
        return Result.success(task);
    }

    /** 开始/下发任务：置为执行中并广播 task_dispatch，触发机器人 SLAM 巡逻 */
    private Task startTask(Long id) {
        Task task = taskService.getById(id);
        if (task == null) {
            throw new BusinessException(404, "巡检任务不存在");
        }
        if ("已取消".equals(task.getTaskStatus()) || "已完成".equals(task.getTaskStatus())) {
            throw new BusinessException(400, "该任务已结束，无法下发");
        }
        Task update = new Task();
        update.setTaskId(id);
        update.setTaskStatus("执行中");
        if (task.getStartTime() == null) {
            update.setStartTime(LocalDateTime.now());
        }
        if (task.getProgress() == null) {
            update.setProgress(0);
        }
        taskService.updateById(update);
        task.setTaskStatus("执行中");
        if (task.getStartTime() == null) {
            task.setStartTime(update.getStartTime());
        }
        if (task.getProgress() == null) {
            task.setProgress(0);
        }
        // 广播给已连接的 ROS 机器人，触发 SLAM 巡逻
        wsBroadcaster.sendTaskDispatch(task);
        return task;
    }

    @Operation(summary = "分页查询任务")
    @GetMapping("/page")
    public Result<?> page(@RequestParam(defaultValue = "1") Integer pageNum,
                          @RequestParam(defaultValue = "10") Integer pageSize,
                          @ModelAttribute Task query) {
        return Result.success(taskService.page(pageNum, pageSize, query));
    }

    @Operation(summary = "根据 ID 查询任务")
    @GetMapping("/{id}")
    public Result<Task> getById(@PathVariable Long id) {
        return Result.success(taskService.getById(id));
    }

    @Operation(summary = "新增任务")
    @PostMapping
    public Result<Boolean> save(@RequestBody Task entity) {
        return Result.success(taskService.save(entity));
    }

    @Operation(summary = "修改任务")
    @PutMapping
    public Result<Boolean> updateById(@RequestBody Task entity) {
        return Result.success(taskService.updateById(entity));
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/{id}")
    public Result<Boolean> removeById(@PathVariable Long id) {
        return Result.success(taskService.removeById(id));
    }
}
