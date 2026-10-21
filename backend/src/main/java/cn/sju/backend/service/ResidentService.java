package cn.sju.backend.service;

import cn.sju.backend.entity.Resident;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 住户 Service
 */
public interface ResidentService {

    IPage<Resident> page(Integer pageNum, Integer pageSize, Resident query);

    Resident getById(Long id);

    boolean save(Resident entity);

    boolean updateById(Resident entity);

    boolean removeById(Long id);
}
