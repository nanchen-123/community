package cn.sju.backend.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 机器人 token 校验过滤器
 * 拦截 /api/robot/**、/api/photo/request、/api/photo/wait
 */
@Component
public class RobotTokenFilter implements Filter {

    @Value("${robot.token}")
    private String robotToken;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String token = httpRequest.getHeader("X-Robot-Token");
        if (!StringUtils.hasText(token)) {
            token = httpRequest.getHeader("Robot-Token");
        }
        if (!StringUtils.hasText(token)) {
            token = httpRequest.getParameter("token");
        }

        if (!StringUtils.hasText(token) || !token.equals(robotToken)) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write("{\"code\":401,\"message\":\"机器人 token 无效\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
