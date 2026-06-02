package com.example.aiagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 安全启动检查器
 * 
 * 在应用启动时自动检查安全配置状态，
 * 提醒用户配置生产环境所需的安全参数。
 */
@Component
public class SecurityStartupChecker implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SecurityStartupChecker.class);

    @Value("${app.api.auth-key:}")
    private String apiAuthKey;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.ai.deepseek.api-key:}")
    private String deepseekApiKey;

    @Override
    public void run(String... args) {
        log.info("===========================================");
        log.info("  🔒 安全配置检查");
        log.info("===========================================");

        boolean allSecure = true;

        // 检查 DeepSeek API Key
        if (deepseekApiKey == null || deepseekApiKey.isEmpty() || deepseekApiKey.startsWith("${")) {
            log.error("  ❌ DeepSeek API Key 未配置！请设置环境变量 DEEPSEEK_API_KEY");
            allSecure = false;
        } else if ("sk-9b10647c6bb44826ba7d60c1377a134b".equals(deepseekApiKey)) {
            log.warn("  ⚠️ 检测到使用默认/示例 API Key，请更换为自己的 Key！");
        } else {
            log.info("  ✅ DeepSeek API Key 已配置（安全）");
        }

        // 检查 API 认证
        if (apiAuthKey == null || apiAuthKey.isEmpty()) {
            log.warn("  ⚠️ API 认证未启用（app.api.auth-key 为空）");
            log.warn("  ⚠️ 生产环境请设置环境变量 API_AUTH_KEY=your_secure_key");
        } else {
            log.info("  ✅ API 认证已启用（安全）");
        }

        // 检查 Redis 密码
        if (redisPassword == null || redisPassword.isEmpty()) {
            log.warn("  ⚠️ Redis 未设置密码（spring.data.redis.password 为空）");
            log.warn("  ⚠️ 生产环境强烈建议为 Redis 配置访问密码");
        } else {
            log.info("  ✅ Redis 密码已配置（安全）");
        }

        if (allSecure) {
            log.info("===========================================");
            log.info("  🎉 所有安全配置检查通过！");
            log.info("===========================================");
        } else {
            log.info("===========================================");
            log.info("  ⚠️ 存在安全配置问题，请尽快修复！");
            log.info("===========================================");
        }
    }
}
