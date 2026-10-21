package cn.sju.backend.controller;

import cn.sju.backend.common.Result;
import cn.sju.backend.entity.Task;
import cn.sju.backend.service.TaskService;
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

/**
 * 任务管理接口
 */
@Tag(name = "任务管理")
@RestController
@RequestMapping("/api/task")
public class TaskController {

    @Autowired
    private TaskService taskService;

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
