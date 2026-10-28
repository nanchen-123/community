package cn.sju.backend.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据范围过滤器（在 JwtAuthFilter、RoleGuardFilter 之后执行）
 * <p>
 * 非超级管理员只能访问本人所属社区（communityId 取自 JWT，不信任前端）：
 * <ul>
 *   <li>查询参数 communityId 被强制改写为 token 中的社区</li>
 *   <li>JSON 请求体中的 communityId 被强制改写为 token 中的社区</li>
 *   <li>按 ID 访问/修改单条记录时校验该记录归属，不属于本社区一律 403</li>
 * </ul>
 */
@Component
public class DataScopeFilter implements Filter {

    private static final String SUPER_ADMIN = "超级管理员";

    /** 路径前缀 -> [表名, 主键列, 实体 ID 字段] */
    private static final Map<String, String[]> RESOURCES = new LinkedHashMap<>();

    static {
        RESOURCES.put("/api/device", new String[]{"device", "device_id", "deviceId"});
        RESOURCES.put("/api/task", new String[]{"task", "task_id", "taskId"});
        RESOURCES.put("/api/alarm", new String[]{"alarm", "alarm_id", "alarmId"});
        RESOURCES.put("/api/resident", new String[]{"resident", "household_id", "householdId"});
        RESOURCES.put("/api/license-plate", new String[]{"license_plate", "plate_id", "plateId"});
        RESOURCES.put("/api/sys-user", new String[]{"sys_user", "user_id", "userId"});
    }

    private static final Pattern SINGLE_RECORD = Pattern.compile("^(/api/[a-z-]+)/(\\d+)(/.*)?$");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        Object roleAttr = req.getAttribute("role");
        Object communityAttr = req.getAttribute("communityId");

        // 未认证（登录/注册/机器人）或超级管理员：不做社区范围限制
        if (roleAttr == null || SUPER_ADMIN.equals(String.valueOf(roleAttr))) {
            chain.doFilter(request, response);
            return;
        }
        if (communityAttr == null) {
            deny((HttpServletResponse) response, "账号未绑定社区，无法访问社区数据");
            return;
        }
        long communityId = Long.parseLong(String.valueOf(communityAttr));

        String path = req.getRequestURI();
        String resource = matchResource(path);
        if (resource == null) {
            chain.doFilter(request, response);
            return;
        }
        String[] meta = RESOURCES.get(resource);

        // 1. 路径中的记录 ID：校验归属
        Matcher matcher = SINGLE_RECORD.matcher(path);
        if (matcher.matches() && resource.equals(matcher.group(1))) {
            Long targetId = parseLong(matcher.group(2));
            if (targetId != null && !belongsToCommunity(meta, targetId, communityId)) {
                deny((HttpServletResponse) response, "无权访问其他社区的数据");
                return;
            }
        }

        // 2. JSON 请求体：强制改写 communityId，并校验体内 ID 归属
        String method = req.getMethod();
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            String contentType = req.getContentType();
            if (contentType != null && contentType.toLowerCase().contains("json")) {
                byte[] body = req.getInputStream().readAllBytes();
                byte[] rewritten = rewriteBody(body, meta, communityId);
                if (rewritten == null) {
                    deny((HttpServletResponse) response, "无权操作其他社区的数据");
                    return;
                }
                chain.doFilter(new ScopedRequest(req, rewritten, communityId), response);
                return;
            }
        }

        // 3. 仅改写查询参数
        chain.doFilter(new ScopedRequest(req, null, communityId), response);
    }

    /**
     * 改写请求体：communityId 强制为本社区；若体内带主键则校验归属。
     *
     * @return 改写后的字节；返回 null 表示越权
     */
    private byte[] rewriteBody(byte[] body, String[] meta, long communityId) throws IOException {
        if (body.length == 0) {
            return body;
        }
        JsonNode node = objectMapper.readTree(body);
        if (!(node instanceof ObjectNode object)) {
            return body;
        }
        JsonNode idNode = object.get(meta[2]);
        if (idNode != null && !idNode.isNull()) {
            Long bodyId = parseLong(idNode.asText());
            if (bodyId != null && !belongsToCommunity(meta, bodyId, communityId)) {
                return null;
            }
        }
        object.put("communityId", communityId);
        return objectMapper.writeValueAsBytes(object);
    }

    private boolean belongsToCommunity(String[] meta, long id, long communityId) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT community_id FROM " + meta[0] + " WHERE " + meta[1] + " = ?", Long.class, id);
        if (ids.isEmpty() || ids.get(0) == null) {
            // 记录不存在或未归属社区：交给业务层返回 404
            return true;
        }
        return ids.get(0) == communityId;
    }

    private String matchResource(String path) {
        for (String prefix : RESOURCES.keySet()) {
            if (path.equals(prefix) || path.startsWith(prefix + "/") || path.startsWith(prefix + "?")) {
                return prefix;
            }
        }
        return null;
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void deny(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"" + message + "\"}");
    }

    /** 强制 communityId 参数/请求体，并缓存请求体以支持多次读取 */
    private static class ScopedRequest extends HttpServletRequestWrapper {

        private final byte[] body;
        private final long communityId;

        ScopedRequest(HttpServletRequest request, byte[] body, long communityId) {
            super(request);
            this.body = body;
            this.communityId = communityId;
        }

        @Override
        public String getParameter(String name) {
            if ("communityId".equals(name)) {
                return String.valueOf(communityId);
            }
            return super.getParameter(name);
        }

        @Override
        public String[] getParameterValues(String name) {
            if ("communityId".equals(name)) {
                return new String[]{String.valueOf(communityId)};
            }
            return super.getParameterValues(name);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (body == null) {
                return super.getInputStream();
            }
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                }

                @Override
                public int read() {
                    return input.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (body == null) {
                return super.getReader();
            }
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body == null ? super.getContentLength() : body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body == null ? super.getContentLengthLong() : body.length;
        }
    }
}
