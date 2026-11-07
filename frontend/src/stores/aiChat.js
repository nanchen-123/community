import { ref, watch } from 'vue'
import { getAuth } from '../utils/viewRole'

/**
 * AI 助手对话状态
 * - 放在模块级：切换菜单页面后内容不丢
 * - 同步到 localStorage：刷新浏览器后仍保留（按登录用户隔离）
 * - 未落库，清空对话即可删除本机记录
 */

const MAX_MESSAGES = 60

function storageKey() {
  const username = getAuth()?.username
  return username ? `ai_chat_${username}` : ''
}

function loadMessages() {
  const key = storageKey()
  if (!key) return []
  try {
    const arr = JSON.parse(localStorage.getItem(key) || '[]')
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

export const messages = ref(loadMessages())
export const input = ref('')
export const sending = ref(false)

// 消息有任何变化就写回本地（只保留最近 60 条，避免 localStorage 越写越大）
watch(messages, (list) => {
  const key = storageKey()
  if (!key) return
  try {
    localStorage.setItem(key, JSON.stringify(list.slice(-MAX_MESSAGES)))
  } catch {
    // 容量超限时忽略，不影响页面使用
  }
}, { deep: true, flush: 'post' })
