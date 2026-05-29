<template>
  <div
    class="chat-view"
    :class="{
      'is-conversation-collapsed': isConversationPanelCollapsed,
      'is-insight-collapsed': isInsightPanelCollapsed
    }"
  >
    <aside v-if="isConversationPanelCollapsed" class="conversation-rail">
      <button class="rail-button" type="button" title="展开对话窗口" @click="isConversationPanelCollapsed = false">
        <el-icon>
          <Expand />
        </el-icon>
        <span>对话</span>
      </button>
    </aside>

    <aside v-else class="conversation-panel">
      <header class="conversation-header">
        <div>
          <h2>对话</h2>
          <span>{{ chatStore.conversations.length }} 个会话</span>
        </div>
        <div class="header-actions">
          <button class="icon-action" type="button" title="收起对话窗口" @click="isConversationPanelCollapsed = true">
            <el-icon>
              <Fold />
            </el-icon>
          </button>
          <button class="icon-action" type="button" title="新建对话" @click="handleCreateConversation">
            <el-icon>
              <EditPen />
            </el-icon>
          </button>
        </div>
      </header>

      <el-input
        v-model.trim="conversationKeyword"
        class="conversation-search"
        clearable
        placeholder="搜索对话"
        :prefix-icon="Search"
      />

      <div class="conversation-list">
        <p class="conversation-group-label">最近</p>
        <button
          v-for="conversation in visibleConversations"
          :key="getConversationId(conversation)"
          class="conversation-item"
          :class="{ active: getConversationId(conversation) === chatStore.currentConvId }"
          type="button"
          @click="handleSelectConversation(getConversationId(conversation))"
          @contextmenu.prevent.stop="openConversationContextMenu($event, conversation)"
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

        <div v-if="!visibleConversations.length" class="conversation-empty">
          暂无匹配对话
        </div>
      </div>
    </aside>

    <section class="chat-main">
      <template v-if="hasActiveConversation">
        <header class="chat-header">
          <div class="chat-title">
            <h2>{{ currentConversationTitle }}</h2>
            <span>RAG 检索增强 · 最近 5 轮上下文</span>
          </div>

          <div class="chat-tools">
            <span class="source-count">
              引用 {{ latestSourceCount }} 个来源
            </span>
          </div>
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
            <p>答案会在生成后附带来源片段，便于核对。</p>
          </div>
        </div>

        <footer class="composer">
          <el-input
            v-model="inputText"
            class="composer-input"
            :autosize="{ minRows: 2, maxRows: 6 }"
            :disabled="chatStore.isGenerating"
            maxlength="500"
            placeholder="继续提问..."
            resize="none"
            type="textarea"
            @keydown="handleInputKeydown"
          />

          <div class="composer-bar">
            <span class="composer-hint">答案生成后会自动附带来源片段</span>
            <el-button
              class="composer-action"
              :disabled="!chatStore.isGenerating && !inputText.trim()"
              :type="chatStore.isGenerating ? 'danger' : 'primary'"
              @click="handleComposerAction"
            >
              <el-icon>
                <CircleCloseFilled v-if="chatStore.isGenerating" />
                <Promotion v-else />
              </el-icon>
            </el-button>
          </div>
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

    <aside v-if="isInsightPanelCollapsed" class="insight-rail">
      <button class="rail-button" type="button" title="展开文档处理状态" @click="isInsightPanelCollapsed = false">
        <el-icon>
          <Expand />
        </el-icon>
        <span>状态</span>
      </button>
    </aside>

    <aside v-else class="insight-panel">
      <section class="insight-card">
        <header class="insight-card__header">
          <h3>文档处理状态</h3>
          <div class="insight-actions">
            <button class="icon-action" type="button" title="收起文档处理状态" @click="isInsightPanelCollapsed = true">
              <el-icon>
                <Fold />
              </el-icon>
            </button>
            <el-button link type="primary" @click="$router.push('/docs')">查看全部</el-button>
          </div>
        </header>

        <div class="status-grid">
          <div
            v-for="stat in docStatusStats"
            :key="stat.label"
            class="status-stat"
          >
            <span>{{ stat.label }}</span>
            <strong>{{ stat.value }}</strong>
          </div>
        </div>

        <div class="doc-process-list">
          <article
            v-for="doc in processingDocuments"
            :key="getDocId(doc)"
            class="doc-process-item"
          >
            <div class="doc-file-icon" :class="`is-${getFileType(doc).toLowerCase()}`">
              {{ getFileType(doc).slice(0, 1) || 'D' }}
            </div>
            <div class="doc-process-main">
              <strong>{{ getDocTitle(doc) }}</strong>
              <span>{{ getDocStatusLabel(getDocStatus(doc)) }}</span>
            </div>
            <el-progress
              class="doc-process-progress"
              :percentage="getDocProgress(getDocStatus(doc))"
              :show-text="false"
              :stroke-width="6"
            />
          </article>

          <div v-if="!processingDocuments.length" class="panel-empty">
            暂无处理中的文档
          </div>
        </div>
      </section>

      <section class="insight-card">
        <header class="insight-card__header">
          <h3>最近上传</h3>
          <el-button link type="primary" @click="$router.push('/docs')">查看全部</el-button>
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

    <div
      v-if="conversationContextMenu.visible"
      class="conversation-context-menu"
      :style="{
        left: `${conversationContextMenu.x}px`,
        top: `${conversationContextMenu.y}px`
      }"
      @click.stop
    >
      <button type="button" @click="openRenameDialog">
        <el-icon>
          <EditPen />
        </el-icon>
        <span>重命名</span>
      </button>
    </div>

    <el-dialog
      v-model="renameDialogVisible"
      title="重命名对话"
      width="420px"
      @closed="resetRenameDialog"
    >
      <el-input
        ref="renameInputRef"
        v-model.trim="renameTitle"
        maxlength="20"
        placeholder="请输入对话名称"
        show-word-limit
        @keydown.enter="confirmRenameConversation"
      />

      <template #footer>
        <el-button @click="renameDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmRenameConversation">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import dayjs from 'dayjs';
