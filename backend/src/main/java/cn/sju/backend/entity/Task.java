package cn.sju.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务实体
 */
@Data
@TableName(value = "task", autoResultMap = true)
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

    /** 巡检路径点 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Waypoint> waypoints;

    /** 循环次数 */
    private Integer loops;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 结束时间 */
    private LocalDateTime endTime;
}
