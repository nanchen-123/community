package cn.sju.backend.controller;

import cn.sju.backend.common.Result;
import cn.sju.backend.service.RobotStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 机器人运行概览（供前端展示，走 JWT 认证）
 */
@Tag(name = "机器人概览")
@RestController
@RequestMapping("/api/robot-status")
public class RobotStatusController {

    @Autowired
    private RobotStateService robotStateService;

    @Operation(summary = "机器人在线数量")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> all = robotStateService.all();
        long online = all.values().stream()
                .filter(state -> state instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("online")))
                .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("online", online);
        result.put("total", all.size());
        result.put("robots", all.values());
        return Result.success(result);
    }
}
