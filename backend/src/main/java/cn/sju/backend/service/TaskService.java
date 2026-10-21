package cn.sju.backend.service;

import cn.sju.backend.entity.Task;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 任务 Service
 */
public interface TaskService {

    IPage<Task> page(Integer pageNum, Integer pageSize, Task query);

    Task getById(Long id);

    boolean save(Task entity);

    boolean updateById(Task entity);

    boolean removeById(Long id);
}
