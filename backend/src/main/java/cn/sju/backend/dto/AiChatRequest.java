package cn.sju.backend.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 对话请求（前端带上本次会话的历史消息）
 */
@Data
public class AiChatRequest {

    /** 历史消息，最后一条必须是 user */
    private List<AiChatMessage> messages;
}
