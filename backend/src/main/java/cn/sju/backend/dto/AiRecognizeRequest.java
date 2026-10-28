package cn.sju.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI 识别请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRecognizeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("image_path")
    private String imagePath;

    @JsonProperty("detect_type")
    private String detectType;

    /** 图片来源: board=T5AI-Board 板子拍摄, local=robot_client 本地摄像头 */
    private String source;
}
