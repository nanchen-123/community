package cn.sju.backend.filter;

import cn.sju.backend.util.JwtUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * JWT 认证过滤器
 * 拦截 /api/**，放行 /api/auth/**
 */
@Component
public class JwtAuthFilter implements Filter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String uri = httpRequest.getRequestURI();
        if (uri.startsWith("/api/auth/") || uri.startsWith("/api/robot/")
                || uri.equals("/api/photo/request") || uri.equals("/api/photo/wait")) {
            chain.doFilter(request, response);
            return;
        }
        // 抓拍图片由 <img> 直接加载，无法携带 Authorization 头，仅放行静态读
        if (uri.startsWith("/uploads/") && "GET".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String token = extractToken(httpRequest);
        if (!StringUtils.hasText(token) || !jwtUtil.validateToken(token)) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write("{\"code\":401,\"message\":\"未登录或 token 已过期\"}");
            return;
        }

        httpRequest.setAttribute("userId", jwtUtil.getUserId(token));
        httpRequest.setAttribute("userName", jwtUtil.getUserName(token));
        httpRequest.setAttribute("role", jwtUtil.getRole(token));
        httpRequest.setAttribute("communityId", jwtUtil.getCommunityId(token));
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
