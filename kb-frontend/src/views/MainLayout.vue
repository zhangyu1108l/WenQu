<template>
  <div class="main-layout">
    <aside class="sidebar">
      <header class="sidebar-brand">
        <div class="brand-logo">问</div>
        <div class="brand-text">
          <h1>问渠</h1>
          <span>企业知识库</span>
        </div>
      </header>

      <nav class="sidebar-nav" aria-label="主导航">
        <button
          v-for="item in menuItems"
          :key="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
          type="button"
          @click="goTo(item.path)"
        >
          <el-icon class="nav-icon">
            <component :is="item.icon" />
          </el-icon>
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <footer class="sidebar-user">
        <div class="user-profile">
          <div class="user-avatar">{{ avatarText }}</div>
          <div class="user-meta">
            <strong>{{ username }}</strong>
            <span>{{ tenantName }}</span>
          </div>
        </div>

        <button class="logout-button" type="button" @click="handleLogout">
          <el-icon>
            <SwitchButton />
          </el-icon>
          <span>登出</span>
        </button>
      </footer>
    </aside>

    <main class="content-area">
      <!-- router-view 是子路由内容渲染区域，/chat、/docs、/eval 等页面会在这里显示。 -->
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue';
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
  { label: '文档管理', path: '/docs', icon: Files, roles: [0, 1] },
  { label: '用户管理', path: '/admin/users', icon: User, roles: [0, 1] },
  { label: '评估中心', path: '/eval', icon: DataAnalysis, roles: [0, 1] },
  { label: '租户管理', path: '/admin/tenants', icon: OfficeBuilding, roles: [0] }
];

const currentRole = computed(() => Number(authStore.userInfo?.role));

// 动态菜单：menuItems 使用 computed 根据 authStore.userInfo.role 过滤菜单列表，只保留当前角色允许访问的菜单项。
const menuItems = computed(() =>
  allMenuItems.filter((item) => item.roles.includes(currentRole.value))
);

const username = computed(() => authStore.userInfo?.username || '用户');
const tenantName = computed(
  () => authStore.userInfo?.tenantName || authStore.userInfo?.tenantCode || '默认租户'
);
const avatarText = computed(() => username.value.trim().slice(0, 1).toUpperCase() || '问');

const isActive = (path) => route.path === path || route.path.startsWith(`${path}/`);

const goTo = (path) => {
  if (route.path !== path) {
    router.push(path);
  }
};

const handleLogout = () => {
  // 登出：authStore.logout() 内部会清空 token 并跳转登录页。
  authStore.logout();
};
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
  display: flex;
  background: var(--color-bg-primary);
}

.sidebar {
  width: 260px;
  min-height: 100vh;
  display: flex;
  flex: 0 0 260px;
  flex-direction: column;
  background: var(--color-bg-sidebar);
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 24px 20px 18px;
}

.brand-logo {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 8px;
  color: #ffffff;
  background: var(--color-primary);
  font-size: 22px;
  font-weight: 700;
  line-height: 1;
}

.brand-text {
  min-width: 0;
}

.brand-text h1 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 20px;
  font-weight: 700;
  line-height: 1.2;
}

.brand-text span {
  display: block;
  margin-top: 3px;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.sidebar-nav {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
}

.nav-item {
  width: 100%;
  height: 44px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 12px;
  border: 0;
  border-radius: 8px;
  color: var(--color-text-primary);
  background: transparent;
  cursor: pointer;
  font-size: 15px;
  line-height: 1;
  text-align: left;
  transition: color 0.16s ease, background-color 0.16s ease;
}

.nav-item:hover {
  background: rgba(16, 163, 127, 0.1);
}

.nav-item.active {
  color: var(--color-primary);
  background: transparent;
  font-weight: 600;
}

.nav-item.active:hover {
  background: rgba(16, 163, 127, 0.1);
}

.nav-icon {
  flex: 0 0 auto;
  font-size: 18px;
}

.sidebar-user {
  padding: 14px 12px 18px;
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 8px;
}

.user-avatar {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 50%;
  color: #ffffff;
  background: var(--color-primary);
  font-size: 15px;
  font-weight: 700;
  line-height: 1;
}

.user-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.user-meta strong,
.user-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-meta strong {
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.3;
}

.user-meta span {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.3;
}

.logout-button {
  width: 100%;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 6px;
  border: 0;
  border-radius: 8px;
  color: var(--color-text-secondary);
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  transition: color 0.16s ease, background-color 0.16s ease;
}

.logout-button:hover {
  color: var(--color-primary);
  background: rgba(16, 163, 127, 0.1);
}

.content-area {
  min-width: 0;
  flex: 1;
  background: var(--color-bg-primary);
}
</style>
