package cn.sju.backend.service;

import cn.sju.backend.vo.UploadResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传服务
 */
public interface UploadService {

    UploadResult save(MultipartFile file);
}
