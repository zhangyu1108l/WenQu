<template>
  <div class="chat-view">
    <aside class="conversation-panel">
      <header class="conversation-header">
        <button class="new-conversation-button" type="button" @click="handleCreateConversation">
          <el-icon>
            <Plus />
          </el-icon>
          <span>新建对话</span>
        </button>
      </header>

      <div class="conversation-list">
        <template v-for="group in conversationGroups" :key="group.label">
          <p v-if="group.items.length" class="conversation-group-label">{{ group.label }}</p>
          <button
            v-for="conversation in group.items"
            :key="getConversationId(conversation)"
            class="conversation-item"
            :class="{ active: getConversationId(conversation) === chatStore.currentConvId }"
            type="button"
            @click="handleSelectConversation(getConversationId(conversation))"
          >
            <span class="conversation-title">{{ getConversationTitle(conversation) }}</span>
            <time>{{ getConversationTime(conversation) }}</time>
            <el-popconfirm
              cancel-button-text="取消"
              confirm-button-text="删除"
              title="确认删除该对话？"
              width="180"
              @confirm="handleDeleteConversation(getConversationId(conversation))"
            >
              <template #reference>
                <span class="delete-trigger" title="删除" @click.stop>
                  <el-icon>
                    <Delete />
                  </el-icon>
                </span>
              </template>
            </el-popconfirm>
          </button>
        </template>

        <div v-if="!chatStore.conversations.length" class="conversation-empty">
          暂无对话
        </div>
      </div>
    </aside>

    <section class="chat-main">
      <template v-if="hasActiveConversation">
        <header class="chat-header">
          <div class="chat-title">
            <h2>{{ currentConversationTitle }}</h2>
            <span>SSE 流式回答 · 最近 5 轮上下文 · 生成完成后返回来源片段</span>
          </div>

          <span class="source-count">引用 {{ latestSourceCount }} 个来源</span>
        </header>

        <div ref="messageListRef" class="message-list" data-chat-message-list>
          <MessageBubble
            v-for="(message, index) in chatStore.messages"
            :key="message.id || `${index}-${message.role}`"
            :is-streaming="isStreamingMessage(index, message)"
            :message="message"
          />

          <div v-if="!chatStore.messages.length" class="empty-thread">
            <div class="empty-thread__mark">
              <el-icon>
                <ChatDotRound />
              </el-icon>
            </div>
            <h3>问一个和企业知识库有关的问题</h3>
            <p>答案生成后会自动附带来源片段，方便核对。</p>
          </div>
        </div>

        <footer class="composer">
          <el-input
            v-model="inputText"
            class="composer-input"
            :disabled="chatStore.isGenerating"
            maxlength="500"
            placeholder="输入问题，Enter 发送"
            @keydown="handleInputKeydown"
          />

          <button
            class="composer-send-text"
            type="button"
            :disabled="!chatStore.isGenerating && !inputText.trim()"
            @click="handleComposerAction"
          >
            {{ chatStore.isGenerating ? '停止' : '发送' }}
          </button>
          <el-button
            class="composer-action"
            circle
            :disabled="!chatStore.isGenerating && !inputText.trim()"
            :type="chatStore.isGenerating ? 'danger' : 'primary'"
            @click="handleComposerAction"
          >
            <el-icon>
              <CircleCloseFilled v-if="chatStore.isGenerating" />
              <Promotion v-else />
            </el-icon>
          </el-button>
        </footer>
      </template>

      <div v-else class="empty-state">
        <div class="empty-thread__mark">
          <el-icon>
            <ChatDotRound />
          </el-icon>
        </div>
        <h2>开始一次知识库问答</h2>
        <p>选择已有会话，或新建对话后提问。</p>
        <el-button type="primary" @click="handleCreateConversation">
          <el-icon>
            <Plus />
          </el-icon>
          <span>新建对话</span>
        </el-button>
      </div>
    </section>

    <aside class="insight-panel">
      <header class="source-panel-header">
        <h3>引用来源</h3>
        <el-button link type="primary" @click="$router.push('/docs')">查看文档</el-button>
      </header>

      <section class="knowledge-summary">
        <span>知识库文档总数</span>
        <strong>{{ docSummaryTotal }}</strong>
      </section>

      <div class="source-status-list">
        <button
          v-for="stat in rightPanelStats"
          :key="stat.label"
          class="source-status-item"
          type="button"
        >
          <span class="source-status-dot" :class="`is-${stat.tone}`" />
          <span>{{ stat.label }}</span>
          <strong>{{ stat.value }}</strong>
        </button>
      </div>

      <section class="recent-doc-section">
        <header class="recent-doc-header">
          <h3>最近文档</h3>
          <el-button link type="primary" @click="$router.push('/docs')">更多</el-button>
        </header>

        <div class="recent-upload-list">
          <article
            v-for="doc in recentDocuments"
            :key="`recent-${getDocId(doc)}`"
            class="recent-upload-item"
          >
            <div class="doc-file-icon" :class="`is-${getFileType(doc).toLowerCase()}`">
              {{ getFileType(doc).slice(0, 1) || 'D' }}
            </div>
            <div>
              <strong>{{ getDocTitle(doc) }}</strong>
              <span>{{ formatDocTime(getCreatedAt(doc)) }}</span>
            </div>
          </article>

          <div v-if="!recentDocuments.length" class="panel-empty">
            暂无上传记录
          </div>
        </div>
      </section>
    </aside>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import dayjs from 'dayjs';
