package cn.sju.backend.config;

import cn.sju.backend.filter.JwtAuthFilter;
import cn.sju.backend.filter.RobotTokenFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 过滤器注册配置
 */
@Configuration
public class FilterConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Autowired
    private RobotTokenFilter robotTokenFilter;

    @Bean
    public FilterRegistrationBean<RobotTokenFilter> robotTokenFilterRegistration() {
        FilterRegistrationBean<RobotTokenFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(robotTokenFilter);
        registration.addUrlPatterns("/api/robot/*", "/api/photo/request", "/api/photo/wait");
        registration.setOrder(1);
        registration.setName("robotTokenFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration() {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(jwtAuthFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(2);
        registration.setName("jwtAuthFilter");
        return registration;
    }
}
