<template>
  <div class="main-layout" :class="{ 'is-sidebar-collapsed': isSidebarCollapsed }">
    <aside class="app-sidebar" aria-label="问渠工作台导航">
      <!-- 折叠状态 -->
      <div v-if="isSidebarCollapsed" class="sidebar-collapsed">
        <button class="collapsed-btn" type="button" title="新建对话" @click="handleCreateConversation">
          <el-icon><ChatLineSquare /></el-icon>
        </button>
        <button class="collapsed-btn" type="button" title="展开侧边栏" @click="toggleSidebar">
          <el-icon><Expand /></el-icon>
        </button>
      </div>

      <!-- 展开状态 -->
      <template v-else>
      <header class="sidebar-top">
        <button class="brand-button" type="button" title="问渠" @click="goTo('/chat')">
          <img class="brand-mark" :src="wenquLogoIcon" alt="" aria-hidden="true" />
          <strong>问渠</strong>
        </button>

        <div class="sidebar-tools">
          <button class="icon-button" type="button" title="搜索" @click="toggleSearch">
            <el-icon>
              <Search />
            </el-icon>
          </button>
          <button
            class="icon-button"
            type="button"
            :title="isSidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
            @click="toggleSidebar"
          >
            <el-icon>
              <Fold />
            </el-icon>
          </button>
        </div>
      </header>

      <div v-if="isSearchOpen && !isSidebarCollapsed" class="sidebar-search">
        <el-input
          v-model="searchKeyword"
          clearable
          placeholder="搜索项目或对话"
          size="large"
        />
      </div>

      <nav v-if="!isSidebarCollapsed" class="primary-nav" aria-label="功能导航">
        <button
          v-for="item in primaryNavItems"
          :key="item.label"
          class="nav-item"
          :class="{ active: isNavActive(item) }"
          :title="item.label"
          type="button"
          @click="handleNavItem(item)"
        >
          <el-icon class="nav-icon">
            <component :is="item.icon" />
          </el-icon>
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <div v-if="!isSidebarCollapsed" class="sidebar-scroll">
        <section class="sidebar-section">
          <h2>项目</h2>

          <button class="project-root" type="button" @click="goTo('/chat')">
            <el-icon>
              <FolderOpened />
            </el-icon>
            <span>WenQu</span>
          </button>

          <div class="project-list">
            <button
              v-for="conversation in projectConversations"
              :key="`project-${getConversationId(conversation)}`"
              class="conversation-link"
              :class="{ active: isConversationActive(conversation) }"
              type="button"
              @click="handleSelectConversation(getConversationId(conversation))"
            >
              <span class="conversation-title">{{ getConversationTitle(conversation) }}</span>
              <span v-if="isConversationActive(conversation)" class="active-dot" aria-hidden="true" />

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

            <button
              v-if="hasMoreProjectConversations"
              class="show-more-button"
              type="button"
              @click="showMoreProjectConversations"
            >
              显示更多
            </button>

            <div v-if="!projectConversations.length" class="sidebar-empty">
              暂无项目会话
            </div>
          </div>
        </section>

        <section class="sidebar-section recent-section">
          <h2>最近</h2>

          <button
            v-for="conversation in recentConversations"
            :key="`recent-${getConversationId(conversation)}`"
            class="recent-link"
            :class="{ active: isConversationActive(conversation) }"
            type="button"
            @click="handleSelectConversation(getConversationId(conversation))"
          >
            {{ getConversationTitle(conversation) }}
          </button>

          <div v-if="!recentConversations.length" class="sidebar-empty">
            暂无最近记录
          </div>
        </section>
      </div>

      <footer v-if="!isSidebarCollapsed" class="sidebar-footer">
        <div class="user-chip">
          <span class="user-avatar">{{ avatarText }}</span>
          <span class="user-meta">
            <strong>{{ username }}</strong>
            <small>{{ roleLabel }}</small>
          </span>
          <el-popconfirm
            title="确定要退出登录吗？"
            confirm-button-text="退出"
            cancel-button-text="取消"
            @confirm="handleLogout"
          >
            <template #reference>
              <el-button class="logout-btn" size="small" text>
                <el-icon><SwitchButton /></el-icon>
                <span>退出</span>
              </el-button>
            </template>
          </el-popconfirm>
        </div>
      </footer>
      </template>
    </aside>

    <main class="content-area" :class="{ 'is-chat-route': route.path.startsWith('/chat') }">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { RouterView, useRoute, useRouter } from 'vue-router';
