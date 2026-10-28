package cn.sju.backend.service;

import cn.sju.backend.client.AiRecognizeClient;
import cn.sju.backend.entity.Alarm;
import cn.sju.backend.entity.LicensePlate;
import cn.sju.backend.entity.Task;
import cn.sju.backend.websocket.WsBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 识别服务
 */
@Slf4j
@Service
public class AiRecognizeService {

    private static final String HANDLE_PENDING = "未处理";

    @Autowired
    private AiRecognizeClient aiRecognizeClient;

    @Autowired
    private WsBroadcaster wsBroadcaster;

    @Autowired
    private PhotoRequestService photoRequestService;

    @Autowired
    private AlarmService alarmService;

    @Autowired
    private LicensePlateService licensePlateService;

    @Autowired
    private TaskService taskService;

    /**
     * 异步触发 AI 识别（仅 T5AI-Board 板子上传时调用）
     *
     * @param requestId     拍照请求 ID
     * @param imagePath     图片访问路径
     * @param detectType    检测类型：person / flame / plate
     * @param persistResult 是否把识别结果落库为告警/车牌记录（防重复，只在 pending/processing 时为 true）
     * @param source        图片来源：固定为 "board"（T5AI-Board 板子）
     */
    @Async("aiTaskExecutor")
    public void recognize(String requestId, String imagePath, String detectType, boolean persistResult, String source) {
        log.info("异步触发 AI 识别: requestId={}, imagePath={}, detectType={}, persist={}, source={}",
                requestId, imagePath, detectType, persistResult, source);
        try {
            Map<String, Object> result = aiRecognizeClient.recognize(imagePath, detectType, source);

            // 回填拍照请求结果，供 /api/photo/wait 获取
            photoRequestService.complete(requestId, result);

            // 拍照点产生的识别结果写入安防台账（告警/车牌），前端直接可见
            if (persistResult) {
                PhotoRequestService.PhotoRequest photoRequest = photoRequestService.get(requestId);
                if (photoRequest != null) {
                    persist(photoRequest, imagePath, detectType, result, source);
                }
            }

            wsBroadcaster.sendRecognitionResult(Map.of(
                    "requestId", requestId,
                    "imagePath", imagePath,
                    "source", source,
                    "result", result
            ));
            log.info("AI 识别完成并广播: requestId={}, source={}", requestId, source);
        } catch (Exception e) {
            log.error("AI 识别失败: requestId={}, source={}", requestId, source, e);
            photoRequestService.fail(requestId, e.getMessage());
            wsBroadcaster.sendRecognitionResult(Map.of(
                    "requestId", requestId,
                    "imagePath", imagePath,
                    "source", source,
                    "error", e.getMessage()
            ));
        }
    }

    private void persist(PhotoRequestService.PhotoRequest photoRequest, String imagePath,
                         String detectType, Map<String, Object> result, String source) {
        // mock 结果是联调用的假数据，不落库，避免产生虚假告警
        if (Boolean.TRUE.equals(result.get("mock"))) {
            log.warn("AI 处于 mock 模式，跳过落库: requestId={}", photoRequest.getRequestId());
            return;
        }

        Long taskId = parseLong(photoRequest.getTaskId());
        Task task = taskId == null ? null : taskService.getById(taskId);
        Long communityId = task != null ? task.getCommunityId() : null;
        Long deviceId = task != null ? task.getDeviceId() : null;
        String location = photoRequest.getWaypoint();

        if ("plate".equalsIgnoreCase(detectType)) {
            for (Map<String, Object> plate : mapList(result.get("plates"))) {
                String plateNumber = text(plate.get("plate_number"));
                if (!StringUtils.hasText(plateNumber)) {
                    continue;
                }
                LicensePlate entity = new LicensePlate();
                entity.setTaskId(taskId);
                entity.setCommunityId(communityId);
                entity.setPlateNumber(plateNumber);
                entity.setImgUrl(imagePath);
                entity.setLocation(location);
                entity.setHandleStatus(HANDLE_PENDING);
                entity.setRemark("AI识别(T5AI-Board) 车牌识别 @ " + (location == null ? "--" : location));
                licensePlateService.save(entity);
                log.info("拍照点车牌落库: plateId={}, plateNumber={}, waypoint={}",
                        entity.getPlateId(), plateNumber, location);
                wsBroadcaster.sendPlateNew(entity);
            }
            return;
        }

        List<Map<String, Object>> objects = mapList(result.get("objects"));
        if (objects.isEmpty()) {
            log.info("拍照点未检测到异常: requestId={}, waypoint={}",
                    photoRequest.getRequestId(), location);
            return;
        }

        boolean person = "person".equalsIgnoreCase(detectType);
        Alarm alarm = new Alarm();
        alarm.setTaskId(taskId);
        alarm.setCommunityId(communityId);
        alarm.setDeviceId(deviceId);
        alarm.setAlarmType(person ? "人员异常" : "消防安全");
        alarm.setLocation(location);
        alarm.setHandleStatus(HANDLE_PENDING);
        alarm.setFindTime(LocalDateTime.now());
        alarm.setLevel(person ? "警告" : "严重");
        alarm.setRecognizeStatus("AI识别(T5AI-Board)");
        alarm.setConfidence(toPercent(maxConfidence(objects)));
        alarm.setImgUrl(imagePath);
        alarm.setRemark(summary(objects));
        alarmService.save(alarm);
        log.info("拍照点告警落库: alarmId={}, type={}, waypoint={}",
                alarm.getAlarmId(), alarm.getAlarmType(), location);
        wsBroadcaster.sendAlarmNew(alarm);
    }

    /** 汇总识别标签，如 resident×1、outsider×2 */
    private String summary(List<Map<String, Object>> objects) {
        return objects.stream()
                .map(item -> text(item.get("label")))
                .filter(StringUtils::hasText)
                .collect(Collectors.groupingBy(label -> label, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> entry.getKey() + "×" + entry.getValue())
                .collect(Collectors.joining("、"));
    }

    private double maxConfidence(List<Map<String, Object>> objects) {
        double max = 0;
        for (Map<String, Object> item : objects) {
            Object value = item.get("confidence");
            if (value instanceof Number number) {
                max = Math.max(max, number.doubleValue());
            }
        }
        return max;
    }

    /** 置信度转百分数 */
    private BigDecimal toPercent(double confidence) {
        return BigDecimal.valueOf(Math.round(confidence * 100));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        return List.of();
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
