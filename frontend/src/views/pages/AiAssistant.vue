<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import request from '../../api/request'
import { messages, input, sending } from '../../stores/aiChat'

/* ====== 对话数据（存于 stores/aiChat.js，切换页面不丢） ====== */
const listRef = ref(null)

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

/* ====== 附件（仅当前页面有效，生成报告后自动清空） ====== */
const fileInputRef = ref(null)
const attaching = ref(false)
const attach = ref(null)   // { fileId, fileName, chars }

const MAX_FILE_MB = 30

// 示例问题（空状态时展示）
const SAMPLES = [
  '怎么发布一次巡检任务？',
  '机器人到达拍照点后为什么停住了？',
  '告警的处理状态有哪些？',
  '普通用户能看到别的社区数据吗？',
  '把文件发给 AI，能帮我写总结报告吗？'
]

/* ====== 滚动到底部 ====== */
async function scrollToBottom() {
  await nextTick()
  const el = listRef.value
  if (el) el.scrollTop = el.scrollHeight
}

/* ====== Markdown 渲染（报告、普通回复都用） ====== */
function renderMarkdown(text) {
  return md.render(text || '')
}

/* ====== 选择文件 → 上传解析 ====== */
function pickFile() {
  if (sending.value || attaching.value) return
  fileInputRef.value?.click()
}

async function onFileChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''   // 允许重复选择同一个文件
  if (!file) return
  if (file.size > MAX_FILE_MB * 1024 * 1024) {
    ElMessage.warning(`文件超过 ${MAX_FILE_MB}MB，请压缩后再上传`)
    return
  }

  attaching.value = true
  try {
    const form = new FormData()
    form.append('file', file)
    const res = await request.post('/ai/file', form, { timeout: 120000 })
    attach.value = {
      fileId: res.fileId,
      fileName: res.fileName,
      chars: res.chars || 0
    }
    ElMessage.success(`已解析 ${(res.chars || 0).toLocaleString()} 字`)
    await nextTick()
    input.value = ''
  } catch (err) {
    // 错误提示由 request 拦截器统一处理
  } finally {
    attaching.value = false
  }
}

/* ====== 移除附件（后端一并清理） ====== */
async function removeFile() {
  const current = attach.value
  attach.value = null
  if (current?.fileId) {
    request.delete(`/ai/file/${current.fileId}`).catch(() => {})
  }
}

/* ====== 下载 Markdown 报告 ====== */
function downloadReport(content) {
  const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `AI总结报告_${stamp}.md`
  a.click()
  URL.revokeObjectURL(url)
}

/* ====== 发送消息（带附件时走总结报告） ====== */
async function send(text) {
  const content = (text !== undefined ? text : input.value).trim()
  if (sending.value || attaching.value) return
  if (!content && !attach.value) return

  const currentAttach = attach.value
  const userText = content || `请总结这份文件《${currentAttach.fileName}》`

  messages.value.push({ role: 'user', content: userText })
  input.value = ''
  sending.value = true
  scrollToBottom()

  try {
    // 只带正常消息，避免把失败提示也当成上下文
    const history = messages.value
      .filter(m => !m.error)
      .map(m => ({ role: m.role, content: m.content }))

    const payload = { messages: history }
    if (currentAttach) payload.fileId = currentAttach.fileId

    // 文件总结可能分段生成，超时放宽到 180 秒；普通问答 60 秒
    const res = await request.post('/ai/chat', payload, { timeout: currentAttach ? 180000 : 60000 })
    messages.value.push({
      role: 'assistant',
      content: res?.content || '（AI 没有返回内容）',
      tokens: (res?.promptTokens || 0) + (res?.completionTokens || 0),
      report: !!currentAttach,
      fileName: currentAttach?.fileName
    })
    if (currentAttach) attach.value = null
  } catch (e) {
    messages.value.push({
      role: 'assistant',
      content: '这次没有问到结果：' + (e?.message || '未知错误') + '\n可以稍后重试，或检查后端 AI 配置。',
      error: true
    })
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

/* ====== 清空对话 ====== */
function clearChat() {
  messages.value = []
  ElMessage.success('已清空对话')
}

/* ====== 复制回复 ====== */
async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('当前浏览器不支持自动复制')
  }
}

// 从其他页面切回来时，直接定位到最新一条消息
onMounted(scrollToBottom)
</script>

