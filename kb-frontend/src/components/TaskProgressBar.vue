<template>
  <div class="task-progress-bar">
    <el-progress
      :percentage="percentage"
      :status="progressStatus"
      :stroke-width="8"
    />
    <div
      class="task-progress-bar__text"
      :class="{ 'is-error': currentStatus === 'FAILED' }"
    >
      {{ statusText }}
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import * as taskApi from '../api/task';

const props = defineProps({
  taskId: {
    type: [Number, String],
    required: true
  },
  docId: {
    type: [Number, String],
    default: null
  }
});

const emit = defineEmits(['done', 'failed', 'progress']);

const percentage = ref(0);
const currentStatus = ref('PENDING');
const errorMsg = ref('');
let timerId = null;
let requestPending = false;

const progressStatus = computed(() => {
  if (currentStatus.value === 'DONE') {
    return 'success';
  }

  if (currentStatus.value === 'FAILED') {
    return 'exception';
  }

  return undefined;
});

const statusText = computed(() => {
  if (currentStatus.value === 'RUNNING') {
    return '处理中...';
  }

  if (currentStatus.value === 'DONE') {
    return '处理完成';
  }

  if (currentStatus.value === 'FAILED') {
    return errorMsg.value || '处理失败';
  }

  return '等待处理...';
});

const stopPolling = () => {
  if (timerId) {
    window.clearInterval(timerId);
    timerId = null;
  }
};

const pollTaskStatus = async () => {
  if (requestPending) {
    return;
  }

  requestPending = true;

  try {
    const result = await taskApi.getTaskStatus(props.taskId);
    const status = result?.status || 'PENDING';
    const progress = Math.max(0, Math.min(Number(result?.progress) || 0, 100));

    currentStatus.value = status;
    percentage.value = status === 'DONE' ? 100 : progress;
    errorMsg.value = result?.errorMsg || result?.error_msg || '';
    emit('progress', {
      taskId: props.taskId,
      docId: props.docId,
      status,
      progress: percentage.value
    });

    if (status === 'DONE') {
      stopPolling();
      emit('done', {
        taskId: props.taskId,
        docId: props.docId
      });
      return;
    }

    if (status === 'FAILED') {
      stopPolling();
      emit('failed', {
        taskId: props.taskId,
        docId: props.docId,
        errorMsg: errorMsg.value || '处理失败'
      });
    }
  } catch (error) {
    errorMsg.value = error?.message || '任务状态获取失败';
  } finally {
    requestPending = false;
  }
};

onMounted(() => {
  pollTaskStatus();
  timerId = window.setInterval(pollTaskStatus, 2000);
});

onUnmounted(() => {
  stopPolling();
});
</script>

<style scoped>
.task-progress-bar {
  width: 100%;
}

.task-progress-bar__text {
  margin-top: 6px;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.4;
}

.task-progress-bar__text.is-error {
  color: var(--el-color-danger);
}
</style>