import {
  ChatDotRound,
  CircleCloseFilled,
  Delete,
  Plus,
  Promotion
} from '@element-plus/icons-vue';
import * as chatApi from '../api/chat';
import MessageBubble from '../components/MessageBubble.vue';
import { useChat } from '../composables/useChat';
import { useChatStore } from '../stores/chat';
import { useDocumentStore } from '../stores/document';

const chatStore = useChatStore();
const documentStore = useDocumentStore();
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

const getConversationCreatedAt = (conversation) =>
  conversation?.createdAt ?? conversation?.created_at ?? conversation?.updatedAt ?? null;

const getConversationTime = (conversation) => {
  const date = dayjs(getConversationCreatedAt(conversation));
  return date.isValid() ? date.format('HH:mm') : '';
};

const conversationGroups = computed(() => {
  const groups = [
    { label: '今天', items: [] },
    { label: '昨天', items: [] },
    { label: '更早', items: [] }
  ];

  chatStore.conversations.forEach((conversation) => {
    const createdAt = dayjs(getConversationCreatedAt(conversation));

    if (createdAt.isValid() && createdAt.isSame(dayjs(), 'day')) {
      groups[0].items.push(conversation);
      return;
    }

    if (createdAt.isValid() && createdAt.isSame(dayjs().subtract(1, 'day'), 'day')) {
      groups[1].items.push(conversation);
      return;
    }

    groups[2].items.push(conversation);
  });

  return groups;
});

const currentConversation = computed(() =>
  chatStore.conversations.find((item) => getConversationId(item) === chatStore.currentConvId)
);

const currentConversationTitle = computed(() =>
  getConversationTitle(currentConversation.value)
);

const hasActiveConversation = computed(() => Boolean(chatStore.currentConvId));

const isUserRole = (role) => role === 0 || role === 'user';

const latestAssistantMessage = computed(() =>
  [...chatStore.messages].reverse().find((message) => !isUserRole(message?.role))
);

const latestSourceCount = computed(() => {
  const chunks = latestAssistantMessage.value?.sourceChunks ||
    latestAssistantMessage.value?.source_chunks ||
    [];

  if (Array.isArray(chunks)) {
    return chunks.length;
  }

  try {
    return JSON.parse(chunks).length;
  } catch {
    return 0;
  }
});

const getDocId = (row) => row?.id ?? row?.docId ?? row?.documentId;

const getDocTitle = (row) => row?.title || row?.name || row?.fileName || '未命名文档';

const getFileType = (row) => {
  const fileType = row?.fileType ?? row?.file_type ?? '';
  return fileType ? String(fileType).toUpperCase() : 'DOC';
};

const getDocStatus = (row) => String(row?.status || 'PENDING').toUpperCase();

const getCreatedAt = (row) => row?.createdAt ?? row?.created_at ?? row?.uploadTime ?? null;

const formatDocTime = (value) => {
  const date = dayjs(value);
  return date.isValid() ? date.format('MM-DD HH:mm') : '-';
};

