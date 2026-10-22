package cn.sju.backend.service;

import cn.sju.backend.client.AiRecognizeClient;
import cn.sju.backend.vo.RecognitionResultMessage;
import cn.sju.backend.websocket.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * AI 识别服务
 */
@Slf4j
@Service
public class AiRecognizeService {

    @Autowired
    private AiRecognizeClient aiRecognizeClient;

    @Autowired
    private WebSocketSessionManager sessionManager;

    /**
     * 异步触发 AI 识别
     *
     * @param requestId  请求 ID
     * @param imagePath  图片访问路径
     * @param detectType 检测类型
     */
    @Async("aiTaskExecutor")
    public void recognize(String requestId, String imagePath, String detectType) {
        log.info("异步触发 AI 识别: requestId={}, imagePath={}", requestId, imagePath);
        try {
            // TODO: 更新 photo_request 状态为识别中（需先创建 photo_request 表及对应 Entity/Mapper）

            Map<String, Object> result = aiRecognizeClient.recognize(imagePath, detectType);

            // TODO: 更新 photo_request 状态为识别完成并保存结果

            RecognitionResultMessage message = new RecognitionResultMessage(requestId, imagePath, result);
            sessionManager.broadcast(message);
            log.info("AI 识别完成并广播: requestId={}", requestId);
        } catch (Exception e) {
            log.error("AI 识别失败: requestId={}", requestId, e);
            // TODO: 更新 photo_request 状态为识别失败
            RecognitionResultMessage message = new RecognitionResultMessage(requestId, imagePath, Map.of("error", e.getMessage()));
            sessionManager.broadcast(message);
        }
    }
}
