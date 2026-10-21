package cn.sju.backend.service.impl;

import cn.sju.backend.entity.Device;
import cn.sju.backend.mapper.DeviceMapper;
import cn.sju.backend.service.DeviceService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 设备 Service 实现
 */
@Service
public class DeviceServiceImpl implements DeviceService {

    @Autowired
    private DeviceMapper deviceMapper;

    @Override
    public IPage<Device> page(Integer pageNum, Integer pageSize, Device query) {
        IPage<Device> page = new Page<>(pageNum, pageSize);
        QueryWrapper<Device> wrapper = new QueryWrapper<>(query);
        return deviceMapper.selectPage(page, wrapper);
    }

    @Override
    public Device getById(Long id) {
        return deviceMapper.selectById(id);
    }

    @Override
    public boolean save(Device entity) {
        return deviceMapper.insert(entity) > 0;
    }

    @Override
    public boolean updateById(Device entity) {
        return deviceMapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(Long id) {
        return deviceMapper.deleteById(id) > 0;
    }
}
