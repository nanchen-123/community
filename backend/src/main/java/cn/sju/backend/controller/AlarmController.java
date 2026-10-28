package cn.sju.backend.controller;

import cn.sju.backend.common.Result;
import cn.sju.backend.entity.Alarm;
import cn.sju.backend.service.AlarmService;
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
 * 告警管理接口
 */
@Tag(name = "告警管理")
@RestController
@RequestMapping("/api/alarm")
public class AlarmController {

    @Autowired
    private AlarmService alarmService;

    @Operation(summary = "分页查询告警")
    @GetMapping("/page")
    public Result<?> page(@RequestParam(defaultValue = "1") Integer pageNum,
                          @RequestParam(defaultValue = "10") Integer pageSize,
                          @RequestParam(required = false) String startTime,
                          @RequestParam(required = false) String endTime,
                          @ModelAttribute Alarm query) {
        return Result.success(alarmService.page(pageNum, pageSize, query, startTime, endTime));
    }

    @Operation(summary = "根据 ID 查询告警")
    @GetMapping("/{id}")
    public Result<Alarm> getById(@PathVariable Long id) {
        return Result.success(alarmService.getById(id));
    }

    @Operation(summary = "新增告警")
    @PostMapping
    public Result<Boolean> save(@RequestBody Alarm entity) {
        return Result.success(alarmService.save(entity));
    }

    @Operation(summary = "修改告警")
    @PutMapping
    public Result<Boolean> updateById(@RequestBody Alarm entity) {
        return Result.success(alarmService.updateById(entity));
    }

    @Operation(summary = "删除告警")
    @DeleteMapping("/{id}")
    public Result<Boolean> removeById(@PathVariable Long id) {
        return Result.success(alarmService.removeById(id));
    }
}
