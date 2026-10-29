package cn.sju.backend.controller;

import cn.sju.backend.service.AiRecognizeService;
import cn.sju.backend.service.PhotoRequestService;
import cn.sju.backend.service.RobotReportService;
import cn.sju.backend.service.RobotStateService;
import cn.sju.backend.service.UploadService;
import cn.sju.backend.vo.UploadResult;
import cn.sju.backend.websocket.WsBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 机器人接口
 * 注意：本类接口不走统一 Result<T> 包装，保持原响应体格式
 */
@Slf4j
@RestController
@RequestMapping("/api/robot")
public class RobotController {

    @Autowired
    private UploadService uploadService;

    @Autowired
    private AiRecognizeService aiRecognizeService;

    @Autowired
    private RobotStateService robotStateService;

    @Autowired
    private RobotReportService robotReportService;

    @Autowired
    private WsBroadcaster wsBroadcaster;

    @Autowired
    private PhotoRequestService photoRequestService;

    /**
     * 心跳 / 实时状态上报（位姿、电量、当前航点、当前任务）
     * 机器人建议 5~10 秒调用一次
     */
    @PutMapping("/state")
    public Map<String, Object> state(@RequestBody(required = false) Map<String, Object> body) {
        String robotId = "default";
        Object rawId = body == null ? null : body.get("robot_id");
        if (rawId != null && StringUtils.hasText(rawId.toString())) {
            robotId = rawId.toString();
        }
        Map<String, Object> snapshot = robotStateService.update(robotId, body);
        wsBroadcaster.sendRobotStatus(snapshot);
        return snapshot;
    }

    /**
     * 小车轮询拍照请求状态
     * <p>
     * waiting = 仍在等待网页人工确认（小车驻车等待）；pending / processing / done = 已放行，
     * 小车可执行本地识别；skipped = 用户跳过；not_found = 请求已被清理。
     */
    @GetMapping("/photo-requests/{requestId}")
    public Map<String, Object> photoRequestStatus(@PathVariable String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("request_id", requestId);
        PhotoRequestService.PhotoRequest request = photoRequestService.get(requestId);
        if (request == null) {
            body.put("status", "not_found");
            return body;
        }
        body.put("status", request.getStatus());
        body.put("detect_type", request.getDetectType());
        body.put("waypoint", request.getWaypoint());
        return body;
    }

    /**
     * 小车撤销拍照请求
     * <p>
     * 等待人工确认超时、或任务被用户停止时调用，避免网页上残留待确认卡片。
     * 幂等：请求已被处理时返回 skipped=false。
     */
    @PostMapping("/photo-requests/{requestId}/skip")
    public Map<String, Object> skipPhotoRequest(@PathVariable String requestId) {
        boolean changed = photoRequestService.skip(requestId);
        PhotoRequestService.PhotoRequest request = photoRequestService.get(requestId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("request_id", requestId);
        body.put("skipped", changed);
        body.put("status", request == null ? "not_found" : request.getStatus());
        return body;
    }

    /**
     * 识别结果上报：人员/消防类写入告警，车牌类写入车牌记录
     */
    @PostMapping("/report")
    public Map<String, Object> report(@RequestBody Map<String, Object> body) {
        return robotReportService.report(body);
    }

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
            @RequestParam(value = "detect_type", required = false, defaultValue = "person") String detectType,
            @RequestParam("image") MultipartFile image) {
        if (!StringUtils.hasText(requestId)) {
            throw new IllegalArgumentException("request_id 不能为空");
        }
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("image 不能为空");
        }
        UploadResult result = uploadService.save(image);

        // 只有 T5AI-Board 板子上传（photoRequest 存在且状态合法）才走 AI 识别
        // robot_client 本地上传没有 photoRequest，只存图返回 URL，AI 识别由它自己在 report 里完成
        PhotoRequestService.PhotoRequest photoRequest = photoRequestService.get(requestId);
        if (photoRequest != null) {
            String status = photoRequest.getStatus();
            boolean notProcessed = !"done".equals(status) && !"failed".equals(status);
            String type = StringUtils.hasText(photoRequest.getDetectType())
                    ? photoRequest.getDetectType() : detectType;
            log.info("T5AI-Board 板子上传: requestId={}, detectType={}, status={}", requestId, type, status);
            aiRecognizeService.recognize(requestId, result.getUrl(), type, notProcessed, "board");
        } else {
            log.info("robot_client 本地上传（仅存图，不触发 AI 识别）: requestId={}", requestId);
        }
        return result;
    }
}
