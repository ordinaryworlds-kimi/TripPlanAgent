<script setup>
defineProps({
  plan: { type: Object, default: null },
})
</script>

<template>
  <div class="plan-box" :class="{ empty: !plan?.state }">
    <template v-if="plan?.state">
      <strong>{{ plan.name || '当前计划' }}</strong>
      <div class="subtask-state">状态：{{ plan.state }}</div>
      <div v-if="plan.description">{{ plan.description }}</div>
      <div
        v-for="st in plan.subtasks || []"
        :key="st.index"
        class="subtask"
        :class="(st.state || '').toLowerCase()"
      >
        <div><strong>{{ st.index + 1 }}. {{ st.name }}</strong></div>
        <div class="subtask-state">{{ st.state }}</div>
        <div v-if="st.description">{{ st.description }}</div>
      </div>
    </template>
    <template v-else>暂无计划</template>
  </div>
</template>
