package cn.sju.backend.dto;

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

    private String imagePath;

    private String detectType;
}
