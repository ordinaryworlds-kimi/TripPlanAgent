<script setup>
import InfraStatus from './InfraStatus.vue'
import PlanPanel from './PlanPanel.vue'

defineProps({
  sessionId: { type: String, required: true },
  infraStatus: { type: Object, default: null },
  plan: { type: Object, default: null },
})

const emit = defineEmits(['new-chat', 'copy', 'download'])
</script>

<template>
  <aside class="sidebar">
    <div class="panel-title">基础设施状态</div>
    <InfraStatus :status="infraStatus" />

    <div class="panel-title">执行计划</div>
    <PlanPanel :plan="plan" />

    <div class="btn-row">
      <button class="secondary" @click="emit('new-chat')">新建对话</button>
      <button class="secondary" @click="emit('copy')">复制结果</button>
      <button class="secondary" @click="emit('download')">下载结果</button>
    </div>
    <div class="session-id">Session: {{ sessionId }}</div>
  </aside>
</template>
