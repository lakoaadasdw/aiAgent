<script setup lang="ts">
import { ref, watch, nextTick, onMounted } from 'vue'
import md from './utils/markdown'
import { parseBlocks } from './utils/streamParser'
import MarkdownParser from './components/MarkdownParser.vue'

// --- types ---
interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  imageUrl?: string   // 用户消息中显示的图片缩略图URL
}

function genId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
}

// --- tab state ---
const activeTab = ref<'chat' | 'md'>('chat')

// --- chat state ---
const messages = ref<Message[]>([])
const input = ref('')
const isStreaming = ref(false)
const mode = ref<'stream' | 'call'>('stream')
const streamingId = ref<string | null>(null)
const abortCtrl = ref<AbortController | null>(null)
const msgEnd = ref<HTMLDivElement | null>(null)

// --- image upload state ---
const imageFile = ref<File | null>(null)
const imagePreviewUrl = ref<string>('')
const fileInputRef = ref<HTMLInputElement | null>(null)

// --- image overlay preview state ---
const showImagePreview = ref(false)
const previewImageUrl = ref('')

function previewImage(url: string) {
  previewImageUrl.value = url
  showImagePreview.value = true
}

function closeImagePreview() {
  showImagePreview.value = false
}

// --- auto scroll ---
watch(messages, () => {
  nextTick(() => msgEnd.value?.scrollIntoView({ behavior: 'smooth' }))
}, { deep: true, flush: 'post' })

// --- actions ---
function stopStream() {
  abortCtrl.value?.abort()
  isStreaming.value = false
  streamingId.value = null
}

function renderMd(text: string): string {
  return md.render(text)
}

/** 处理选择的图片文件 */
function handleImageFile(file: File) {
  if (!file.type.startsWith('image/')) {
    alert('请选择图片文件')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    alert('图片大小不能超过10MB')
    return
  }

  imageFile.value = file
  if (imagePreviewUrl.value) URL.revokeObjectURL(imagePreviewUrl.value)
  imagePreviewUrl.value = URL.createObjectURL(file)
}

/** 从剪贴板粘贴图片 */
function onPaste(e: ClipboardEvent) {
  const items = e.clipboardData?.items
  if (!items) return

  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) {
        handleImageFile(file)
        e.preventDefault()
        break
      }
    }
  }
}

/** 触发文件选择 */
function triggerImageUpload() {
  fileInputRef.value?.click()
}

/** 移除已选择的图片 */
function removeImage() {
  imageFile.value = null
  if (imagePreviewUrl.value) {
    URL.revokeObjectURL(imagePreviewUrl.value)
    imagePreviewUrl.value = ''
  }
}

