package cn.sju.backend.controller;

import cn.sju.backend.common.Result;
import cn.sju.backend.entity.Device;
import cn.sju.backend.service.DeviceService;
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
 * 设备管理接口
 */
@Tag(name = "设备管理")
@RestController
@RequestMapping("/api/device")
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    @Operation(summary = "分页查询设备")
    @GetMapping("/page")
    public Result<?> page(@RequestParam(defaultValue = "1") Integer pageNum,
                          @RequestParam(defaultValue = "10") Integer pageSize,
                          @ModelAttribute Device query) {
        return Result.success(deviceService.page(pageNum, pageSize, query));
    }

    @Operation(summary = "根据 ID 查询设备")
    @GetMapping("/{id}")
    public Result<Device> getById(@PathVariable Long id) {
        return Result.success(deviceService.getById(id));
    }

    @Operation(summary = "新增设备")
    @PostMapping
    public Result<Boolean> save(@RequestBody Device entity) {
        return Result.success(deviceService.save(entity));
    }

    @Operation(summary = "修改设备")
    @PutMapping
    public Result<Boolean> updateById(@RequestBody Device entity) {
        return Result.success(deviceService.updateById(entity));
    }

    @Operation(summary = "删除设备")
    @DeleteMapping("/{id}")
    public Result<Boolean> removeById(@PathVariable Long id) {
        return Result.success(deviceService.removeById(id));
    }
}
