import { onUnmounted } from 'vue';
import * as taskApi from '../api/task';

export function useTaskPoller() {
  const timers = new Set();

  const clearTimer = (timer) => {
    if (!timer) {
      return;
    }

    clearInterval(timer);
    timers.delete(timer);
  };

  const pollTask = (taskId, onDone, onFailed) => {
    if (!taskId) {
      return () => {};
    }

    const timer = setInterval(async () => {
      try {
        const result = await taskApi.getTaskStatus(taskId);
        const status = result?.status;

        if (status === 'DONE') {
          clearTimer(timer);
          onDone?.();
          return;
        }

        if (status === 'FAILED') {
          clearTimer(timer);
          onFailed?.(result?.errorMsg || result?.error_msg || '任务执行失败');
        }
      } catch (error) {
        clearTimer(timer);
        onFailed?.(error?.message || '任务状态查询失败');
      }
    }, 2000);

    timers.add(timer);

    // 返回清理函数，供组件卸载时主动停止轮询，避免定时器在页面离开后继续请求接口。
    return () => clearTimer(timer);
  };

  onUnmounted(() => {
    timers.forEach((timer) => clearInterval(timer));
    timers.clear();
  });

  return {
    pollTask
  };
}

export default useTaskPoller;
