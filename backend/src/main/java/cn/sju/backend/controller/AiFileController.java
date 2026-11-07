package cn.sju.backend.controller;

import cn.sju.backend.common.Result;
import cn.sju.backend.service.AiFileStore;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 助手文件接口：上传解析 / 移除
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiFileController {

    /** 每个用户每分钟最多上传次数 */
    private static final int MAX_UPLOADS_PER_MINUTE = 5;

    private final AiFileStore aiFileStore;

    private final Map<String, Deque<Long>> uploadRecords = new ConcurrentHashMap<>();

    /**
     * 上传文件并解析为文本（磁盘文件解析完即删，内存只留文本）
     */
    @PostMapping("/file")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                              HttpServletRequest httpRequest) {
        String userName = String.valueOf(httpRequest.getAttribute("userName"));
        if (!tryAcquire(userName)) {
            return Result.fail(429, "上传太频繁了，请一分钟后再试");
        }
        try {
            AiFileStore.Entry entry = aiFileStore.receive(file, userName);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("fileId", entry.fileId());
            data.put("fileName", entry.fileName());
            data.put("ext", entry.ext());
            data.put("chars", entry.chars());
            data.put("preview", preview(entry.text()));
            return Result.success(data);
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("AI 文件上传异常: user={}", userName, e);
            return Result.fail(500, "文件处理失败，请稍后重试");
        }
    }

    /**
     * 用户移除已上传的文件
     */
    @DeleteMapping("/file/{fileId}")
    public Result<Void> remove(@PathVariable String fileId, HttpServletRequest httpRequest) {
        String userName = String.valueOf(httpRequest.getAttribute("userName"));
        aiFileStore.remove(fileId, userName);
        return Result.success();
    }

    /** 摘要预览：取前 60 字，压掉换行 */
    private String preview(String text) {
        String flat = text.replace('\n', ' ').strip();
        return flat.length() <= 60 ? flat : flat.substring(0, 60) + "…";
    }

    /** 一分钟滑动窗口限流 */
    private boolean tryAcquire(String userName) {
        Deque<Long> records = uploadRecords.computeIfAbsent(userName, key -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (records) {
            while (!records.isEmpty() && now - records.peekFirst() > 60_000) {
                records.pollFirst();
            }
            if (records.size() >= MAX_UPLOADS_PER_MINUTE) {
                return false;
            }
            records.addLast(now);
            return true;
        }
    }
}
