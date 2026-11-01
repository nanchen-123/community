<script setup>
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../api/request'

/* ====== 对话数据 ====== */
const messages = ref([])      // { role, content, error?, tokens? }
const input = ref('')
const sending = ref(false)
const listRef = ref(null)

// 示例问题（空状态时展示）
const SAMPLES = [
  '怎么发布一次巡检任务？',
  '机器人到达拍照点后为什么停住了？',
  '告警的处理状态有哪些？',
  '普通用户能看到别的社区数据吗？'
]

/* ====== 滚动到底部 ====== */
async function scrollToBottom() {
  await nextTick()
  const el = listRef.value
  if (el) el.scrollTop = el.scrollHeight
}

/* ====== 发送消息 ====== */
async function send(text) {
  const content = (text !== undefined ? text : input.value).trim()
  if (!content || sending.value) return

  messages.value.push({ role: 'user', content })
  input.value = ''
  sending.value = true
  scrollToBottom()

  try {
    // 只带正常消息，避免把失败提示也当成上下文
    const history = messages.value
      .filter(m => !m.error)
      .map(m => ({ role: m.role, content: m.content }))

    // AI 生成较慢，单独把超时放宽到 60 秒
    const res = await request.post('/ai/chat', { messages: history }, { timeout: 60000 })
    messages.value.push({
      role: 'assistant',
      content: res?.content || '（AI 没有返回内容）',
      tokens: (res?.promptTokens || 0) + (res?.completionTokens || 0)
    })
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
            <div :class="['chat-bubble', m.role === 'user' ? 'bubble-user' : 'bubble-ai', m.error ? 'bubble-error' : '']">
              {{ m.content }}
            </div>
            <div class="chat-meta">
              <span v-if="m.tokens" class="chat-tokens">本次约 {{ m.tokens }} tokens</span>
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
        <el-input
          v-model="input"
          type="textarea"
          resize="none"
          :autosize="{ minRows: 1, maxRows: 4 }"
          placeholder="输入你的问题，Enter 发送，Shift + Enter 换行"
          :disabled="sending"
          @keydown.enter.exact.prevent="send()"
        />
        <div class="chat-actions">
          <span class="chat-hint">Enter 发送 · Shift + Enter 换行</span>
          <div class="spacer"></div>
          <el-button :disabled="sending || !messages.length" @click="clearChat">清空对话</el-button>
          <el-button type="primary" :loading="sending" :disabled="!input.trim()" @click="send()">发送</el-button>
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
