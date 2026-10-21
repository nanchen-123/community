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

    /** 经度 */
    private Double longitude;

    /** 纬度 */
    private Double latitude;

    /** 备注 */
    private String remark;
}
