package com.example.aiagent.config;

import com.example.aiagent.Tools.AgentToolsInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 全局异常处理器（直接修复模式）
 *
 * 功能特性：
 * 1. 统一捕获所有控制器层异常，返回标准JSON格式的错误响应
 * 2. 捕获异常后直接调用AI大模型分析根因并调用工具类进行自动修复
 * 3. 不生成错误报告文件，不保存Redis摘要，异常即修复
 *
 * @author aiAgent
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * ChatClient构建器，用于调用AI大模型进行自动修复
     */
    @Autowired(required = false)
    private ChatClient.Builder chatClientBuilder;

    /**
     * 注入所有实现了 AgentToolsInterface 的工具类，供AI修复时调用
     */
    @Autowired(required = false)
    private List<AgentToolsInterface> agentTools;

    /**
     * 修复用的 system prompt - 指导AI直接修复异常
     */
    private static final String REPAIR_SYSTEM_PROMPT =
            "你是一个系统自动修复AI。\n" +
            "你的任务：收到异常信息后，直接分析根因并调用工具类进行修复。\n" +
            "规则：\n" +
            "1. 直接分析异常堆栈，定位出错的文件、方法和行号\n" +
            "2. 调用 viewFile 工具查看相关源码，理解上下文\n" +
            "3. 分析根因后调用 modifyFile 工具直接修复\n" +
            "4. 不需要询问用户确认，不需要生成报告，直接执行修复\n" +
            "5. 修复完成后简要说明修改了什么";

    // ==================== 系统内部错误（500） ====================

    /**
     * 捕获所有未预期的运行时异常
     * 1. 返回友好的JSON错误响应
     * 2. 异步调用AI大模型直接分析并修复异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        // 获取当前请求信息
        HttpServletRequest request = getCurrentRequest();
        final String requestUri = (request != null) ? request.getRequestURI() : "unknown";
        final String requestMethod = (request != null) ? request.getMethod() : "unknown";
        // 使用三元表达式一次性赋值，确保sessionId为有效final
        String rawSessionId = (request != null) ? request.getHeader("X-Session-Id") : "default_session";
        final String sessionId = (rawSessionId == null || rawSessionId.isEmpty()) ? "default_session" : rawSessionId;

        // 记录异常堆栈到日志
        log.error("系统异常 - URI: [{}] {}, 异常类型: {}, 异常信息: {}",
                requestMethod, requestUri, e.getClass().getSimpleName(), e.getMessage(), e);

        // 构建标准错误响应（不暴露敏感堆栈信息给前端）
        Map<String, Object> errorResponse = buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "服务器内部错误，请稍后重试",
                requestUri,
                e.getClass().getSimpleName()
        );

        // === 直接调用AI自动修复（异步执行，不阻塞请求返回） ===
        CompletableFuture.runAsync(() -> {
            try {
                autoRepair(e, requestUri, requestMethod, sessionId);
            } catch (Exception ex) {
                log.error("AI自动修复任务执行失败: {}", ex.getMessage(), ex);
            }
        });

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    // ==================== 参数校验异常（400） ====================

    /**
     * 捕获参数校验异常（仅返回错误信息，不触发AI修复）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        HttpServletRequest request = getCurrentRequest();
        String requestUri = (request != null) ? request.getRequestURI() : "unknown";

        log.warn("参数异常 - URI: {}, 信息: {}", requestUri, e.getMessage());

        Map<String, Object> errorResponse = buildErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                e.getMessage() != null ? e.getMessage() : "请求参数不合法",
                requestUri,
                e.getClass().getSimpleName()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // ==================== 空指针/系统错误（500） ====================

    /**
     * 捕获空指针等系统异常，统一处理为500并触发AI修复
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Map<String, Object>> handleNullPointer(NullPointerException e) {
        HttpServletRequest request = getCurrentRequest();
        final String requestUri = (request != null) ? request.getRequestURI() : "unknown";
        // 使用三元表达式一次性赋值，确保sessionId为有效final
        String rawSessionId = (request != null) ? request.getHeader("X-Session-Id") : "default_session";
        final String sessionId = (rawSessionId == null || rawSessionId.isEmpty()) ? "default_session" : rawSessionId;

        log.error("空指针异常 - URI: {}, 信息: {}", requestUri, e.getMessage(), e);

        Map<String, Object> errorResponse = buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "系统处理异常，请联系管理员",
                requestUri,
                e.getClass().getSimpleName()
        );

        // 空指针也属于系统错误，直接触发AI修复
        CompletableFuture.runAsync(() -> {
            try {
                autoRepair(e, requestUri, requestMethod(request), sessionId);
            } catch (Exception ex) {
                log.error("AI自动修复任务执行失败: {}", ex.getMessage(), ex);
            }
        });

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    // ==================== AI自动修复核心方法 ====================

    /**
     * 直接调用AI大模型分析异常并自动修复
     * 不生成报告文件，不保存Redis摘要，异常即修复
     *
     * @param e             异常对象
     * @param requestUri    请求URI
     * @param requestMethod 请求方法
     * @param sessionId     会话ID
     */
    private void autoRepair(Exception e, String requestUri, String requestMethod, String sessionId) {
        // 检查依赖是否就绪
        if (chatClientBuilder == null) {
            log.warn("ChatClient.Builder 未注入，无法执行AI自动修复");
            return;
        }
        if (agentTools == null || agentTools.isEmpty()) {
            log.warn("AgentTools 未注入或为空，无法执行AI自动修复");
            return;
        }

        try {
            // 构建异常修复prompt（包含完整的异常上下文）
            String repairPrompt = buildRepairPrompt(e, requestUri, requestMethod);

            log.info("🚀 触发AI自动修复 - URI: {} {}, 异常: {}",
                    requestMethod, requestUri, e.getClass().getSimpleName());

            // 调用AI大模型进行分析和修复
            ChatClient chatClient = chatClientBuilder.build();
            String repairResult = chatClient
                    .prompt(repairPrompt)
                    .system(REPAIR_SYSTEM_PROMPT)
                    .tools(agentTools.toArray())
                    .call()
                    .content();

            log.info("✅ AI自动修复完成 - 结果: {}", repairResult != null ? repairResult.substring(0, Math.min(200, repairResult.length())) : "无返回");
        } catch (Exception repairEx) {
            log.error("❌ AI自动修复过程抛出异常: {}", repairEx.getMessage(), repairEx);
        }
    }

    /**
     * 构建异常修复prompt，将异常信息组装成结构化的修复任务
     */
    private String buildRepairPrompt(Exception e, String requestUri, String requestMethod) {
        StringBuilder sb = new StringBuilder();
        sb.append("【系统异常自动修复任务】\n\n");

        // 请求信息
        sb.append("请求: ").append(requestMethod).append(" ").append(requestUri).append("\n");

        // 异常基本信息
        sb.append("异常类型: ").append(e.getClass().getName()).append("\n");
        sb.append("异常信息: ").append(e.getMessage() != null ? e.getMessage() : "无详细信息").append("\n");

        // 异常链
        Throwable cause = e.getCause();
        if (cause != null) {
            sb.append("根因: ").append(cause.getClass().getName())
              .append(": ").append(cause.getMessage() != null ? cause.getMessage() : "").append("\n");
        }

        // 堆栈跟踪（提取前30行项目代码）
        sb.append("\n堆栈跟踪（关键部分）:\n");
        StackTraceElement[] stackTrace = e.getStackTrace();
        int count = 0;
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            // 优先展示项目自定义代码
            if (className != null && !className.startsWith("java.")
                    && !className.startsWith("javax.")
                    && !className.startsWith("org.springframework.")
                    && !className.startsWith("org.apache.")
                    && !className.startsWith("com.fasterxml.")
                    && !className.startsWith("jakarta.")
                    && !className.startsWith("sun.")
                    && !className.startsWith("jdk.internal.")) {
                sb.append("  at ").append(element.toString()).append("\n");
                count++;
            }
        }
        // 如果项目代码堆栈太少，补充更多堆栈
        if (count < 5) {
            sb.append("  --- 更多堆栈 ---\n");
            for (int i = 0; i < Math.min(stackTrace.length, 20); i++) {
                sb.append("  at ").append(stackTrace[i].toString()).append("\n");
            }
        }

        sb.append("\n请分析根因并直接修复。先查看相关源码文件，然后调用修改工具修复。\n");
        sb.append("无需询问用户确认，无需生成报告，直接执行修复操作。\n");

        return sb.toString();
    }

    /**
     * 获取当前请求的HTTP方法（辅助方法）
     */
    private String requestMethod(HttpServletRequest request) {
        return request != null ? request.getMethod() : "GET";
    }

    // ==================== 通用方法 ====================

    /**
     * 构建标准错误响应体
     */
    private Map<String, Object> buildErrorResponse(int status, String message, String path, String exceptionType) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        body.put("status", status);
        body.put("error", message);
        body.put("path", path);
        body.put("exception", exceptionType);

        // 如果是500错误，额外添加提示字段，说明AI正在修复
        if (status == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            body.put("autoRepair", "AI正在自动分析并修复该异常，请稍后查看修复结果");
        }

        return body;
    }

    /**
     * 获取当前请求（可能为null）
     */
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return attributes.getRequest();
            }
        } catch (Exception e) {
            // 忽略获取请求时的异常
        }
        return null;
    }
}
