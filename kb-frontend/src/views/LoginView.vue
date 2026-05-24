<template>
  <main class="login-page">
    <section class="login-card">
      <header class="login-header">
        <div class="brand-logo">问</div>
        <div>
          <h1 class="brand-name">问渠</h1>
          <p class="brand-subtitle">企业智能知识库</p>
        </div>
      </header>

      <el-form
        ref="loginFormRef"
        class="login-form"
        :model="loginForm"
        :rules="loginRules"
        label-position="top"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="租户标识" prop="tenantCode">
          <el-input
            v-model.trim="loginForm.tenantCode"
            clearable
            placeholder="例如 acme-tech"
            size="large"
          />
        </el-form-item>

        <el-form-item label="用户名" prop="username">
          <el-input
            v-model.trim="loginForm.username"
            clearable
            placeholder="请输入用户名"
            size="large"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="loginForm.password"
            placeholder="请输入密码"
            show-password
            size="large"
            type="password"
          />
        </el-form-item>

        <el-button
          class="login-button"
          :loading="loading"
          size="large"
          type="primary"
          @click="handleLogin"
        >
          登录
        </el-button>
      </el-form>

      <footer class="login-footer">WenQu 2026</footer>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../stores/auth';

const router = useRouter();
const authStore = useAuthStore();

const loginFormRef = ref(null);
const loading = ref(false);

const loginForm = reactive({
  tenantCode: '',
  username: '',
  password: ''
});

const loginRules = {
  tenantCode: [{ required: true, message: '请输入租户标识', trigger: 'blur' }],
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 50, message: '用户名长度为 2-50 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度为 6-64 个字符', trigger: 'blur' }
  ]
};

const handleLogin = async () => {
  if (loading.value) {
    return;
  }

  const valid = await loginFormRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }

  loading.value = true;

  try {
    await authStore.login({
      tenantCode: loginForm.tenantCode,
      username: loginForm.username,
      password: loginForm.password
    });
    router.push('/chat');
  } catch (error) {
    ElMessage.error(error?.message || '登录失败，请检查账号信息');
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  background: var(--color-bg-secondary);
}

.login-card {
  width: min(400px, 100%);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-primary);
  padding: 40px 36px 28px;
}

.login-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 32px;
}

.brand-logo {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 8px;
  color: #ffffff;
  background: var(--color-primary);
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
}

.brand-name {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
}

.brand-subtitle {
  margin: 6px 0 0;
  color: var(--color-text-secondary);
  font-size: 14px;
  line-height: 1.4;
}

.login-button {
  width: 100%;
  margin-top: 4px;
}

.login-footer {
  margin-top: 28px;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
}

@media (max-width: 480px) {
  .login-card {
    padding: 32px 24px 24px;
  }
}
</style>
