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
 * 任务实体
 */
@Data
@TableName("task")
public class Task implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    @TableId(value = "task_id", type = IdType.AUTO)
    private Long taskId;

    /** 设备 ID */
    private Long deviceId;

    /** 用户 ID */
    private Long userId;

    /** 任务名称 */
    private String taskName;

    /** 任务状态 */
    private String taskStatus;

    /** 触点信息 */
    private String contactInfo;

    /** 循环次数 */
    private Integer loopCount;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 结束时间 */
    private LocalDateTime endTime;
}
