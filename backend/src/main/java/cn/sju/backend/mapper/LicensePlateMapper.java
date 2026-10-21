package cn.sju.backend.mapper;

import cn.sju.backend.entity.LicensePlate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 车牌识别 Mapper
 */
@Mapper
public interface LicensePlateMapper extends BaseMapper<LicensePlate> {
}