async function sendMsg() {
  const text = input.value.trim()
  const hasImage = imageFile.value !== null
  if ((!text && !hasImage) || isStreaming.value) return

  const uid = genId()
  const aid = genId()

  // 保存当前图片预览URL到消息中（用于在用户气泡中显示）
  const currentPreviewUrl = imagePreviewUrl.value

  // 显示用户消息（含图片缩略图）
  messages.value.push({
    id: uid,
    role: 'user',
    content: text,
    imageUrl: hasImage ? currentPreviewUrl : undefined
  })
  messages.value.push({ id: aid, role: 'assistant', content: '' })
  streamingId.value = aid
  isStreaming.value = true

  // 清空预览（但不要 revoke，因为消息气泡还在用这个 URL）
  const currentImage = imageFile.value
  imageFile.value = null
  imagePreviewUrl.value = ''

  // 确定接口路径
  const endpoint = hasImage
    ? (mode.value === 'stream' ? '/chat/stream-with-image' : '/chat/call-with-image')
    : (mode.value === 'stream' ? '/chat/stream' : '/chat/call')

  if (hasImage) {
    // --- 带图片的请求（FormData） ---
    const controller = new AbortController()
    abortCtrl.value = controller

    const formData = new FormData()
    formData.append('question', text || '请描述这张图片')
    formData.append('image', currentImage!)

    try {
      if (mode.value === 'call') {
        const res = await fetch(endpoint, {
          method: 'POST',
          body: formData,
          signal: controller.signal,
        })
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const data = await res.text()
        const m = messages.value.find(x => x.id === aid)
        if (m) m.content = data
      } else {
        // stream mode with image
        const res = await fetch(endpoint, {
          method: 'POST',
          body: formData,
          signal: controller.signal,
        })
        if (!res.ok) throw new Error(`HTTP ${res.status}`)

        const reader = res.body?.getReader()
        if (!reader) throw new Error('No reader')

        const decoder = new TextDecoder()
        let buf = ''
        let acc = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) {
            if (buf.startsWith('data:')) {
              const rem = buf.slice(5)
              if (rem.trim() !== '[DONE]' && rem !== '') acc += rem
              if (rem === '') acc += '\n'
            }
            const m = messages.value.find(x => x.id === aid)
            if (m) m.content = acc
            break
          }

          buf += decoder.decode(value, { stream: true })
          const lines = buf.split('\n')
          buf = lines.pop() || ''

          let inData = false
          for (const ln of lines) {
            if (ln.startsWith('data:')) {
              inData = true
              const val = ln.slice(5)
              if (val.trim() === '[DONE]') continue
              acc += val || '\n'
            } else if (inData && ln === '') {
              inData = false
            } else if (inData) {
              acc += '\n' + ln
            }
            const m = messages.value.find(x => x.id === aid)
            if (m) m.content = acc
          }
        }
      }
    } catch (err) {
      if (err instanceof Error && err.name !== 'AbortError') {
        const m = messages.value.find(x => x.id === aid)
        if (m) m.content = '请求失败，请检查网络后重试。'
      }
    } finally {
      isStreaming.value = false
      streamingId.value = null
      abortCtrl.value = null
    }

  } else {
    // --- 纯文本请求（原有逻辑） ---
    input.value = ''

    if (mode.value === 'call') {
      try {
        const res = await fetch('/chat/call', {
          method: 'POST',
          headers: { 'Content-Type': 'text/plain' },
          body: text,
        })
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const data = await res.text()
        const m = messages.value.find(x => x.id === aid)
        if (m) m.content = data
      } catch {
        const m = messages.value.find(x => x.id === aid)
        if (m) m.content = '请求失败，请检查网络后重试。'
      } finally {
        isStreaming.value = false
        streamingId.value = null
      }
      return
    }

    // --- stream mode (text only) ---
    const controller = new AbortController()
    abortCtrl.value = controller

    try {
      const res = await fetch('/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'text/plain' },
        body: text,
        signal: controller.signal,
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)

      const reader = res.body?.getReader()
      if (!reader) throw new Error('No reader')

      const decoder = new TextDecoder()
      let buf = ''
      let acc = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          if (buf.startsWith('data:')) {
            const rem = buf.slice(5)
            if (rem.trim() !== '[DONE]' && rem !== '') acc += rem
            if (rem === '') acc += '\n'
          }
          const m = messages.value.find(x => x.id === aid)
          if (m) m.content = acc
          break
        }

        buf += decoder.decode(value, { stream: true })
        const lines = buf.split('\n')
        buf = lines.pop() || ''

        let inData = false
        for (const ln of lines) {
          if (ln.startsWith('data:')) {
            inData = true
            const val = ln.slice(5)
            if (val.trim() === '[DONE]') continue
            acc += val || '\n'
          } else if (inData && ln === '') {
            inData = false
          } else if (inData) {
            acc += '\n' + ln
          }
          const m = messages.value.find(x => x.id === aid)
          if (m) m.content = acc
        }
      }
    } catch (err) {
      if (err instanceof Error && err.name !== 'AbortError') {
        const m = messages.value.find(x => x.id === aid)
        if (m) m.content = '请求失败，请检查网络后重试。'
      }
    } finally {
      isStreaming.value = false
      streamingId.value = null
      abortCtrl.value = null
    }
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMsg()
  }
}