const documents = computed(() => documentStore.docList || []);

const docSummaryTotal = computed(() => documents.value.length);

const rightPanelStats = computed(() => {
  const ready = documents.value.filter((doc) => getDocStatus(doc) === 'READY').length;
  const parsing = documents.value.filter((doc) => getDocStatus(doc) === 'PARSING').length;
  const embedding = documents.value.filter((doc) => getDocStatus(doc) === 'EMBEDDING').length;
  const pending = documents.value.filter((doc) => getDocStatus(doc) === 'PENDING').length;
  const failed = documents.value.filter((doc) => getDocStatus(doc) === 'FAILED').length;

  return [
    { label: 'READY', value: ready, tone: 'ready' },
    { label: 'PARSING', value: parsing + pending, tone: 'processing' },
    { label: 'EMBEDDING', value: embedding, tone: 'embedding' },
    { label: 'FAILED', value: failed, tone: 'failed' }
  ];
});

const recentDocuments = computed(() => documents.value.slice(0, 5));

const isStreamingMessage = (index, message) =>
  chatStore.isGenerating &&
  index === chatStore.messages.length - 1 &&
  (message?.role === 1 || message?.role === 'assistant');

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
  const nextConversationId = getConversationId(conversations[0]);

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
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    handleSend();
  }
};

onMounted(async () => {
  const [conversationsResult] = await Promise.allSettled([
    chatStore.loadConversations(),
    documentStore.loadDocList({ page: 1, size: 20 })
  ]);

  const conversations = conversationsResult.status === 'fulfilled'
    ? conversationsResult.value
    : [];
  const firstConversationId = getConversationId(conversations[0]);

  if (firstConversationId) {
    await chatStore.selectConversation(firstConversationId);
    scrollToBottom();
    return;
  }

  chatStore.currentConvId = null;
  chatStore.messages = [];
});

onBeforeUnmount(() => {
  if (chatStore.isGenerating) {
    stopGenerating();
  }
});
</script>

<style scoped>
.chat-view {
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-columns: 286px minmax(0, 1fr) 300px;
  gap: 8px;
  overflow: hidden;
  background: #f4f7fb;
  padding: 8px;
}

.conversation-panel,
.chat-main,
.insight-panel {
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #ffffff;
}

.conversation-panel {
  display: flex;
  flex-direction: column;
  padding: 16px;
}

.conversation-header {
  flex: 0 0 auto;
}

.new-conversation-button {
  width: 100%;
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 0;
  border-radius: 8px;
  background: var(--color-primary);
  color: #ffffff;
  cursor: pointer;
  font-size: 15px;
  font-weight: 800;
  box-shadow: 0 12px 24px rgba(23, 105, 255, 0.22);
}

.conversation-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-top: 18px;
}

.conversation-group-label {
  margin: 14px 0 10px;
  color: #475467;
  font-size: 14px;
  font-weight: 800;
}

.conversation-item {
  position: relative;
  width: 100%;
  height: 52px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #475467;
  cursor: pointer;
  padding: 0 12px;
  text-align: left;
}

.conversation-item:hover,
.conversation-item.active {
  background: #eef5ff;
  color: var(--color-primary);
}

.conversation-item.active {
  font-weight: 800;
}

.conversation-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-item time {
  color: #98a2b3;
  font-size: 12px;
}

.delete-trigger {
  position: absolute;
  right: 8px;
  top: 50%;
  width: 26px;
  height: 26px;
  display: none;
  place-items: center;
  border-radius: 6px;
  color: #98a2b3;
  transform: translateY(-50%);
}

.conversation-item:hover .delete-trigger {
  display: grid;
  background: #ffffff;
}

.conversation-empty,
.panel-empty {
  display: grid;
  place-items: center;
  min-height: 120px;
  color: var(--color-text-tertiary);
  font-size: 13px;
}

.chat-main {
  display: grid;
  grid-template-rows: 62px minmax(0, 1fr) 82px;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 18px;
  border-bottom: 1px solid var(--color-border);
  padding: 0 18px;
}

.chat-title {
  min-width: 0;
}

