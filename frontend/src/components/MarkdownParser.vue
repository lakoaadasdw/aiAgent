<script setup lang="ts">
import { ref, computed } from 'vue'
import md from '../utils/markdown'

const raw = ref('')
const isDragging = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const rendered = computed(() => {
  if (!raw.value.trim()) return ''
  return md.render(raw.value)
})

const stats = computed(() => {
  const t = raw.value
  return {
    lines: t ? t.split('\n').length : 0,
    chars: t.length,
    words: t ? t.replace(/\n/g, ' ').split(/\s+/).filter(Boolean).length : 0,
  }
})

function readFile(file: File) {
  if (!file.name.endsWith('.md') && !file.name.endsWith('.markdown')) {
    alert('请选择 .md 文件')
    return
  }
  const r = new FileReader()
  r.onload = () => { raw.value = r.result as string }
  r.readAsText(file)
}

function onFileChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (file) readFile(file)
}

function onDrop(e: DragEvent) {
  isDragging.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) readFile(file)
}

function clearAll() {
  raw.value = ''
  if (fileInput.value) fileInput.value.value = ''
}

function copyHtml() {
  navigator.clipboard.writeText(md.render(raw.value))
}

function copyMarkdown() {
  navigator.clipboard.writeText(raw.value)
}
</script>

<template>
  <div :class="['md-parser', { dragging: isDragging }]">
    <!-- toolbar -->
    <div class="md-toolbar">
      <label class="md-btn md-btn-upload">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
          <polyline points="17 8 12 3 7 8" />
          <line x1="12" y1="3" x2="12" y2="15" />
        </svg>
        上传 .md
        <input type="file" accept=".md,.markdown" hidden @change="onFileChange" ref="fileInput" />
      </label>

      <button class="md-btn" @click="copyHtml" :disabled="!raw.trim()">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
          <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
        </svg>
        复制 HTML
      </button>

      <button class="md-btn" @click="copyMarkdown" :disabled="!raw.trim()">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
          <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
        </svg>
        复制 MD
      </button>

      <button class="md-btn md-btn-danger" @click="clearAll" :disabled="!raw.trim()">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="3 6 5 6 21 6" />
          <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
        </svg>
        清空
      </button>
    </div>

    <!-- body: editor + preview -->
    <div class="md-body" @drop.prevent="onDrop" @dragover.prevent="isDragging = true" @dragleave.prevent="isDragging = false">
      <!-- editor -->
      <div class="md-editor">
        <div class="md-pane-header">
          <span class="md-pane-title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
            </svg>
            编辑
          </span>
          <span v-if="isDragging" class="md-drop-hint">释放文件以导入</span>
        </div>
        <textarea
          v-model="raw"
          class="md-textarea"
          placeholder="在此输入 Markdown，或拖拽 / 点击上传 .md 文件..."
          spellcheck="false"
        />
      </div>

      <!-- preview -->
      <div class="md-preview">
        <div class="md-pane-header">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14">
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
            <circle cx="12" cy="12" r="3" />
          </svg>
          预览
        </div>
        <div class="md-preview-content">
          <div v-if="rendered" class="markdown-body" v-html="rendered" />
          <div v-else class="md-preview-empty">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
              <polyline points="14 2 14 8 20 8" />
              <line x1="16" y1="13" x2="8" y2="13" />
              <line x1="16" y1="17" x2="8" y2="17" />
              <polyline points="10 9 9 9 8 9" />
            </svg>
            <p>输入 Markdown 或上传 .md 文件</p>
            <p>预览效果将实时显示在此处</p>
          </div>
        </div>
      </div>
    </div>

    <!-- stats -->
    <div class="md-stats">
      <span>行数 {{ stats.lines }}</span>
      <span class="md-stats-divider" />
      <span>字符 {{ stats.chars }}</span>
      <span class="md-stats-divider" />
      <span>单词 {{ stats.words }}</span>
      <span class="md-stats-divider" />
      <span>预估阅读 {{ Math.max(1, Math.ceil(stats.words / 200)) }} 分钟</span>
    </div>
  </div>
</template>
