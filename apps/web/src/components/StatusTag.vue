<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{ status?: string | null }>();

interface TagStyle {
  color: string;
  text: string;
}

const MAP: Record<string, TagStyle> = {
  COMPLETED: { color: 'success', text: '完成' },
  RUNNING: { color: 'processing', text: '运行中' },
  FAILED: { color: 'error', text: '失败' },
  PENDING_APPROVAL: { color: 'warning', text: '待审' },
  REJECTED: { color: 'default', text: '已拒绝' },
  PENDING: { color: 'warning', text: '待处理' },
  APPROVED: { color: 'success', text: '已批准' },
  CANCELLED: { color: 'default', text: '已取消' },
  ACTIVE: { color: 'success', text: '正常' },
  UNKNOWN: { color: 'default', text: '待对账' },
  MISSING: { color: 'error', text: '企业侧已删除' },
  HIGH: { color: 'red', text: '高' },
  MEDIUM: { color: 'orange', text: '中' },
  LOW: { color: 'green', text: '低' }
};

const style = computed<TagStyle>(() => {
  const key = props.status ?? '';
  return MAP[key] ?? { color: 'default', text: key || '-' };
});
</script>

<template>
  <a-tag :color="style.color">{{ style.text }}</a-tag>
</template>