/** 格式化文件大小 */
function formatSize(bytes: number): string {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<template>
  <div class="app">
    <div class="chat-container">
      <!-- header with tabs (without image tab) -->
      <header class="chat-header">
        <div class="header-brand">
          <div class="header-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
            </svg>
          </div>
          <div>
            <h1 class="header-title">AI Chat</h1>
            <p class="header-subtitle">智能对话助手</p>
          </div>
        </div>
        <nav class="header-tabs">
          <button :class="['tab-btn', { active: activeTab === 'chat' }]" @click="activeTab = 'chat'">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
            </svg>
            对话
          </button>
          <button :class="['tab-btn', { active: activeTab === 'md' }]" @click="activeTab = 'md'">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
              <polyline points="14 2 14 8 20 8" />
              <line x1="16" y1="13" x2="8" y2="13" />
              <line x1="16" y1="17" x2="8" y2="17" />
            </svg>
            Markdown
          </button>
        </nav>
      </header>

      <!-- chat view -->
      <template v-if="activeTab === 'chat'">
        <div class="mode-toggle">
          <button :class="['mode-btn', { active: mode === 'stream' }]" @click="mode = 'stream'">
            <span class="mode-dot" />流式对话
          </button>
          <button :class="['mode-btn', { active: mode === 'call' }]" @click="mode = 'call'">
            <span class="mode-dot" />普通对话
          </button>
        </div>

        <div class="messages-area">
          <div v-if="messages.length === 0" class="empty-state">
            <div class="empty-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 2L2 7l10 5 10-5-10-5z" />
                <path d="M2 17l10 5 10-5" />
                <path d="M2 12l10 5 10-5" />
              </svg>
            </div>
            <p class="empty-title">开始对话</p>
            <p class="empty-desc">输入您的问题，AI 将为您解答 · 支持上传图片或 Ctrl+V 粘贴图片</p>
          </div>

          <div v-for="msg in messages" :key="msg.id" :class="['message', msg.role]">
            <div class="message-avatar">
              <svg v-if="msg.role === 'user'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                <circle cx="12" cy="7" r="4" />
              </svg>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 2L2 7l10 5 10-5-10-5z" />
                <path d="M2 17l10 5 10-5" />
                <path d="M2 12l10 5 10-5" />
              </svg>
            </div>
            <div class="message-bubble">
              <!-- assistant: streaming → block 级渲染 -->
              <div v-if="msg.role === 'assistant' && streamingId === msg.id && msg.content" class="stream-body">
                <template v-for="block in parseBlocks(msg.content)" :key="block.id">
                  <div v-if="block.complete" class="markdown-body" v-html="renderMd(block.content)" />
                  <div v-else class="streaming-text">{{ block.content }}</div>
                </template>
                <span class="stream-cursor" />
              </div>
              <!-- assistant: completed → markdown -->
              <div v-else-if="msg.role === 'assistant' && msg.content" class="markdown-body" v-html="renderMd(msg.content)" />
              <!-- user: with image support -->
              <div v-else-if="msg.role === 'user'" class="user-content">
                <!-- 图片缩略图 -->
                <div v-if="msg.imageUrl" class="user-image-wrapper">
                  <img
                    :src="msg.imageUrl"
                    class="user-image-thumb"
                    alt="用户发送的图片"
                    @click="previewImage(msg.imageUrl!)"
                  />
                </div>
                <!-- 文本内容 -->
                <div v-if="msg.content" class="user-text">{{ msg.content }}</div>
              </div>
              <!-- typing indicator -->
              <div v-else-if="msg.role === 'assistant' && streamingId === msg.id" class="typing-indicator">
                <span class="typing-dot" />
                <span class="typing-dot" />
                <span class="typing-dot" />
              </div>
            </div>
          </div>
          <div ref="msgEnd" />
        </div>

        <div class="input-area">
          <!-- 图片预览 -->
          <div v-if="imagePreviewUrl" class="image-preview-bar">
            <div class="image-preview-item">
              <img :src="imagePreviewUrl" alt="待发送图片" class="image-preview-thumb" />
              <span class="image-preview-name">{{ imageFile?.name || '剪贴板图片' }}</span>
              <span class="image-preview-size">{{ formatSize(imageFile?.size) }}</span>
              <button class="image-preview-remove" @click="removeImage" title="移除图片">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18" />
                  <line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </button>
            </div>
          </div>

          <div class="input-wrapper">
            <!-- 隐藏的文件选择器 -->
            <input
              ref="fileInputRef"
              type="file"
              accept="image/*"
              style="display: none"
              @change="(e: any) => {
                const file = e.target.files?.[0]
                if (file) handleImageFile(file)
                e.target.value = ''
              }"
            />

            <!-- 图片上传按钮 -->
            <button
              class="image-upload-btn"
              :disabled="isStreaming"
              @click="triggerImageUpload"
              title="上传图片"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="3" width="18" height="18" rx="2" />
                <circle cx="8.5" cy="8.5" r="1.5" />
                <path d="M21 15l-5-5L5 21" />
              </svg>
            </button>

            <textarea
              v-model="input"
              class="chat-input"
              :placeholder="mode === 'stream' ? '输入问题，支持粘贴图片...' : '输入问题，支持粘贴图片...'"
              rows="1"
              :disabled="isStreaming"
              @keydown="onKeydown"
              @paste="onPaste"
            />
            <button v-if="isStreaming" class="stop-btn" @click="stopStream" title="停止">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="6" y="6" width="12" height="12" rx="2" />
              </svg>
            </button>
            <button v-else class="send-btn" :disabled="!input.trim() && !imageFile" @click="sendMsg" title="发送">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="22" y1="2" x2="11" y2="13" />
                <polygon points="22 2 15 22 11 13 2 9 22 2" />
              </svg>
            </button>
          </div>
          <p class="input-hint">Enter 发送 · Shift+Enter 换行 · 支持上传图片或 Ctrl+V 粘贴</p>
        </div>
      </template>

      <!-- md parser view -->
      <MarkdownParser v-else-if="activeTab === 'md'" class="parser-view" />
    </div>

    <!-- 图片放大预览遮罩层 -->
    <div
      v-if="showImagePreview"
      class="image-overlay"
      @click="closeImagePreview"
    >
      <button class="image-overlay-close" @click="closeImagePreview">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18" />
          <line x1="6" y1="6" x2="18" y2="18" />
        </svg>
      </button>
      <img :src="previewImageUrl" class="image-overlay-img" alt="图片预览" @click.stop />
    </div>
  </div>