import {
  ChatDotRound,
  CircleCloseFilled,
  Delete,
  EditPen,
  Expand,
  Fold,
  Plus,
  Promotion,
  Search
} from '@element-plus/icons-vue';
import * as chatApi from '../api/chat';
import MessageBubble from '../components/MessageBubble.vue';
import { useChat } from '../composables/useChat';
import { useAuthStore } from '../stores/auth';
import { useChatStore } from '../stores/chat';
import { useDocumentStore } from '../stores/document';

const STATUS_LABEL_MAP = {
  PENDING: '等待处理',
  PARSING: '解析中',
  EMBEDDING: '向量化中',
  READY: '已完成',
  FAILED: '失败'
};

const STATUS_PROGRESS_MAP = {
  PENDING: 10,
  PARSING: 30,
  EMBEDDING: 68,
  READY: 100,
  FAILED: 100
};

const chatStore = useChatStore();
const documentStore = useDocumentStore();
const authStore = useAuthStore();
const inputText = ref('');
const conversationKeyword = ref('');
const isConversationPanelCollapsed = ref(false);
const isInsightPanelCollapsed = ref(false);
const conversationTitleOverrides = ref({});
const conversationContextMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  conversation: null
});
const renameDialogVisible = ref(false);
const renameInputRef = ref(null);
const renameConversation = ref(null);
const renameTitle = ref('');

const {
  messageListRef,
  sendMessage,
  stopGenerating,
  scrollToBottom
} = useChat();

const getConversationId = (conversation) =>
  conversation?.id ?? conversation?.conversationId ?? conversation?.convId;

const renameStorageKey = computed(() => {
  const userId = authStore.userInfo?.id ?? authStore.userInfo?.userId ?? 'anonymous';
  return `wenqu:conversation-title-overrides:${userId}`;
});

const getConversationTitle = (conversation) => {
  const conversationId = getConversationId(conversation);
  const overrideTitle = conversationTitleOverrides.value[String(conversationId)];
  return overrideTitle || conversation?.title || conversation?.name || '新对话';
};

const getConversationCreatedAt = (conversation) =>
  conversation?.createdAt ?? conversation?.created_at ?? conversation?.updatedAt ?? null;

const getConversationTime = (conversation) => {
  const date = dayjs(getConversationCreatedAt(conversation));
  return date.isValid() ? date.format('HH:mm') : '';
};

