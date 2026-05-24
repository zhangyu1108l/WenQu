<template>
  <el-tag
    :type="statusConfig.type"
    effect="light"
    round
  >
    {{ statusConfig.label }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  status: {
    type: String,
    required: true
  }
});

// 文档状态对应 async_task 的处理阶段：等待、解析、向量化、完成可用或失败。
const statusMap = {
  PENDING: {
    type: 'info',
    label: '等待处理'
  },
  PARSING: {
    type: 'warning',
    label: '解析中'
  },
  EMBEDDING: {
    type: 'warning',
    label: '向量化中'
  },
  READY: {
    type: 'success',
    label: '可用'
  },
  FAILED: {
    type: 'danger',
    label: '处理失败'
  }
};

const statusConfig = computed(() => statusMap[props.status] || statusMap.PENDING);
</script>
