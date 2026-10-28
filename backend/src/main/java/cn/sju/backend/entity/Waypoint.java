package cn.sju.backend.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * 巡检路径点
 */
@Data
public class Waypoint implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 顺序 */
    private Integer order;

    /** 航点名称 */
    private String name;

    /** ROS 世界坐标系 X（米） */
    private Double x;

    /** ROS 世界坐标系 Y（米） */
    private Double y;

    /** ROS 世界坐标系 Z（米） */
    private Double z;

    /** 朝向四元数 qx */
    private Double qx;

    /** 朝向四元数 qy */
    private Double qy;

    /** 朝向四元数 qz */
    private Double qz;

    /** 朝向四元数 qw */
    private Double qw;

    /** 到达航点是否自动拍照识别 */
    private Boolean autoPhoto;

    /** 拍照识别类型：person / flame / plate */
    private String photoType;

    /** 经度 */
    private Double longitude;

    /** 纬度 */
    private Double latitude;

    /** 备注 */
    private String remark;
}
