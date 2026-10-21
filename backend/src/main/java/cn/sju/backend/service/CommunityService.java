package cn.sju.backend.service;

import cn.sju.backend.entity.Community;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 小区 Service
 */
public interface CommunityService {

    IPage<Community> page(Integer pageNum, Integer pageSize, Community query);

    Community getById(Long id);

    boolean save(Community entity);

    boolean updateById(Community entity);

    boolean removeById(Long id);
}
