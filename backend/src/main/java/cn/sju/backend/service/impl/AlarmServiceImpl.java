package cn.sju.backend.service.impl;

import cn.sju.backend.entity.Alarm;
import cn.sju.backend.mapper.AlarmMapper;
import cn.sju.backend.service.AlarmService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 告警 Service 实现
 */
@Service
public class AlarmServiceImpl implements AlarmService {

    @Autowired
    private AlarmMapper alarmMapper;

    @Override
    public IPage<Alarm> page(Integer pageNum, Integer pageSize, Alarm query) {
        IPage<Alarm> page = new Page<>(pageNum, pageSize);
        QueryWrapper<Alarm> wrapper = new QueryWrapper<>(query);
        return alarmMapper.selectPage(page, wrapper);
    }

    @Override
    public Alarm getById(Long id) {
        return alarmMapper.selectById(id);
    }

    @Override
    public boolean save(Alarm entity) {
        return alarmMapper.insert(entity) > 0;
    }

    @Override
    public boolean updateById(Alarm entity) {
        return alarmMapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(Long id) {
        return alarmMapper.deleteById(id) > 0;
    }
}
