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
 * 住户实体
 */
@Data
@TableName("resident")
public class Resident implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 住户 ID */
    @TableId(value = "household_id", type = IdType.AUTO)
    private Long householdId;

    /** 小区 ID */
    private Long communityId;

    /** 住户名 */
    private String householdName;

    /** 电话 */
    private String phone;

    /** 房间号 */
    private String roomNo;

    /** 住户车牌号 */
    private String plateNumber;

    /** 家庭人数 */
    private Integer familyNum;

    /** 居住状态：正常 / 居住 */
    private String residenceStatus;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 截止时间 */
    private LocalDateTime deadline;

    /** 备注 */
    private String remark;
}
