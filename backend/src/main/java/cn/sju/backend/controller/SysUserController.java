package cn.sju.backend.controller;

import cn.sju.backend.common.Result;
import cn.sju.backend.entity.SysUser;
import cn.sju.backend.service.SysUserService;
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
 * 用户管理接口
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/sys-user")
public class SysUserController {

    @Autowired
    private SysUserService sysUserService;

    @Operation(summary = "分页查询用户")
    @GetMapping("/page")
    public Result<?> page(@RequestParam(defaultValue = "1") Integer pageNum,
                          @RequestParam(defaultValue = "10") Integer pageSize,
                          @ModelAttribute SysUser query) {
        return Result.success(sysUserService.page(pageNum, pageSize, query));
    }

    @Operation(summary = "根据 ID 查询用户")
    @GetMapping("/{id}")
    public Result<SysUser> getById(@PathVariable Long id) {
        return Result.success(sysUserService.getById(id));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    public Result<Boolean> save(@RequestBody SysUser entity) {
        return Result.success(sysUserService.save(entity));
    }

    @Operation(summary = "修改用户")
    @PutMapping
    public Result<Boolean> updateById(@RequestBody SysUser entity) {
        return Result.success(sysUserService.updateById(entity));
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public Result<Boolean> removeById(@PathVariable Long id) {
        return Result.success(sysUserService.removeById(id));
    }
}
