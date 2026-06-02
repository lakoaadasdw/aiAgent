package com.example.aiagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 跨域配置类
 * 允许前端跨域请求后端接口
 * 
 * 安全说明：
 * - 生产环境建议通过 CORS_ALLOWED_ORIGINS 环境变量配置允许的来源域名
 * - 多个域名用逗号分隔，例如：http://localhost:5173,https://yourdomain.com
 * - 开发环境默认允许 localhost:5173（Vite默认端口）和 localhost:3000
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 允许的跨域来源，通过环境变量配置，多个用逗号分隔
     */
    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String allowedOriginsConfig;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 解析允许的来源列表
        String[] allowedOrigins = parseAllowedOrigins();

        registry.addMapping("/**")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedOrigins(allowedOrigins)
                .allowedHeaders("*")
                .allowCredentials(true)  // 允许携带凭证（cookie等）
                .maxAge(3600);           // 预检请求缓存1小时
    }

    /**
     * 解析允许的来源配置
     * 支持环境变量注入，多个域名用逗号分隔
     */
    private String[] parseAllowedOrigins() {
        if (allowedOriginsConfig == null || allowedOriginsConfig.trim().isEmpty()) {
            return new String[]{"http://localhost:5173"};
        }
        return allowedOriginsConfig.split(",");
    }
}
