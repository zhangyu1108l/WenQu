<template>
  <div class="chat-view">
    <aside class="conversation-panel">
      <div class="conversation-header">
        <el-button class="new-chat-button" type="primary" @click="handleCreateConversation">
          <el-icon>
            <Plus />
          </el-icon>
          <span>新建对话</span>
        </el-button>
      </div>

      <div class="conversation-list">
        <button
          v-for="conversation in chatStore.conversations"
          :key="getConversationId(conversation)"
          class="conversation-item"
          :class="{ active: getConversationId(conversation) === chatStore.currentConvId }"
          type="button"
          @click="handleSelectConversation(getConversationId(conversation))"
        >
          <span class="conversation-title">{{ getConversationTitle(conversation) }}</span>
          <el-popconfirm
            title="确认删除该对话？"
            confirm-button-text="删除"
            cancel-button-text="取消"
            width="180"
            @confirm="handleDeleteConversation(getConversationId(conversation))"
          >
            <template #reference>
              <span class="delete-trigger" @click.stop>
                <el-icon>
                  <Delete />
                </el-icon>
              </span>
            </template>
          </el-popconfirm>
        </button>
      </div>
    </aside>

    <section class="chat-main">
      <template v-if="hasActiveConversation">
        <header class="chat-header">
          <h2>{{ currentConversationTitle }}</h2>
        </header>

        <div
          ref="messageListRef"
          class="message-list"
          data-chat-message-list
        >
          <MessageBubble
            v-for="(message, index) in chatStore.messages"
            :key="message.id || `${index}-${message.role}`"
            :message="message"
            :is-streaming="isStreamingMessage(index, message)"
          />
        </div>

        <footer class="composer">
          <el-input
            v-model="inputText"
            class="composer-input"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 6 }"
            resize="none"
            maxlength="500"
            show-word-limit
            placeholder="输入你的问题"
            :disabled="chatStore.isGenerating"
            @keydown="handleInputKeydown"
          />
          <el-button
            class="composer-action"
            :type="chatStore.isGenerating ? 'danger' : 'primary'"
            :disabled="!chatStore.isGenerating && !inputText.trim()"
            @click="handleComposerAction"
          >
            <el-icon>
              <CircleCloseFilled v-if="chatStore.isGenerating" />
              <Promotion v-else />
            </el-icon>
            <span>{{ chatStore.isGenerating ? '停止' : '发送' }}</span>
          </el-button>
        </footer>
      </template>

      <!-- 空状态展示逻辑：当没有任何会话或尚未选中会话时，显示居中欢迎引导，提示用户先新建对话。 -->
      <div v-else class="empty-state">
        <h2>👋 欢迎使用问渠</h2>
        <p>上传文档后，即可开始智能问答</p>
        <el-button type="primary" @click="handleCreateConversation">
          新建对话 →
        </el-button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { CircleCloseFilled, Delete, Plus, Promotion } from '@element-plus/icons-vue';
import * as chatApi from '../api/chat';
import MessageBubble from '../components/MessageBubble.vue';
import { useChat } from '../composables/useChat';
import { useChatStore } from '../stores/chat';

const chatStore = useChatStore();
const inputText = ref('');

const {
  messageListRef,
  sendMessage,
  stopGenerating,
  scrollToBottom
} = useChat();

const getConversationId = (conversation) =>
  conversation?.id ?? conversation?.conversationId ?? conversation?.convId;

const getConversationTitle = (conversation) =>
  conversation?.title || conversation?.name || '新对话';

const currentConversation = computed(() =>
  chatStore.conversations.find((item) => getConversationId(item) === chatStore.currentConvId)
);

const currentConversationTitle = computed(() =>
  getConversationTitle(currentConversation.value)
);

const hasActiveConversation = computed(() => Boolean(chatStore.currentConvId));

const isStreamingMessage = (index, message) =>
  chatStore.isGenerating && index === chatStore.messages.length - 1 && (message?.role === 1 || message?.role === 'assistant');

const handleCreateConversation = async () => {
  if (chatStore.isGenerating) {
    stopGenerating();
  }

  await chatStore.createConversation();
  inputText.value = '';
  scrollToBottom();
};

const handleSelectConversation = async (convId) => {
  if (!convId || convId === chatStore.currentConvId) {
    return;
  }

  if (chatStore.isGenerating) {
    stopGenerating();
  }

  await chatStore.selectConversation(convId);
  inputText.value = '';
  scrollToBottom();
};

const handleDeleteConversation = async (convId) => {
  if (!convId) {
    return;
  }

  if (chatStore.isGenerating && convId === chatStore.currentConvId) {
    stopGenerating();
  }

  await chatApi.deleteConversation(convId);
  const conversations = await chatStore.loadConversations();
  const nextConversation = conversations[0];
  const nextConversationId = getConversationId(nextConversation);

  if (nextConversationId) {
    await chatStore.selectConversation(nextConversationId);
  } else {
    chatStore.currentConvId = null;
    chatStore.messages = [];
  }

  ElMessage.success('对话已删除');
  scrollToBottom();
};

