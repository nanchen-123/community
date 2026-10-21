package cn.sju.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警实体
 */
@Data
@TableName("alarm")
public class Alarm implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 告警 ID */
    @TableId(value = "alarm_id", type = IdType.AUTO)
    private Long alarmId;

    /** 设备 ID */
    private Long deviceId;

    /** 类型 */
    private String alarmType;

    /** 位置 */
    private String location;

    /** 处理状态 */
    private String handleStatus;

    /** 发现时间 */
    private LocalDateTime findTime;

    /** 处理时间 */
    private LocalDateTime handleTime;

    /** 级别 */
    private String category;

    /** 识别情况 */
    private String recognizeStatus;

    /** 图片 URL */
    private String picture;

    /** 备注 */
    private String remark;
}
