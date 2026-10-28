package cn.sju.backend.service.impl;

import cn.sju.backend.service.UploadService;
import cn.sju.backend.vo.UploadResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传服务实现
 */
@Service
public class UploadServiceImpl implements UploadService {

    /** 仅允许的图片扩展名，避免 html/svg 等可执行内容落盘 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;

    @Value("${upload.dir:uploads}")
    private String uploadDir;

    @Override
    public UploadResult save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过 10MB");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String ext = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("仅支持 jpg/jpeg/png/gif/webp/bmp 格式的图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("上传文件必须为图片类型");
        }

        String dateDir = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String filename = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;
        // 必须转成绝对路径：MultipartFile.transferTo 收到相对路径时会写到 Tomcat 临时目录下，父目录不存在导致保存失败
        Path targetDir = Paths.get(uploadDir, dateDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(targetDir);
            Path targetPath = targetDir.resolve(filename);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            String url = "/uploads/" + dateDir + "/" + filename;
            return new UploadResult(url, filename, true);
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败", e);
        }
    }

    private String getExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int idx = filename.lastIndexOf('.');
        return idx == -1 ? "" : filename.substring(idx + 1).toLowerCase();
    }
}
