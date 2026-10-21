package cn.sju.backend.service;

import cn.sju.backend.entity.Device;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 设备 Service
 */
public interface DeviceService {

    IPage<Device> page(Integer pageNum, Integer pageSize, Device query);

    Device getById(Long id);

    boolean save(Device entity);

    boolean updateById(Device entity);

    boolean removeById(Long id);
}