const visibleConversations = computed(() => {
  const keyword = conversationKeyword.value.trim().toLowerCase();

  if (!keyword) {
    return chatStore.conversations;
  }

  return chatStore.conversations.filter((conversation) =>
    getConversationTitle(conversation).toLowerCase().includes(keyword)
  );
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

const hasProcessingStatus = (status) =>
  ['PENDING', 'PARSING', 'EMBEDDING', 'FAILED'].includes(status);

const getDocId = (row) => row?.id ?? row?.docId ?? row?.documentId;

const getDocTitle = (row) => row?.title || row?.name || row?.fileName || '未命名文档';

const getFileType = (row) => {
  const fileType = row?.fileType ?? row?.file_type ?? '';
  return fileType ? String(fileType).toUpperCase() : 'DOC';
};

const getDocStatus = (row) => String(row?.status || 'PENDING').toUpperCase();

const getDocStatusLabel = (status) => STATUS_LABEL_MAP[status] || status;

const getDocProgress = (status) => STATUS_PROGRESS_MAP[status] || 0;

const getCreatedAt = (row) => row?.createdAt ?? row?.created_at ?? row?.uploadTime ?? null;

const formatDocTime = (value) => {
  const date = dayjs(value);
  return date.isValid() ? date.format('MM-DD HH:mm') : '-';
};

const documents = computed(() => documentStore.docList || []);

const docStatusStats = computed(() => {
  const total = documents.value.length;
  const processing = documents.value.filter((doc) => hasProcessingStatus(getDocStatus(doc))).length;
  const failed = documents.value.filter((doc) => getDocStatus(doc) === 'FAILED').length;
  const ready = documents.value.filter((doc) => getDocStatus(doc) === 'READY').length;

  return [
    { label: '总文档', value: total },
    { label: '处理中', value: processing },
    { label: '失败', value: failed },
    { label: '已完成', value: ready }
  ];
});

const processingDocuments = computed(() =>
  documents.value.filter((doc) => hasProcessingStatus(getDocStatus(doc))).slice(0, 5)
);

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
  removeConversationTitleOverride(convId);
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

const loadConversationTitleOverrides = () => {
  try {
    const rawValue = localStorage.getItem(renameStorageKey.value);
    conversationTitleOverrides.value = rawValue ? JSON.parse(rawValue) : {};
  } catch {
    conversationTitleOverrides.value = {};
  }
};

const persistConversationTitleOverrides = () => {
  localStorage.setItem(
    renameStorageKey.value,
    JSON.stringify(conversationTitleOverrides.value)
  );
};

const setConversationTitleOverride = (convId, title) => {
  if (!convId) {
    return;
  }

  conversationTitleOverrides.value = {
    ...conversationTitleOverrides.value,
    [String(convId)]: title
  };
  persistConversationTitleOverrides();
};

const removeConversationTitleOverride = (convId) => {
  if (!convId || !conversationTitleOverrides.value[String(convId)]) {
    return;
  }

  const nextOverrides = { ...conversationTitleOverrides.value };
  delete nextOverrides[String(convId)];
  conversationTitleOverrides.value = nextOverrides;
  persistConversationTitleOverrides();
};

const closeConversationContextMenu = () => {
  conversationContextMenu.visible = false;
  conversationContextMenu.conversation = null;
};

const openConversationContextMenu = (event, conversation) => {
  conversationContextMenu.visible = true;
  conversationContextMenu.x = Math.min(event.clientX, window.innerWidth - 140);
  conversationContextMenu.y = Math.min(event.clientY, window.innerHeight - 48);
  conversationContextMenu.conversation = conversation;
};

const openRenameDialog = async () => {
  renameConversation.value = conversationContextMenu.conversation;
  renameTitle.value = getConversationTitle(renameConversation.value);
  closeConversationContextMenu();
  renameDialogVisible.value = true;

  await nextTick();
  renameInputRef.value?.focus?.();
};

const resetRenameDialog = () => {
  renameConversation.value = null;
  renameTitle.value = '';
};

const confirmRenameConversation = () => {
  const nextTitle = renameTitle.value.trim();
  const conversationId = getConversationId(renameConversation.value);

  if (!nextTitle) {
    ElMessage.warning('对话名称不能为空');
    return;
  }

  setConversationTitleOverride(conversationId, nextTitle);
  const targetConversation = chatStore.conversations.find(
    (conversation) => getConversationId(conversation) === conversationId
  );

  if (targetConversation) {
    targetConversation.title = nextTitle;
  }

  renameDialogVisible.value = false;
  ElMessage.success('对话已重命名');
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
  loadConversationTitleOverrides();
  window.addEventListener('click', closeConversationContextMenu);
  window.addEventListener('scroll', closeConversationContextMenu, true);

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
  window.removeEventListener('click', closeConversationContextMenu);
  window.removeEventListener('scroll', closeConversationContextMenu, true);
});
</script>

<style scoped>
.chat-view {
  min-height: calc(100vh - var(--topbar-height));
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr) 330px;
  background: var(--color-bg-secondary);
}

.chat-view.is-conversation-collapsed {
  grid-template-columns: 48px minmax(0, 1fr) 330px;
}