</template>

<style scoped>
/* ===== 图片预览条 ===== */
.image-preview-bar {
  display: flex;
  padding: 6px 8px 0 8px;
  flex-shrink: 0;
}

.image-preview-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: var(--accent-light);
  border: 1px solid var(--ai-border);
  border-radius: 8px;
  max-width: 280px;
  animation: fadeIn 0.2s ease-out;
}

.image-preview-thumb {
  width: 32px;
  height: 32px;
  border-radius: 4px;
  object-fit: cover;
  flex-shrink: 0;
}

.image-preview-name {
  font-size: 12px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.image-preview-size {
  font-size: 11px;
  color: var(--text-muted);
  white-space: nowrap;
}

.image-preview-remove {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  border-radius: 4px;
  padding: 0;
  flex-shrink: 0;
  transition: all 0.15s ease;
}

.image-preview-remove:hover {
  background: rgba(0,0,0,0.06);
  color: #ef4444;
}

.image-preview-remove svg {
  width: 14px;
  height: 14px;
}

/* ===== 图片上传按钮 ===== */
.image-upload-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  border-radius: 8px;
  flex-shrink: 0;
  transition: all 0.15s ease;
}

.image-upload-btn:hover:not(:disabled) {
  background: var(--accent-light);
  color: var(--accent);
}

.image-upload-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.image-upload-btn svg {
  width: 18px;
  height: 18px;
}

/* ===== 用户消息中的图片展示 ===== */
.user-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.user-image-wrapper {
  max-width: 240px;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid var(--ai-border);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.user-image-wrapper:hover {
  transform: scale(1.02);
  box-shadow: 0 2px 12px rgba(0,0,0,0.12);
}

.user-image-thumb {
  display: block;
  width: 100%;
  height: auto;
  max-height: 200px;
  object-fit: cover;
  border-radius: 7px;
}

.user-text {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}

/* ===== 图片放大预览遮罩 ===== */
.image-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: zoom-out;
  animation: overlayFadeIn 0.2s ease-out;
}

.image-overlay-img {
  max-width: 90vw;
  max-height: 90vh;
  border-radius: 8px;
  object-fit: contain;
  box-shadow: 0 4px 24px rgba(0,0,0,0.4);
  cursor: default;
}

.image-overlay-close {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 40px;
  height: 40px;
  border: none;
  background: rgba(255,255,255,0.15);
  color: #fff;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s ease;
}

.image-overlay-close:hover {
  background: rgba(255,255,255,0.3);
}

.image-overlay-close svg {
  width: 20px;
  height: 20px;
}

@keyframes overlayFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
