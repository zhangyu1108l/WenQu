<template>
  <div class="main-layout">
    <header class="topbar">
      <div class="topbar-brand">
        <div class="brand-mark" aria-hidden="true">
          <span />
        </div>
        <strong>WenQu</strong>
      </div>

      <div class="topbar-actions">
        <button class="user-chip" type="button" @click="handleLogout">
          <span class="user-avatar">{{ avatarText }}</span>
          <span class="user-name">{{ username }}</span>
          <small>{{ roleLabel }}</small>
          <el-icon>
            <SwitchButton />
          </el-icon>
        </button>
      </div>
    </header>

    <div class="workspace-shell">
      <aside class="sidebar" aria-label="主导航">
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

      <main class="content-area">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue';
import { RouterView, useRoute, useRouter } from 'vue-router';
import {
  ChatDotRound,
  DataAnalysis,
  Files,
  OfficeBuilding,
  SwitchButton,
  User
} from '@element-plus/icons-vue';
import { useAuthStore } from '../stores/auth';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const allMenuItems = [
  { label: '对话', path: '/chat', icon: ChatDotRound, roles: [0, 1, 2] },
  { label: '文档', path: '/docs', icon: Files, roles: [0, 1, 2] },
  { label: '用户', path: '/admin/users', icon: User, roles: [0, 1] },
  { label: '评估', path: '/eval', icon: DataAnalysis, roles: [0, 1] },
  { label: '租户', path: '/admin/tenants', icon: OfficeBuilding, roles: [0] }
];

const currentRole = computed(() => Number(authStore.userInfo?.role));
const menuItems = computed(() =>
  allMenuItems.filter((item) => item.roles.includes(currentRole.value))
);

const username = computed(() => authStore.userInfo?.username || '用户');
const avatarText = computed(() => username.value.trim().slice(0, 1).toUpperCase() || '问');
const roleLabel = computed(() => {
  const role = currentRole.value;

  if (role === 0) {
    return '超管';
  }

  if (role === 1) {
    return '管理员';
  }

  return '用户';
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

onMounted(() => {
  authStore.restoreFromStorage();
});
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-primary);
}

.topbar {
  height: var(--topbar-height);
  display: flex;
  flex: 0 0 var(--topbar-height);
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.92);
  padding: 0 20px;
  backdrop-filter: blur(18px);
}

.topbar-brand {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-mark {
  position: relative;
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 8px;
  background: linear-gradient(145deg, #4f7cff, #23cce7);
  box-shadow: 0 10px 24px rgba(63, 109, 246, 0.22);
  transform: rotate(45deg);
}

.brand-mark::before,
.brand-mark::after,
.brand-mark span {
  position: absolute;
  width: 9px;
  height: 9px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.76);
  content: '';
}

.brand-mark::before {
  top: 6px;
  left: 6px;
}

.brand-mark::after {
  right: 6px;
  bottom: 6px;
}

.brand-mark span {
  top: 6px;
  right: 6px;
  background: rgba(255, 255, 255, 0.48);
}

.topbar-brand > strong {
  color: var(--color-text-primary);
  font-size: 20px;
  font-weight: 760;
  line-height: 1;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-chip {
  height: 38px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--color-text-primary);
  cursor: pointer;
  padding: 0 6px 0 0;
}

.user-chip:hover {
  background: var(--color-bg-secondary);
}

.user-avatar {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 50%;
  color: #ffffff;
  background: linear-gradient(135deg, #5a84ff, #23cce7);
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
}

.user-name {
  max-width: 92px;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-chip small {
  color: var(--color-text-tertiary);
  font-size: 12px;
}

.workspace-shell {
  min-height: 0;
  display: flex;
  flex: 1;
}

.sidebar {
  width: var(--sidebar-width);
  min-height: calc(100vh - var(--topbar-height));
  display: flex;
  flex: 0 0 var(--sidebar-width);
  flex-direction: column;
  border-right: 1px solid var(--color-border);
  background: var(--color-bg-sidebar);
}

.sidebar-nav {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  padding: 16px 9px;
}

.nav-item {
  width: 100%;
  min-height: 54px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 5px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #667085;
  cursor: pointer;
  padding: 7px 4px;
  transition: color 0.16s ease, background-color 0.16s ease;
}

.nav-item:hover {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.nav-item.active {
  background: linear-gradient(180deg, #eef4ff, #f6fbff);
  color: var(--color-primary);
  font-weight: 600;
  box-shadow: inset 3px 0 0 var(--color-primary);
}

.nav-icon {
  flex: 0 0 auto;
  font-size: 19px;
}

.nav-item span {
  font-size: 12px;
  line-height: 1.2;
}

.content-area {
  min-width: 0;
  min-height: calc(100vh - var(--topbar-height));
  flex: 1;
  background: var(--color-bg-secondary);
}

@media (max-width: 720px) {
  .topbar {
    height: auto;
    min-height: var(--topbar-height);
    align-items: flex-start;
    flex-direction: column;
    padding: 12px 14px;
  }

  .topbar-actions,
  .topbar-brand {
    width: 100%;
  }

  .topbar-actions {
    justify-content: space-between;
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

  .content-area {
    min-height: auto;
  }
}
</style>
