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

  const pollTask = (taskId, onDone, onFailed, onProgress) => {
    if (!taskId) {
      return () => {};
    }

    let requestPending = false;
    let timer = null;

    const tick = async () => {
      if (requestPending) {
        return;
      }

      requestPending = true;

      try {
        const result = await taskApi.getTaskStatus(taskId);
        const status = result?.status;

        onProgress?.(result);

        if (status === 'DONE') {
          clearTimer(timer);
          onDone?.(result);
          return;
        }

        if (status === 'FAILED') {
          clearTimer(timer);
          onFailed?.(result?.errorMsg || result?.error_msg || '任务执行失败', result);
        }
      } catch (error) {
        clearTimer(timer);
        onFailed?.(error?.message || '任务状态查询失败');
      } finally {
        requestPending = false;
      }
    };

    timer = window.setInterval(tick, 2000);
    timers.add(timer);
    tick();

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