import {
  ArrowDown,
  ChatLineSquare,
  DataLine,
  Delete,
  Document,
  Expand,
  Files,
  Fold,
  More,
  OfficeBuilding,
  Search,
  Setting,
  SwitchButton
} from '@element-plus/icons-vue';
import * as chatApi from '../api/chat';
import { useAuthStore } from '../stores/auth';
import { useChatStore } from '../stores/chat';
import wenquLogoIcon from '../assets/wenqu-logo-b-icon-white.png';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const chatStore = useChatStore();

const isSidebarCollapsed = ref(false);
const isSearchOpen = ref(false);
const searchKeyword = ref('');
const projectLimit = ref(5);

const functionItems = [
  { label: '文档管理', path: '/docs', icon: Document, roles: [0, 1, 2] },
  { label: '评估面板', path: '/eval', icon: DataLine, roles: [0, 1] },
  { label: '用户管理', path: '/admin/users', icon: Setting, roles: [0, 1] },
  { label: '租户管理', path: '/admin/tenants', icon: OfficeBuilding, roles: [0] }
];

const currentRole = computed(() => Number(authStore.userInfo?.role));
const visibleFunctionItems = computed(() =>
  functionItems.filter((item) => item.roles.includes(currentRole.value))
);

const primaryNavItems = computed(() => [
  { label: '新对话', icon: ChatLineSquare, action: 'new' },
  ...visibleFunctionItems.value,
  { label: '更多', icon: More, action: 'more' }
]);

const username = computed(() => authStore.userInfo?.username || '用户');
const avatarText = computed(() => username.value.trim().slice(0, 1).toUpperCase() || 'W');
const roleLabel = computed(() => {
  const role = currentRole.value;

  if (role === 0) {
    return '超级管理员';
  }

  if (role === 1) {
    return '管理员';
  }

  return '普通用户';
});

const normalizedSearch = computed(() => searchKeyword.value.trim().toLowerCase());

const filteredConversations = computed(() => {
  if (!normalizedSearch.value) {
    return chatStore.conversations;
  }

  return chatStore.conversations.filter((conversation) =>
    getConversationTitle(conversation).toLowerCase().includes(normalizedSearch.value)
  );
});

const projectConversations = computed(() =>
  filteredConversations.value.slice(0, projectLimit.value)
);

const hasMoreProjectConversations = computed(() =>
  filteredConversations.value.length > projectLimit.value
);

const recentConversations = computed(() =>
  filteredConversations.value.slice(projectLimit.value, projectLimit.value + 8)
);

const getConversationId = (conversation) =>
  conversation?.id ?? conversation?.conversationId ?? conversation?.convId;

const getConversationTitle = (conversation) =>
  conversation?.title || conversation?.name || '新对话';

const isRouteActive = (path) => route.path === path || route.path.startsWith(`${path}/`);

const isNavActive = (item) => Boolean(item.path && isRouteActive(item.path));

const isConversationActive = (conversation) =>
  route.path.startsWith('/chat') && getConversationId(conversation) === chatStore.currentConvId;

const goTo = (path) => {
  if (route.path !== path) {
    router.push(path);
  }
};

const toggleSidebar = () => {
  isSidebarCollapsed.value = !isSidebarCollapsed.value;

  if (isSidebarCollapsed.value) {
    isSearchOpen.value = false;
  }
};

const toggleSearch = () => {
  if (isSidebarCollapsed.value) {
    isSidebarCollapsed.value = false;
  }

  isSearchOpen.value = !isSearchOpen.value;
};

const showMoreProjectConversations = () => {
  projectLimit.value += 5;
};

const ensureChatRoute = async () => {
  if (!route.path.startsWith('/chat')) {
    await router.push('/chat');
  }
};

const guardGenerating = () => {
  if (!chatStore.isGenerating) {
    return false;
  }

  ElMessage.warning('当前回答仍在生成中，请稍后再切换对话');
  return true;
};

const handleCreateConversation = async () => {
  if (guardGenerating()) {
    return;
  }

  await ensureChatRoute();
  await chatStore.createConversation();
};