.chat-view.is-insight-collapsed {
  grid-template-columns: 250px minmax(0, 1fr) 48px;
}

.chat-view.is-conversation-collapsed.is-insight-collapsed {
  grid-template-columns: 48px minmax(0, 1fr) 48px;
}

.conversation-panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--color-border);
  background: #ffffff;
}

.conversation-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 16px 12px;
}

.header-actions,
.insight-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.conversation-header h2,
.chat-header h2,
.insight-card__header h3 {
  margin: 0;
  color: var(--color-text-primary);
}

.conversation-header h2 {
  font-size: 18px;
  font-weight: 700;
}

.conversation-header span,
.chat-title span {
  color: var(--color-text-tertiary);
  font-size: 12px;
  line-height: 1.4;
}

.icon-action {
  display: grid;
  place-items: center;
  border: 0;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.icon-action {
  width: 32px;
  height: 32px;
  border-radius: 7px;
  font-size: 16px;
}

.icon-action:hover {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.conversation-rail,
.insight-rail {
  min-height: 0;
  display: flex;
  justify-content: center;
  background: #ffffff;
  padding: 12px 6px;
}

.conversation-rail {
  border-right: 1px solid var(--color-border);
}

.insight-rail {
  border-left: 1px solid var(--color-border);
  background: var(--color-bg-secondary);
}

.rail-button {
  width: 34px;
  min-height: 92px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex-direction: column;
  gap: 8px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #ffffff;
  color: var(--color-text-secondary);
  cursor: pointer;
  padding: 10px 0;
}

.rail-button:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-soft);
}

.rail-button span {
  writing-mode: vertical-rl;
  letter-spacing: 0;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}

.conversation-search {
  padding: 0 14px 12px;
}

.conversation-search :deep(.el-input__wrapper) {
  background: var(--color-bg-subtle);
}

.conversation-list {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding: 4px 10px 18px;
}

.conversation-group-label {
  margin: 8px 8px 6px;
  color: var(--color-text-tertiary);
  font-size: 12px;
  font-weight: 600;
}

.conversation-item {
  position: relative;
  width: 100%;
  min-height: 44px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 6px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-primary);
  cursor: pointer;
  padding: 8px 34px 8px 10px;
  text-align: left;
  transition: background-color 0.16s ease, color 0.16s ease;
}

.conversation-item:hover,
.conversation-item.active {
  background: var(--color-primary-soft);
}

.conversation-item.active {
  color: var(--color-primary);
  font-weight: 650;
}

.conversation-title {
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-item time {
  color: var(--color-text-tertiary);
  font-size: 12px;
}

.delete-trigger {
  position: absolute;
  top: 50%;
  right: 6px;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  color: var(--color-text-secondary);
  opacity: 0;
  transform: translateY(-50%);
}

.conversation-item:hover .delete-trigger,
.conversation-item.active .delete-trigger {
  opacity: 1;
}

.delete-trigger:hover {
  color: #f56c6c;
  background: rgba(245, 108, 108, 0.1);
}

.conversation-context-menu {
  position: fixed;
  z-index: 2400;
  width: 126px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 12px 30px rgba(16, 24, 40, 0.14);
  padding: 6px;
}

.conversation-context-menu button {
  width: 100%;
  height: 34px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-primary);
  cursor: pointer;
  font-size: 13px;
  padding: 0 9px;
  text-align: left;
}

