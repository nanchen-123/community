package cn.sju.backend.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 识别结果广播消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecognitionResultMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type = "recognition_result";

    private String requestId;

    private String imagePath;

    private Object result;

    private LocalDateTime timestamp;

    public RecognitionResultMessage(String requestId, String imagePath, Object result) {
        this.requestId = requestId;
        this.imagePath = imagePath;
        this.result = result;
        this.timestamp = LocalDateTime.now();
    }
}