const handleSelectConversation = async (convId) => {
  if (!convId || guardGenerating()) {
    return;
  }

  await ensureChatRoute();

  if (convId !== chatStore.currentConvId) {
    await chatStore.selectConversation(convId);
  }
};

const handleDeleteConversation = async (convId) => {
  if (!convId || guardGenerating()) {
    return;
  }

  await chatApi.deleteConversation(convId);
  const conversations = await chatStore.loadConversations();

  if (convId === chatStore.currentConvId) {
    const nextConversationId = getConversationId(conversations[0]);

    if (nextConversationId) {
      await chatStore.selectConversation(nextConversationId);
    } else {
      chatStore.currentConvId = null;
      chatStore.messages = [];
    }
  }

  ElMessage.success('对话已删除');
};

const handleNavItem = (item) => {
  if (item.action === 'new') {
    handleCreateConversation();
    return;
  }

  if (item.action === 'more') {
    ElMessage.info('更多功能将根据权限逐步开放');
    return;
  }

  if (item.path) {
    goTo(item.path);
  }
};

const handleLogout = () => {
  authStore.logout();
};

const loadConversationNav = async () => {
  try {
    await chatStore.loadConversations();
  } catch {
    // 导航列表加载失败不阻塞主页面渲染。
  }
};

const syncAuthFromStorage = () => {
  authStore.restoreFromStorage();
};

onMounted(() => {
  syncAuthFromStorage();
  loadConversationNav();
  window.addEventListener('wenqu:auth-updated', syncAuthFromStorage);
  window.addEventListener('wenqu:auth-cleared', syncAuthFromStorage);
  window.addEventListener('storage', syncAuthFromStorage);
});

onBeforeUnmount(() => {
  window.removeEventListener('wenqu:auth-updated', syncAuthFromStorage);
  window.removeEventListener('wenqu:auth-cleared', syncAuthFromStorage);
  window.removeEventListener('storage', syncAuthFromStorage);
});
</script>

<style scoped>
.main-layout {
  height: 100vh;
  height: 100dvh;
  min-height: 0;
  display: flex;
  overflow: hidden;
  background: #ffffff;
}

.app-sidebar {
  width: 260px;
  min-height: 0;
  flex: 0 0 260px;
  display: flex;
  flex-direction: column;
  background: #f5f6f8;
  color: #202123;
  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI',
    'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 14px;
  font-weight: 400;
  transition: flex-basis 0.18s ease, width 0.18s ease;
}

.is-sidebar-collapsed .app-sidebar {
  width: 72px;
  flex-basis: 72px;
  background: #ffffff;
}

.sidebar-top {
  min-height: 58px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px 8px 18px;
}

.brand-button,
.icon-button,
.nav-item,
.project-root,
.conversation-link,
.recent-link,
.show-more-button,
.user-chip {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  letter-spacing: 0;
}

.brand-button {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 0;
}

.brand-mark {
  width: 34px;
  height: 34px;
  display: block;
  flex: 0 0 auto;
  border-radius: 8px;
  background: #ffffff;
  object-fit: cover;
}

