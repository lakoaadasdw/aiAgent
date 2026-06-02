package com.example.aiagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 上下文记忆服务（基于 Redis List + LTRIM）
 *
 * 设计原理：
 * - 使用 Redis List 存储每次对话的精炼摘要
 * - 按会话（session）隔离，每个 session 独立 List
 * - 动态窗口控制：每个窗口最多 15 条，超出自动裁剪最早数据
 * - 替代原有的 MEMORY.md 文件存储方式
 *
 * Redis Key 命名规范：
 * - memory:context:{sessionId}  → 存储精炼摘要的 List
 * - memory:meta:{sessionId}     → 存储会话元信息
 * - memory:systemPrompt:{sessionId} → 存储 system prompt 模板（支持动态注入）
 */
@Service
public class ContextMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ContextMemoryService.class);

    /**
     * 上下文记忆的 Redis Key 前缀
     */
    private static final String CONTEXT_KEY_PREFIX = "memory:context:";

    /**
     * 会话元信息的 Redis Key 前缀
     */
    private static final String META_KEY_PREFIX = "memory:meta:";

    /**
     * System Prompt 模板的 Redis Key 前缀
     */
    private static final String SYSTEM_PROMPT_KEY_PREFIX = "memory:systemPrompt:";

    /**
     * 每个会话最大保留的精炼摘要条数（动态窗口大小）
     */
    private static final int MAX_WINDOW_SIZE = 15;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // ==================== System Prompt 动态注入相关方法 ====================

    /**
     * 保存 system prompt 模板到 Redis（按会话隔离）
     * 可以在不修改代码的情况下动态调整 AI 的 system 规则
     *
     * @param sessionId 会话ID（传 "default" 表示全局默认模板）
     * @param prompt    system prompt 内容，可使用 {sessionId} 占位符动态替换
     */
    public void saveSystemPrompt(String sessionId, String prompt) {
        String key = SYSTEM_PROMPT_KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, prompt);
        log.info("已保存会话 [{}] 的 system prompt 到 Redis", sessionId);
    }

    /**
     * 从 Redis 读取 system prompt 模板
     * 优先按 sessionId 精确查找，如果不存在则尝试读取全局默认模板 (default)
     *
     * @param sessionId 会话ID
     * @return system prompt 内容，如果 Redis 中不存在则返回空字符串
     */
    public String getSystemPrompt(String sessionId) {
        // 1. 优先按当前 sessionId 查找
        String key = SYSTEM_PROMPT_KEY_PREFIX + sessionId;
        String prompt = redisTemplate.opsForValue().get(key);
        if (prompt != null && !prompt.isEmpty()) {
            log.debug("从 Redis 读取到会话 [{}] 的 system prompt", sessionId);
            return prompt;
        }

        // 2. 如果当前 session 没有，尝试读取全局默认模板
        String defaultKey = SYSTEM_PROMPT_KEY_PREFIX + "default";
        String defaultPrompt = redisTemplate.opsForValue().get(defaultKey);
        if (defaultPrompt != null && !defaultPrompt.isEmpty()) {
            log.debug("使用全局默认 system prompt（会话 [{}] 无专属配置）", sessionId);
            return defaultPrompt;
        }

        // 3. Redis 中没有任何配置
        log.debug("Redis 中未找到 system prompt 配置（会话 [{}]）", sessionId);
        return "";
    }

    /**
     * 删除指定会话的 system prompt 配置
     *
     * @param sessionId 会话ID
     */
    public void deleteSystemPrompt(String sessionId) {
        String key = SYSTEM_PROMPT_KEY_PREFIX + sessionId;
        redisTemplate.delete(key);
        log.info("已删除会话 [{}] 的 system prompt 配置", sessionId);
    }

    // ==================== 历史记忆相关方法 ====================

    /**
     * 保存一条精炼摘要到会话的记忆列表
     * 使用 RPUSH 追加到列表尾部，然后 LTRIM 裁剪窗口
     *
     * @param sessionId 会话ID
     * @param summary   精炼后的对话摘要（纯文本）
     */
    public void saveSummary(String sessionId, String summary) {
        String contextKey = CONTEXT_KEY_PREFIX + sessionId;
        String metaKey = META_KEY_PREFIX + sessionId;

        // 1. 将精炼摘要追加到列表尾部
        redisTemplate.opsForList().rightPush(contextKey, summary);
        log.debug("已追加摘要到会话 [{}] 的记忆列表", sessionId);

        // 2. 使用 LTRIM 裁剪窗口，只保留最近 MAX_WINDOW_SIZE 条
        Long listSize = redisTemplate.opsForList().size(contextKey);
        if (listSize != null && listSize > MAX_WINDOW_SIZE) {
            // 保留最后 MAX_WINDOW_SIZE 条
            redisTemplate.opsForList().trim(contextKey, -MAX_WINDOW_SIZE, -1);
            log.info("会话 [{}] 记忆列表超过 {} 条，已自动裁剪最早的数据（当前：{} 条）",
                    sessionId, MAX_WINDOW_SIZE, MAX_WINDOW_SIZE);
        }

        // 3. 更新会话元信息（最近活动时间）
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        redisTemplate.opsForHash().put(metaKey, "lastActiveTime", now);
        redisTemplate.opsForHash().put(metaKey, "summaryCount", String.valueOf(
                Math.min(listSize != null ? listSize + 1 : 1, MAX_WINDOW_SIZE)
        ));

        log.info("会话 [{}] 记忆已保存，当前窗口摘要数：{}", sessionId,
                Math.min(listSize != null ? listSize + 1 : 1, MAX_WINDOW_SIZE));
    }

    /**
     * 批量保存多条精炼摘要（用于初始化或恢复场景）
     *
     * @param sessionId 会话ID
     * @param summaries 精炼摘要列表（按时间正序）
     */
    public void saveSummaries(String sessionId, List<String> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return;
        }

        String contextKey = CONTEXT_KEY_PREFIX + sessionId;

        // 清空并重新写入
        redisTemplate.delete(contextKey);
        for (String summary : summaries) {
            redisTemplate.opsForList().rightPush(contextKey, summary);
        }

        // 裁剪窗口
        Long listSize = redisTemplate.opsForList().size(contextKey);
        if (listSize != null && listSize > MAX_WINDOW_SIZE) {
            redisTemplate.opsForList().trim(contextKey, -MAX_WINDOW_SIZE, -1);
        }

        log.info("会话 [{}] 批量写入 {} 条精炼摘要", sessionId, summaries.size());
    }

    /**
     * 获取会话的完整上下文（所有精炼摘要，按时间正序）
     * 返回的列表可以直接拼接给 AI 作为上下文
     *
     * @param sessionId 会话ID
     * @return 精炼摘要列表（最早的在前，最新的在后）
     */
    public List<String> getContext(String sessionId) {
        String contextKey = CONTEXT_KEY_PREFIX + sessionId;
        List<String> context = redisTemplate.opsForList().range(contextKey, 0, -1);

        if (context == null || context.isEmpty()) {
            log.debug("会话 [{}] 暂无历史记忆", sessionId);
            return List.of();
        }

        log.debug("会话 [{}] 读取到 {} 条历史记忆", sessionId, context.size());
        return context;
    }

    /**
     * 获取格式化的上下文文本（直接可注入到 system prompt 中）
     *
     * @param sessionId 会话ID
     * @return 格式化的历史上下文文本，如果没有历史则返回空字符串
     */
    public String getFormattedContext(String sessionId) {
        List<String> context = getContext(sessionId);
        if (context.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== 📜 历史对话记忆（精炼摘要） ===\n");
        sb.append("以下是当前会话的历史记忆摘要（最近 ").append(context.size()).append(" 条，按时间正序）：\n\n");

        for (int i = 0; i < context.size(); i++) {
            sb.append("【记忆 ").append(i + 1).append("】").append(context.get(i)).append("\n");
        }

        sb.append("\n=== 📜 记忆结束 ===\n");
        sb.append("请基于以上历史记忆理解用户意图并回答问题。\n");

        return sb.toString();
    }

    /**
     * 获取会话当前的记忆条目数
     *
     * @param sessionId 会话ID
     * @return 记忆条目数
     */
    public long getContextSize(String sessionId) {
        String contextKey = CONTEXT_KEY_PREFIX + sessionId;
        Long size = redisTemplate.opsForList().size(contextKey);
        return size != null ? size : 0;
    }

    /**
     * 清空指定会话的所有记忆
     *
     * @param sessionId 会话ID
     */
    public void clearContext(String sessionId) {
        String contextKey = CONTEXT_KEY_PREFIX + sessionId;
        String metaKey = META_KEY_PREFIX + sessionId;
        redisTemplate.delete(contextKey);
        redisTemplate.delete(metaKey);
        log.info("会话 [{}] 的记忆已清空", sessionId);
    }

    /**
     * 获取所有活跃的会话ID列表
     *
     * @return 会话ID集合
     */
    public Set<String> getActiveSessions() {
        Set<String> keys = redisTemplate.keys(CONTEXT_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Set.of();
        }
        // 去除前缀，只返回 sessionId
        return keys.stream()
                .map(key -> key.substring(CONTEXT_KEY_PREFIX.length()))
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 获取会话的元信息
     *
     * @param sessionId 会话ID
     * @return 元信息Map
     */
    public java.util.Map<Object, Object> getSessionMeta(String sessionId) {
        String metaKey = META_KEY_PREFIX + sessionId;
        return redisTemplate.opsForHash().entries(metaKey);
    }
}
