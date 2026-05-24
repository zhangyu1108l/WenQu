import { nextTick, onUnmounted, shallowRef } from 'vue';
import { ElMessage } from 'element-plus';
import * as chatApi from '../api/chat';
import { useChatStore } from '../stores/chat';
import SseClient from '../utils/sse';

export function useChat() {
  const chatStore = useChatStore();
  const sseClient = shallowRef(null);
  const messageListRef = shallowRef(null);

  // Composable 模式将对话逻辑从组件中抽离，便于在不同页面复用，也更容易单独测试 SSE、校验和滚动行为。
  const sendMessage = (question) => {
    const normalizedQuestion = String(question ?? '').trim();

    if (!normalizedQuestion) {
      ElMessage.warning('请输入问题');
      return false;
    }

    if (normalizedQuestion.length > 500) {
      ElMessage.warning('问题不能超过 500 字');
      return false;
    }

    if (chatStore.isGenerating) {
      ElMessage.warning('当前回答仍在生成中');
      return false;
    }

    if (!chatStore.currentConvId) {
      ElMessage.warning('请先新建对话');
      return false;
    }

    chatStore.startStreaming(normalizedQuestion);
    scrollToBottom();

    // buildAskUrl 不走 Axios，因为 SSE 使用浏览器原生 EventSource 长连接，不是普通 HTTP 请求。
    const url = chatApi.buildAskUrl(chatStore.currentConvId, normalizedQuestion);
    const client = new SseClient(url, {
      onToken: (token) => {
        chatStore.appendToken(token);
        scrollToBottom();
      },
      onDone: (chunks) => {
        chatStore.finishStreaming(chunks);
        scrollToBottom();
        client.close();
        sseClient.value = null;
      },
      onError: (err) => {
        chatStore.isGenerating = false;
        ElMessage.error(err || '连接异常');
        client.close();
        sseClient.value = null;
      }
    });

    sseClient.value = client;
    client.connect();
    return true;
  };

  const stopGenerating = () => {
    // 用户可以手动中止生成，关闭 SSE 后不再接收后续 token。
    sseClient.value?.close();
    sseClient.value = null;
    chatStore.isGenerating = false;
  };

  const scrollToBottom = async () => {
    // 每次 appendToken 后需要自动滚动到底部；nextTick 保证 DOM 更新完成后再计算 scrollHeight。
    await nextTick();
    const container =
      messageListRef.value || document.querySelector('[data-chat-message-list]');

    if (container) {
      container.scrollTop = container.scrollHeight;
    }
  };

  onUnmounted(() => {
    // 组件卸载时必须关闭 SSE 连接，避免页面离开后长连接继续占用资源。
    sseClient.value?.close();
  });

  return {
    messageListRef,
    sseClient,
    sendMessage,
    stopGenerating,
    scrollToBottom
  };
}

export default useChat;
