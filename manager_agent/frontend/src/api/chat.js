const BASE_URL = '/api'

export async function createSession() {
  const res = await fetch(`${BASE_URL}/session`, { method: 'POST' })
  return res.json()
}

export async function fetchHistory(sessionId) {
  const res = await fetch(`${BASE_URL}/session/${sessionId}/history`)
  return res.json()
}

export async function fetchInfraStatus() {
  const res = await fetch(`${BASE_URL}/status`)
  return res.json()
}

export function openChatStream(sessionId, prompt, autoOnly = false) {
  const params = new URLSearchParams({
    sessionId,
    prompt,
    auto: String(autoOnly),
  })
  return new EventSource(`${BASE_URL}/chat/stream?${params}`)
}
