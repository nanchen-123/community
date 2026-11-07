package cn.sju.backend.service;

import cn.sju.backend.client.DeepSeekClient;
import cn.sju.backend.dto.AiChatMessage;
import cn.sju.backend.dto.AiChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件总结报告：短文档一次性生成，长文档先分段提炼要点再合并
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReportService {

    /** 最终报告的结构要求（单次生成与合并生成都用这份） */
    private static final String REPORT_RULE = """
            你是文档总结助手，面向小区物业和安保人员。
            严格依据用户提供的原文生成报告，不得编造内容；原文没有提到的写「原文未说明」；
            数字、日期、人名必须与原文一致，不要改写单位。
            输出简体中文 Markdown，结构固定：

            # <文档标题>
            ## 一、文档概览
            （用一句话说明这是什么文档、篇幅多大、涉及哪些主题）
            ## 二、主要内容
            （分 3~6 点，每点一段，保留关键细节）
            ## 三、关键数据与结论
            （表格：项目 | 数值或结论 | 出处）
            ## 四、风险与待确认事项
            ## 五、建议

            如果原文内容不足以填写某一节，该节写「原文未说明」，不要凑字数。
            """;

    private static final String CHUNK_RULE = """
            你是文档要点提炼助手。用户会给你一份长文档的其中一部分，
            请提炼出 300 字以内的要点，保留数字、日期、人名和结论性表述。
            用无序列表输出，不要写开头和结尾的客套话。
            """;

    private final DeepSeekClient deepSeekClient;

    @Value("${ai-file.chunk-chars:10000}")
    private int chunkChars;

    @Value("${ai-file.max-chunks:10}")
    private int maxChunks;

    /**
     * 生成总结报告
     *
     * @param entry 已解析的文件
     * @param focus 用户补充的侧重点（可为空）
     */
    public AiChatResponse summarize(AiFileStore.Entry entry, String focus) {
        List<String> chunks = splitByParagraph(entry.text(), chunkChars);
        if (chunks.size() > maxChunks) {
            throw new IllegalArgumentException("文档过长，请拆分后再上传（当前按每段 "
                    + chunkChars + " 字切分需要 " + chunks.size() + " 段）");
        }

        if (chunks.size() == 1) {
            log.info("文件总结: 单次生成, file={}, chars={}", entry.fileName(), entry.chars());
            return deepSeekClient.chat(List.of(
                    new AiChatMessage("system", REPORT_RULE),
                    new AiChatMessage("user", buildFullPrompt(entry, focus, chunks.get(0)))));
        }

        log.info("文件总结: 分段生成, file={}, chars={}, chunks={}",
                entry.fileName(), entry.chars(), chunks.size());
        List<String> points = new ArrayList<>();
        int promptTokens = 0;
        int completionTokens = 0;
        for (int i = 0; i < chunks.size(); i++) {
            AiChatResponse part = deepSeekClient.chat(List.of(
                    new AiChatMessage("system", CHUNK_RULE),
                    new AiChatMessage("user", "《" + entry.fileName() + "》第 " + (i + 1) + "/"
                            + chunks.size() + " 部分，请提炼要点：\n\n" + chunks.get(i))));
            points.add(part.getContent());
            promptTokens += part.getPromptTokens() == null ? 0 : part.getPromptTokens();
            completionTokens += part.getCompletionTokens() == null ? 0 : part.getCompletionTokens();
        }

        AiChatResponse merged = deepSeekClient.chat(List.of(
                new AiChatMessage("system", REPORT_RULE),
                new AiChatMessage("user", buildMergePrompt(entry, focus, points))));
        int totalPrompt = promptTokens + (merged.getPromptTokens() == null ? 0 : merged.getPromptTokens());
        int totalCompletion = completionTokens + (merged.getCompletionTokens() == null ? 0 : merged.getCompletionTokens());
        return new AiChatResponse(merged.getContent(), totalPrompt, totalCompletion);
    }

    /** 单次生成：原文 + 侧重点一起给模型 */
    private String buildFullPrompt(AiFileStore.Entry entry, String focus, String text) {
        return "【文件】" + entry.fileName() + "（约 " + entry.chars() + " 字）\n"
                + "【总结侧重点】" + (focus == null || focus.isBlank() ? "未指定，按通用结构总结" : focus) + "\n\n"
                + "【原文开始】\n" + text + "\n【原文结束】";
    }

    /** 合并生成：把各段要点汇总成最终报告 */
    private String buildMergePrompt(AiFileStore.Entry entry, String focus, List<String> points) {
        StringBuilder sb = new StringBuilder();
        sb.append("【文件】").append(entry.fileName())
                .append("（约 ").append(entry.chars()).append(" 字，共 ")
                .append(points.size()).append(" 段）\n");
        sb.append("【总结侧重点】")
                .append(focus == null || focus.isBlank() ? "未指定，按通用结构总结" : focus)
                .append("\n\n【各部分要点开始】\n");
        for (int i = 0; i < points.size(); i++) {
            sb.append("—— 第").append(i + 1).append("部分 ——\n").append(points.get(i)).append('\n');
        }
        sb.append("【各部分要点结束】\n\n"
                + "请把以上要点合并成一份完整报告，去掉重复内容，保持结构固定。");
        return sb.toString();
    }

    /**
     * 按段落边界切分，避免把句子从中间截断
     */
    private List<String> splitByParagraph(String text, int size) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String block : text.split("\n")) {
            if (current.length() + block.length() + 1 > size && current.length() > 0) {
                chunks.add(current.toString().strip());
                current.setLength(0);
            }
            // 单段超长时硬切
            if (block.length() > size) {
                for (int i = 0; i < block.length(); i += size) {
                    chunks.add(block.substring(i, Math.min(block.length(), i + size)));
                }
                continue;
            }
            current.append(block).append('\n');
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().strip());
        }
        return chunks;
    }
}
