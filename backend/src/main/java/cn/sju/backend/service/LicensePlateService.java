package cn.sju.backend.service;

import cn.sju.backend.entity.LicensePlate;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 车牌识别 Service
 */
public interface LicensePlateService {

    IPage<LicensePlate> page(Integer pageNum, Integer pageSize, LicensePlate query);

    LicensePlate getById(Long id);

    boolean save(LicensePlate entity);

    boolean updateById(LicensePlate entity);

    boolean removeById(Long id);
}