.brand-button strong {
  overflow: hidden;
  color: #111827;
  font-size: 22px;
  font-weight: 650;
  line-height: 1.1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-tools {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

.icon-button {
  width: 36px;
  height: 36px;
  display: inline-grid;
  place-items: center;
  border-radius: 8px;
  color: #4b5563;
  font-size: 20px;
  transition: background 0.15s, color 0.15s;
}

.icon-button:hover {
  background: #f0f1f3;
  color: #111827;
}

.sidebar-search {
  padding: 0 14px 10px;
}

.sidebar-search :deep(.el-input__wrapper) {
  height: 42px;
  border-radius: 8px;
  background: #f7f7f8;
}

.primary-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 12px 10px;
}

.nav-item {
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-radius: 8px;
  color: #1a1a1a;
  padding: 0 10px;
  text-align: left;
}

.nav-item:hover,
.nav-item.active {
  background: #f2f2f2;
}

.nav-item.active {
  font-weight: 600;
}

.nav-icon {
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  font-size: 18px;
  color: #1a1a1a;
}

.nav-item span {
  min-width: 0;
  overflow: hidden;
  font-size: 15px;
  font-weight: 400;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-item.active span {
  font-weight: 600;
}

.sidebar-scroll {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding: 6px 6px 14px;
}

.sidebar-section {
  padding: 18px 0 0;
}

.sidebar-section h2 {
  margin: 0;
  color: #111827;
  font-size: 15px;
  font-weight: 650;
  line-height: 1.35;
  padding: 0 14px 10px;
}

.project-root {
  width: 100%;
  min-height: 38px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-radius: 8px;
  color: #202123;
  padding: 0 14px;
  text-align: left;
}

.project-root:hover {
  background: #f2f2f2;
}

.project-root .el-icon {
  font-size: 21px;
}

.project-root span {
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-list {
  padding-top: 3px;
}

.conversation-link,
.recent-link {
  position: relative;
  width: 100%;
  min-height: 38px;
  display: flex;
  align-items: center;
  border-radius: 8px;
  color: #202123;
  padding: 0 28px 0 36px;
  text-align: left;
}

.conversation-link:hover,
.conversation-link.active,
.recent-link:hover,
.recent-link.active {
  background: #f2f2f2;
}

.conversation-title,
.recent-link {
  overflow: hidden;
  font-size: 14px;
  font-weight: 400;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-link.active .conversation-title,
.recent-link.active {
  font-weight: 600;
}

.active-dot {
  position: absolute;
  right: 18px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-primary);
}

.delete-trigger {
  position: absolute;
  right: 10px;
  width: 26px;
  height: 26px;
  display: none;
  place-items: center;
  border-radius: 6px;
  color: #777b83;
}

.conversation-link:hover .active-dot {
  display: none;
}

.conversation-link:hover .delete-trigger {
  display: grid;
}

.delete-trigger:hover {
  background: #ffffff;
  color: #e5484d;
}

.show-more-button,
.sidebar-empty {
  width: 100%;
  min-height: 38px;
  display: flex;
  align-items: center;
  color: #8b8f97;
  padding: 0 48px;
  text-align: left;
}

.show-more-button {
  border-radius: 8px;
  font-size: 14px;
}

.show-more-button:hover {
  background: #f2f2f2;
  color: #111827;
}

.sidebar-empty {
  font-size: 14px;
}

.recent-section {
  padding-top: 24px;
}

.recent-link {
  min-height: 38px;
  padding-right: 14px;
  padding-left: 14px;
}

.sidebar-footer {
  flex: 0 0 auto;
  border-top: 1px solid #eeeeef;
  padding: 10px 12px;
}

.user-chip {
  width: 100%;
  min-height: 48px;
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  border-radius: 8px;
  padding: 6px 8px;
  text-align: left;
}

.logout-btn {
  color: #303133;
  padding: 4px 6px;
}

.user-avatar {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-primary);
  color: #ffffff;
  font-size: 14px;
  font-weight: 700;
}

.user-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.user-meta strong,
.user-meta small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-meta strong {
  color: #111827;
  font-size: 14px;
  font-weight: 600;
}

.user-meta small {
  color: #8b8f97;
  font-size: 12px;
}

.content-area {
  min-width: 0;
  min-height: 0;
  flex: 1;
  overflow: auto;
  background: var(--color-bg-secondary);
}

.content-area.is-chat-route {
  overflow: hidden;
  background: #ffffff;
}

.sidebar-collapsed {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
}

.collapsed-btn {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #667085;
  cursor: pointer;
  font-size: 20px;
  transition: background 0.15s, color 0.15s;
}

.collapsed-btn:hover {
  background: #edf0f5;
  color: #303133;
}

@media (max-width: 920px) {
  .app-sidebar {
    width: 240px;
    flex-basis: 240px;
  }
}

@media (max-width: 720px) {
  .main-layout {
    align-items: stretch;
  }

  .app-sidebar {
    width: 72px;
    flex-basis: 72px;
  }

  .brand-button strong,
  .sidebar-search,
  .sidebar-scroll,
  .nav-item span,
  .user-meta,
  .user-chip > .el-icon {
    display: none;
  }

  .sidebar-top {
    min-height: 118px;
    flex-direction: column;
    justify-content: center;
    gap: 10px;
    padding: 14px 10px 8px;
  }

  .sidebar-tools {
    flex-direction: column;
    gap: 6px;
    margin-left: 0;
  }

  .primary-nav {
    padding: 8px;
  }

  .nav-item {
    justify-content: center;
    padding: 0;
  }
}
</style>
