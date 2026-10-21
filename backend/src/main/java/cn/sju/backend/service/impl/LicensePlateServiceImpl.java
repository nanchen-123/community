package cn.sju.backend.service.impl;

import cn.sju.backend.entity.LicensePlate;
import cn.sju.backend.mapper.LicensePlateMapper;
import cn.sju.backend.service.LicensePlateService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 车牌识别 Service 实现
 */
@Service
public class LicensePlateServiceImpl implements LicensePlateService {

    @Autowired
    private LicensePlateMapper licensePlateMapper;

    @Override
    public IPage<LicensePlate> page(Integer pageNum, Integer pageSize, LicensePlate query) {
        IPage<LicensePlate> page = new Page<>(pageNum, pageSize);
        QueryWrapper<LicensePlate> wrapper = new QueryWrapper<>(query);
        return licensePlateMapper.selectPage(page, wrapper);
    }

    @Override
    public LicensePlate getById(Long id) {
        return licensePlateMapper.selectById(id);
    }

    @Override
    public boolean save(LicensePlate entity) {
        return licensePlateMapper.insert(entity) > 0;
    }

    @Override
    public boolean updateById(LicensePlate entity) {
        return licensePlateMapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(Long id) {
        return licensePlateMapper.deleteById(id) > 0;
    }
}
