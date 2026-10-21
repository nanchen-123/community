package cn.sju.backend.mapper;

import cn.sju.backend.entity.Alarm;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警 Mapper
 */
@Mapper
public interface AlarmMapper extends BaseMapper<Alarm> {
}
