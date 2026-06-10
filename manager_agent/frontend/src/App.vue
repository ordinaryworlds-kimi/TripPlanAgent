<script setup>
import AppSidebar from './components/AppSidebar.vue'
import ChatPanel from './components/ChatPanel.vue'
import ChatInput from './components/ChatInput.vue'
import { useChat } from './composables/useChat'

const {
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
} = useChat()
</script>

<template>
  <div class="layout">
    <AppSidebar
      :session-id="sessionId"
      :infra-status="infraStatus"
      :plan="plan"
      @new-chat="newChat"
      @copy="copyResult"
      @download="downloadResult"
    />

    <section class="main">
      <div class="header">
        <h1>AI 自驾游规划助手</h1>
        <p>与主管 Agent 对话，自动编排路线与行程规划</p>
      </div>

      <ChatPanel :messages="messages" :render-markdown="renderMarkdown" />

      <ChatInput
        v-model="inputText"
        :sending="sending"
        :send-button-text="sendButtonText"
        @send="sendMessage"
        @set-prompt="setPrompt"
      />
    </section>
  </div>
</template>