.conversation-context-menu button:hover {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.conversation-empty,
.panel-empty {
  color: var(--color-text-tertiary);
  font-size: 13px;
  line-height: 1.6;
  padding: 14px 10px;
}

.chat-main {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #ffffff;
}

.chat-header {
  min-height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 1px solid var(--color-border);
  padding: 0 24px;
}

.chat-title {
  min-width: 0;
}

.chat-title h2 {
  overflow: hidden;
  font-size: 17px;
  font-weight: 700;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-tools {
  display: flex;
  align-items: center;
  gap: 14px;
}

.source-count {
  height: 30px;
  display: inline-flex;
  align-items: center;
  padding: 0 10px;
  border: 1px solid var(--color-primary);
  border-radius: var(--app-radius);
  background: #ffffff;
  color: var(--color-primary);
  font-size: 13px;
  white-space: nowrap;
}

.message-list {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 22px 24px 18px;
  background:
    linear-gradient(180deg, rgba(248, 250, 255, 0.64), rgba(255, 255, 255, 0) 220px),
    #ffffff;
}

.empty-thread,
.empty-state {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
  color: var(--color-text-secondary);
  text-align: center;
}

.empty-thread {
  min-height: 100%;
}

.empty-thread__mark {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: linear-gradient(135deg, #eef4ff, #e9fbff);
  color: var(--color-primary);
  font-size: 20px;
}

.empty-thread h3,
.empty-state h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-weight: 700;
}

.empty-thread h3 {
  font-size: 18px;
}

.empty-state h2 {
  font-size: 24px;
}

.empty-thread p,
.empty-state p {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
}

.composer {
  margin: 0 24px 18px;
  border: 1px solid var(--color-primary-tint);
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 12px 30px rgba(63, 109, 246, 0.08);
  padding: 10px;
}

.composer :deep(.el-textarea__inner) {
  min-height: 50px !important;
  border: 0;
  box-shadow: none;
  padding: 6px 4px 8px;
  line-height: 1.65;
}

.composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-top: 1px solid var(--color-border);
  padding-top: 9px;
}

.composer-hint {
  overflow: hidden;
  color: var(--color-text-tertiary);
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.composer-action {
  width: 36px;
  height: 32px;
  flex: 0 0 auto;
  padding: 0;
}

.empty-state {
  min-height: 100%;
  padding: 28px;
}

.insight-panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
  border-left: 1px solid var(--color-border);
  background: var(--color-bg-secondary);
  padding: 14px;
}

.insight-card {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(16, 24, 40, 0.04);
  padding: 16px;
}

.insight-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.insight-actions {
  flex: 0 0 auto;
}

.insight-card__header h3 {
  font-size: 15px;
  font-weight: 700;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.status-stat {
  min-width: 0;
}

.status-stat span {
  display: block;
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-stat strong {
  display: block;
  margin-top: 4px;
  color: var(--color-text-primary);
  font-size: 17px;
  font-weight: 760;
  line-height: 1.2;
}

.doc-process-list,
.recent-upload-list {
  display: grid;
  gap: 12px;
}

.doc-process-item,
.recent-upload-item {
  min-width: 0;
  display: grid;
  align-items: center;
  gap: 10px;
}

.doc-process-item {
  grid-template-columns: 32px minmax(0, 1fr) 52px;
}

.recent-upload-item {
  grid-template-columns: 32px minmax(0, 1fr);
}

.doc-file-icon {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #eef4ff;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 760;
}

.doc-file-icon.is-pdf {
  background: #fff1f0;
  color: #e5484d;
}

.doc-file-icon.is-docx,
.doc-file-icon.is-doc {
  background: #edf5ff;
  color: #2f6feb;
}

.doc-process-main,
.recent-upload-item > div:last-child {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.doc-process-main strong,
.recent-upload-item strong {
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-process-main span,
.recent-upload-item span {
  overflow: hidden;
  color: var(--color-text-tertiary);
  font-size: 12px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-process-progress {
  width: 52px;
}

@media (max-width: 1180px) {
  .chat-view {
    grid-template-columns: 230px minmax(0, 1fr);
  }

  .chat-view.is-conversation-collapsed {
    grid-template-columns: 48px minmax(0, 1fr);
  }

  .chat-view.is-insight-collapsed,
  .chat-view.is-conversation-collapsed.is-insight-collapsed {
    grid-template-columns: 230px minmax(0, 1fr);
  }

  .insight-panel {
    display: none;
  }

  .insight-rail {
    display: none;
  }
}

@media (max-width: 820px) {
  .chat-view {
    min-height: auto;
    grid-template-columns: 1fr;
  }

  .chat-view.is-conversation-collapsed,
  .chat-view.is-insight-collapsed,
  .chat-view.is-conversation-collapsed.is-insight-collapsed {
    grid-template-columns: 1fr;
  }

  .conversation-rail {
    min-height: 52px;
    justify-content: flex-start;
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
    padding: 8px 12px;
  }

  .rail-button {
    width: auto;
    min-height: 34px;
    align-items: center;
    flex-direction: row;
    padding: 0 10px;
  }

  .rail-button span {
    writing-mode: horizontal-tb;
  }

  .conversation-panel {
    max-height: 230px;
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .conversation-list {
    display: flex;
    gap: 8px;
    overflow-x: auto;
    overflow-y: hidden;
    padding-bottom: 12px;
  }

  .conversation-group-label,
  .conversation-empty {
    display: none;
  }

  .conversation-item {
    width: 190px;
    flex: 0 0 190px;
  }

  .chat-header,
  .composer-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .chat-header {
    padding: 14px 16px;
  }

  .message-list {
    padding: 18px 16px;
  }

  .composer {
    margin: 0 16px 16px;
  }

  .composer-hint {
    white-space: normal;
  }
}
</style>
