package cn.sju.backend.service;

import cn.sju.backend.entity.Alarm;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 告警 Service
 */
public interface AlarmService {

    IPage<Alarm> page(Integer pageNum, Integer pageSize, Alarm query);

    Alarm getById(Long id);

    boolean save(Alarm entity);

    boolean updateById(Alarm entity);

    boolean removeById(Long id);
}