.chat-title h2 {
  margin: 0;
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: 20px;
  font-weight: 850;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-title span {
  display: block;
  margin-top: 7px;
  color: var(--color-text-tertiary);
  font-size: 13px;
}

.source-count {
  height: 34px;
  display: inline-flex;
  align-items: center;
  margin-left: auto;
  border-radius: 8px;
  background: #edf5ff;
  color: var(--color-primary);
  padding: 0 13px;
  font-size: 13px;
  font-weight: 800;
  white-space: nowrap;
}

.message-list {
  min-height: 0;
  overflow-y: auto;
  background: linear-gradient(180deg, #ffffff 0%, #fbfcff 100%);
  padding: 22px 28px 36px;
}

.empty-thread,
.empty-state {
  height: 100%;
  display: grid;
  place-items: center;
  align-content: center;
  color: var(--color-text-secondary);
  text-align: center;
}

.empty-thread__mark {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: 24px;
  margin: 0 auto 16px;
}

.empty-thread h3,
.empty-state h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 20px;
  font-weight: 850;
}

.empty-thread p,
.empty-state p {
  margin: 10px 0 0;
  color: var(--color-text-tertiary);
  font-size: 14px;
}

.empty-state {
  padding: 32px;
}

.empty-state .el-button {
  margin-top: 22px;
}

.composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto 44px;
  align-items: center;
  gap: 10px;
  border-top: 1px solid var(--color-border);
  background: #ffffff;
  padding: 14px 18px;
}

.composer-input :deep(.el-input__wrapper) {
  height: 52px;
  border-radius: 8px;
  background: #ffffff;
}

.composer-send-text {
  height: 36px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--color-primary);
  cursor: pointer;
  padding: 0 8px;
  font-size: 14px;
  font-weight: 800;
}

.composer-send-text:disabled {
  cursor: not-allowed;
  color: #b6c0cf;
}

.composer-action {
  width: 44px;
  height: 44px;
}

.insight-panel {
  display: grid;
  grid-template-rows: 52px auto auto minmax(0, 1fr);
}

.source-panel-header,
.recent-doc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.source-panel-header {
  border-bottom: 1px solid var(--color-border);
  padding: 0 16px;
}

.source-panel-header h3,
.recent-doc-header h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 17px;
  font-weight: 850;
}

.knowledge-summary {
  margin: 18px;
  border: 1px solid #dfe7f4;
  border-radius: 8px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  padding: 16px;
}

.knowledge-summary span {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.knowledge-summary strong {
  display: block;
  margin-top: 8px;
  color: var(--color-text-primary);
  font-size: 32px;
  font-weight: 850;
  line-height: 1;
}

.source-status-list {
  padding: 0 18px 14px;
}

.source-status-item {
  width: 100%;
  min-height: 34px;
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  border: 0;
  background: transparent;
  color: #475467;
  padding: 0;
  text-align: left;
}

.source-status-item strong {
  color: var(--color-text-primary);
  font-size: 14px;
}

.source-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-primary);
}

.source-status-dot.is-ready {
  background: #16b978;
}

.source-status-dot.is-processing,
.source-status-dot.is-embedding {
  background: #1769ff;
}

.source-status-dot.is-failed {
  background: #f05252;
}

.recent-doc-section {
  min-height: 0;
  overflow: hidden;
  border-top: 1px solid var(--color-border);
  padding: 16px 16px 0;
}

.recent-upload-list {
  min-height: 0;
  overflow-y: auto;
  padding-top: 12px;
}

.recent-upload-item {
  min-height: 54px;
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
}

.doc-file-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #4f8cff;
  color: #ffffff;
  font-size: 12px;
  font-weight: 900;
}

.doc-file-icon.is-pdf {
  background: #ff6b66;
}

.doc-file-icon.is-xlsx,
.doc-file-icon.is-xls {
  background: #33b176;
}

.recent-upload-item strong {
  display: block;
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-upload-item span {
  color: var(--color-text-tertiary);
  font-size: 12px;
}

@media (max-width: 1280px) {
  .chat-view {
    grid-template-columns: 260px minmax(0, 1fr);
  }

  .insight-panel {
    display: none;
  }
}

@media (max-width: 820px) {
  .chat-view {
    grid-template-columns: 1fr;
    overflow: auto;
  }

  .conversation-panel {
    max-height: 280px;
  }

  .chat-main {
    min-height: 680px;
  }
}
</style>