<template>
  <div class="ai-page">
    <div class="page-header">
      <div class="page-title">AI 助手</div>
      <div class="page-sub">
        <svg class="sub-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8.9h.5a8.48 8.48 0 0 1 8 8v.5z"></path>
        </svg>
        <span>系统使用问答（基于 DeepSeek 大模型，请勿输入敏感信息）</span>
      </div>
    </div>

    <div class="card chat-card">
      <!-- 消息区 -->
      <div ref="listRef" class="chat-body">
        <!-- 空状态 -->
        <div v-if="!messages.length" class="chat-empty">
          <div class="chat-empty-title">你好，我是社区安防巡检系统助手</div>
          <div class="chat-empty-sub">可以问我系统怎么用，比如：</div>
          <div class="chat-samples">
            <div
              v-for="s in SAMPLES"
              :key="s"
              class="chat-sample"
              @click="send(s)"
            >{{ s }}</div>
          </div>
        </div>

        <!-- 消息 -->
        <div
          v-for="(m, i) in messages"
          :key="i"
          :class="['chat-row', m.role === 'user' ? 'chat-row-user' : 'chat-row-ai']"
        >
          <div v-if="m.role === 'assistant'" class="chat-avatar ai">AI</div>
          <div class="chat-bubble-wrap">
            <div
              v-if="m.role === 'assistant' && !m.error"
              class="chat-bubble bubble-ai bubble-md"
              v-html="renderMarkdown(m.content)"
            ></div>
            <div
              v-else
              :class="['chat-bubble', m.role === 'user' ? 'bubble-user' : 'bubble-ai', m.error ? 'bubble-error' : '']"
            >
              {{ m.content }}
            </div>
            <div class="chat-meta">
              <span v-if="m.fileName" class="chat-file-tag">📄 {{ m.fileName }}</span>
              <span v-if="m.tokens" class="chat-tokens">本次约 {{ m.tokens }} tokens</span>
              <span v-if="m.report && !m.error" class="chat-copy" @click="downloadReport(m.content)">下载 .md</span>
              <span v-if="m.role === 'assistant' && !m.error" class="chat-copy" @click="copyText(m.content)">复制</span>
            </div>
          </div>
          <div v-if="m.role === 'user'" class="chat-avatar user">我</div>
        </div>

        <!-- 思考中 -->
        <div v-if="sending" class="chat-row chat-row-ai">
          <div class="chat-avatar ai">AI</div>
          <div class="chat-bubble bubble-ai bubble-typing">正在思考<span class="dot">.</span><span class="dot">.</span><span class="dot">.</span></div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="chat-input">
        <!-- 已选文件 -->
        <div v-if="attach" class="attach-chip">
          <span class="attach-icon">📄</span>
          <span class="attach-name">{{ attach.fileName }}</span>
          <span class="attach-chars">{{ attach.chars.toLocaleString() }} 字 · 直接发送即生成总结报告</span>
          <span class="attach-remove" title="移除" @click="removeFile">×</span>
        </div>

        <el-input
          v-model="input"
          type="textarea"
          resize="none"
          :autosize="{ minRows: 1, maxRows: 4 }"
          :placeholder="attach ? '可留空直接发送，也可填写总结侧重点，例如「重点看消防部分」' : '输入你的问题，Enter 发送，Shift + Enter 换行'"
          :disabled="sending"
          @keydown.enter.exact.prevent="send()"
        />
        <input
          ref="fileInputRef"
          type="file"
          accept=".docx,.pdf,.txt,.md,.csv,.xlsx"
          hidden
          @change="onFileChange"
        />
        <div class="chat-actions">
          <el-button
            text
            type="primary"
            :loading="attaching"
            :disabled="sending || !!attach"
            @click="pickFile"
          >📎 {{ attaching ? '解析中…' : '上传文件' }}</el-button>
          <span class="chat-hint">支持 docx / pdf / txt / md / xlsx，单个不超过 30MB</span>
          <div class="spacer"></div>
          <el-button :disabled="sending || !messages.length" @click="clearChat">清空对话</el-button>
          <el-button type="primary" :loading="sending" :disabled="!input.trim() && !attach" @click="send()">发送</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai-page {
  display: flex;
  flex-direction: column;
}

/* 对话卡片：固定高度，内部滚动 */
.chat-card {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 190px);
  min-height: 420px;
  padding: 0;
  overflow: hidden;
}

/* 消息区 */
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px;
  background: var(--surface-soft);
}

.chat-body::-webkit-scrollbar {
  width: 6px;
}
.chat-body::-webkit-scrollbar-thumb {
  background: #D3E0E5;
  border-radius: 3px;
}

