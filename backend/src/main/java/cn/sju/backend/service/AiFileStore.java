package cn.sju.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 助手文件暂存
 * 上传后立刻抽成文本并删除磁盘文件，内存里只留文本（按用户隔离、超时自动清理）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiFileStore {

    /** 允许的类型 */
    public static final Set<String> ALLOWED_EXT = Set.of("docx", "pdf", "txt", "md", "csv", "xlsx");

    private final AiFileTextExtractor extractor;

    @Value("${ai-file.tmp-dir:uploads/ai-tmp}")
    private String tmpDir;

    @Value("${ai-file.max-size-mb:30}")
    private long maxSizeMb;

    @Value("${ai-file.max-chars:100000}")
    private int maxChars;

    @Value("${ai-file.keep-minutes:30}")
    private long keepMinutes;

    /** fileId -> 抽取结果 */
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    private volatile long lastSweep = 0L;

    /** 抽取结果（文本不对外序列化，只在后端使用） */
    public record Entry(String fileId, String userId, String fileName, String ext,
                        String text, int chars, long expireAt) {
    }

    /**
     * 接收并解析上传文件
     *
     * @throws IllegalArgumentException 校验或解析失败（消息可直接展示给用户）
     */
    public Entry receive(MultipartFile file, String userId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("没有收到文件");
        }
        if (file.getSize() > maxSizeMb * 1024L * 1024L) {
            throw new IllegalArgumentException("文件超过 " + maxSizeMb + "MB，请压缩后再上传");
        }

        String originalName = file.getOriginalFilename();
        String ext = extensionOf(originalName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException("暂不支持该格式，请上传 docx / pdf / txt / md / xlsx 文件");
        }
        checkMagic(file, ext);

        Path target = null;
        try {
            Path dir = Paths.get(tmpDir).toAbsolutePath();
            Files.createDirectories(dir);
            target = dir.resolve(UUID.randomUUID() + "." + ext);
            file.transferTo(target.toFile());

            String text = extractor.extract(target, ext);
            if (text.isBlank()) {
                throw new IllegalArgumentException("未提取到文字，可能是扫描件或加密文档，请提供可复制文字的版本");
            }
            if (text.length() > maxChars) {
                throw new IllegalArgumentException("文档太长（约 " + text.length()
                        + " 字，上限 " + maxChars + " 字），建议拆分后再上传");
            }

            String fileId = "aidoc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            Entry entry = new Entry(fileId, userId, safeName(originalName, ext), ext, text, text.length(),
                    System.currentTimeMillis() + keepMinutes * 60_000L);
            entries.put(fileId, entry);
            log.info("AI 文件解析成功: user={}, name={}, ext={}, chars={}",
                    userId, entry.fileName(), ext, entry.chars());
            return entry;
        } catch (IOException e) {
            log.error("AI 文件解析失败: user={}, name={}", userId, originalName, e);
            throw new IllegalArgumentException("文件解析失败，请确认文件没有加密或损坏");
        } finally {
            // 文本已进内存，磁盘临时文件立刻删除
            if (target != null) {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException e) {
                    log.warn("临时文件删除失败: {}", target);
                }
            }
        }
    }

    /** 取文本，并校验归属（别人拿到 fileId 也用不了） */
    public Entry get(String fileId, String userId) {
        sweep();
        Entry entry = entries.get(fileId);
        if (entry == null) {
            throw new IllegalArgumentException("文件已过期，请重新上传");
        }
        if (!entry.userId().equals(userId)) {
            throw new IllegalArgumentException("无权使用该文件，请自己上传");
        }
        if (entry.expireAt() < System.currentTimeMillis()) {
            entries.remove(fileId);
            throw new IllegalArgumentException("文件已过期，请重新上传");
        }
        return entry;
    }

    /** 用户主动移除 */
    public void remove(String fileId, String userId) {
        Entry entry = entries.get(fileId);
        if (entry != null && entry.userId().equals(userId)) {
            entries.remove(fileId);
        }
    }

    /** 懒清理：每次上传/读取时顺带扫一遍过期条目（避免额外引入定时任务） */
    private void sweep() {
        long now = System.currentTimeMillis();
        if (now - lastSweep < 60_000L) {
            return;
        }
        lastSweep = now;
        entries.values().removeIf(entry -> entry.expireAt() < now);
    }

    /** 校验文件真实类型，防止改后缀绕过白名单 */
    private void checkMagic(MultipartFile file, String ext) {
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(4);
            boolean zip = head.length >= 2 && head[0] == 'P' && head[1] == 'K';
            boolean pdf = head.length >= 4 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F';
            if ((ext.equals("docx") || ext.equals("xlsx")) && !zip) {
                throw new IllegalArgumentException("文件内容与扩展名不符，请确认是 docx / xlsx 文件");
            }
            if (ext.equals("pdf") && !pdf) {
                throw new IllegalArgumentException("文件内容不是有效的 PDF");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("文件读取失败，请重新上传");
        }
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 只用于展示：去掉路径分隔符并限长 */
    private String safeName(String originalName, String ext) {
        String name = originalName == null ? "未命名" : originalName;
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.length() > 120) {
            name = name.substring(0, 120);
        }
        if (name.isBlank()) {
            name = "未命名." + ext;
        }
        return name;
    }
}
