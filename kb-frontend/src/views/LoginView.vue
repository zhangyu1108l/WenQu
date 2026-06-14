<template>
  <main class="login-page">
    <section class="login-hero">
      <div class="hero-brand">
        <img class="brand-mark" :src="wenquLogoIcon" alt="" aria-hidden="true" />
        <strong>WenQu</strong>
      </div>
      <h1>企业级 RAG 知识库系统</h1>
      <p>安全、可靠、可追溯的企业知识中枢，支持文档解析、向量检索、流式问答与 Ragas 评估。</p>
      <div class="hero-points">
        <span>多租户隔离</span>
        <span>SSE 流式问答</span>
        <span>来源可核对</span>
      </div>
    </section>

    <section class="login-card" aria-label="账号登录">
      <header class="card-header">
        <div>
          <h2>{{ authMode === 'login' ? '账号登录' : '注册账号' }}</h2>
        </div>
        <button class="text-button" type="button" @click="toggleAuthMode">
          {{ authMode === 'login' ? '注册账号' : '返回登录' }}
        </button>
      </header>

      <div class="auth-switch" role="tablist" aria-label="认证方式">
        <button
          class="auth-switch__item"
          :class="{ active: authMode === 'login' }"
          type="button"
          @click="setAuthMode('login')"
        >
          登录
        </button>
        <button
          class="auth-switch__item"
          :class="{ active: authMode === 'register' }"
          type="button"
          @click="setAuthMode('register')"
        >
          注册
        </button>
      </div>

      <el-form
        ref="loginFormRef"
        class="login-form"
        :model="loginForm"
        :rules="authRules"
        label-position="top"
        :validate-on-rule-change="false"
        @keyup.enter="handleSubmit"
      >
        <el-form-item label="租户代码" prop="tenantCode">
          <el-input
            v-model.trim="loginForm.tenantCode"
            autocomplete="organization"
            clearable
            placeholder="例如 acme-tech"
            :prefix-icon="OfficeBuilding"
            size="large"
          />
        </el-form-item>

        <el-form-item label="用户名" prop="username">
          <el-input
            v-model.trim="loginForm.username"
            autocomplete="username"
            clearable
            placeholder="请输入用户名"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="loginForm.password"
            :autocomplete="authMode === 'login' ? 'current-password' : 'new-password'"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
            size="large"
            type="password"
          />
        </el-form-item>

        <el-form-item
          v-if="authMode === 'register'"
          label="确认密码"
          prop="confirmPassword"
        >
          <el-input
            v-model="loginForm.confirmPassword"
            autocomplete="new-password"
            placeholder="请再次输入密码"
            :prefix-icon="Lock"
            show-password
            size="large"
            type="password"
          />
        </el-form-item>

        <div class="form-meta">
          <el-checkbox v-model="rememberMe">记住用户</el-checkbox>
        </div>

        <el-button
          class="login-button"
          :loading="loading"
          size="large"
          type="primary"
          @click="handleSubmit"
        >
          {{ submitText }}
        </el-button>
      </el-form>

      <footer class="login-footer">
        <span>{{ footerText }}</span>
        <button class="text-button is-strong" type="button" @click="toggleAuthMode">
          {{ footerActionText }}
        </button>
      </footer>
    </section>
  </main>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Lock, OfficeBuilding, User } from '@element-plus/icons-vue';
import { useAuthStore } from '../stores/auth';
import wenquLogoIcon from '../assets/wenqu-logo-b-icon-white.png';

const router = useRouter();
const authStore = useAuthStore();

const loginFormRef = ref(null);
const loading = ref(false);
const authMode = ref('login');
const rememberMe = ref(false);
const loginForm = reactive({
  tenantCode: '',
  username: '',
  password: '',
  confirmPassword: ''
});

// 初始化：如果 localStorage 记录了租户代码，自动填充并勾选
if (typeof localStorage !== 'undefined') {
  const savedTenantCode = localStorage.getItem('wenqu_remembered_tenant');
  if (savedTenantCode) {
    loginForm.tenantCode = savedTenantCode;
    rememberMe.value = true;
  }
}

const validateConfirmPassword = (_rule, value, callback) => {
  if (authMode.value !== 'register') {
    callback();
    return;
  }

  if (!value) {
    callback(new Error('请再次输入密码'));
    return;
  }

  if (value !== loginForm.password) {
    callback(new Error('两次输入的密码不一致'));
    return;
  }

  callback();
};

