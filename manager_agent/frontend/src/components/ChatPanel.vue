<script setup>
import { ref, watch, nextTick } from 'vue'
import ChatMessage from './ChatMessage.vue'

const props = defineProps({
  messages: { type: Array, required: true },
  renderMarkdown: { type: Function, required: true },
})

const containerRef = ref(null)

watch(
  () => props.messages,
  async () => {
    await nextTick()
    if (containerRef.value) {
      containerRef.value.scrollTop = containerRef.value.scrollHeight
    }
  },
  { deep: true },
)
</script>

<template>
  <div ref="containerRef" class="chat-container">
    <ChatMessage
      v-for="msg in messages"
      :key="msg.id"
      :message="msg"
      :render-markdown="renderMarkdown"
    />
  </div>
</template>
