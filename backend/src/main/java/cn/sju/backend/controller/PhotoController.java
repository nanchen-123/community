package cn.sju.backend.controller;

import cn.sju.backend.entity.Task;
import cn.sju.backend.service.PhotoRequestService;
import cn.sju.backend.service.TaskService;
import cn.sju.backend.websocket.WsBroadcaster;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 拍照请求接口
 * <p>
 * 巡逻车创建拍照请求 → 后端广播给目标拍照设备 → 目标设备拍照上传（/api/robot/upload）
 * → AI 识别 → 巡逻车通过 /api/photo/wait 取结果。
 * 走机器人 token 校验，按项目约定不包装统一 Result&lt;T&gt;。
 */
@Slf4j
@RestController
@RequestMapping("/api/photo")
public class PhotoController {

    /** 默认等待超时（毫秒） */
    private static final long DEFAULT_WAIT_MS = 15_000L;
    private static final long MAX_WAIT_MS = 120_000L;

    @Autowired
    private PhotoRequestService photoRequestService;

    @Autowired
    private WsBroadcaster wsBroadcaster;

    @Autowired
    private TaskService taskService;

    private static final String SUPER_ADMIN = "超级管理员";

    /**
     * 网页查询待人工确认的拍照请求
     * <p>
     * 小车到达航点后请求进入 waiting，页面轮询该接口展示「立即拍照 / 跳过」入口。
     * 非超级管理员只能看到本人所属社区的请求。
     */
    @GetMapping("/requests")
    public Map<String, Object> requests(HttpServletRequest httpRequest) {
        Long myCommunityId = userCommunityId(httpRequest);
        List<Map<String, Object>> records = new ArrayList<>();
        if (isSuperAdmin(httpRequest) || myCommunityId != null) {
            for (PhotoRequestService.PhotoRequest request : photoRequestService.listWaiting()) {
                if (!isSuperAdmin(httpRequest)
                        && !myCommunityId.equals(taskCommunityId(request.getTaskId()))) {
                    continue;
                }
                Map<String, Object> item = photoRequestService.toWebPayload(request);
                item.put("community_id", taskCommunityId(request.getTaskId()));
                records.add(item);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", records.size());
        result.put("records", records);
        return result;
    }

    /** 网页点击「立即拍照」：放行给 T5AI-Board，小车随即做本地识别 */
    @PostMapping("/{requestId}/confirm")
    public ResponseEntity<Map<String, Object>> confirm(@PathVariable String requestId,
                                                       HttpServletRequest httpRequest) {
        return decide(requestId, httpRequest, true);
    }

    /** 网页点击「跳过该点」：不拍照，也不做本地识别 */
    @PostMapping("/{requestId}/skip")
    public ResponseEntity<Map<String, Object>> skip(@PathVariable String requestId,
                                                    HttpServletRequest httpRequest) {
        return decide(requestId, httpRequest, false);
    }

    private ResponseEntity<Map<String, Object>> decide(String requestId, HttpServletRequest httpRequest,
                                                       boolean confirm) {
        PhotoRequestService.PhotoRequest request = photoRequestService.get(requestId);
        if (request == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "拍照请求不存在或已失效"));
        }
        if (!canOperate(httpRequest, request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "无权操作其他社区的拍照请求"));
        }
        boolean changed = confirm ? photoRequestService.confirm(requestId)
                : photoRequestService.skip(requestId);
        if (!changed) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "该请求已处理，当前状态：" + request.getStatus(),
                            "request_id", requestId, "status", request.getStatus()));
        }
        Map<String, Object> payload = photoRequestService.toWebPayload(request);
        if (confirm) {
            wsBroadcaster.sendPhotoCommand(payload);
            log.info("拍照请求已放行: requestId={}, waypoint={}", requestId, request.getWaypoint());
        } else {
            log.info("拍照请求已跳过: requestId={}, waypoint={}", requestId, request.getWaypoint());
        }
        Map<String, Object> body = new LinkedHashMap<>(payload);
        body.put("request_id", requestId);
        return ResponseEntity.ok(body);
    }

    private boolean isSuperAdmin(HttpServletRequest httpRequest) {
        return SUPER_ADMIN.equals(String.valueOf(httpRequest.getAttribute("role")));
    }

    private Long userCommunityId(HttpServletRequest httpRequest) {
        Object value = httpRequest.getAttribute("communityId");
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 请求归属社区：按 task_id 反查任务所属社区 */
    private Long taskCommunityId(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return null;
        }
        try {
            Task task = taskService.getById(Long.parseLong(taskId.trim()));
            return task == null ? null : task.getCommunityId();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean canOperate(HttpServletRequest httpRequest,
                               PhotoRequestService.PhotoRequest request) {
        if (isSuperAdmin(httpRequest)) {
            return true;
        }
        Long myCommunityId = userCommunityId(httpRequest);
        Long requestCommunityId = taskCommunityId(request.getTaskId());
        return myCommunityId != null && myCommunityId.equals(requestCommunityId);
    }

    /** 创建拍照请求 */
    @PostMapping("/request")
    public Map<String, Object> request(@RequestBody Map<String, Object> body) {
        PhotoRequestService.PhotoRequest request = photoRequestService.create(body);
        // 广播给已连接的设备（目标拍照设备按 target_robot 过滤）
        wsBroadcaster.sendPhotoRequest(photoRequestService.toPayload(request));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("request_id", request.getRequestId());
        result.put("status", request.getStatus());
        return result;
    }

    /**
     * 拍照协同接口，支持两种调用方式：
     * <p>
     * 1. 巡逻车查询识别结果：GET /api/photo/wait?request_id=xxx
     * 2. 拍照设备（T5AI-Board）轮询待拍照任务：GET /api/photo/wait?robot_id=t5ai-001
     * 无任务时返回 204，有任务时返回该请求（含 request_id / detect_type）。
     * 设备端 HTTP 超时为 5s，因此 robot_id 轮询最多等待 2.5s。
     */
    @GetMapping("/wait")
    public ResponseEntity<Object> wait(
            @RequestParam(value = "request_id", required = false) String requestId,
            @RequestParam(value = "robot_id", required = false) String robotId,
            @RequestParam(value = "timeout", defaultValue = "15") Long timeoutSeconds) {
        long timeoutMs = timeoutSeconds == null ? DEFAULT_WAIT_MS : timeoutSeconds * 1000L;
        timeoutMs = Math.min(Math.max(timeoutMs, 0), MAX_WAIT_MS);

        // 巡逻车按 request_id 阻塞等待识别结果
        if (StringUtils.hasText(requestId)) {
            return ResponseEntity.ok(photoRequestService.await(requestId, timeoutMs));
        }

        // 拍照设备按 robot_id 轮询待拍照任务
        if (!StringUtils.hasText(robotId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "request_id 或 robot_id 不能为空"));
        }
        long boardWaitMs = Math.min(timeoutMs, 2500);
        long deadline = System.currentTimeMillis() + boardWaitMs;
        while (true) {
            List<Map<String, Object>> pending = photoRequestService.pendingForTarget(robotId);
            if (!pending.isEmpty()) {
                Map<String, Object> first = pending.get(0);
                String pendingRequestId = (String) first.get("request_id");
                // 立即原子抢占，防止后续 poll 重复拿到同一请求
                boolean claimed = photoRequestService.markProcessing(pendingRequestId);
                if (!claimed) {
                    // 其他 poll 刚抢走了，等下一轮
                    if (System.currentTimeMillis() >= deadline) {
                        return ResponseEntity.noContent().build();
                    }
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return ResponseEntity.noContent().build();
                    }
                    continue;
                }
                log.info("拍照设备领取任务: robotId={}, requestId={}", robotId, pendingRequestId);
                return ResponseEntity.ok(first);
            }
            if (System.currentTimeMillis() >= deadline) {
                return ResponseEntity.noContent().build();
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ResponseEntity.noContent().build();
            }
        }
    }
}
