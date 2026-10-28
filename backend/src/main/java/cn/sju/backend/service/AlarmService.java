package cn.sju.backend.service;

import cn.sju.backend.entity.Alarm;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 告警 Service
 */
public interface AlarmService {

    IPage<Alarm> page(Integer pageNum, Integer pageSize, Alarm query);

    /** 分页查询，支持按发现时间区间过滤（时间格式 yyyy-MM-dd HH:mm:ss） */
    IPage<Alarm> page(Integer pageNum, Integer pageSize, Alarm query, String startTime, String endTime);

    Alarm getById(Long id);

    boolean save(Alarm entity);

    boolean updateById(Alarm entity);

    boolean removeById(Long id);
}
