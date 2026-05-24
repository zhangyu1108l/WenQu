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
          <!-- tenantCode 是租户唯一标识：多租户系统中，不同公司使用不同 code 登录。 -->
          <el-input
            v-model.trim="loginForm.tenantCode"
            placeholder="公司标识"
            size="large"
            clearable
          />
        </el-form-item>

        <el-form-item label="用户名" prop="username">
          <el-input
            v-model.trim="loginForm.username"
            placeholder="请输入用户名"
            size="large"
            clearable
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="loginForm.password"
            placeholder="请输入密码"
            type="password"
            size="large"
            show-password
          />
        </el-form-item>

        <el-button
          class="login-button"
          type="primary"
          size="large"
          :loading="loading"
          @click="handleLogin"
        >
          登录
        </el-button>
      </el-form>

      <footer class="login-footer">© 2026 问渠 WenQu</footer>
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
  // 必填：租户标识用于定位当前登录用户所属公司，缺失时无法完成租户隔离。
  tenantCode: [{ required: true, message: '请输入公司标识', trigger: 'blur' }],
  // 必填与长度限制：用户名是租户内登录身份，2~20 字符避免无效账号与异常长输入。
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度为 2~20 个字符', trigger: 'blur' }
  ],
  // 必填与长度限制：密码用于身份校验，6~20 字符符合当前账号密码策略。
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为 6~20 个字符', trigger: 'blur' }
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

    // 登录成功后统一跳转到 /chat；普通用户和管理员先进入对话页，后续通过 role 控制菜单显示。
    router.push('/chat');
  } catch (error) {
    // 登录失败时使用 Element Plus 的 ElMessage.error 进行页面提示，展示后端错误信息或通用文案。
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
  padding: 40px 36px 28px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-primary);
  box-shadow: 0 18px 48px rgba(13, 13, 13, 0.08);
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

.login-form {
  width: 100%;
}

.login-button {
  width: 100%;
  margin-top: 4px;
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.login-button:hover,
.login-button:focus {
  background: var(--color-primary-hover);
  border-color: var(--color-primary-hover);
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

  .login-header {
    margin-bottom: 28px;
  }
}
</style>
