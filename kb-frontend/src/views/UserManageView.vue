<template>
  <div class="user-manage-view">
    <header class="page-header">
      <div>
        <h2>用户管理</h2>
        <p>查看本租户用户，按权限修改普通用户与租户管理员角色。</p>
      </div>
      <el-button type="primary" plain @click="loadUsers">刷新列表</el-button>
    </header>

    <section class="summary-grid">
      <article class="summary-card">
        <span>用户总数</span>
        <strong>{{ visibleUsers.length }}</strong>
      </article>
      <article class="summary-card">
        <span>租户管理员</span>
        <strong>{{ countByRole(1) }}</strong>
      </article>
      <article class="summary-card">
        <span>普通用户</span>
        <strong>{{ countByRole(2) }}</strong>
      </article>
    </section>

    <section class="table-card">
      <header class="panel-head">
        <h3>用户列表</h3>
      </header>

      <el-table
        v-loading="loading"
        :data="visibleUsers"
        empty-text="暂无用户"
        row-key="id"
        stripe
      >
        <el-table-column label="用户名" min-width="180">
          <template #default="{ row }">
            <span class="entity-name">{{ getUsername(row) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="角色" width="170">
          <template #default="{ row }">
            <el-tag :type="getRoleTagType(getUserRole(row))" effect="light">
              {{ getRoleLabel(getUserRole(row)) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="isEnabled(row.status) ? 'success' : 'info'" effect="light">
              {{ isEnabled(row.status) ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt || row.created_at) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" min-width="280" fixed="right">
          <template #default="{ row }">
            <div v-if="canEditRole(row)" class="role-action">
              <el-select
                class="role-select"
                :disabled="roleSubmittingMap[getUserId(row)]"
                :model-value="getDraftRole(row)"
                placeholder="选择角色"
                size="small"
                @change="(nextRole) => handleRoleSelect(row, nextRole)"
              >
                <el-option
                  v-for="role in editableRoleOptions"
                  :key="role.value"
                  :label="role.label"
                  :value="role.value"
                />
              </el-select>

              <el-popconfirm
                cancel-button-text="取消"
                confirm-button-text="确认修改"
                :title="getRoleConfirmTitle(row)"
                width="260"
                @cancel="cancelRoleChange(row)"
                @confirm="confirmRoleChange(row)"
              >
                <template #reference>
                  <el-button
                    link
                    type="primary"
                    :disabled="!hasRoleChanged(row)"
                    :loading="Boolean(roleSubmittingMap[getUserId(row)])"
                  >
                    修改角色
                  </el-button>
                </template>
              </el-popconfirm>
            </div>
            <span v-else class="readonly-action">不可修改</span>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import dayjs from 'dayjs';
import * as adminApi from '../api/admin';
import { normalizeRole, useAuthStore } from '../stores/auth';

const ROLE_LABEL_MAP = {
  0: '超级管理员',
  1: '租户管理员',
  2: '普通用户'
};

const ROLE_TAG_TYPE_MAP = {
  0: 'danger',
  1: 'warning',
  2: 'info'
};

const editableRoleOptions = [
  { label: '租户管理员', value: 1 },
  { label: '普通用户', value: 2 }
];

const authStore = useAuthStore();

const loading = ref(false);
const userList = ref([]);
const roleDraftMap = reactive({});
const roleSubmittingMap = reactive({});

const currentUserId = computed(() => authStore.userInfo?.userId ?? authStore.userInfo?.id);

const visibleUsers = computed(() => userList.value);

const normalizeList = (data) => {
  if (Array.isArray(data)) {
    return data;
  }

  return data?.records || data?.list || data?.items || [];
};

const countByRole = (role) =>
  visibleUsers.value.filter((user) => getUserRole(user) === role).length;

const resetRoleDrafts = (list) => {
  Object.keys(roleDraftMap).forEach((key) => {
    delete roleDraftMap[key];
  });

  list.forEach((user) => {
    const userId = getUserId(user);

    if (userId !== null && userId !== undefined) {
      roleDraftMap[userId] = getUserRole(user);
    }
  });
};

const loadUsers = async () => {
  loading.value = true;

  try {
    const data = await adminApi.getUserList();
    const list = normalizeList(data);
    userList.value = list;
    resetRoleDrafts(list);
  } finally {
    loading.value = false;
  }
};

const getUserId = (row) => row?.id ?? row?.userId;

const getUsername = (row) => row?.username || '-';

const getUserRole = (row) => normalizeRole(row?.role);

const getDraftRole = (row) => {
  const userId = getUserId(row);
  return roleDraftMap[userId] ?? getUserRole(row);
};

const getRoleLabel = (role) => ROLE_LABEL_MAP[normalizeRole(role)] || '未知角色';

const getRoleTagType = (role) => ROLE_TAG_TYPE_MAP[normalizeRole(role)] || 'info';

const isEnabled = (status) =>
  status === 1 || status === true || status === '1' || status === 'ENABLED';

const formatDateTime = (value) => {
  if (!value) {
    return '-';
  }

  const date = dayjs(value);
  return date.isValid() ? date.format('YYYY-MM-DD HH:mm:ss') : '-';
};

const isSelf = (row) => {
  const rowUserId = getUserId(row);

  if (rowUserId === null || rowUserId === undefined || currentUserId.value === null || currentUserId.value === undefined) {
    return false;
  }

  return String(rowUserId) === String(currentUserId.value);
};

const canEditRole = (row) => !isSelf(row) && getUserRole(row) !== 0;

const handleRoleSelect = (row, nextRole) => {
  const userId = getUserId(row);

  if (userId === null || userId === undefined) {
    return;
  }

  roleDraftMap[userId] = normalizeRole(nextRole);
};

const hasRoleChanged = (row) => getDraftRole(row) !== getUserRole(row);

const getRoleConfirmTitle = (row) =>
  `确认将 ${getUsername(row)} 的角色修改为 ${getRoleLabel(getDraftRole(row))}？`;

const cancelRoleChange = (row) => {
  const userId = getUserId(row);

  if (userId !== null && userId !== undefined) {
    roleDraftMap[userId] = getUserRole(row);
  }
};

const confirmRoleChange = async (row) => {
  const userId = getUserId(row);
  const nextRole = getDraftRole(row);

  if (userId === null || userId === undefined || !hasRoleChanged(row)) {
    return;
  }

  roleSubmittingMap[userId] = true;

  try {
    await adminApi.updateUserRole(userId, { role: nextRole });
    ElMessage.success('用户角色已更新');
    await loadUsers();
  } finally {
    roleSubmittingMap[userId] = false;
  }
};

onMounted(() => {
  loadUsers();
});
</script>

<style scoped>
.user-manage-view {
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

.role-action {
  display: flex;
  align-items: center;
  gap: 10px;
}

.role-select {
  width: 150px;
}

.readonly-action {
  color: var(--color-text-tertiary);
  font-size: 13px;
  font-weight: 700;
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
