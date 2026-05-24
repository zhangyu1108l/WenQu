<template>
  <div v-if="isSuperAdmin" class="tenant-manage-view">
    <header class="page-toolbar">
      <h2>租户管理</h2>

      <el-button type="primary" @click="openCreateDialog">
        <el-icon>
          <Plus />
        </el-icon>
        <span>新建租户</span>
      </el-button>
    </header>

    <section class="table-wrap">
      <el-table
        v-loading="loading"
        :data="tenantList"
        empty-text="暂无租户"
        row-key="id"
        stripe
      >
        <el-table-column label="租户名称" min-width="180">
          <template #default="{ row }">
            <span class="entity-name">{{ row.name || '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="标识Code" min-width="180">
          <template #default="{ row }">
            <span class="tenant-code">{{ row.code || '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="isTenantEnabled(row) ? 'success' : 'info'" effect="light">
              {{ isTenantEnabled(row) ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt || row.created_at) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <!-- 禁用租户后该租户所有用户无法登录，因此状态切换前必须二次确认。 -->
            <el-popconfirm
              cancel-button-text="取消"
              confirm-button-text="确认"
              :title="getTenantStatusConfirmTitle(row)"
              width="270"
              @confirm="confirmTenantStatus(row)"
            >
              <template #reference>
                <el-switch
                  :before-change="preventDirectSwitchChange"
                  :disabled="Boolean(statusSubmittingMap[getTenantId(row)])"
                  :loading="Boolean(statusSubmittingMap[getTenantId(row)])"
                  :model-value="isTenantEnabled(row)"
                  active-text="启用"
                  inactive-text="禁用"
                  inline-prompt
                />
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog
      v-model="createDialogVisible"
      title="新建租户"
      width="520px"
      @closed="resetCreateForm"
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-position="top"
      >
        <el-form-item label="租户名称" prop="name">
          <el-input v-model.trim="createForm.name" maxlength="100" placeholder="请输入租户名称" />
        </el-form-item>

        <el-form-item label="租户标识Code" prop="code">
          <el-input v-model.trim="createForm.code" maxlength="50" placeholder="例如 acme-tech" />
        </el-form-item>

        <el-form-item label="初始管理员用户名" prop="adminUsername">
          <el-input
            v-model.trim="createForm.adminUsername"
            maxlength="50"
            placeholder="请输入管理员用户名"
          />
        </el-form-item>

        <el-form-item label="初始管理员密码" prop="adminPassword">
          <el-input
            v-model="createForm.adminPassword"
            maxlength="64"
            placeholder="请输入管理员密码"
            show-password
            type="password"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreateTenant">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import * as adminApi from '../api/admin';
import { useAuthStore } from '../stores/auth';

const ROLE_VALUE_MAP = {
  SUPER_ADMIN: 0,
  TENANT_ADMIN: 1,
  USER: 2
};

const router = useRouter();
const authStore = useAuthStore();

const loading = ref(false);
const tenantList = ref([]);
const createDialogVisible = ref(false);
const createSubmitting = ref(false);
const createFormRef = ref(null);
const statusSubmittingMap = reactive({});

const createForm = reactive({
  name: '',
  code: '',
  adminUsername: '',
  adminPassword: ''
});

// 租户标识只允许小写英文、数字、短横线，与 MinIO Bucket 命名规则 tenant-{tenantId} 保持一致。
const tenantCodePattern = /^[a-z0-9-]+$/;

const createRules = {
  name: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入租户标识Code', trigger: 'blur' },
    {
      pattern: tenantCodePattern,
      message: '只允许小写英文、数字、短横线',
      trigger: 'blur'
    }
  ],
  adminUsername: [{ required: true, message: '请输入初始管理员用户名', trigger: 'blur' }],
  adminPassword: [{ required: true, message: '请输入初始管理员密码', trigger: 'blur' }]
};

const currentRole = computed(() => normalizeRole(authStore.userInfo?.role));
const isSuperAdmin = computed(() => currentRole.value === 0);

const normalizeRole = (role) => {
  if (role === null || role === undefined || role === '') {
    return null;
  }

  if (typeof role === 'number') {
    return role;
  }

  if (ROLE_VALUE_MAP[role] !== undefined) {
    return ROLE_VALUE_MAP[role];
  }

  const numberRole = Number(role);
  return Number.isNaN(numberRole) ? null : numberRole;
};

const normalizeList = (data) => {
  if (Array.isArray(data)) {
    return data;
  }

  return data?.records || data?.list || data?.items || [];
};

const loadTenants = async () => {
  loading.value = true;

  try {
    const data = await adminApi.getTenantList();
    tenantList.value = normalizeList(data);
  } finally {
    loading.value = false;
  }
};

const getTenantId = (row) => row?.id ?? row?.tenantId;

const isTenantEnabled = (row) =>
  row?.status === 1 || row?.status === true || row?.status === '1' || row?.status === 'ENABLED';

const getNextTenantStatus = (row) => (isTenantEnabled(row) ? 0 : 1);

const getTenantStatusConfirmTitle = (row) =>
  isTenantEnabled(row)
    ? `确认禁用租户 ${row.name || row.code || ''}？禁用后该租户所有用户无法登录。`
    : `确认启用租户 ${row.name || row.code || ''}？`;

const preventDirectSwitchChange = () => false;

const formatDateTime = (value) => {
  if (!value) {
    return '-';
  }

  const date = dayjs(value);
  return date.isValid() ? date.format('YYYY-MM-DD HH:mm:ss') : '-';
};

const openCreateDialog = () => {
  createDialogVisible.value = true;
};

const resetCreateForm = () => {
  createForm.name = '';
  createForm.code = '';
  createForm.adminUsername = '';
  createForm.adminPassword = '';
  createFormRef.value?.clearValidate();
};

const submitCreateTenant = async () => {
  await createFormRef.value?.validate();
  createSubmitting.value = true;

  try {
    await adminApi.createTenant({
      name: createForm.name,
      code: createForm.code,
      adminUsername: createForm.adminUsername,
      adminPassword: createForm.adminPassword
    });
    ElMessage.success('租户已创建');
    createDialogVisible.value = false;
    await loadTenants();
  } finally {
    createSubmitting.value = false;
  }
};

const confirmTenantStatus = async (row) => {
  const tenantId = getTenantId(row);

  if (tenantId === null || tenantId === undefined) {
    return;
  }

  statusSubmittingMap[tenantId] = true;

  try {
    await adminApi.updateTenantStatus(tenantId, {
      status: getNextTenantStatus(row)
    });
    ElMessage.success('租户状态已更新');
    await loadTenants();
  } finally {
    statusSubmittingMap[tenantId] = false;
  }
};

onMounted(() => {
  if (!isSuperAdmin.value) {
    ElMessage.warning('仅超级管理员可访问租户管理');
    router.replace('/chat');
    return;
  }

  loadTenants();
});
</script>

<style scoped>
.tenant-manage-view {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #ffffff;
}

.page-toolbar {
  height: 68px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--color-border);
  padding: 0 28px;
}

.page-toolbar h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 20px;
  font-weight: 700;
  line-height: 1.3;
}

.table-wrap {
  min-height: 0;
  flex: 1;
  padding: 0 28px;
  overflow: auto;
}

.table-wrap :deep(.el-table) {
  --el-table-header-bg-color: #ffffff;
  --el-table-row-hover-bg-color: rgba(16, 163, 127, 0.06);
}

.entity-name,
.tenant-code {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  color: var(--color-text-primary);
  line-height: 1.4;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}

.entity-name {
  font-weight: 600;
}

.tenant-code {
  color: var(--color-text-secondary);
  font-family: ui-monospace, SFMono-Regular, Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
}

:deep(.el-dialog__body) {
  padding-top: 8px;
}

@media (max-width: 900px) {
  .page-toolbar,
  .table-wrap {
    padding-left: 18px;
    padding-right: 18px;
  }

  .page-toolbar {
    height: auto;
    align-items: flex-start;
    flex-direction: column;
    padding-top: 16px;
    padding-bottom: 16px;
  }
}
</style>
