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
 * 用户实体
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;

    /** 小区 ID */
    private Long communityId;

    /** 用户名 */
    private String userName;

    /** 密码 */
    private String password;

    /** 账号状态 */
    private String accountStatus;

    /** 联系电话 */
    private String phone;

    /** 真实姓名 */
    private String name;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 角色：普通用户 / 管理员 / 高级管理员 */
    private String role;
}