const handleSend = () => {
  const sent = sendMessage(inputText.value);

  if (sent) {
    inputText.value = '';
  }
};

const handleComposerAction = () => {
  if (chatStore.isGenerating) {
    stopGenerating();
    return;
  }

  handleSend();
};

const handleInputKeydown = (event) => {
  if (event.key !== 'Enter') {
    return;
  }

  // Enter 发送、Shift+Enter 换行通过 keydown 事件判断 event.shiftKey；发送时阻止默认行为，避免 textarea 插入换行。
  if (!event.shiftKey) {
    event.preventDefault();
    handleSend();
  }
};

onMounted(async () => {
  const conversations = await chatStore.loadConversations();
  const firstConversationId = getConversationId(conversations[0]);

  if (firstConversationId) {
    await chatStore.selectConversation(firstConversationId);
    scrollToBottom();
    return;
  }

  chatStore.currentConvId = null;
  chatStore.messages = [];
});
</script>

<style scoped>
.chat-view {
  min-height: 100vh;
  display: flex;
  background: var(--color-bg-primary);
}

.conversation-panel {
  width: 260px;
  min-height: 100vh;
  display: flex;
  flex: 0 0 260px;
  flex-direction: column;
  border-right: 1px solid var(--color-border);
  background: var(--color-bg-secondary);
}

.conversation-header {
  padding: 16px 14px 12px;
}

.new-chat-button {
  width: 100%;
  justify-content: center;
}

.conversation-list {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding: 4px 8px 14px;
}

.conversation-item {
  width: 100%;
  height: 42px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 0;
  border-radius: 8px;
  padding: 0 8px 0 12px;
  color: var(--color-text-primary);
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background-color 0.16s ease, color 0.16s ease;
}

.conversation-item:hover,
.conversation-item.active {
  background: rgba(16, 163, 127, 0.1);
}

.conversation-item.active {
  color: var(--color-primary);
  font-weight: 600;
}

.conversation-title {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.delete-trigger {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 6px;
  color: var(--color-text-secondary);
  opacity: 0;
  transition: color 0.16s ease, background-color 0.16s ease, opacity 0.16s ease;
}

.conversation-item:hover .delete-trigger,
.conversation-item.active .delete-trigger {
  opacity: 1;
}

.delete-trigger:hover {
  color: #f56c6c;
  background: rgba(245, 108, 108, 0.12);
}

.chat-main {
  min-width: 0;
  min-height: 100vh;
  display: flex;
  flex: 1;
  flex-direction: column;
  background: #ffffff;
}

.chat-header {
  height: 64px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--color-border);
  padding: 0 28px;
}

.chat-header h2 {
  overflow: hidden;
  margin: 0;
  color: var(--color-text-primary);
  font-size: 18px;
  font-weight: 600;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-list {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px 28px;
}

.composer {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  border-top: 1px solid var(--color-border);
  padding: 16px 28px 20px;
  background: #ffffff;
}

.composer-input {
  min-width: 0;
  flex: 1;
}

.composer :deep(.el-textarea__inner) {
  min-height: 44px !important;
  border-radius: 8px;
  padding: 11px 12px;
  line-height: 1.6;
  box-shadow: 0 0 0 1px var(--color-border) inset;
}

.composer-action {
  min-width: 92px;
  height: 44px;
  flex: 0 0 auto;
}

.empty-state {
  min-height: 100vh;
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12px;
  padding: 28px;
  text-align: center;
}

.empty-state h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 28px;
  font-weight: 700;
  line-height: 1.25;
}

.empty-state p {
  margin: 0 0 8px;
  color: var(--color-text-secondary);
  font-size: 15px;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .chat-view {
    min-height: 100vh;
  }

  .conversation-panel {
    width: 220px;
    flex-basis: 220px;
  }

  .message-list {
    padding: 20px 18px;
  }

  .chat-header,
  .composer {
    padding-left: 18px;
    padding-right: 18px;
  }
}

@media (max-width: 720px) {
  .chat-view {
    flex-direction: column;
  }

  .conversation-panel {
    width: 100%;
    min-height: auto;
    max-height: 220px;
    flex: 0 0 auto;
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .conversation-list {
    display: flex;
    gap: 6px;
    overflow-x: auto;
    overflow-y: hidden;
    padding: 0 10px 12px;
  }

  .conversation-item {
    width: 180px;
    flex: 0 0 180px;
  }

  .chat-main,
  .empty-state {
    min-height: calc(100vh - 220px);
  }

  .composer {
    align-items: stretch;
    flex-direction: column;
  }

  .composer-action {
    width: 100%;
  }
}
</style>
