<script setup lang="ts">
import { ref, nextTick } from 'vue'
import md from '../utils/markdown'

// ===== 状态 =====
const selectedFile = ref<File | null>(null)
const previewUrl = ref<string>('')
const isUploading = ref(false)
const resultData = ref<any>(null)
const rawResult = ref<string>('')
const aiAnalysis = ref<string>('')
const isAnalyzing = ref(false)
const dragOver = ref(false)
const errorMsg = ref('')
const activeView = ref<'visual' | 'raw' | 'ai'>('visual')
const uploadedImages = ref<Array<{ name: string; url: string; result: any }>>([])

// ===== 方法 =====

/** 触发文件选择 */
function triggerUpload() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.onchange = (e) => {
    const file = (e.target as HTMLInputElement).files?.[0]
    if (file) handleFile(file)
  }
  input.click()
}

/** 处理文件选择 */
function handleFile(file: File) {
  // 校验
  if (!file.type.startsWith('image/')) {
    errorMsg.value = '请选择图片文件'
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    errorMsg.value = '图片大小不能超过10MB'
    return
  }

  errorMsg.value = ''
  selectedFile.value = file
  resultData.value = null
  rawResult.value = ''
  aiAnalysis.value = ''

  // 生成预览
  const url = URL.createObjectURL(file)
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = url
}

/** 处理拖放 */
function onDragOver(e: DragEvent) {
  e.preventDefault()
  dragOver.value = true
}

function onDragLeave() {
  dragOver.value = false
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  dragOver.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) handleFile(file)
}

/** 上传并识别 */
async function uploadAndRecognize() {
  if (!selectedFile.value || isUploading.value) return

  isUploading.value = true
  errorMsg.value = ''
  resultData.value = null
  rawResult.value = ''

  const formData = new FormData()
  formData.append('image', selectedFile.value)

  try {
    const res = await fetch('/api/image/recognize', {
      method: 'POST',
      body: formData,
    })
    const json = await res.json()

    if (!json.success) {
      errorMsg.value = json.error || '识别失败'
      return
    }

    // 解析识别结果
    let data = json.data
    if (typeof data === 'string') {
      try {
        data = JSON.parse(data)
      } catch { }
    }

    resultData.value = data
    rawResult.value = JSON.stringify(data, null, 2)
    activeView.value = 'visual'

    // 添加到历史列表
    if (data.success !== false) {
      uploadedImages.value.unshift({
        name: json.filename || selectedFile.value.name,
        url: previewUrl.value,
        result: data,
      })
      if (uploadedImages.value.length > 20) {
        uploadedImages.value.pop()
      }
    }

  } catch (e: any) {
    errorMsg.value = `请求失败: ${e.message}`
  } finally {
    isUploading.value = false
  }
}

/** 让AI分析识别结果 */
async function analyzeWithAI() {
  if (!resultData.value || isAnalyzing.value) return

  isAnalyzing.value = true
  aiAnalysis.value = ''

  // 构建分析提示词
  const prompt = buildAnalysisPrompt(resultData.value)

  try {
    const res = await fetch('/chat/call', {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain' },
      body: prompt,
    })
    const text = await res.text()
    aiAnalysis.value = text
    activeView.value = 'ai'
  } catch (e: any) {
    aiAnalysis.value = `AI分析失败: ${e.message}`
  } finally {
    isAnalyzing.value = false
  }
}

/** 构建分析提示词 */
function buildAnalysisPrompt(data: any): string {
  let prompt = `请分析以下图片识别结果，用中文总结图片内容：\n\n`

  if (data.image_info) {
    prompt += `【图像基本信息】\n`
    prompt += `- 尺寸: ${data.image_info.width} × ${data.image_info.height} 像素\n`
    prompt += `- 格式: ${data.image_info.format}\n`
    prompt += `- 宽高比: ${data.image_info.aspect_ratio}\n\n`
  }

  if (data.image_type) {
    prompt += `【图像类型】${data.image_type}\n\n`
  }

  if (data.dominant_colors && data.dominant_colors.length > 0) {
    prompt += `【主色调】\n`
    data.dominant_colors.forEach((c: any) => {
      if (!c.error) {
        prompt += `- ${c.name} (${c.hex}, 占比${c.percentage})\n`
      }
    })
    prompt += '\n'
  }

  if (data.brightness) {
    prompt += `【亮度】${data.brightness.brightness_level} (平均亮度: ${data.brightness.average_brightness})\n\n`
  }

  if (data.sharpness) {
    prompt += `【清晰度】${data.sharpness.sharpness_level}\n\n`
  }

  if (data.ocr) {
    prompt += `【OCR文字识别】\n`
    if (data.ocr.available && data.ocr.text) {
      prompt += `识别到的文字内容：\n\`\`\`\n${data.ocr.text}\n\`\`\`\n`
    } else if (data.ocr.error) {
      prompt += `OCR状态: ${data.ocr.error}\n`
    } else {
      prompt += `未识别到文字\n`
    }
  }

  prompt += `\n请根据以上信息：\n`
  prompt += `1. 用通俗的语言描述这张图片的内容和特点\n`
  prompt += `2. 如果识别到文字，整理文字内容\n`
  prompt += `3. 给出图片的使用建议或优化方向\n`

  return prompt
}

