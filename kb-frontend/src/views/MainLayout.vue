<template>
  <div class="main-layout">
    <header class="topbar">
      <button class="topbar-brand" type="button" @click="goTo('/chat')">
        <span class="brand-mark" aria-hidden="true">
          <span />
        </span>
        <strong>WenQu</strong>
      </button>

      <button class="topbar-menu" type="button" title="菜单">
        <el-icon>
          <Menu />
        </el-icon>
      </button>

      <nav class="topbar-tabs" aria-label="主导航">
        <button
          v-for="item in topTabs"
          :key="item.path"
          class="topbar-tab"
          :class="{ active: isActive(item.path) }"
          type="button"
          @click="goTo(item.path)"
        >
          {{ item.topLabel || item.label }}
        </button>
      </nav>

      <div class="topbar-actions">
        <button class="user-chip" type="button" title="退出登录" @click="handleLogout">
          <span class="user-avatar">{{ avatarText }}</span>
          <span class="user-name">{{ username }}</span>
          <small>{{ roleLabel }}</small>
          <el-icon>
            <ArrowDown />
          </el-icon>
        </button>
      </div>
    </header>

    <div class="workspace-shell">
      <aside class="sidebar" aria-label="侧边导航">
        <nav class="sidebar-nav">
          <button
            v-for="item in menuItems"
            :key="item.path"
            class="nav-item"
            :class="{ active: isActive(item.path) }"
            :title="item.label"
            type="button"
            @click="goTo(item.path)"
          >
            <el-icon class="nav-icon">
              <component :is="item.icon" />
            </el-icon>
            <span>{{ item.label }}</span>
          </button>
        </nav>
      </aside>

      <main class="content-area" :class="{ 'is-chat-route': route.path.startsWith('/chat') }">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { RouterView, useRoute, useRouter } from 'vue-router';
import {
  ArrowDown,
  ChatDotRound,
  DataAnalysis,
  Files,
  Menu,
  OfficeBuilding,
  User
} from '@element-plus/icons-vue';
import { useAuthStore } from '../stores/auth';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const allMenuItems = [
  { label: '对话', topLabel: '对话问答', path: '/chat', icon: ChatDotRound, roles: [0, 1, 2] },
  { label: '文档管理', topLabel: '文档管理', path: '/docs', icon: Files, roles: [0, 1, 2] },
  { label: '评估面板', topLabel: '评估', path: '/eval', icon: DataAnalysis, roles: [0, 1] },
  { label: '用户管理', topLabel: '用户', path: '/admin/users', icon: User, roles: [0, 1] },
  { label: '租户管理', topLabel: '租户', path: '/admin/tenants', icon: OfficeBuilding, roles: [0] }
];

const currentRole = computed(() => Number(authStore.userInfo?.role));
const menuItems = computed(() =>
  allMenuItems.filter((item) => item.roles.includes(currentRole.value))
);
const topTabs = computed(() => menuItems.value);

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

const isActive = (path) => route.path === path || route.path.startsWith(`${path}/`);

const goTo = (path) => {
  if (route.path !== path) {
    router.push(path);
  }
};

const handleLogout = () => {
  authStore.logout();
};

const syncAuthFromStorage = () => {
  authStore.restoreFromStorage();
};

