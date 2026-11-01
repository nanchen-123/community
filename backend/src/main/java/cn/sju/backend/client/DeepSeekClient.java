package cn.sju.backend.client;

import cn.sju.backend.dto.AiChatMessage;
import cn.sju.backend.dto.AiChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 大模型客户端（OpenAI 兼容格式）
 * API Key 只在本类内部使用，不对外暴露、不打印日志
 */
@Slf4j
@Component
public class DeepSeekClient {

    @Value("${deepseek.api-key:}")
    private String apiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${deepseek.model:deepseek-chat}")
    private String model;

    private final RestTemplate restTemplate;

    public DeepSeekClient() {
        // 连接超时 10s，读取超时 60s（大模型生成较慢，不能用默认超时）
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(60_000);
        this.restTemplate = new RestTemplate(factory);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getModel() {
        return model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 调用对话补全接口
     *
     * @param messages 完整消息列表（含 system 提示）
     * @return 回复内容与 token 用量
     */
    @SuppressWarnings("unchecked")
    public AiChatResponse chat(List<AiChatMessage> messages) {
        if (!isConfigured()) {
            throw new IllegalStateException("未配置 DeepSeek API Key");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/chat/completions", new HttpEntity<>(body, headers), Map.class);
            Map<String, Object> result = response.getBody();
            if (result == null) {
                throw new IllegalStateException("DeepSeek 返回内容为空");
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IllegalStateException("DeepSeek 返回内容为空");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = message == null ? null : (String) message.get("content");
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("DeepSeek 返回内容为空");
            }

            Map<String, Object> usage = (Map<String, Object>) result.get("usage");
            Integer promptTokens = usage == null ? null : (Integer) usage.get("prompt_tokens");
            Integer completionTokens = usage == null ? null : (Integer) usage.get("completion_tokens");

            log.info("DeepSeek 调用成功: model={}, promptTokens={}, completionTokens={}",
                    model, promptTokens, completionTokens);
            return new AiChatResponse(content, promptTokens, completionTokens);
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            log.warn("DeepSeek 调用失败: status={}", status);
            if (status == 401) {
                throw new IllegalStateException("DeepSeek API Key 无效，请检查配置");
            }
            if (status == 402) {
                throw new IllegalStateException("DeepSeek 账户余额不足，请充值后重试");
            }
            if (status == 429) {
                throw new IllegalStateException("调用太频繁，请稍后再试");
            }
            throw new IllegalStateException("DeepSeek 调用失败（HTTP " + status + "）");
        } catch (ResourceAccessException e) {
            log.warn("DeepSeek 网络异常: {}", e.getMessage());
            throw new IllegalStateException("连接 DeepSeek 超时或网络异常，请稍后重试");
        }
    }
}
