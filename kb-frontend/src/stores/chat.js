import { defineStore } from 'pinia';
import * as chatApi from '../api/chat';

const toList = (data) => {
  if (Array.isArray(data)) {
    return data;
  }

  return data?.records || data?.list || data?.items || [];
};

const getConversationId = (conversation) =>
  conversation?.id ?? conversation?.conversationId ?? conversation?.convId;

export const useChatStore = defineStore('chat', {
  state: () => ({
    conversations: [],
    currentConvId: null,
    messages: [],
    isGenerating: false,
    // streamingContent 独立保存正在生成的内容，UI 可优先绑定它，避免频繁替换 messages 数组引发大量重渲染。
    streamingContent: '',
    sourceChunks: []
  }),

  actions: {
    async loadConversations() {
      const data = await chatApi.getConversationList();
      this.conversations = toList(data);
      return this.conversations;
    },

    async selectConversation(convId) {
      this.currentConvId = convId;

      const data = await chatApi.getMessageList(convId);
      this.messages = toList(data);
      this.streamingContent = '';
      this.sourceChunks = [];

      return this.messages;
    },

    async createConversation() {
      const conversation = await chatApi.createConversation();
      const conversationId = getConversationId(conversation);

      this.conversations = [
        conversation,
        ...this.conversations.filter((item) => getConversationId(item) !== conversationId)
      ];

      await this.selectConversation(conversationId);
      return conversation;
    },

    startStreaming(question) {
      this.isGenerating = true;
      this.streamingContent = '';
      this.sourceChunks = [];

      this.messages.push({
        role: 0,
        content: question,
        sourceChunks: []
      });

      this.messages.push({
        role: 1,
        content: '',
        sourceChunks: []
      });
    },

    appendToken(token) {
      this.streamingContent += token;

      const lastMessage = this.messages[this.messages.length - 1];
      if (lastMessage && lastMessage.role === 1) {
        // appendToken 时同时更新 streamingContent 和末尾 assistant 占位消息，保证 UI 实时渲染且消息历史保持一致。
        lastMessage.content = this.streamingContent;
      }
    },

    finishStreaming(chunks) {
      this.isGenerating = false;
      this.sourceChunks = Array.isArray(chunks) ? chunks : [];

      const lastMessage = this.messages[this.messages.length - 1];
      if (lastMessage && lastMessage.role === 1) {
        lastMessage.sourceChunks = this.sourceChunks;
      }
    }
  }
});
