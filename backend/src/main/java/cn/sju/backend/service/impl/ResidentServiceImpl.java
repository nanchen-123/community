package cn.sju.backend.service.impl;

import cn.sju.backend.entity.Resident;
import cn.sju.backend.mapper.ResidentMapper;
import cn.sju.backend.service.ResidentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 住户 Service 实现
 */
@Service
public class ResidentServiceImpl implements ResidentService {

    @Autowired
    private ResidentMapper residentMapper;

    @Override
    public IPage<Resident> page(Integer pageNum, Integer pageSize, Resident query) {
        IPage<Resident> page = new Page<>(pageNum, pageSize);
        QueryWrapper<Resident> wrapper = new QueryWrapper<>(query);
        return residentMapper.selectPage(page, wrapper);
    }

    @Override
    public Resident getById(Long id) {
        return residentMapper.selectById(id);
    }

    @Override
    public boolean save(Resident entity) {
        return residentMapper.insert(entity) > 0;
    }

    @Override
    public boolean updateById(Resident entity) {
        return residentMapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(Long id) {
        return residentMapper.deleteById(id) > 0;
    }
}
