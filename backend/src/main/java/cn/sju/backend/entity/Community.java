package cn.sju.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 小区实体
 */
@Data
@TableName("community")
public class Community implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 小区 ID */
    @TableId(value = "community_id", type = IdType.AUTO)
    private Long communityId;

    /** 小区名称 */
    private String communityName;

    /** 地址 */
    private String address;
}
