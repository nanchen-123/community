package cn.sju.backend.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 上传结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String url;

    private String filename;

    private Boolean aiTriggered;
}
