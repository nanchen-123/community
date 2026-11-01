package cn.sju.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 对话回复
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {

    /** 模型回复文本 */
    private String content;

    /** 本次消耗的输入 token */
    private Integer promptTokens;

    /** 本次消耗的输出 token */
    private Integer completionTokens;
}
