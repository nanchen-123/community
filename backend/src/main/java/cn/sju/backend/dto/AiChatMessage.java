package cn.sju.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话消息（role: system / user / assistant）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatMessage {

    /** 角色：system / user / assistant */
    private String role;

    /** 消息内容 */
    private String content;
}
