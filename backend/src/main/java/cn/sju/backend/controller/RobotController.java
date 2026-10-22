package cn.sju.backend.controller;

import cn.sju.backend.service.AiRecognizeService;
import cn.sju.backend.service.UploadService;
import cn.sju.backend.vo.UploadResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 机器人接口
 * 注意：本类接口不走统一 Result<T> 包装，保持原响应体格式
 */
@RestController
@RequestMapping("/api/robot")
public class RobotController {

    @Autowired
    private UploadService uploadService;

    @Autowired
    private AiRecognizeService aiRecognizeService;

    /**
     * 图片上传
     *
     * @param requestId  请求 ID（文本字段需排在 image 之前）
     * @param detectType 检测类型
     * @param image      图片文件
     */
    @PostMapping("/upload")
    public UploadResult upload(
            @RequestParam(value = "request_id", required = false) String requestId,
            @RequestParam(value = "detect_type", required = false, defaultValue = "object") String detectType,
            @RequestParam("image") MultipartFile image) {
        if (!StringUtils.hasText(requestId)) {
            throw new IllegalArgumentException("request_id 不能为空");
        }
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("image 不能为空");
        }
        UploadResult result = uploadService.save(image);
        aiRecognizeService.recognize(requestId, result.getUrl(), detectType);
        return result;
    }
}
