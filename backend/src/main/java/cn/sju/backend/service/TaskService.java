package cn.sju.backend.service;

import cn.sju.backend.entity.Task;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Collection;
import java.util.List;

/**
 * 任务 Service
 */
public interface TaskService {

    IPage<Task> page(Integer pageNum, Integer pageSize, Task query);

    /** 按状态集合查询任务 */
    List<Task> listByStatuses(Collection<String> statuses);

    Task getById(Long id);

    boolean save(Task entity);

    boolean updateById(Task entity);

    boolean removeById(Long id);
}
