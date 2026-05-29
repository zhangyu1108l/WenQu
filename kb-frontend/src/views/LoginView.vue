<template>
  <main class="login-page">
    <div class="login-aura" aria-hidden="true" />

    <section class="login-shell">
      <header class="login-brand">
        <div class="brand-mark" aria-hidden="true">
          <span class="brand-mark__core" />
        </div>
        <h1>WenQu</h1>
        <p>企 业 智 能 知 识 库</p>
      </header>

      <section class="login-card" aria-label="登录">
        <el-form
          ref="loginFormRef"
          class="login-form"
          :model="loginForm"
          :rules="loginRules"
          label-position="top"
          @keyup.enter="handleLogin"
        >
          <el-form-item label="租户代码" prop="tenantCode">
            <el-input
              v-model.trim="loginForm.tenantCode"
              autocomplete="organization"
              clearable
              placeholder="请输入租户代码"
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
              autocomplete="current-password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              size="large"
              type="password"
            />
          </el-form-item>

          <div class="login-options">
            <el-checkbox v-model="rememberMe">记住我</el-checkbox>
            <button class="text-button" type="button">忘记密码?</button>
          </div>

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

        <footer class="login-footer">
          <span>还没有账号?</span>
          <button class="text-button is-strong" type="button">立即注册</button>
        </footer>
      </section>

      <p class="copyright">© 2026 WenQu. 保留所有权利。</p>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Lock, OfficeBuilding, User } from '@element-plus/icons-vue';
import { useAuthStore } from '../stores/auth';

const router = useRouter();
const authStore = useAuthStore();

const loginFormRef = ref(null);
const loading = ref(false);
const rememberMe = ref(false);

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
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 18px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(246, 249, 255, 0.96)),
    linear-gradient(135deg, #f8fbff 0%, #ffffff 48%, #eef5ff 100%);
}

.login-page::before,
.login-page::after {
  position: absolute;
  right: -8%;
  bottom: -22%;
  left: -8%;
  height: 38%;
  content: '';
  pointer-events: none;
}

.login-page::before {
  background:
    repeating-linear-gradient(
      168deg,
      rgba(63, 109, 246, 0.14) 0 1px,
      transparent 1px 16px
    );
  clip-path: ellipse(68% 54% at 36% 100%);
}

.login-page::after {
  background: linear-gradient(120deg, rgba(63, 109, 246, 0.18), rgba(34, 199, 232, 0.2));
  clip-path: ellipse(74% 58% at 22% 100%);
  opacity: 0.72;
}

.login-aura {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(120deg, rgba(63, 109, 246, 0.08), transparent 30%),
    linear-gradient(240deg, rgba(34, 199, 232, 0.08), transparent 38%);
  pointer-events: none;
}

.login-shell {
  position: relative;
  z-index: 1;
  width: min(420px, 100%);
  display: grid;
  justify-items: center;
}

.login-card {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: var(--color-shadow);
  padding: 26px 28px 24px;
  backdrop-filter: blur(18px);
}

.login-brand {
  display: grid;
  justify-items: center;
  margin-bottom: 28px;
  text-align: center;
}

.brand-mark {
  position: relative;
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: linear-gradient(145deg, #4f7cff, #20d2e7);
  box-shadow: 0 16px 34px rgba(63, 109, 246, 0.25);
  transform: rotate(45deg);
}

.brand-mark::before,
.brand-mark::after,
.brand-mark__core {
  position: absolute;
  content: '';
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.78);
}

.brand-mark::before {
  width: 16px;
  height: 16px;
  top: 9px;
  left: 9px;
}

.brand-mark::after {
  width: 16px;
  height: 16px;
  right: 9px;
  bottom: 9px;
}

.brand-mark__core {
  width: 16px;
  height: 16px;
  right: 9px;
  top: 9px;
  background: rgba(255, 255, 255, 0.48);
}

.login-brand h1 {
  margin: 26px 0 0;
  color: var(--color-text-primary);
  font-size: 34px;
  font-weight: 760;
  line-height: 1.1;
}

.login-brand p {
  margin: 14px 0 0;
  color: #344054;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 19px;
}

.login-form :deep(.el-form-item__label) {
  color: #344054;
  font-size: 13px;
  font-weight: 600;
}

.login-form :deep(.el-input__wrapper) {
  min-height: 44px;
  background: rgba(255, 255, 255, 0.92);
}

.login-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: -2px 0 18px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.text-button {
  border: 0;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  padding: 0;
}

.text-button:hover,
.text-button.is-strong {
  color: var(--color-primary);
}

.login-button {
  width: 100%;
  min-height: 46px;
  font-weight: 650;
}

.login-footer {
  display: flex;
  justify-content: center;
  gap: 6px;
  margin-top: 22px;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.copyright {
  margin: 24px 0 0;
  color: var(--color-text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 480px) {
  .login-card {
    padding: 24px 20px 22px;
  }

  .login-brand h1 {
    font-size: 30px;
  }
}
</style>
