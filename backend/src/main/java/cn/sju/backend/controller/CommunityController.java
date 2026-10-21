package cn.sju.backend.controller;

import cn.sju.backend.common.Result;
import cn.sju.backend.entity.Community;
import cn.sju.backend.service.CommunityService;
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
 * 小区管理接口
 */
@Tag(name = "小区管理")
@RestController
@RequestMapping("/api/community")
public class CommunityController {

    @Autowired
    private CommunityService communityService;

    @Operation(summary = "分页查询小区")
    @GetMapping("/page")
    public Result<?> page(@RequestParam(defaultValue = "1") Integer pageNum,
                          @RequestParam(defaultValue = "10") Integer pageSize,
                          @ModelAttribute Community query) {
        return Result.success(communityService.page(pageNum, pageSize, query));
    }

    @Operation(summary = "根据 ID 查询小区")
    @GetMapping("/{id}")
    public Result<Community> getById(@PathVariable Long id) {
        return Result.success(communityService.getById(id));
    }

    @Operation(summary = "新增小区")
    @PostMapping
    public Result<Boolean> save(@RequestBody Community entity) {
        return Result.success(communityService.save(entity));
    }

    @Operation(summary = "修改小区")
    @PutMapping
    public Result<Boolean> updateById(@RequestBody Community entity) {
        return Result.success(communityService.updateById(entity));
    }

    @Operation(summary = "删除小区")
    @DeleteMapping("/{id}")
    public Result<Boolean> removeById(@PathVariable Long id) {
        return Result.success(communityService.removeById(id));
    }
}
