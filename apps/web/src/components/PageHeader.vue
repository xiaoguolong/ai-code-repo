<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>();
</script>

<template>
  <div class="page-title">
    <div>
      <h2>{{ title }}</h2>
      <div v-if="subtitle" style="color: #8c8c8c; font-size: 13px">{{ subtitle }}</div>
    </div>
    <div>
      <slot name="extra" />
    </div>
  </div>
</template>
