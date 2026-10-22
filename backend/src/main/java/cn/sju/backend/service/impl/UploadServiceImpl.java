package cn.sju.backend.service.impl;

import cn.sju.backend.service.UploadService;
import cn.sju.backend.vo.UploadResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件上传服务实现
 */
@Service
public class UploadServiceImpl implements UploadService {

    @Value("${upload.dir:uploads}")
    private String uploadDir;

    @Override
    public UploadResult save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String ext = getExtension(originalFilename);
        if (!StringUtils.hasText(ext)) {
            ext = "jpg";
        }

        String dateDir = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String filename = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;
        Path targetDir = Paths.get(uploadDir, dateDir);

        try {
            Files.createDirectories(targetDir);
            Path targetPath = targetDir.resolve(filename);
            file.transferTo(targetPath.toFile());

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
