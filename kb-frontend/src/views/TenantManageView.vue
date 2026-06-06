<template>
  <div class="tenant-manage-view">
    <header class="page-header">
      <div>
        <h2>租户管理</h2>
        <p>超级管理员可创建租户，并启用或禁用租户访问。</p>
      </div>

      <el-button type="primary" @click="openCreateDialog">
        <el-icon>
          <Plus />
        </el-icon>
        <span>新建租户</span>
      </el-button>
    </header>

    <section class="summary-grid">
      <article class="summary-card">
        <span>租户总数</span>
        <strong>{{ tenantList.length }}</strong>
      </article>
      <article class="summary-card">
        <span>启用租户</span>
        <strong>{{ enabledTenantCount }}</strong>
      </article>
      <article class="summary-card">
        <span>禁用租户</span>
        <strong>{{ tenantList.length - enabledTenantCount }}</strong>
      </article>
    </section>

    <section class="table-card">
      <header class="panel-head">
        <h3>租户列表</h3>
      </header>

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

        <el-table-column label="租户标识 Code" min-width="180">
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

        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-popconfirm
              cancel-button-text="取消"
              confirm-button-text="确认"
              :title="getTenantStatusConfirmTitle(row)"
              width="280"
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
          <el-input
            v-model.trim="createForm.name"
            maxlength="100"
            placeholder="请输入租户名称"
          />
        </el-form-item>

        <el-form-item label="租户标识 Code" prop="code">
          <el-input
            v-model.trim="createForm.code"
            maxlength="50"
            placeholder="例如 acme-tech"
          />
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
        <el-button :loading="createSubmitting" type="primary" @click="submitCreateTenant">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import * as adminApi from '../api/admin';

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

const tenantCodePattern = /^[a-z0-9-]+$/;

const createRules = {
  name: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入租户标识 Code', trigger: 'blur' },
    {
      pattern: tenantCodePattern,
      message: '仅允许小写英文、数字、短横线',
      trigger: 'blur'
    }
  ],
  adminUsername: [{ required: true, message: '请输入初始管理员用户名', trigger: 'blur' }],
  adminPassword: [{ required: true, message: '请输入初始管理员密码', trigger: 'blur' }]
};

const enabledTenantCount = computed(() =>
  tenantList.value.filter((tenant) => isTenantEnabled(tenant)).length
);

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
    ? `确认禁用租户 ${row.name || row.code || ''}？禁用后该租户用户将无法登录。`
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
  loadTenants();
});
</script>

<style scoped>
.tenant-manage-view {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: var(--color-bg-secondary);
  padding: 18px;
}

.page-header,
.table-card,
.summary-card {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 18px 40px rgba(16, 24, 40, 0.04);
}

.page-header {
  min-height: 76px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 0 20px;
}

.page-header h2,
.panel-head h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 22px;
  font-weight: 850;
}

.page-header p {
  margin: 8px 0 0;
  color: var(--color-text-tertiary);
  font-size: 13px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  padding: 18px;
}

.summary-card span {
  color: #667085;
  font-size: 13px;
  font-weight: 800;
}

.summary-card strong {
  display: block;
  margin-top: 10px;
  color: var(--color-text-primary);
  font-size: 32px;
  font-weight: 900;
  line-height: 1;
}

.table-card {
  overflow: hidden;
}

.panel-head {
  min-height: 64px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--color-border);
  padding: 0 18px;
}

.panel-head h3 {
  font-size: 18px;
}

.entity-name {
  color: var(--color-text-primary);
  font-weight: 800;
}

.tenant-code {
  font-family: Consolas, 'SFMono-Regular', monospace;
  color: #175cd3;
  font-weight: 800;
}

@media (max-width: 860px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .page-header {
    align-items: flex-start;
    flex-direction: column;
    padding: 16px 18px;
  }
}
</style>
