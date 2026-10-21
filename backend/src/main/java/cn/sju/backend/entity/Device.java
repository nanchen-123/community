package cn.sju.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备实体
 */
@Data
@TableName("device")
public class Device implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 设备 ID */
    @TableId(value = "device_id", type = IdType.AUTO)
    private Long deviceId;

    /** 小区 ID */
    private Long communityId;

    /** 设备号 */
    private String deviceNum;

    /** 机器人名称 */
    private String robotName;

    /** 设备状态 */
    private String deviceStatus;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 截止时间 */
    private LocalDateTime deadline;
}