const authRules = computed(() => ({
  tenantCode: [{ required: true, message: '请输入租户代码' }],
  username: [
    { required: true, message: '请输入用户名' },
    { min: 2, max: 20, message: '用户名长度为 2-20 个字符' }
  ],
  password: [
    { required: true, message: '请输入密码' },
    { min: 6, max: 20, message: '密码长度为 6-20 个字符' }
  ],
  confirmPassword:
    authMode.value === 'register'
      ? [{ validator: validateConfirmPassword }]
      : []
}));

const submitText = computed(() => (authMode.value === 'login' ? '立即登录' : '注册并登录'));
const footerText = computed(() => (authMode.value === 'login' ? '还没有账号？' : '已有账号？'));
const footerActionText = computed(() => (authMode.value === 'login' ? '立即注册' : '返回登录'));

const setAuthMode = async (mode) => {
  if (authMode.value === mode) {
    return;
  }

  authMode.value = mode;
  loginFormRef.value?.resetFields();
};

const toggleAuthMode = () => {
  setAuthMode(authMode.value === 'login' ? 'register' : 'login');
};

const handleSubmit = async () => {
  if (loading.value) {
    return;
  }

  const valid = await loginFormRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }

  loading.value = true;

  try {
    const payload = {
      tenantCode: loginForm.tenantCode,
      username: loginForm.username,
      password: loginForm.password
    };

    if (authMode.value === 'login') {
      await authStore.login(payload);
      ElMessage.success('登录成功');
    } else {
      await authStore.register(payload);
      ElMessage.success('注册成功');
    }

    // 根据复选框状态持久化或清除租户代码
    if (typeof localStorage !== 'undefined') {
      if (rememberMe.value) {
        localStorage.setItem('wenqu_remembered_tenant', loginForm.tenantCode);
      } else {
        localStorage.removeItem('wenqu_remembered_tenant');
      }
    }

    router.push('/chat');
  } catch {
    // 请求拦截器已经展示了服务端错误信息。
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) 480px;
  align-items: center;
  gap: 52px;
  overflow: hidden;
  background:
    radial-gradient(circle at 18% 80%, rgba(23, 105, 255, 0.11), transparent 30%),
    linear-gradient(180deg, #ffffff 0%, #f3f7ff 100%);
  padding: 64px clamp(36px, 7vw, 120px);
}

.login-hero {
  max-width: 640px;
}

.hero-brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 42px;
}

.brand-mark {
  width: 44px;
  height: 44px;
  display: block;
  flex: 0 0 auto;
  border-radius: 12px;
  background: #ffffff;
  object-fit: cover;
}

.hero-brand strong {
  color: #0b1220;
  font-size: 32px;
  font-weight: 850;
}

.login-hero h1 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: clamp(36px, 5vw, 56px);
  font-weight: 850;
  line-height: 1.12;
}

.login-hero p {
  max-width: 560px;
  margin: 24px 0 0;
  color: #475467;
  font-size: 17px;
  font-weight: 500;
  line-height: 1.85;
}

.hero-points {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 34px;
}

.hero-points span {
  height: 34px;
  display: inline-flex;
  align-items: center;
  border: 1px solid #d6e4ff;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  color: #175cd3;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 800;
}

.login-card {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 24px 60px rgba(16, 24, 40, 0.1);
  padding: 28px;
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 22px;
}

.card-header h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 24px;
  font-weight: 850;
}

.card-header p {
  margin: 8px 0 0;
  color: var(--color-text-tertiary);
  font-size: 13px;
}

.auth-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-secondary);
  padding: 4px;
  margin-bottom: 22px;
}

.auth-switch__item {
  height: 36px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: 14px;
  font-weight: 750;
}

.auth-switch__item.active {
  background: #ffffff;
  color: var(--color-primary);
  box-shadow: 0 8px 16px rgba(16, 24, 40, 0.08);
}

.login-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.login-form :deep(.el-form-item__label) {
  color: #344054;
  font-size: 13px;
  font-weight: 700;
}

.login-form :deep(.el-input__wrapper) {
  min-height: 44px;
  background: #ffffff;
}

.form-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: -2px 0 20px;
  color: var(--color-text-tertiary);
  font-size: 12px;
}

.text-button {
  border: 0;
  background: transparent;
  color: var(--color-primary);
  cursor: pointer;
  padding: 0;
  font-size: 13px;
  font-weight: 800;
  white-space: nowrap;
}

.login-button {
  width: 100%;
  min-height: 46px;
  font-weight: 800;
}

.login-footer {
  display: flex;
  justify-content: center;
  gap: 6px;
  margin-top: 22px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

@media (max-width: 920px) {
  .login-page {
    grid-template-columns: 1fr;
    gap: 32px;
    padding: 38px 20px;
  }

  .login-hero {
    max-width: none;
  }

  .login-card {
    max-width: 480px;
    justify-self: center;
  }
}
</style>
