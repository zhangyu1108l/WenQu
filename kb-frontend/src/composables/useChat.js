import { nextTick, onUnmounted, shallowRef } from 'vue';
import { ElMessage } from 'element-plus';
import * as chatApi from '../api/chat';
import { useChatStore } from '../stores/chat';
import { refreshAccessToken } from '../utils/request';
import SseClient from '../utils/sse';

const normalizeDonePayload = (payload) => {
  if (Array.isArray(payload)) {
    return payload;
  }

  return payload?.sourceChunks || payload?.source_chunks || payload?.chunks || [];
};

export function useChat() {
  const chatStore = useChatStore();
  const sseClient = shallowRef(null);
  const messageListRef = shallowRef(null);

  const scrollToBottom = async () => {
    await nextTick();
    const container =
      messageListRef.value || document.querySelector('[data-chat-message-list]');

    if (container) {
      container.scrollTop = container.scrollHeight;
    }
  };

  const sendMessage = (question) => {
    const normalizedQuestion = String(question ?? '').trim();

    if (!normalizedQuestion) {
      ElMessage.warning('请输入问题');
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

    const url = chatApi.buildAskUrl(chatStore.currentConvId);

    const createClient = (hasRetried = false) => {
      const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
      const client = new SseClient(url, {
        method: 'POST',
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ question: normalizedQuestion }),
        onToken: (token) => {
          chatStore.appendToken(token);
          scrollToBottom();
        },
        onDone: (payload) => {
          chatStore.finishStreaming(normalizeDonePayload(payload));
          scrollToBottom();
          client.close();
          sseClient.value = null;
        },
        onError: async (message, error) => {
          client.close();

          if (error?.status === 401 && !hasRetried) {
            try {
              await refreshAccessToken();
              const retryClient = createClient(true);
              sseClient.value = retryClient;
              retryClient.connect();
              return;
            } catch {
              // 继续走常规 SSE 失败处理流程。
            }
          }

          chatStore.failStreaming(message || 'SSE 连接异常');
          ElMessage.error(message || 'SSE 连接异常');
          sseClient.value = null;
        }
      });

      return client;
    };

    const client = createClient();
    sseClient.value = client;
    client.connect();
    return true;
  };

  const stopGenerating = () => {
    sseClient.value?.close();
    sseClient.value = null;
    chatStore.isGenerating = false;
  };

  onUnmounted(() => {
    sseClient.value?.close();
    sseClient.value = null;
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
