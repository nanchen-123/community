package cn.sju.backend.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 拍照请求（内存）
 * <p>
 * 巡逻车到达航点后创建拍照请求，目标拍照设备上传图片后由 AI 识别回填结果，
 * 巡逻车通过 /api/photo/wait 阻塞获取结果。
 */
@Slf4j
@Service
public class PhotoRequestService {

    /** 待人工确认：小车到达航点后创建，等待网页点击「立即拍照」 */
    private static final String WAITING = "waiting";
    private static final String PENDING = "pending";
    private static final String PROCESSING = "processing";
    private static final String DONE = "done";
    private static final String FAILED = "failed";
    /** 用户跳过该航点：不拍照，也不做本地识别 */
    private static final String SKIPPED = "skipped";

    /** 已放行请求的有效期：超过该时长仍未处理的请求不再下发给拍照设备 */
    private static final long PENDING_TTL_SECONDS = 180;

    /** 待确认请求的有效期：与小车最长等待时长一致，超时后不再展示也不再受理 */
    private static final long WAITING_TTL_SECONDS = 180;

    private final Map<String, PhotoRequest> requests = new ConcurrentHashMap<>();

    public PhotoRequest create(Map<String, Object> body) {
        PhotoRequest request = new PhotoRequest();
        request.setRequestId("req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        request.setTaskId(text(body.get("task_id")));
        request.setWaypoint(text(body.get("waypoint")));
        request.setDetectType(text(body.get("detect_type")));
        request.setSourceRobot(text(body.get("source_robot")));
        request.setTargetRobot(text(body.get("target_robot")));
        request.setCoordinate(body.get("coordinate"));
        request.setStatus(WAITING);
        request.setCreatedAt(LocalDateTime.now());
        requests.put(request.getRequestId(), request);
        log.info("创建拍照请求（待人工确认）: requestId={}, waypoint={}, detectType={}, target={}",
                request.getRequestId(), request.getWaypoint(), request.getDetectType(), request.getTargetRobot());
        return request;
    }

    public PhotoRequest get(String requestId) {
        return requestId == null ? null : requests.get(requestId);
    }

    /**
     * 拍照设备（T5AI-Board）轮询待拍照任务
     *
     * @param targetRobot 设备 robot_id，为空时返回所有待处理请求
     */
    public List<Map<String, Object>> pendingForTarget(String targetRobot) {
        LocalDateTime expireBefore = LocalDateTime.now().minusSeconds(PENDING_TTL_SECONDS);
        List<PhotoRequest> list = new ArrayList<>();
        for (PhotoRequest request : requests.values()) {
            if (!PENDING.equals(request.getStatus())) {
                continue;
            }
            // 有效期从「用户放行」开始计算，避免用户在页面停留过久导致请求一放行就过期
            LocalDateTime released = request.getReleasedAt() != null
                    ? request.getReleasedAt() : request.getCreatedAt();
            if (released != null && released.isBefore(expireBefore)) {
                continue;
            }
            if (targetRobot != null && !targetRobot.isBlank()
                    && request.getTargetRobot() != null
                    && !targetRobot.equals(request.getTargetRobot())) {
                continue;
            }
            list.add(request);
        }
        list.sort(Comparator.comparing(PhotoRequest::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (PhotoRequest request : list) {
            Map<String, Object> item = toPayload(request);
            item.put("created_at", request.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    /**
     * 网页点击「立即拍照」：waiting → pending
     * <p>
     * 放行后拍照设备下一轮轮询即可领取；重复点击只有第一次生效。
     */
    public boolean confirm(String requestId) {
        PhotoRequest request = requests.get(requestId);
        if (request == null) {
            return false;
        }
        synchronized (request) {
            if (!WAITING.equals(request.getStatus())) {
                return false;
            }
            request.setStatus(PENDING);
            request.setReleasedAt(LocalDateTime.now());
        }
        log.info("用户确认拍照，已放行给拍照设备: requestId={}", requestId);
        return true;
    }

    /**
     * 网页点击「跳过该点」：waiting → skipped
     * <p>
     * 小车据此放弃该航点，既不触发拍照设备，也不做本地识别。
     */
    public boolean skip(String requestId) {
        PhotoRequest request = requests.get(requestId);
        if (request == null) {
            return false;
        }
        synchronized (request) {
            if (!WAITING.equals(request.getStatus())) {
                return false;
            }
            request.setStatus(SKIPPED);
        }
        log.info("用户跳过该航点，取消拍照: requestId={}", requestId);
        return true;
    }

    /** 仍在等待人工确认、且未超时的请求列表 */
    public List<PhotoRequest> listWaiting() {
        LocalDateTime expireBefore = LocalDateTime.now().minusSeconds(WAITING_TTL_SECONDS);
        List<PhotoRequest> list = new ArrayList<>();
        for (PhotoRequest request : requests.values()) {
            if (!WAITING.equals(request.getStatus())) {
                continue;
            }
            if (request.getCreatedAt() != null && request.getCreatedAt().isBefore(expireBefore)) {
                continue;
            }
            list.add(request);
        }
        list.sort(Comparator.comparing(PhotoRequest::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return list;
    }

    /** 供网页展示的请求详情 */
    public Map<String, Object> toWebPayload(PhotoRequest request) {
        Map<String, Object> map = toPayload(request);
        map.put("status", request.getStatus());
        map.put("created_at", request.getCreatedAt());
        map.put("released_at", request.getReleasedAt());
        return map;
    }

    /**
     * 抢占请求，保证同一请求只被识别一次（避免板子重复上传产生重复告警）
     */
    public boolean markProcessing(String requestId) {
        PhotoRequest request = requests.get(requestId);
        if (request == null) {
            return false;
        }
        synchronized (request) {
            if (!PENDING.equals(request.getStatus())) {
                return false;
            }
            request.setStatus(PROCESSING);
            return true;
        }
    }

    public boolean isDone(String requestId) {
        PhotoRequest request = requests.get(requestId);
        return request != null && DONE.equals(request.getStatus());
    }

    /** AI 识别成功，回填结果 */
    public void complete(String requestId, Map<String, Object> result) {
        PhotoRequest request = requests.get(requestId);
        if (request == null) {
            return;
        }
        request.setStatus(DONE);
        request.setResult(result);
        log.info("拍照请求识别完成: requestId={}", requestId);
    }

    public void fail(String requestId, String error) {
        PhotoRequest request = requests.get(requestId);
        if (request == null) {
            return;
        }
        request.setStatus(FAILED);
        request.setError(error);
        log.warn("拍照请求识别失败: requestId={}, error={}", requestId, error);
    }

    /** 阻塞等待结果，超时返回当前状态（status=pending 表示还没识别完） */
    public Map<String, Object> await(String requestId, long timeoutMs) {
        PhotoRequest request = requests.get(requestId);
        if (request == null) {
            throw new IllegalArgumentException("拍照请求不存在: " + requestId);
        }
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        while ((WAITING.equals(request.getStatus()) || PENDING.equals(request.getStatus())
                || PROCESSING.equals(request.getStatus()))
                && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return snapshot(request);
    }

    private Map<String, Object> snapshot(PhotoRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("request_id", request.getRequestId());
        map.put("task_id", request.getTaskId());
        map.put("waypoint", request.getWaypoint());
        map.put("detect_type", request.getDetectType());
        map.put("target_robot", request.getTargetRobot());
        map.put("status", request.getStatus());
        map.put("result", request.getResult());
        map.put("error", request.getError());
        map.put("created_at", request.getCreatedAt());
        map.put("released_at", request.getReleasedAt());
        return map;
    }

    /**
     * 按来源机器人找最近一个活跃的拍照请求（pending/processing 且在时间窗口内）。
     * 用于匹配 robot_client 本地上传（request_id 是自造的 "robot-xxx-timestamp" 格式，
     * 没有经过 create，所以直接 get 会返回 null）。
     */
    public PhotoRequest findRecentActiveBySource(String sourceRobot, long withinMs) {
        if (sourceRobot == null) return null;
        LocalDateTime threshold = LocalDateTime.now().minusNanos(withinMs * 1_000_000);
        PhotoRequest best = null;
        for (PhotoRequest request : requests.values()) {
            if (!sourceRobot.equals(request.getSourceRobot())) continue;
            String status = request.getStatus();
            if (!PENDING.equals(status) && !PROCESSING.equals(status)) continue;
            if (request.getCreatedAt() == null || request.getCreatedAt().isBefore(threshold)) continue;
            if (best == null || request.getCreatedAt().isAfter(best.getCreatedAt())) {
                best = request;
            }
        }
        return best;
    }
    public Map<String, Object> toPayload(PhotoRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("request_id", request.getRequestId());
        map.put("task_id", request.getTaskId());
        map.put("waypoint", request.getWaypoint());
        map.put("detect_type", request.getDetectType());
        map.put("source_robot", request.getSourceRobot());
        map.put("target_robot", request.getTargetRobot());
        map.put("coordinate", request.getCoordinate());
        return map;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @Data
    public static class PhotoRequest {
        private String requestId;
        private String taskId;
        private String waypoint;
        private String detectType;
        private String sourceRobot;
        private String targetRobot;
        private Object coordinate;
        private String status;
        private Map<String, Object> result;
        private String error;
        private LocalDateTime createdAt;
        /** 用户点击「立即拍照」的放行时间，空表示尚未确认 */
        private LocalDateTime releasedAt;
    }
}
