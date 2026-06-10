<script setup>
defineProps({
  modelValue: { type: String, default: '' },
  sending: { type: Boolean, default: false },
  sendButtonText: { type: String, default: '发送' },
})

const emit = defineEmits(['update:modelValue', 'send', 'set-prompt'])

const examples = [
  { label: '深圳→惠州 3日游', text: '帮我制定深圳到惠州3日游自驾游计划，包含吃住行、天气、酒店和美食' },
  { label: '广州→桂林 5日游', text: '从广州到桂林5日自驾游，偏好自然风光' },
  { label: '确认计划', text: '好的，请按此计划执行' },
]

function onKeydown(event) {
  // 中文输入法选词确认时也会触发 Enter，此时不应发送
  if (event.isComposing || event.keyCode === 229) {
    return
  }
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    emit('send')
  }
}
</script>

<template>
  <div class="examples">
    <span
      v-for="item in examples"
      :key="item.label"
      class="tag"
      @click="emit('set-prompt', item.text)"
    >
      {{ item.label }}
    </span>
  </div>
  <div class="input-area">
    <textarea
      :value="modelValue"
      placeholder="请输入旅游需求或确认意见（如：好的 / 确认）"
      @input="emit('update:modelValue', $event.target.value)"
      @keydown="onKeydown"
    />
    <button class="primary" :disabled="sending" @click="emit('send')">
      {{ sendButtonText }}
    </button>
  </div>
</template>
