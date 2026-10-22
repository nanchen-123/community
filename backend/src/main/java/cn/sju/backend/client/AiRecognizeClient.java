package cn.sju.backend.client;

import cn.sju.backend.dto.AiRecognizeRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * AI 识别客户端
 */
@Slf4j
@Component
public class AiRecognizeClient {

    @Value("${ai.url:http://localhost:5000/recognize}")
    private String aiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 调用 AI 识别服务
     *
     * @param imagePath  图片路径（相对或绝对路径）
     * @param detectType 检测类型
     * @return AI 返回结果
     */
    public Map<String, Object> recognize(String imagePath, String detectType) {
        AiRecognizeRequest request = new AiRecognizeRequest(imagePath, detectType);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(aiUrl, request, Map.class);
            return response.getBody();
        } catch (RestClientException e) {
            log.error("AI 识别服务调用失败: url={}, imagePath={}", aiUrl, imagePath, e);
            throw new RuntimeException("AI 识别服务调用失败: " + e.getMessage(), e);
        }
    }
}
