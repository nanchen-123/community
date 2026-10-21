package cn.sju.backend.controller;

import cn.sju.backend.common.Result;
import cn.sju.backend.entity.LicensePlate;
import cn.sju.backend.service.LicensePlateService;
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
 * 车牌识别管理接口
 */
@Tag(name = "车牌识别管理")
@RestController
@RequestMapping("/api/license-plate")
public class LicensePlateController {

    @Autowired
    private LicensePlateService licensePlateService;

    @Operation(summary = "分页查询车牌识别记录")
    @GetMapping("/page")
    public Result<?> page(@RequestParam(defaultValue = "1") Integer pageNum,
                          @RequestParam(defaultValue = "10") Integer pageSize,
                          @ModelAttribute LicensePlate query) {
        return Result.success(licensePlateService.page(pageNum, pageSize, query));
    }

    @Operation(summary = "根据 ID 查询车牌识别记录")
    @GetMapping("/{id}")
    public Result<LicensePlate> getById(@PathVariable Long id) {
        return Result.success(licensePlateService.getById(id));
    }

    @Operation(summary = "新增车牌识别记录")
    @PostMapping
    public Result<Boolean> save(@RequestBody LicensePlate entity) {
        return Result.success(licensePlateService.save(entity));
    }

    @Operation(summary = "修改车牌识别记录")
    @PutMapping
    public Result<Boolean> updateById(@RequestBody LicensePlate entity) {
        return Result.success(licensePlateService.updateById(entity));
    }

    @Operation(summary = "删除车牌识别记录")
    @DeleteMapping("/{id}")
    public Result<Boolean> removeById(@PathVariable Long id) {
        return Result.success(licensePlateService.removeById(id));
    }
}
