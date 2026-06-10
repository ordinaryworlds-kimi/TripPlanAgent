<script setup>
defineProps({
  message: { type: Object, required: true },
  renderMarkdown: { type: Function, required: true },
})
</script>

<template>
  <div class="message" :class="message.role">
    <div class="bubble" :class="message.type">
      <div v-if="message.label" class="label">{{ message.label }}</div>
      <div
        v-if="message.role === 'agent'"
        class="body"
        v-html="renderMarkdown(message.content)"
      />
      <div v-else class="body">{{ message.content }}</div>
    </div>
  </div>
</template>
