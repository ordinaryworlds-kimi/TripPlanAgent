import { ref, computed, onMounted, onUnmounted } from 'vue'
import { marked } from 'marked'
import { createSession, fetchHistory, fetchInfraStatus, openChatStream } from '../api/chat'

const SESSION_KEY = 'aitrip_session_id'

const WELCOME = {
  id: 'welcome',
  role: 'agent',
  type: 'message',
  content: '您好！请描述您的自驾游需求，生成计划后输入「好的」或「确认」即可开始执行。',
  label: '',
}

const EVENT_LABELS = {
  thinking: '思考过程',
  tool: '工具调用',
  auto: '自动推进',
}

export function useChat() {
  const sessionId = ref(localStorage.getItem(SESSION_KEY) || '')
  const messages = ref([])
  const plan = ref(null)
  const infraStatus = ref(null)
  const inputText = ref('')
  const sending = ref(false)
  const processingMode = ref('idle') // idle | chatting | auto

  let eventSource = null
  let currentStreamId = null
  let currentStreamText = ''

  const sendButtonText = computed(() => {
    if (!sending.value) return '发送'
    return processingMode.value === 'auto' ? '自动执行中...' : '处理中...'
  })

  const agentText = computed(() =>
    messages.value
      .filter((m) => m.role === 'agent')
      .map((m) => m.content)
      .join('\n\n'),
  )

  function renderMarkdown(text) {
    return marked.parse(text || '')
  }

  function addMessage(msg) {
    messages.value.push({ id: `${Date.now()}-${Math.random()}`, ...msg })
  }

  function updateStreamMessage(chunk) {
    currentStreamText += chunk
    const existing = messages.value.find((m) => m.id === currentStreamId)
    if (existing) {
      existing.content = currentStreamText
    } else {
      currentStreamId = `stream-${Date.now()}`
      messages.value.push({
        id: currentStreamId,
        role: 'agent',
        type: 'message',
        content: currentStreamText,
        label: '',
      })
    }
  }

  function resetStreamState() {
    currentStreamId = null
    currentStreamText = ''
  }

  function finishSending() {
    closeStream()
    sending.value = false
    processingMode.value = 'idle'
    resetStreamState()
  }

  async function ensureSession() {
    if (!sessionId.value) {
      const data = await createSession()
      sessionId.value = data.sessionId
      localStorage.setItem(SESSION_KEY, sessionId.value)
    }
  }

  async function refreshInfraStatus() {
    try {
      infraStatus.value = await fetchInfraStatus()
    } catch {
      infraStatus.value = null
    }
  }

  async function loadHistory() {
    try {
      const history = await fetchHistory(sessionId.value)
      messages.value = []
      let lastPlan = null

      history.forEach((item) => {
        if (item.type === 'plan') {
          try {
            lastPlan = JSON.parse(item.content)
          } catch {
            /* ignore */
          }
          return
        }
        const role =
          item.role === 'user' ? 'user' : item.role === 'agent' ? 'agent' : 'system'
        addMessage({
          role,
          type: item.type,
          content: item.content,
          label: EVENT_LABELS[item.type] || '',
        })
      })

      plan.value = lastPlan
      if (!messages.value.length) {
        messages.value = [{ ...WELCOME }]
      }
    } catch {
      messages.value = [{ ...WELCOME }]
    }
  }

  async function init() {
    await ensureSession()
    await refreshInfraStatus()
    await loadHistory()
  }

  async function newChat() {
    finishSending()
    const data = await createSession()
    sessionId.value = data.sessionId
    localStorage.setItem(SESSION_KEY, sessionId.value)
    messages.value = [{ ...WELCOME }]
    plan.value = null
    inputText.value = ''
  }

  function closeStream() {
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
  }

  function bindStreamHandlers(source, autoOnly) {
    const handlePayload = (type, e) => {
      if (!e.data) return
      try {
        const data = JSON.parse(e.data)
        if (type === 'message') {
          updateStreamMessage(data.content)
        } else if (type === 'plan') {
          plan.value = data
        } else if (type === 'error') {
          addMessage({
            role: 'system',
            type: 'error',
            content: data.message || '请求出错',
            label: '',
          })
        } else {
          addMessage({
            role: 'system',
            type,
            content: data.content,
            label: EVENT_LABELS[type] || '',
          })
        }
      } catch {
        /* ignore */
      }
    }

    ;['thinking', 'tool', 'message', 'auto'].forEach((type) => {
      source.addEventListener(type, (e) => handlePayload(type, e))
    })
    source.addEventListener('plan', (e) => handlePayload('plan', e))
    source.addEventListener('error', (e) => handlePayload('error', e))
    source.addEventListener('ping', () => {
      /* 心跳保活，避免长任务期间 SSE 被判定超时 */
    })

    source.addEventListener('done', (e) => {
      let autoContinue = false
      try {
        autoContinue = JSON.parse(e.data).autoContinue === true
      } catch {
        /* ignore */
      }
      closeStream()
      resetStreamState()

      if (autoContinue) {
        processingMode.value = 'auto'
        startStream(true)
      } else {
        finishSending()
      }
    })

    const currentSource = source
    source.onerror = () => {
      setTimeout(() => {
        if (sending.value && currentSource.readyState === EventSource.CLOSED) {
          finishSending()
        }
      }, 1000)
    }

    processingMode.value = autoOnly ? 'auto' : 'chatting'
  }

  function startStream(autoOnly, prompt = '') {
    closeStream()
    resetStreamState()
    eventSource = openChatStream(sessionId.value, prompt, autoOnly)
    bindStreamHandlers(eventSource, autoOnly)
  }

  function sendMessage() {
    const prompt = inputText.value.trim()
    if (!prompt || sending.value) return

    addMessage({ role: 'user', type: 'user', content: prompt, label: '' })
    inputText.value = ''
    sending.value = true
    startStream(false, prompt)
  }

  function setPrompt(text) {
    inputText.value = text
  }

  async function copyResult() {
    await navigator.clipboard.writeText(agentText.value)
    alert('已复制到剪贴板')
  }

  function downloadResult() {
    const blob = new Blob([agentText.value], { type: 'text/plain;charset=utf-8' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `trip-plan-${sessionId.value.slice(0, 8)}.txt`
    a.click()
    URL.revokeObjectURL(a.href)
  }

  let infraTimer = null

  onMounted(async () => {
    await init()
    infraTimer = setInterval(refreshInfraStatus, 30000)
  })

  onUnmounted(() => {
    if (infraTimer) clearInterval(infraTimer)
    finishSending()
  })

  return {
    sessionId,
    messages,
    plan,
    infraStatus,
    inputText,
    sending,
    sendButtonText,
    renderMarkdown,
    sendMessage,
    setPrompt,
    newChat,
    copyResult,
    downloadResult,
  }
}
