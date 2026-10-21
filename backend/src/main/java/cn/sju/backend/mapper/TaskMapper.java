package cn.sju.backend.mapper;

import cn.sju.backend.entity.Task;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务 Mapper
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {
}
