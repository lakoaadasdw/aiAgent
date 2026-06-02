package com.example.aiagent.Tools;

import com.example.aiagent.service.ContextMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 上下文记忆工具类
 *
 * 提供AI读写上下文记忆的能力，基于Redis List + LTRIM实现
 * 每次对话AI应调用 saveMemory 将本次对话精炼成摘要保存
 * 每次对话开始时应调用 loadMemory 读取历史记忆
 *
 * 记忆窗口：每个会话最多15条，超出自动裁剪最早的数据
 */
@SuppressWarnings({"all"})
@Component
public class MemoryTools implements AgentToolsInterface {

    private static final Logger log = LoggerFactory.getLogger(MemoryTools.class);

    @Autowired
    private ContextMemoryService contextMemoryService;

    /**
     * 保存对话精炼摘要到Redis记忆系统
     * AI在每次对话结束时，应将本次对话精炼成一两句纯文本摘要并保存
     * 系统会自动维护15条的动态窗口，超出自动舍弃最早的数据
     *
     * @param sessionId 会话ID，用于隔离不同会话的记忆
     * @param summary   本次对话的精炼摘要（纯文本），格式建议：
     *                  "[时间] 用户问: xxx | AI答: xxx | 操作: xxx"
     */
    @Tool(description = "保存本次对话的精炼摘要到Redis记忆系统（自动维护15条动态窗口，超出自动舍弃最早数据）")
    public String saveMemory(
            @ToolParam(description = "会话ID，用于隔离不同会话的记忆") String sessionId,
            @ToolParam(description = "本次对话的精炼摘要（纯文本），格式建议：[时间] 用户问: xxx | AI答: xxx | 操作: xxx") String summary) {

        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = "default_session";
        }
        if (summary == null || summary.isEmpty()) {
            return "保存失败：摘要内容不能为空";
        }

        try {
            contextMemoryService.saveSummary(sessionId, summary);
            long currentSize = contextMemoryService.getContextSize(sessionId);
            log.info("会话 [{}] 记忆已保存，当前共 {} 条摘要", sessionId, currentSize);
            return "记忆保存成功（当前会话共 " + currentSize + " 条历史摘要，窗口上限15条）";
        } catch (Exception e) {
            log.error("记忆保存失败：{}", e.getMessage());
            return "记忆保存失败：" + e.getMessage();
        }
    }

    /**
     * 读取历史对话记忆（精炼摘要列表）
     * AI在对话开始时调用此方法获取历史上下文，以便理解对话背景
     *
     * @param sessionId 会话ID
     * @return 格式化的历史记忆文本
     */
    @Tool(description = "读取当前会话的历史对话记忆（精炼摘要），用于理解对话背景和上下文")
    public String loadMemory(
            @ToolParam(description = "会话ID") String sessionId) {

        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = "default_session";
        }

        try {
            String formattedContext = contextMemoryService.getFormattedContext(sessionId);
            if (formattedContext.isEmpty()) {
                return "当前会话暂无历史记忆，这是新的对话开始。";
            }

            long size = contextMemoryService.getContextSize(sessionId);
            log.info("会话 [{}] 读取到 {} 条历史记忆", sessionId, size);
            return formattedContext;
        } catch (Exception e) {
            log.error("读取记忆失败：{}", e.getMessage());
            return "读取记忆失败：" + e.getMessage();
        }
    }

    /**
     * 清空当前会话的所有记忆
     *
     * @param sessionId 会话ID
     */
    @Tool(description = "清空当前会话的所有历史记忆（谨慎使用）")
    public String clearMemory(
            @ToolParam(description = "会话ID") String sessionId) {

        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = "default_session";
        }

        try {
            contextMemoryService.clearContext(sessionId);
            log.info("会话 [{}] 的记忆已清空", sessionId);
            return "当前会话的历史记忆已全部清空。";
        } catch (Exception e) {
            log.error("清空记忆失败：{}", e.getMessage());
            return "清空记忆失败：" + e.getMessage();
        }
    }
}
