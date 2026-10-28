package cn.sju.backend.controller;

import cn.sju.backend.common.Result;
import cn.sju.backend.entity.SysUser;
import cn.sju.backend.exception.BusinessException;
import cn.sju.backend.service.SysUserService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
 * 用户管理接口（管理员）
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/sys-user")
public class SysUserController {

    @Autowired
    private SysUserService sysUserService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Operation(summary = "分页查询用户")
    @GetMapping("/page")
    public Result<?> page(@RequestParam(defaultValue = "1") Integer pageNum,
                          @RequestParam(defaultValue = "10") Integer pageSize,
                          @ModelAttribute SysUser query) {
        IPage<SysUser> page = sysUserService.page(pageNum, pageSize, query);
        // 抹掉密码字段
        page.getRecords().forEach(u -> u.setPassword(null));
        return Result.success(page);
    }

    @Operation(summary = "根据 ID 查询用户")
    @GetMapping("/{id}")
    public Result<SysUser> getById(@PathVariable Long id) {
        SysUser u = sysUserService.getById(id);
        if (u != null) u.setPassword(null);
        return Result.success(u);
    }

    @Operation(summary = "新增用户")
    @PostMapping
    public Result<Boolean> save(@RequestBody SysUser entity) {
        // 账号唯一性校验
        if (entity.getUserName() != null && !entity.getUserName().isBlank()) {
            SysUser exist = sysUserService.getByUserName(entity.getUserName());
            if (exist != null) {
                throw new BusinessException(400, "账号已存在，不允许重复");
            }
        }
        if (entity.getPassword() != null && !entity.getPassword().isBlank()) {
            entity.setPassword(passwordEncoder.encode(entity.getPassword()));
        }
        if (entity.getAccountStatus() == null) entity.setAccountStatus("正常");
        if (entity.getRole() == null) entity.setRole("普通用户");
        return Result.success(sysUserService.save(entity));
    }

    @Operation(summary = "修改用户")
    @PutMapping
    public Result<Boolean> updateById(@RequestBody SysUser entity) {
        // 账号唯一性校验：修改后的用户名不能与其他用户重复
        if (entity.getUserName() != null && !entity.getUserName().isBlank()) {
            SysUser exist = sysUserService.getByUserName(entity.getUserName());
            if (exist != null && !exist.getUserId().equals(entity.getUserId())) {
                throw new BusinessException(400, "账号已存在，不允许重复");
            }
        }
        // 系统至少保留一个可用的超级管理员：不能降级/禁用最后一个超管
        SysUser old = entity.getUserId() == null ? null : sysUserService.getById(entity.getUserId());
        if (old != null && "超级管理员".equals(old.getRole())) {
            boolean demote = entity.getRole() != null && !"超级管理员".equals(entity.getRole());
            boolean disable = "禁用".equals(entity.getAccountStatus());
            if ((demote || disable) && countSuperAdmins() <= 1) {
                throw new BusinessException(400, "系统至少需要保留一个超级管理员，无法降级或禁用");
            }
        }
        // 如果前端传了新密码才加密更新，否则保留原密码
        if (entity.getPassword() != null && !entity.getPassword().isBlank()) {
            entity.setPassword(passwordEncoder.encode(entity.getPassword()));
        } else {
            entity.setPassword(null); // 跳过密码更新
        }
        return Result.success(sysUserService.updateById(entity));
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public Result<Boolean> removeById(@PathVariable Long id) {
        // 系统至少保留一个超级管理员：只剩一个时禁止删除
        SysUser old = sysUserService.getById(id);
        if (old != null && "超级管理员".equals(old.getRole()) && countSuperAdmins() <= 1) {
            throw new BusinessException(400, "系统至少需要保留一个超级管理员，无法删除");
        }
        return Result.success(sysUserService.removeById(id));
    }

    /** 统计超级管理员数量 */
    private long countSuperAdmins() {
        SysUser query = new SysUser();
        query.setRole("超级管理员");
        return sysUserService.page(1, 1, query).getTotal();
    }
}
