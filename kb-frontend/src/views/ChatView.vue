<template>
  <div class="chat-view">
    <section class="chat-main">
      <template v-if="hasActiveConversation">
        <header class="chat-header">
          <div class="chat-title">
            <h2>{{ currentConversationTitle }}</h2>
            <span>SSE 流式回答 · 最近 5 轮上下文 · 生成完成后返回来源片段</span>
          </div>

          <span class="source-count">来源 {{ latestSourceCount }}</span>
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
                <MagicStick />
              </el-icon>
            </div>
            <h3>问一个和企业知识库有关的问题</h3>
            <p>答案生成后会自动附带来源片段，方便核对。</p>
          </div>
        </div>

        <footer class="composer">
          <button class="composer-tool" type="button" title="文档管理" @click="$router.push('/docs')">
            <el-icon>
              <Files />
            </el-icon>
          </button>

          <el-input
            v-model="inputText"
            class="composer-input"
            :disabled="chatStore.isGenerating"
            maxlength="500"
            placeholder="请输入你的问题..."
            @keydown="handleInputKeydown"
          />

          <el-button
            class="composer-action"
            circle
            :disabled="!chatStore.isGenerating && !inputText.trim()"
            :type="chatStore.isGenerating ? 'danger' : 'primary'"
            @click="handleComposerAction"
          >
            <el-icon size="18">
              <CircleCloseFilled v-if="chatStore.isGenerating" />
              <Top v-else />
            </el-icon>
          </el-button>
        </footer>
      </template>

      <div v-else class="empty-state">
        <div class="empty-thread__mark">
          <el-icon>
            <MagicStick />
          </el-icon>
        </div>
        <h2>开始一次知识库问答</h2>
        <p>选择左侧已有会话，或新建对话后提问。</p>
        <el-button class="create-conv-btn" size="large" round @click="handleCreateConversation">
          <el-icon>
            <ChatLineRound />
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
import dayjs from 'dayjs';
import {
  ChatLineRound,
  CircleCloseFilled,
  Files,
  MagicStick,
  Plus,
  Top
} from '@element-plus/icons-vue';
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
  const conversationsPromise = chatStore.conversations.length
    ? Promise.resolve(chatStore.conversations)
    : chatStore.loadConversations();

  const [conversationsResult] = await Promise.allSettled([
    conversationsPromise,
    documentStore.loadDocList({ page: 1, size: 20 })
  ]);

  const conversations = conversationsResult.status === 'fulfilled'
    ? conversationsResult.value
    : [];

  if (chatStore.currentConvId) {
    if (!chatStore.messages.length) {
      await chatStore.selectConversation(chatStore.currentConvId);
    }

    scrollToBottom();
    return;
  }

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
  grid-template-columns: minmax(0, 1fr) 304px;
  overflow: hidden;
  background: #ffffff;
}

.chat-main,
.insight-panel {
  min-height: 0;
  overflow: hidden;
}

.chat-main {
  background: #ffffff;
}

.insight-panel {
  background: #f5f6f8;
}

.chat-main {
  display: grid;
  grid-template-rows: 72px minmax(0, 1fr) 92px;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 0 clamp(22px, 5vw, 64px);
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
  height: 32px;
  display: inline-flex;
  align-items: center;
  margin-left: auto;
  border-radius: 8px;
  background: #edf5ff;
  color: var(--color-primary);
  padding: 0 12px;
  font-size: 13px;
  font-weight: 800;
  white-space: nowrap;
}

.message-list {
  min-height: 0;
  overflow-y: auto;
  background: #ffffff;
  padding: 28px clamp(22px, 6vw, 78px) 36px;
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
  grid-row: 1 / -1;
  padding: 32px;
}

.create-conv-btn {
  margin-top: 22px;
  padding: 12px 28px;
  font-size: 15px;
  font-weight: 600;
  height: auto;
  box-shadow: 0 2px 8px rgba(23, 105, 255, 0.25);
  transition: box-shadow 0.2s, transform 0.2s;
}

.create-conv-btn:hover {
  box-shadow: 0 4px 16px rgba(23, 105, 255, 0.35);
  transform: translateY(-1px);
}

.composer {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) 44px;
  align-items: center;
  gap: 12px;
  background: #ffffff;
  padding: 16px 24px;
}

.composer-tool {
  width: 40px;
  height: 40px;
  display: inline-grid;
  place-items: center;
  border: 0;
  border-radius: 8px;
  background: #ffffff;
  color: #4b5563;
  cursor: pointer;
  font-size: 20px;
}

.composer-tool:hover {
  background: #ececec;
  color: #111827;
}

.composer-input :deep(.el-input__wrapper) {
  height: 52px;
  border-radius: 26px;
  background: #ffffff;
  box-shadow: 0 0 0 1px #e4e7ed inset;
  padding: 0 20px;
}

.composer-input :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #c8ccd4 inset;
}

.composer-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px var(--color-primary) inset;
  background: #ffffff;
}

.composer-action {
  width: 44px;
  height: 44px;
}

.insight-panel {
  display: grid;
  grid-template-rows: 56px auto auto minmax(0, 1fr);
  border-left: 1px solid #eeeeef;
}

.source-panel-header,
.recent-doc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.source-panel-header {
  border-bottom: 1px solid #eeeeef;
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
  background: #fbfcff;
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
  border-top: 1px solid #eeeeef;
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

.panel-empty {
  display: grid;
  place-items: center;
  min-height: 120px;
  color: var(--color-text-tertiary);
  font-size: 13px;
}

@media (max-width: 1180px) {
  .chat-view {
    grid-template-columns: minmax(0, 1fr);
  }

  .insight-panel {
    display: none;
  }
}

@media (max-width: 820px) {
  .chat-main {
    grid-template-rows: 72px minmax(0, 1fr) auto;
  }

  .composer {
    grid-template-columns: 40px minmax(0, 1fr) 44px;
  }

  .knowledge-chip,
  .composer-send-text {
    display: none;
  }
}
</style>