/** 清空当前选择 */
function clearSelection() {
  selectedFile.value = null
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  resultData.value = null
  rawResult.value = ''
  aiAnalysis.value = ''
  errorMsg.value = ''
}

/** 复制识别结果 */
function copyResult() {
  if (rawResult.value) {
    navigator.clipboard.writeText(rawResult.value)
  }
}

/** 格式化文件大小 */
function formatSize(bytes: number): string {
  if (!bytes) return '未知'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<template>
  <div class="image-recognition">
    <!-- 工具栏 -->
    <div class="ir-toolbar">
      <button class="ir-btn ir-btn-primary" @click="triggerUpload" :disabled="isUploading">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
          <polyline points="17 8 12 3 7 8" />
          <line x1="12" y1="3" x2="12" y2="15" />
        </svg>
        选择图片
      </button>
      <button
        v-if="selectedFile"
        class="ir-btn ir-btn-accent"
        :disabled="isUploading"
        @click="uploadAndRecognize"
      >
        <svg v-if="!isUploading" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polygon points="5 3 19 12 5 21 5 3" />
        </svg>
        <span v-else class="ir-spinner" />
        {{ isUploading ? '识别中...' : '开始识别' }}
      </button>
      <button v-if="selectedFile" class="ir-btn" @click="clearSelection">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18" />
          <line x1="6" y1="6" x2="18" y2="18" />
        </svg>
        清空
      </button>
    </div>

    <!-- 拖放区域/预览 -->
    <div
      :class="['ir-dropzone', { 'ir-dragover': dragOver, 'ir-has-image': previewUrl }]"
      @dragover="onDragOver"
      @dragleave="onDragLeave"
      @drop="onDrop"
      @click="!previewUrl && triggerUpload()"
    >
      <template v-if="!previewUrl">
        <div class="ir-drop-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
            <polyline points="17 8 12 3 7 8" />
            <line x1="12" y1="3" x2="12" y2="15" />
          </svg>
        </div>
        <p class="ir-drop-text">点击选择图片或拖放图片到此处</p>
        <p class="ir-drop-hint">支持 JPG / PNG / WebP / BMP，最大 10MB</p>
      </template>

      <template v-else>
        <div class="ir-preview-container">
          <img :src="previewUrl" alt="预览" class="ir-preview" />
          <div class="ir-preview-overlay">
            <span class="ir-preview-name">{{ selectedFile?.name }}</span>
            <span class="ir-preview-size">{{ formatSize(selectedFile?.size) }}</span>
          </div>
        </div>
      </template>
    </div>

    <!-- 错误提示 -->
    <div v-if="errorMsg" class="ir-error">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10" />
        <line x1="15" y1="9" x2="9" y2="15" />
        <line x1="9" y1="9" x2="15" y2="15" />
      </svg>
      {{ errorMsg }}
    </div>

    <!-- 识别结果 -->
    <div v-if="resultData" class="ir-results">
      <!-- 视图切换 -->
      <div class="ir-result-tabs">
        <button
          :class="['ir-result-tab', { active: activeView === 'visual' }]"
          @click="activeView = 'visual'"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2" />
            <circle cx="8.5" cy="8.5" r="1.5" />
            <path d="M21 15l-5-5L5 21" />
          </svg>
          可视化
        </button>
        <button
          :class="['ir-result-tab', { active: activeView === 'raw' }]"
          @click="activeView = 'raw'"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="16 18 22 12 16 6" />
            <polyline points="8 6 2 12 8 18" />
          </svg>
          JSON
        </button>
        <button
          :class="['ir-result-tab', { active: activeView === 'ai' }]"
          @click="activeView = 'ai'"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 2L2 7l10 5 10-5-10-5z" />
            <path d="M2 17l10 5 10-5" />
            <path d="M2 12l10 5 10-5" />
          </svg>
          AI分析
        </button>
      </div>

      <!-- 可视化视图 -->
      <div v-if="activeView === 'visual'" class="ir-visual">
        <div class="ir-visual-grid">
          <!-- 图像信息 -->
          <div class="ir-card" v-if="resultData.image_info">
            <div class="ir-card-header">📷 图像信息</div>
            <div class="ir-card-body">
              <div class="ir-info-row">
                <span class="ir-label">尺寸</span>
                <span class="ir-value">{{ resultData.image_info.width }} × {{ resultData.image_info.height }}</span>
              </div>
              <div class="ir-info-row">
                <span class="ir-label">格式</span>
                <span class="ir-value">{{ resultData.image_info.format }}</span>
              </div>
              <div class="ir-info-row">
                <span class="ir-label">宽高比</span>
                <span class="ir-value">{{ resultData.image_info.aspect_ratio }}</span>
              </div>
              <div class="ir-info-row">
                <span class="ir-label">类型</span>
                <span class="ir-value">{{ resultData.image_type || '未知' }}</span>
              </div>
            </div>
          </div>

          <!-- 主色调 -->
          <div class="ir-card" v-if="resultData.dominant_colors?.length">
            <div class="ir-card-header">🎨 主色调</div>
            <div class="ir-card-body">
              <div
                v-for="(color, idx) in resultData.dominant_colors"
                :key="idx"
                class="ir-color-row"
              >
                <span class="ir-color-swatch" :style="{ background: color.hex || color.rgb }" />
                <span class="ir-color-name">{{ color.name }}</span>
                <span class="ir-color-hex">{{ color.hex }}</span>
                <span class="ir-color-pct">{{ color.percentage }}</span>
              </div>
            </div>
          </div>

          <!-- 亮度 -->
          <div class="ir-card" v-if="resultData.brightness">
            <div class="ir-card-header">☀️ 亮度分析</div>
            <div class="ir-card-body">
              <div class="ir-info-row">
                <span class="ir-label">等级</span>
                <span class="ir-value">{{ resultData.brightness.brightness_level }}</span>
              </div>
              <div class="ir-info-row">
                <span class="ir-label">平均亮度</span>
                <span class="ir-value">{{ resultData.brightness.average_brightness }}</span>
              </div>
              <div class="ir-info-row">
                <span class="ir-label">范围</span>
                <span class="ir-value">{{ resultData.brightness.min_brightness }} ~ {{ resultData.brightness.max_brightness }}</span>
              </div>
            </div>
          </div>

          <!-- 清晰度 -->
          <div class="ir-card" v-if="resultData.sharpness">
            <div class="ir-card-header">🔍 清晰度</div>
            <div class="ir-card-body">
              <div class="ir-info-row">
                <span class="ir-label">等级</span>
                <span class="ir-value">{{ resultData.sharpness.sharpness_level }}</span>
              </div>
              <div class="ir-info-row">
                <span class="ir-label">评分</span>
                <span class="ir-value">{{ resultData.sharpness.sharpness_score }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- OCR 结果 -->
        <div v-if="resultData.ocr" class="ir-card ir-card-full">
          <div class="ir-card-header">
            📝 OCR 文字识别
            <span v-if="!resultData.ocr.available" class="ir-ocr-unavailable">(不可用)</span>
          </div>
          <div class="ir-card-body">
            <template v-if="resultData.ocr.available">
              <div v-if="resultData.ocr.text && resultData.ocr.text !== '未识别到文字'" class="ir-ocr-text">
                <pre>{{ resultData.ocr.text }}</pre>
              </div>
              <div v-else class="ir-ocr-empty">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <circle cx="12" cy="12" r="10" />
                  <path d="M16 16s-1.5-2-4-2-4 2-4 2" />
                  <line x1="9" y1="9" x2="9.01" y2="9" />
                  <line x1="15" y1="9" x2="15.01" y2="9" />
                </svg>
                <p>未识别到文字内容</p>
              </div>
            </template>
            <div v-else class="ir-ocr-empty">
              <p>{{ resultData.ocr.error }}</p>
            </div>
          </div>
        </div>

        <!-- AI分析按钮 -->
        <div class="ir-ai-section">
          <button
            class="ir-btn ir-btn-accent ir-btn-lg"
            @click="analyzeWithAI"
            :disabled="isAnalyzing"
          >
            <svg v-if="!isAnalyzing" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2L2 7l10 5 10-5-10-5z" />
              <path d="M2 17l10 5 10-5" />
              <path d="M2 12l10 5 10-5" />
            </svg>
            <span v-else class="ir-spinner" />
            {{ isAnalyzing ? 'AI分析中...' : '🤖 AI智能分析图片内容' }}
          </button>
        </div>
      </div>

      <!-- JSON视图 -->
      <div v-if="activeView === 'raw'" class="ir-raw">
        <div class="ir-raw-header">
          <span>识别结果 JSON</span>
          <button class="ir-btn ir-btn-sm" @click="copyResult">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="9" y="9" width="13" height="13" rx="2" />
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
            </svg>
            复制
          </button>
        </div>
        <pre class="ir-json">{{ rawResult }}</pre>
      </div>

      <!-- AI分析视图 -->
      <div v-if="activeView === 'ai'" class="ir-ai-result">
        <div v-if="!aiAnalysis && !isAnalyzing" class="ir-ai-placeholder">
          <p>点击上方「AI智能分析图片内容」按钮，让AI解析识别结果</p>
        </div>
        <div v-else-if="isAnalyzing" class="ir-ai-loading">
          <span class="ir-spinner ir-spinner-lg" />
          <p>AI正在分析图片内容...</p>
        </div>
        <div v-else class="ir-ai-content markdown-body" v-html="md.render(aiAnalysis)" />
      </div>
    </div>

    <!-- 识别历史 -->
    <div v-if="uploadedImages.length > 0 && !resultData" class="ir-history">
      <div class="ir-history-header">📋 识别历史</div>
      <div class="ir-history-list">
        <div
          v-for="(item, idx) in uploadedImages"
          :key="idx"
          class="ir-history-item"
          @click="selectHistory(item)"
        >
          <img :src="item.url" :alt="item.name" class="ir-history-thumb" />
          <div class="ir-history-info">
            <span class="ir-history-name">{{ item.name }}</span>
            <span class="ir-history-type">{{ item.result?.image_type || '图片' }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ===== 图片识别容器 ===== */
.image-recognition {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  padding: 16px 20px;
  gap: 14px;
}

.image-recognition::-webkit-scrollbar { width: 4px; }
.image-recognition::-webkit-scrollbar-thumb { background: rgba(0,0,0,0.1); border-radius: 10px; }

/* ===== 工具栏 ===== */
.ir-toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.ir-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid var(--ai-border);
  border-radius: 8px;
  background: var(--card-bg);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;
}

.ir-btn svg { width: 16px; height: 16px; }

.ir-btn:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--text-primary);
}