onMounted(() => {
  syncAuthFromStorage();
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
  flex-direction: column;
  background: var(--color-bg-secondary);
}

.topbar {
  height: 68px;
  flex: 0 0 68px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 1px 0 rgba(16, 24, 40, 0.02);
}

.topbar-brand {
  width: 168px;
  height: 100%;
  flex: 0 0 168px;
  display: flex;
  align-items: center;
  gap: 12px;
  border: 0;
  border-right: 1px solid var(--color-border);
  background: transparent;
  cursor: pointer;
  padding: 0 24px;
}

.brand-mark {
  position: relative;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 10px;
  background: linear-gradient(135deg, #1769ff 0%, #55d4ff 100%);
  box-shadow: 0 12px 24px rgba(23, 105, 255, 0.22);
}

.brand-mark::before,
.brand-mark::after,
.brand-mark span {
  position: absolute;
  width: 12px;
  height: 12px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.82);
  content: '';
  transform: rotate(45deg);
}

.brand-mark::before {
  left: 8px;
  top: 8px;
}

.brand-mark::after {
  right: 8px;
  bottom: 8px;
}

.brand-mark span {
  right: 8px;
  top: 8px;
  background: rgba(255, 255, 255, 0.52);
}

.topbar-brand strong {
  color: #0b1220;
  font-size: 25px;
  font-weight: 850;
  line-height: 1;
}

.topbar-menu {
  width: 36px;
  height: 36px;
  display: inline-grid;
  place-items: center;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #475467;
  cursor: pointer;
  font-size: 18px;
}

.topbar-menu {
  margin-left: 20px;
}

.topbar-menu:hover {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.topbar-tabs {
  height: 100%;
  display: flex;
  align-items: center;
  gap: 28px;
  margin-left: 20px;
  min-width: 0;
}

.topbar-tab {
  position: relative;
  height: 100%;
  border: 0;
  background: transparent;
  color: var(--color-text-primary);
  cursor: pointer;
  padding: 0 2px;
  font-size: 15px;
  font-weight: 700;
  white-space: nowrap;
}

.topbar-tab.active {
  color: var(--color-primary);
}

.topbar-tab.active::after {
  position: absolute;
  right: -12px;
  bottom: 0;
  left: -12px;
  height: 3px;
  border-radius: 999px 999px 0 0;
  background: var(--color-primary);
  content: '';
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
  padding: 0 22px;
}

.user-chip {
  height: 42px;
  display: flex;
  align-items: center;
  gap: 9px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-primary);
  cursor: pointer;
  padding: 0 6px;
}

.user-chip:hover {
  background: var(--color-bg-secondary);
}

.user-avatar {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 50%;
  color: #ffffff;
  background: linear-gradient(135deg, #1769ff, #55d4ff);
  font-size: 14px;
  font-weight: 800;
}

.user-name {
  max-width: 96px;
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-chip small {
  color: var(--color-text-tertiary);
  font-size: 12px;
}

.workspace-shell {
  min-height: 0;
  flex: 1;
  display: flex;
}

.sidebar {
  width: 168px;
  min-height: 0;
  flex: 0 0 168px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--color-border);
  background: #ffffff;
}

.sidebar-nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 8px;
}

.nav-item {
  width: 100%;
  min-height: 48px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #475467;
  cursor: pointer;
  padding: 0 16px;
  transition: color 0.16s ease, background-color 0.16s ease;
}

.nav-item:hover,
.nav-item.active {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.nav-item.active {
  font-weight: 800;
}

.nav-icon {
  flex: 0 0 auto;
  font-size: 19px;
}

.nav-item span {
  font-size: 14px;
  line-height: 1.2;
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
}

@media (max-width: 920px) {
  .topbar-tabs {
    gap: 18px;
  }

  .sidebar {
    width: 76px;
    flex-basis: 76px;
  }

  .sidebar-nav {
    padding: 12px 8px;
  }

  .nav-item {
    justify-content: center;
    padding: 0;
  }

  .nav-item span {
    display: none;
  }
}

@media (max-width: 720px) {
  .topbar {
    height: auto;
    min-height: var(--topbar-height);
    align-items: flex-start;
    flex-direction: column;
    padding: 12px 14px;
  }

  .topbar-brand {
    width: 100%;
    height: 44px;
    flex-basis: auto;
    border-right: 0;
    padding: 0;
  }

  .topbar-menu,
  .topbar-tabs {
    display: none;
  }

  .topbar-actions {
    width: 100%;
    justify-content: space-between;
    padding: 8px 0 0;
  }

  .workspace-shell {
    flex-direction: column;
  }

  .sidebar {
    width: 100%;
    min-height: auto;
    flex: 0 0 auto;
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .sidebar-nav {
    flex-direction: row;
    overflow-x: auto;
    padding: 8px 12px;
  }

  .nav-item {
    min-width: 72px;
    flex: 0 0 auto;
  }

  .nav-item span {
    display: block;
  }
}
</style>
