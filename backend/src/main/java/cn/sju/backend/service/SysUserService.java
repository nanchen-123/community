package cn.sju.backend.service;

import cn.sju.backend.entity.SysUser;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 用户 Service
 */
public interface SysUserService {

    IPage<SysUser> page(Integer pageNum, Integer pageSize, SysUser query);

    SysUser getById(Long id);

    boolean save(SysUser entity);

    boolean updateById(SysUser entity);

    boolean removeById(Long id);
}
