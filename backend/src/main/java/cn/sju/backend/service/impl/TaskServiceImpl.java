package cn.sju.backend.service.impl;

import cn.sju.backend.entity.Task;
import cn.sju.backend.mapper.TaskMapper;
import cn.sju.backend.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 任务 Service 实现
 */
@Service
public class TaskServiceImpl implements TaskService {

    @Autowired
    private TaskMapper taskMapper;

    @Override
    public IPage<Task> page(Integer pageNum, Integer pageSize, Task query) {
        IPage<Task> page = new Page<>(pageNum, pageSize);
        QueryWrapper<Task> wrapper = new QueryWrapper<>(query);
        return taskMapper.selectPage(page, wrapper);
    }

    @Override
    public Task getById(Long id) {
        return taskMapper.selectById(id);
    }

    @Override
    public boolean save(Task entity) {
        return taskMapper.insert(entity) > 0;
    }

    @Override
    public boolean updateById(Task entity) {
        return taskMapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(Long id) {
        return taskMapper.deleteById(id) > 0;
    }
}
