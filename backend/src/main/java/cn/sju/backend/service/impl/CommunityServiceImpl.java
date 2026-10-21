package cn.sju.backend.service.impl;

import cn.sju.backend.entity.Community;
import cn.sju.backend.mapper.CommunityMapper;
import cn.sju.backend.service.CommunityService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 小区 Service 实现
 */
@Service
public class CommunityServiceImpl implements CommunityService {

    @Autowired
    private CommunityMapper communityMapper;

    @Override
    public IPage<Community> page(Integer pageNum, Integer pageSize, Community query) {
        IPage<Community> page = new Page<>(pageNum, pageSize);
        QueryWrapper<Community> wrapper = new QueryWrapper<>(query);
        return communityMapper.selectPage(page, wrapper);
    }

    @Override
    public Community getById(Long id) {
        return communityMapper.selectById(id);
    }

    @Override
    public boolean save(Community entity) {
        return communityMapper.insert(entity) > 0;
    }

    @Override
    public boolean updateById(Community entity) {
        return communityMapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(Long id) {
        return communityMapper.deleteById(id) > 0;
    }
}
