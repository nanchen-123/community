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
 * 车牌识别实体
 */
@Data
@TableName("license_plate")
public class LicensePlate implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 车牌 ID */
    @TableId(value = "plate_id", type = IdType.AUTO)
    private Long plateId;

    /** 任务 ID */
    private Long taskId;

    /** 小区 ID */
    private Long communityId;

    /** 住户 ID */
    private Long householdId;

    /** 车牌号 */
    private String plateNumber;

    /** 图片 URL */
    private String picture;

    /** 位置 */
    private String location;

    /** 处理状态 */
    private String handleStatus;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 备注 */
    private String remark;
}
