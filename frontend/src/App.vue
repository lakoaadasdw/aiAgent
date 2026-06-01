<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import md from './utils/markdown'
import { parseBlocks } from './utils/streamParser'
import MarkdownParser from './components/MarkdownParser.vue'

// --- types ---
interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
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

async function sendMsg() {
  const text = input.value.trim()
  if (!text || isStreaming.value) return
  input.value = ''

  const uid = genId()
  const aid = genId()
  messages.value.push({ id: uid, role: 'user', content: text })
  messages.value.push({ id: aid, role: 'assistant', content: '' })
  streamingId.value = aid
  isStreaming.value = true

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

  // --- stream mode ---
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
        // flush any remaining buf
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
          // empty data: value = newline token
          acc += val || '\n'
        } else if (inData && ln === '') {
          // empty line after data: = event boundary
          inData = false
        } else if (inData) {
          // continuation of multi-line data value
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

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMsg()
  }
}
</script>

<template>
  <div class="app">
    <div class="chat-container">
      <!-- header with tabs -->
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
            <p class="empty-desc">输入您的问题，AI 将为您解答</p>
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
              <!-- user → plain text -->
              <div v-else-if="msg.role === 'user' && msg.content" class="user-text">{{ msg.content }}</div>
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
          <div class="input-wrapper">
            <textarea
              v-model="input"
              class="chat-input"
              :placeholder="mode === 'stream' ? '输入问题，流式对话...' : '输入问题，普通对话...'"
              rows="1"
              :disabled="isStreaming"
              @keydown="onKeydown"
            />
            <button v-if="isStreaming" class="stop-btn" @click="stopStream" title="停止">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="6" y="6" width="12" height="12" rx="2" />
              </svg>
            </button>
            <button v-else class="send-btn" :disabled="!input.trim()" @click="sendMsg" title="发送">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="22" y1="2" x2="11" y2="13" />
                <polygon points="22 2 15 22 11 13 2 9 22 2" />
              </svg>
            </button>
          </div>
          <p class="input-hint">Enter 发送 · Shift+Enter 换行</p>
        </div>
      </template>

      <!-- md parser view -->
      <MarkdownParser v-else class="parser-view" />
    </div>
  </div>
</template>
