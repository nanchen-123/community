package cn.sju.backend.service.impl;

import cn.sju.backend.entity.SysUser;
import cn.sju.backend.mapper.SysUserMapper;
import cn.sju.backend.service.SysUserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 用户 Service 实现
 */
@Service
public class SysUserServiceImpl implements SysUserService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Override
    public IPage<SysUser> page(Integer pageNum, Integer pageSize, SysUser query) {
        IPage<SysUser> page = new Page<>(pageNum, pageSize);
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>(query);
        return sysUserMapper.selectPage(page, wrapper);
    }

    @Override
    public SysUser getById(Long id) {
        return sysUserMapper.selectById(id);
    }

    @Override
    public boolean save(SysUser entity) {
        return sysUserMapper.insert(entity) > 0;
    }

    @Override
    public boolean updateById(SysUser entity) {
        return sysUserMapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(Long id) {
        return sysUserMapper.deleteById(id) > 0;
    }
}
