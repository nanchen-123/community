package cn.sju.backend.controller;

import cn.sju.backend.client.DeepSeekClient;
import cn.sju.backend.common.Result;
import cn.sju.backend.dto.AiChatMessage;
import cn.sju.backend.dto.AiChatRequest;
import cn.sju.backend.dto.AiChatResponse;
import cn.sju.backend.service.AiFileStore;
import cn.sju.backend.service.AiReportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 助手接口（对话转发 DeepSeek，Key 只在后端使用）
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    /** 系统提示：限定助手职责，避免乱答 */
    private static final String SYSTEM_PROMPT = """
            你是「社区安防智能巡检系统」内置的 AI 助手，面向小区物业和安保人员。
            已知系统功能（只回答这些范围内的内容）：
            1. 巡检任务：从预设航点 Point_1~Point_11 中选择路线发布给机器人；可对某个航点勾选「到达拍照」并选择识别类型（人员识别 / 火焰识别 / 车牌识别）。
            2. 拍照确认：机器人到达拍照点后会停住等待，网页出现「待确认拍照」提示；点击「立即拍照」后由 T5AI-Board 摄像头拍照并做 AI 识别，「跳过」则该点不拍照。
            3. 安防台账：AI 识别结果以告警形式记录，处理状态分为未处理 / 已处理 / 已忽略。
            4. 其他模块：首页概览（本周统计）、设备管理、车牌识别、居民管理、用户管理、社区管理、数据分析。
            5. 角色与数据：超级管理员可跨社区查看；管理员和普通用户只能看到所属社区的数据。
            回答要求：使用简体中文，简洁分点，给出可操作步骤；不确定或超出安防巡检范围时直接说明不知道，不要编造菜单或按钮名称。
            """;

    /** 每次请求最多带多少条历史消息 */
    private static final int MAX_CONTEXT_MESSAGES = 20;

    /** 单条消息最大长度 */
    private static final int MAX_CONTENT_LENGTH = 2000;

    /** 每个用户每分钟最多调用次数 */
    private static final int MAX_CALLS_PER_MINUTE = 20;

    private final DeepSeekClient deepSeekClient;

    private final AiFileStore aiFileStore;

    private final AiReportService aiReportService;

    /** 简单内存限流：userName -> 最近一分钟的调用时间戳 */
    private final Map<String, Deque<Long>> callRecords = new ConcurrentHashMap<>();

    /**
     * 配置检查（不返回 Key 本身）
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configured", deepSeekClient.isConfigured());
        data.put("model", deepSeekClient.getModel());
        data.put("baseUrl", deepSeekClient.getBaseUrl());
        return Result.success(data);
    }

    /**
     * 对话：带 fileId 走文件总结报告，否则为普通问答
     */
    @PostMapping("/chat")
    public Result<AiChatResponse> chat(@RequestBody AiChatRequest request, HttpServletRequest httpRequest) {
        String userName = String.valueOf(httpRequest.getAttribute("userName"));
        String fileId = request == null ? null : request.getFileId();
        if (fileId != null && !fileId.isBlank()) {
            return summarize(request, fileId.strip(), userName);
        }

        List<AiChatMessage> incoming = request == null ? null : request.getMessages();
        if (incoming == null || incoming.isEmpty()) {
            return Result.fail(400, "消息不能为空");
        }

        AiChatMessage last = incoming.get(incoming.size() - 1);
        if (!"user".equals(last.getRole()) || last.getContent() == null || last.getContent().isBlank()) {
            return Result.fail(400, "最后一条消息必须是用户提问");
        }
        if (last.getContent().length() > MAX_CONTENT_LENGTH) {
            return Result.fail(400, "问题太长了，请精简到 " + MAX_CONTENT_LENGTH + " 字以内");
        }

        if (!tryAcquire(userName)) {
            return Result.fail(429, "提问太频繁了，请一分钟后再试");
        }

        // 组装：system 提示 + 最近若干轮历史 + 本次提问
        List<AiChatMessage> messages = new ArrayList<>();
        messages.add(new AiChatMessage("system", SYSTEM_PROMPT));
        int from = Math.max(0, incoming.size() - MAX_CONTEXT_MESSAGES);
        for (AiChatMessage message : incoming.subList(from, incoming.size())) {
            String role = message.getRole();
            if (("user".equals(role) || "assistant".equals(role))
                    && message.getContent() != null && !message.getContent().isBlank()) {
                messages.add(message);
            }
        }

        try {
            return Result.success(deepSeekClient.chat(messages));
        } catch (IllegalStateException e) {
            log.warn("AI 对话失败: user={}, reason={}", userName, e.getMessage());
            return Result.fail(500, e.getMessage());
        } catch (Exception e) {
            log.error("AI 对话异常: user={}", userName, e);
            return Result.fail(500, "AI 服务异常，请稍后重试");
        }
    }

    /**
     * 文件总结报告分支
     */
    private Result<AiChatResponse> summarize(AiChatRequest request, String fileId, String userName) {
        if (!tryAcquire(userName)) {
            return Result.fail(429, "提问太频繁了，请一分钟后再试");
        }

        // 侧重点：用户最后一句问话（可以为空，空则按通用结构总结）
        String focus = "";
        List<AiChatMessage> incoming = request.getMessages();
        if (incoming != null && !incoming.isEmpty()) {
            AiChatMessage last = incoming.get(incoming.size() - 1);
            if ("user".equals(last.getRole()) && last.getContent() != null) {
                focus = last.getContent().strip();
            }
        }
        if (focus.length() > MAX_CONTENT_LENGTH) {
            return Result.fail(400, "侧重点太长了，请精简到 " + MAX_CONTENT_LENGTH + " 字以内");
        }

        try {
            AiFileStore.Entry entry = aiFileStore.get(fileId, userName);
            AiChatResponse response = aiReportService.summarize(entry, focus);
            // 生成完成即释放，避免同一文件被反复复用
            aiFileStore.remove(fileId, userName);
            return Result.success(response);
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("AI 文件总结失败: user={}, reason={}", userName, e.getMessage());
            return Result.fail(500, e.getMessage());
        } catch (Exception e) {
            log.error("AI 文件总结异常: user={}", userName, e);
            return Result.fail(500, "AI 服务异常，请稍后重试");
        }
    }

    /**
     * 一分钟滑动窗口限流
     */
    private boolean tryAcquire(String userName) {
        Deque<Long> records = callRecords.computeIfAbsent(userName, key -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (records) {
            while (!records.isEmpty() && now - records.peekFirst() > 60_000) {
                records.pollFirst();
            }
            if (records.size() >= MAX_CALLS_PER_MINUTE) {
                return false;
            }
            records.addLast(now);
            return true;
        }
    }
}
