package cn.sju.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 文档（Swagger UI）配置
 * 启动后访问：http://localhost:8080/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("社区安防智能巡检系统 - 后端接口")
                        .version("1.0.0")
                        .description("基于 Spring Boot 4 + MyBatis-Plus 的社区安防巡检系统后端 API")
                        .contact(new Contact().name("社区巡检项目组")));
    }
}
