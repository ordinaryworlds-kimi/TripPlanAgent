<script setup>
defineProps({
  status: { type: Object, default: null },
})
</script>

<template>
  <div v-if="status" class="infra-list">
    <div class="status-item">
      <span>Nacos (8848)</span>
      <span class="dot" :class="status.nacos ? 'ok' : 'err'" />
    </div>
    <div class="status-item">
      <span>路线 Agent (8082)</span>
      <span class="dot" :class="status.routeMakingAgent ? 'ok' : 'err'" />
    </div>
    <div class="status-item">
      <span>行程 Agent (8085)</span>
      <span class="dot" :class="status.tripPlannerAgent ? 'ok' : 'err'" />
    </div>
    <div class="status-item">
      <span>主管 Agent (8081)</span>
      <span class="dot ok" />
    </div>
  </div>
  <div v-else class="status-item">
    <span>状态获取失败</span>
    <span class="dot err" />
  </div>
</template>