.ir-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ir-btn-primary {
  background: var(--accent);
  color: white;
  border-color: var(--accent);
}

.ir-btn-primary:hover:not(:disabled) {
  background: var(--accent-hover) !important;
  border-color: var(--accent-hover) !important;
  color: white !important;
}

.ir-btn-accent {
  background: #27272a;
  color: white;
  border-color: #27272a;
}

.ir-btn-accent:hover:not(:disabled) {
  background: #18181b !important;
  border-color: #18181b !important;
  color: white !important;
}

.ir-btn-lg { padding: 10px 24px; font-size: 14px; gap: 8px; }

.ir-btn-sm { padding: 5px 10px; font-size: 12px; }

/* ===== 拖放区域 ===== */
.ir-dropzone {
  border: 2px dashed var(--ai-border);
  border-radius: var(--radius-md);
  padding: 32px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s ease;
  flex-shrink: 0;
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ir-dropzone:hover { border-color: var(--accent); background: var(--accent-light); }

.ir-dragover {
  border-color: var(--accent);
  background: var(--accent-light);
  transform: scale(1.01);
}

.ir-has-image {
  padding: 8px;
  cursor: default;
  border-style: solid;
}

.ir-drop-icon svg { width: 48px; height: 48px; color: var(--text-muted); margin-bottom: 8px; }

.ir-drop-text { font-size: 15px; color: var(--text-secondary); font-weight: 500; }
.ir-drop-hint { font-size: 12.5px; color: var(--text-muted); margin-top: 4px; }

/* 预览 */
.ir-preview-container {
  position: relative;
  width: 100%;
  max-width: 400px;
  margin: 0 auto;
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.ir-preview {
  width: 100%;
  height: auto;
  max-height: 300px;
  object-fit: contain;
  display: block;
}

.ir-preview-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(transparent, rgba(0,0,0,0.6));
  padding: 20px 12px 8px;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  color: white;
  font-size: 12px;
}

.ir-preview-name { font-weight: 500; max-width: 70%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ir-preview-size { opacity: 0.8; }

/* ===== 错误提示 ===== */
.ir-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #fef2f2;
  border: 1px solid #fca5a5;
  border-radius: var(--radius-sm);
  color: #dc2626;
  font-size: 13px;
}

.ir-error svg { width: 18px; height: 18px; flex-shrink: 0; }

/* ===== 结果区域 ===== */
.ir-results {
  display: flex;
  flex-direction: column;
  gap: 14px;
  animation: fadeIn 0.3s ease-out;
}

/* 结果Tab切换 */
.ir-result-tabs {
  display: flex;
  gap: 4px;
  background: var(--accent-light);
  border-radius: 10px;
  padding: 3px;
  flex-shrink: 0;
}

.ir-result-tab {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 7px 14px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.ir-result-tab svg { width: 15px; height: 15px; }

.ir-result-tab.active {
  background: var(--card-bg);
  color: var(--text-primary);
  font-weight: 600;
  box-shadow: var(--shadow-sm);
}

/* ===== 可视化视图 ===== */
.ir-visual {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ir-visual-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

@media (max-width: 600px) {
  .ir-visual-grid { grid-template-columns: 1fr; }
}

.ir-card {
  background: var(--accent-light);
  border-radius: var(--radius-sm);
  overflow: hidden;
  border: 1px solid var(--ai-border);
}

.ir-card-full { grid-column: 1 / -1; }

.ir-card-header {
  padding: 10px 14px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  border-bottom: 1px solid var(--ai-border);
  background: rgba(255,255,255,0.5);
}

.ir-card-body { padding: 10px 14px; }

.ir-info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 5px 0;
  font-size: 13px;
}

.ir-label { color: var(--text-secondary); }
.ir-value { font-weight: 500; color: var(--text-primary); }

/* 颜色行 */
.ir-color-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 13px;
}

.ir-color-swatch {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  border: 1px solid rgba(0,0,0,0.08);
  flex-shrink: 0;
}

.ir-color-name { flex: 1; color: var(--text-primary); }
.ir-color-hex { color: var(--text-muted); font-family: monospace; font-size: 12px; }
.ir-color-pct { color: var(--text-secondary); font-size: 12px; min-width: 40px; text-align: right; }

/* OCR */
.ir-ocr-text {
  background: var(--card-bg);
  border-radius: 6px;
  padding: 12px;
  max-height: 200px;
  overflow-y: auto;
}

.ir-ocr-text pre {
  font-family: inherit;
  font-size: 13.5px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
}

.ir-ocr-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 16px;
  color: var(--text-muted);
  text-align: center;
}

