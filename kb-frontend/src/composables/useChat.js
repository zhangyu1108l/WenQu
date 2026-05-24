import { nextTick, onUnmounted, shallowRef } from 'vue';
import { ElMessage } from 'element-plus';
import * as chatApi from '../api/chat';
import { useChatStore } from '../stores/chat';
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

    const url = chatApi.buildAskUrl(chatStore.currentConvId, normalizedQuestion);
    const client = new SseClient(url, {
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
      onError: (message) => {
        chatStore.failStreaming(message || 'SSE 连接异常');
        ElMessage.error(message || 'SSE 连接异常');
        client.close();
        sseClient.value = null;
      }
    });

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
