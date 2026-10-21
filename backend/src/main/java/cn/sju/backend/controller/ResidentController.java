package cn.sju.backend.controller;

import cn.sju.backend.common.Result;
import cn.sju.backend.entity.Resident;
import cn.sju.backend.service.ResidentService;
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
 * 住户管理接口
 */
@Tag(name = "住户管理")
@RestController
@RequestMapping("/api/resident")
public class ResidentController {

    @Autowired
    private ResidentService residentService;

    @Operation(summary = "分页查询住户")
    @GetMapping("/page")
    public Result<?> page(@RequestParam(defaultValue = "1") Integer pageNum,
                          @RequestParam(defaultValue = "10") Integer pageSize,
                          @ModelAttribute Resident query) {
        return Result.success(residentService.page(pageNum, pageSize, query));
    }

    @Operation(summary = "根据 ID 查询住户")
    @GetMapping("/{id}")
    public Result<Resident> getById(@PathVariable Long id) {
        return Result.success(residentService.getById(id));
    }

    @Operation(summary = "新增住户")
    @PostMapping
    public Result<Boolean> save(@RequestBody Resident entity) {
        return Result.success(residentService.save(entity));
    }

    @Operation(summary = "修改住户")
    @PutMapping
    public Result<Boolean> updateById(@RequestBody Resident entity) {
        return Result.success(residentService.updateById(entity));
    }

    @Operation(summary = "删除住户")
    @DeleteMapping("/{id}")
    public Result<Boolean> removeById(@PathVariable Long id) {
        return Result.success(residentService.removeById(id));
    }
}