.ir-ocr-empty svg { width: 32px; height: 32px; opacity: 0.4; }
.ir-ocr-empty p { font-size: 13px; }

.ir-ocr-unavailable { color: var(--text-muted); font-weight: 400; font-size: 12px; }

/* AI分析按钮 */
.ir-ai-section {
  display: flex;
  justify-content: center;
  padding: 6px 0;
}

/* ===== JSON视图 ===== */
.ir-raw {
  background: #1e293b;
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.ir-raw-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: rgba(255,255,255,0.05);
  color: #94a3b8;
  font-size: 12px;
  font-weight: 600;
}

.ir-raw-header .ir-btn { color: #94a3b8; border-color: #334155; }
.ir-raw-header .ir-btn:hover { color: #e2e8f0; border-color: #94a3b8; }

.ir-json {
  padding: 14px;
  margin: 0;
  font-family: 'SF Mono', 'JetBrains Mono', Consolas, monospace;
  font-size: 12.5px;
  line-height: 1.5;
  color: #e2e8f0;
  overflow-x: auto;
  max-height: 400px;
  overflow-y: auto;
  white-space: pre;
}

/* ===== AI分析视图 ===== */
.ir-ai-content {
  padding: 16px;
  background: var(--card-bg);
  border: 1px solid var(--ai-border);
  border-radius: var(--radius-sm);
}

.ir-ai-placeholder {
  text-align: center;
  padding: 32px;
  color: var(--text-muted);
}

.ir-ai-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 32px;
  color: var(--text-secondary);
}

/* ===== Spinner ===== */
.ir-spinner {
  display: inline-block;
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: ir-spin 0.6s linear infinite;
}

.ir-spinner-lg { width: 28px; height: 28px; border-width: 3px; }

@keyframes ir-spin {
  to { transform: rotate(360deg); }
}

/* ===== 历史记录 ===== */
.ir-history-header {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  padding: 4px 0;
}

.ir-history-list {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.ir-history-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid var(--ai-border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
  max-width: 240px;
}

.ir-history-item:hover { border-color: var(--accent); background: var(--accent-light); }

.ir-history-thumb {
  width: 36px;
  height: 36px;
  border-radius: 4px;
  object-fit: cover;
  flex-shrink: 0;
}

.ir-history-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.ir-history-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ir-history-type {
  font-size: 11px;
  color: var(--text-muted);
}
</style>
