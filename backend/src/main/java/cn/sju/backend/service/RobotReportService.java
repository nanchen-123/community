package cn.sju.backend.service;

import cn.sju.backend.entity.Alarm;
import cn.sju.backend.entity.LicensePlate;
import cn.sju.backend.entity.Task;
import cn.sju.backend.websocket.WsBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 机器人识别结果上报
 * <p>
 * 人员/消防类写入 alarm 表，车牌类写入 license_plate 表，并广播给前端。
 */
@Slf4j
@Service
public class RobotReportService {

    private static final String HANDLE_PENDING = "未处理";

    @Autowired
    private AlarmService alarmService;

    @Autowired
    private LicensePlateService licensePlateService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private WsBroadcaster wsBroadcaster;

    public Map<String, Object> report(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("上报内容不能为空");
        }
        String rawType = text(body.get("type"));
        if (!StringUtils.hasText(rawType)) {
            throw new IllegalArgumentException("type 不能为空");
        }

        Long taskId = parseLong(body.get("task_id"));
        Task task = taskId == null ? null : taskService.getById(taskId);
        Long communityId = task != null ? task.getCommunityId() : parseLong(body.get("community_id"));
        Long deviceId = task != null ? task.getDeviceId() : parseLong(body.get("device_id"));
        String location = text(body.get("location"));
        String imgUrl = firstText(body.get("image_url"), body.get("img_url"));

        if (isPlate(rawType)) {
            return savePlate(taskId, communityId, location, imgUrl, body);
        }
        return saveAlarm(taskId, communityId, deviceId, location, imgUrl, rawType, body);
    }

    private Map<String, Object> saveAlarm(Long taskId, Long communityId, Long deviceId,
                                          String location, String imgUrl,
                                          String rawType, Map<String, Object> body) {
        Alarm alarm = new Alarm();
        alarm.setTaskId(taskId);
        alarm.setCommunityId(communityId);
        alarm.setDeviceId(deviceId);
        alarm.setAlarmType(toAlarmType(rawType));
        alarm.setLocation(location);
        alarm.setHandleStatus(HANDLE_PENDING);
        alarm.setFindTime(LocalDateTime.now());
        alarm.setLevel(text(body.get("level")));
        alarm.setRecognizeStatus(firstText(body.get("recognize_status"), "识别成功"));
        alarm.setConfidence(parseDecimal(body.get("confidence")));
        alarm.setImgUrl(imgUrl);
        alarm.setRemark(text(body.get("remark")));
        alarmService.save(alarm);

        log.info("机器人上报告警: alarmId={}, type={}, location={}", alarm.getAlarmId(), alarm.getAlarmType(), location);
        wsBroadcaster.sendAlarmNew(alarm);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saved", true);
        result.put("kind", "alarm");
        result.put("id", alarm.getAlarmId());
        return result;
    }

    private Map<String, Object> savePlate(Long taskId, Long communityId, String location,
                                          String imgUrl, Map<String, Object> body) {
        String plateNumber = firstText(body.get("plate_number"), body.get("plateNumber"));
        if (!StringUtils.hasText(plateNumber)) {
            throw new IllegalArgumentException("车牌上报必须包含 plate_number");
        }
        LicensePlate plate = new LicensePlate();
        plate.setTaskId(taskId);
        plate.setCommunityId(communityId);
        plate.setPlateNumber(plateNumber);
        plate.setImgUrl(imgUrl);
        plate.setLocation(location);
        plate.setHandleStatus(HANDLE_PENDING);
        plate.setRemark(text(body.get("remark")));
        licensePlateService.save(plate);

        log.info("机器人上报车牌: plateId={}, plateNumber={}, location={}", plate.getPlateId(), plateNumber, location);
        wsBroadcaster.sendPlateNew(plate);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saved", true);
        result.put("kind", "plate");
        result.put("id", plate.getPlateId());
        return result;
    }

    /** 机器人上报类型 → 系统告警类型 */
    private String toAlarmType(String rawType) {
        String type = rawType.toLowerCase();
        if (type.contains("person") || type.contains("人员") || type.contains("行人")) {
            return "人员异常";
        }
        if (type.contains("flame") || type.contains("fire") || type.contains("火灾")
                || type.contains("消防") || type.contains("corridor") || type.contains("堆积")) {
            return "消防安全";
        }
        return "其他";
    }

    private boolean isPlate(String rawType) {
        String type = rawType.toLowerCase();
        return type.contains("plate") || type.contains("车辆") || type.contains("车牌");
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text) && !"null".equals(text)) {
                return text;
            }
        }
        return null;
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

    private BigDecimal parseDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
