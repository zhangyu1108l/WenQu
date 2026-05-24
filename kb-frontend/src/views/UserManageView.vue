<template>
  <div v-if="isAllowed" class="user-manage-view">
    <header class="page-toolbar">
      <h2>用户管理</h2>
    </header>

    <section class="table-wrap">
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

        <el-table-column label="角色" width="140">
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

        <el-table-column label="操作" min-width="260" fixed="right">
          <template #default="{ row }">
            <div class="role-action">
              <el-select
                class="role-select"
                :disabled="isSelf(row) || roleSubmittingMap[getUserId(row)]"
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
                width="240"
                @cancel="cancelRoleChange(row)"
                @confirm="confirmRoleChange(row)"
              >
                <template #reference>
                  <el-button
                    link
                    type="primary"
                    :disabled="isSelf(row) || !hasRoleChanged(row)"
                    :loading="Boolean(roleSubmittingMap[getUserId(row)])"
                  >
                    修改角色
                  </el-button>
                </template>
              </el-popconfirm>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import dayjs from 'dayjs';
import * as adminApi from '../api/admin';
import { useAuthStore } from '../stores/auth';

const ROLE_VALUE_MAP = {
  SUPER_ADMIN: 0,
  TENANT_ADMIN: 1,
  USER: 2
};

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

const router = useRouter();
const authStore = useAuthStore();

const loading = ref(false);
const userList = ref([]);
const roleDraftMap = reactive({});
const roleSubmittingMap = reactive({});

const currentRole = computed(() => normalizeRole(authStore.userInfo?.role));
const currentUserId = computed(() => authStore.userInfo?.userId ?? authStore.userInfo?.id);
const isAllowed = computed(() => currentRole.value === 0 || currentRole.value === 1);

const visibleUsers = computed(() =>
  userList.value.filter((user) => {
    // 超级管理员不在此列表中：前端再次过滤 role=0，避免租户管理员误操作超级管理员账号。
    return getUserRole(user) !== 0;
  })
);

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

const isEnabled = (status) => status === 1 || status === true || status === '1' || status === 'ENABLED';

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

  // 禁止修改自己的角色：当前用户 ID 与行记录 ID 相同则禁用角色下拉和提交按钮，避免把自己降权后失去管理入口。
  return String(rowUserId) === String(currentUserId.value);
};

const handleRoleSelect = (row, nextRole) => {
  const userId = getUserId(row);

  if (userId === null || userId === undefined) {
    return;
  }

  roleDraftMap[userId] = normalizeRole(nextRole);
};

const hasRoleChanged = (row) => getDraftRole(row) !== getUserRole(row);

const getRoleConfirmTitle = (row) =>
  `确认将 ${getUsername(row)} 的角色修改为${getRoleLabel(getDraftRole(row))}？`;

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
  if (!isAllowed.value) {
    ElMessage.warning('仅管理员可访问用户管理');
    router.replace('/chat');
    return;
  }

  loadUsers();
});
</script>

<style scoped>
.user-manage-view {
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

.entity-name {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  color: var(--color-text-primary);
  font-weight: 600;
  line-height: 1.4;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}

.role-action {
  display: flex;
  align-items: center;
  gap: 10px;
}

.role-select {
  width: 132px;
  flex: 0 0 132px;
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
