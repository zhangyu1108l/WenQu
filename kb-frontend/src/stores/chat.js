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
        query: question,
        sourceChunks: []
      });
    },

    appendToken(token) {
      this.streamingContent += token;

      const lastMessage = this.messages[this.messages.length - 1];
      if (lastMessage && (lastMessage.role === 1 || lastMessage.role === 'assistant')) {
        lastMessage.content = this.streamingContent;
      }
    },

    finishStreaming(chunks) {
      this.isGenerating = false;
      this.sourceChunks = Array.isArray(chunks) ? chunks : [];

      const lastMessage = this.messages[this.messages.length - 1];
      if (lastMessage && (lastMessage.role === 1 || lastMessage.role === 'assistant')) {
        lastMessage.sourceChunks = this.sourceChunks;
        lastMessage.source_chunks = this.sourceChunks;
      }
    },

    failStreaming(message = '回答生成失败') {
      this.isGenerating = false;

      const lastMessage = this.messages[this.messages.length - 1];
      if (lastMessage && (lastMessage.role === 1 || lastMessage.role === 'assistant') && !lastMessage.content) {
        lastMessage.content = message;
      }
    }
  }
});
