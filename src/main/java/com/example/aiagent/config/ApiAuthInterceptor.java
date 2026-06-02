package com.example.aiagent.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API请求认证拦截器
 * 
 * 功能：
 * 1. 对 /chat/** 接口进行 Token 认证
 * 2. 通过请求头 X-API-Key 传递认证凭证
 * 3. API Key 通过环境变量 API_AUTH_KEY 配置
 * 4. 认证失败返回 401 未授权
 * 
 * 使用方式：
 * - 开发环境：设置环境变量 API_AUTH_KEY=your_secret_key
 * - 或通过启动参数：--app.api.auth-key=your_secret_key
 * - 客户端请求时在 Header 中添加：X-API-Key: your_secret_key
 * 
 * 安全建议：
 * - 生产环境必须配置强密码级别的 API Key
 * - 定期轮换 API Key
 * - 建议结合 IP 白名单使用
 */
@Component
public class ApiAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiAuthInterceptor.class);

    /**
     * API 认证密钥，通过环境变量或配置注入
     * 格式: API_AUTH_KEY=your_secure_key
     * 
     * 留空表示关闭认证（仅用于开发调试）
     */
    @Value("${app.api.auth-key:}")
    private String apiAuthKey;

    /**
     * 是否启用认证（当配置了有效的 api-auth-key 时自动启用）
     */
    private volatile Boolean authEnabled;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 首次访问时初始化认证状态
        if (authEnabled == null) {
            authEnabled = (apiAuthKey != null && !apiAuthKey.trim().isEmpty());
            if (authEnabled) {
                log.info("🔐 API认证已启用");
            } else {
                log.warn("⚠️ API认证未配置（app.api.auth-key为空），接口处于开放状态，生产环境请务必配置！");
            }
        }

        // 如果未启用认证，直接放行
        if (!authEnabled) {
            return true;
        }

        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 只对 /chat/** 路径进行认证
        String requestURI = request.getRequestURI();
        if (!requestURI.startsWith("/chat")) {
            return true;
        }

        // 从请求头获取 API Key
        String requestApiKey = request.getHeader("X-API-Key");

        // 校验 API Key
        if (requestApiKey == null || !requestApiKey.equals(apiAuthKey)) {
            log.warn("🔒 API认证失败 - IP: {}, URI: {}, Method: {}",
                    getClientIp(request), requestURI, request.getMethod());

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未授权访问，请提供有效的API密钥（请求头: X-API-Key）\"}");
            return false;
        }

        return true;
    }

    /**
     * 获取客户端真实IP（考虑代理转发）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个真实IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
