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
    type: Number,
    required: true
  },
  docId: {
    type: Number,
    default: null
  }
});

const emit = defineEmits(['done', 'failed']);

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
  if (currentStatus.value === 'PENDING') {
    return '等待处理...';
  }

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
    clearInterval(timerId);
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

    currentStatus.value = status;

    if (status === 'PENDING') {
      percentage.value = 0;
      errorMsg.value = '';
      return;
    }

    if (status === 'RUNNING') {
      percentage.value = Math.max(0, Math.min(Number(result?.progress) || 0, 100));
      errorMsg.value = '';
      return;
    }

    if (status === 'DONE') {
      percentage.value = 100;
      errorMsg.value = '';
      stopPolling();
      // emit('done') 让父组件在任务完成后刷新文档列表，把文档状态更新为可用。
      emit('done', {
        taskId: props.taskId,
        docId: props.docId
      });
      return;
    }

    if (status === 'FAILED') {
      errorMsg.value = result?.errorMsg || '处理失败';
      stopPolling();
      emit('failed', {
        taskId: props.taskId,
        docId: props.docId,
        errorMsg: errorMsg.value
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
  // 轮询和 onUnmounted 必须配合使用：组件销毁后定时器若继续运行，会造成内存泄漏和无意义的接口请求。
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