/* 空状态 */
.chat-empty {
  padding: 26px 10px;
  text-align: center;
}
.chat-empty-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 6px;
}
.chat-empty-sub {
  font-size: 12px;
  color: var(--text-soft);
  margin-bottom: 14px;
}
.chat-samples {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}
.chat-sample {
  padding: 7px 12px;
  font-size: 12px;
  color: var(--primary);
  background: var(--primary-tint);
  border: 1px solid var(--primary-tint-2);
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease;
}
.chat-sample:hover {
  background: var(--primary-tint-2);
  border-color: var(--primary-soft);
}

/* 消息行 */
.chat-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 12px;
}
.chat-row-user {
  justify-content: flex-end;
}

.chat-avatar {
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  border-radius: 50%;
  color: #fff;
  margin-top: 2px;
}
.chat-avatar.ai {
  background: var(--primary);
}
.chat-avatar.user {
  background: var(--primary-light);
}

.chat-bubble-wrap {
  max-width: 74%;
}

/* 气泡 */
.chat-bubble {
  padding: 9px 13px;
  font-size: 13px;
  line-height: 1.72;
  white-space: pre-wrap;
  word-break: break-word;
  border-radius: 10px;
}
.bubble-ai {
  background: #FFFFFF;
  border: 1px solid var(--border-deep);
  color: var(--text-main);
  border-top-left-radius: 3px;
}
.bubble-user {
  background: var(--primary);
  color: #fff;
  border-top-right-radius: 3px;
}
.bubble-error {
  background: #FDF3F3;
  border-color: #F0D2D2;
  color: #B04A4A;
}

/* Markdown 内容（总结报告） */
.bubble-md {
  white-space: normal;
}
.bubble-md > :first-child {
  margin-top: 0;
}
.bubble-md > :last-child {
  margin-bottom: 0;
}
.bubble-md h1 {
  font-size: 16px;
  margin: 14px 0 8px;
}
.bubble-md h2 {
  font-size: 14px;
  margin: 12px 0 6px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--border-deep);
}
.bubble-md h3 {
  font-size: 13px;
  margin: 10px 0 4px;
}
.bubble-md p {
  margin: 6px 0;
}
.bubble-md ul,
.bubble-md ol {
  margin: 6px 0;
  padding-left: 20px;
}
.bubble-md li {
  margin: 2px 0;
}
.bubble-md table {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 12px;
}
.bubble-md th,
.bubble-md td {
  border: 1px solid var(--border-deep);
  padding: 5px 8px;
  text-align: left;
}
.bubble-md th {
  background: var(--surface-soft);
  font-weight: 600;
}
.bubble-md code {
  background: var(--surface-soft);
  padding: 1px 4px;
  border-radius: 3px;
}
.bubble-md blockquote {
  margin: 6px 0;
  padding-left: 10px;
  border-left: 3px solid var(--primary-tint-2);
  color: var(--text-soft);
}

/* 思考中动画 */
.bubble-typing {
  color: var(--text-soft);
  font-size: 12.5px;
}
.bubble-typing .dot {
  animation: blink 1.2s infinite;
}
.bubble-typing .dot:nth-child(2) { animation-delay: 0.2s; }
.bubble-typing .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink {
  0%, 100% { opacity: 0.2; }
  50% { opacity: 1; }
}

/* 气泡下方小字 */
.chat-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
  padding: 0 2px;
  font-size: 11px;
  color: var(--text-soft);
}
.chat-row-user .chat-meta {
  justify-content: flex-end;
}
.chat-copy {
  cursor: pointer;
  color: var(--primary);
}
.chat-copy:hover {
  text-decoration: underline;
}

/* 报告关联的文件名 */
.chat-file-tag {
  color: var(--text-soft);
}

/* 已选文件条 */
.attach-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 8px;
  font-size: 12px;
  background: var(--primary-tint);
  border: 1px solid var(--primary-tint-2);
  border-radius: 8px;
}
.attach-icon {
  font-size: 14px;
}
.attach-name {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  color: var(--text-main);
}
.attach-chars {
  color: var(--text-soft);
}
.attach-remove {
  margin-left: auto;
  font-size: 16px;
  line-height: 1;
  color: var(--text-soft);
  cursor: pointer;
}
.attach-remove:hover {
  color: #B04A4A;
}

/* 输入区 */
.chat-input {
  border-top: 1px solid var(--border-deep);
  padding: 12px 14px;
  background: #fff;
}
.chat-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}
.chat-hint {
  font-size: 11px;
  color: var(--text-soft);
}

@media (max-width: 900px) {
  .chat-card {
    height: calc(100vh - 210px);
  }
  .chat-bubble-wrap {
    max-width: 84%;
  }
}
</style>
