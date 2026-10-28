package cn.sju.backend.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * 接口授权过滤器（在 JwtAuthFilter 之后执行）
 * <p>
 * JwtAuthFilter 只负责认证，本过滤器按「请求方法 + 路径」校验角色：
 * <ul>
 *   <li>/api/sys-user/**：管理员、超级管理员</li>
 *   <li>/api/community/** 的写操作：仅超级管理员</li>
 *   <li>设备、居民、车牌的新增/修改/删除：管理员、超级管理员</li>
 *   <li>告警的新增/删除：管理员、超级管理员（处置 PUT 普通用户可用）</li>
 *   <li>任务删除：管理员、超级管理员（创建/下发/停止普通用户可用）</li>
 * </ul>
 */
@Component
public class RoleGuardFilter implements Filter {

    private static final Set<String> ADMIN_ROLES = Set.of("管理员", "超级管理员");
    private static final String SUPER_ADMIN = "超级管理员";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        Object roleAttr = req.getAttribute("role");
        // 未认证请求（如登录、注册）由 JwtAuthFilter / 白名单处理
        if (roleAttr == null) {
            chain.doFilter(request, response);
            return;
        }

        String role = String.valueOf(roleAttr);
        String path = req.getRequestURI();
        String method = req.getMethod();

        if (path.startsWith("/api/sys-user") && !ADMIN_ROLES.contains(role)) {
            deny((HttpServletResponse) response, "仅管理员可进行用户管理");
            return;
        }
        if (path.startsWith("/api/community") && !"GET".equals(method) && !SUPER_ADMIN.equals(role)) {
            deny((HttpServletResponse) response, "仅超级管理员可管理社区");
            return;
        }
        if ((path.startsWith("/api/device") || path.startsWith("/api/resident")
                || path.startsWith("/api/license-plate"))
                && !"GET".equals(method) && !ADMIN_ROLES.contains(role)) {
            deny((HttpServletResponse) response, "仅管理员可执行该操作");
            return;
        }
        if (path.startsWith("/api/alarm") && ("POST".equals(method) || "DELETE".equals(method))
                && !ADMIN_ROLES.contains(role)) {
            deny((HttpServletResponse) response, "仅管理员可执行该操作");
            return;
        }
        if (path.startsWith("/api/task") && "DELETE".equals(method) && !ADMIN_ROLES.contains(role)) {
            deny((HttpServletResponse) response, "仅管理员可删除巡检任务");
            return;
        }

        chain.doFilter(request, response);
    }

    private void deny(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"" + message + "\"}");
    }
}
