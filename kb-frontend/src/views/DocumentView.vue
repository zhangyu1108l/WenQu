<template>
  <div class="document-view">
    <header class="document-toolbar">
      <h2>知识库文档</h2>

      <el-upload
        v-if="canManageDocs"
        ref="uploadRef"
        accept=".pdf,.docx"
        :auto-upload="true"
        :before-upload="beforeUpload"
        :http-request="uploadDocument"
        :on-error="handleUploadError"
        :on-success="handleUploadSuccess"
        :show-file-list="false"
      >
        <el-button :loading="uploading" type="primary">
          <el-icon>
            <UploadFilled />
          </el-icon>
          <span>上传文档</span>
        </el-button>
      </el-upload>
    </header>

    <section class="search-bar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索文档名称"
        :prefix-icon="Search"
      />
    </section>

    <section v-if="activeUploadTasks.length" class="upload-task-panel">
      <header class="upload-task-panel__header">
        <span>上传处理进度</span>
      </header>

      <TaskProgressBar
        v-for="task in activeUploadTasks"
        :key="task.taskId"
        :doc-id="task.docId"
        :task-id="task.taskId"
        @done="handleTaskDone"
        @failed="handleTaskFailed"
        @progress="handleTaskProgress"
      />
    </section>

    <section class="document-table-wrap">
      <el-table
        v-loading="loading"
        :data="documentStore.docList"
        empty-text="暂无文档"
        row-key="id"
        stripe
      >
        <el-table-column label="文档名称" min-width="220">
          <template #default="{ row }">
            <span class="doc-title">{{ getDocTitle(row) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag effect="plain" size="small">{{ getFileType(row) }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <DocStatusBadge :status="getDocStatus(row)" />
          </template>
        </el-table-column>

        <el-table-column label="版本" width="90">
          <template #default="{ row }">
            {{ getRowVersionText(row) }}
          </template>
        </el-table-column>

        <el-table-column label="过期时间" min-width="160">
          <template #default="{ row }">
            {{ formatExpireAt(getExpireAt(row)) }}
          </template>
        </el-table-column>

        <el-table-column label="上传时间" min-width="160">
          <template #default="{ row }">
            {{ formatDateTime(getCreatedAt(row)) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" :width="canManageDocs ? 310 : 170" fixed="right">
          <template #default="{ row }">
            <el-space :size="4" wrap>
              <el-button link type="primary" @click="handleDownload(row)">
                <el-icon>
                  <Download />
                </el-icon>
                <span>下载</span>
              </el-button>

              <el-button link type="primary" @click="openVersionDialog(row)">
                <el-icon>
                  <Clock />
                </el-icon>
                <span>版本</span>
              </el-button>

              <el-button
                v-if="canManageDocs"
                link
                type="primary"
                @click="openExpireDialog(row)"
              >
                <el-icon>
                  <Calendar />
                </el-icon>
                <span>过期</span>
              </el-button>

              <el-popconfirm
                v-if="canManageDocs"
                cancel-button-text="取消"
                confirm-button-text="删除"
                title="确认删除该文档？"
                width="180"
                @confirm="handleDelete(row)"
              >
                <template #reference>
                  <el-button link type="danger">
                    <el-icon>
                      <Delete />
                    </el-icon>
                    <span>删除</span>
                  </el-button>
                </template>
              </el-popconfirm>
            </el-space>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <footer class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :page-sizes="[10, 20, 50]"
        :total="documentStore.total"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </footer>

    <el-dialog v-model="versionDialogVisible" title="版本历史" width="680px">
      <el-table
        v-loading="versionLoading"
        :data="versionList"
        empty-text="暂无版本"
        row-key="id"
      >
        <el-table-column label="版本号" width="100">
          <template #default="{ row }">
            {{ getVersionText(row) }}
          </template>
        </el-table-column>

        <el-table-column label="文件大小" min-width="120">
          <template #default="{ row }">
            {{ formatFileSize(getFileSize(row)) }}
          </template>
        </el-table-column>

        <el-table-column label="上传时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(getCreatedAt(row)) }}
          </template>
        </el-table-column>

        <el-table-column label="是否激活" width="110">
          <template #default="{ row }">
            <el-tag :type="isVersionActive(row) ? 'success' : 'info'" effect="light">
              {{ isVersionActive(row) ? '当前' : '历史' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="expireDialogVisible" title="设置过期时间" width="460px">
      <el-form label-position="top">
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="expireAt"
            clearable
            format="YYYY-MM-DD HH:mm:ss"
            placeholder="清空表示永不过期"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="expireDialogVisible = false">取消</el-button>
        <el-button :loading="expireSubmitting" type="primary" @click="handleSubmitExpire">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import dayjs from 'dayjs';
import {
  Calendar,
  Clock,
  Delete,
  Download,
  Search,
  UploadFilled
} from '@element-plus/icons-vue';
import * as documentApi from '../api/document';
import DocStatusBadge from '../components/DocStatusBadge.vue';
import TaskProgressBar from '../components/TaskProgressBar.vue';
import { useAuthStore } from '../stores/auth';
import { useDocumentStore } from '../stores/document';

const authStore = useAuthStore();
const documentStore = useDocumentStore();

const uploadRef = ref(null);
const keyword = ref('');
const page = ref(1);
const size = ref(10);
const loading = ref(false);
const uploading = ref(false);
const versionDialogVisible = ref(false);
const versionLoading = ref(false);
const versionList = ref([]);
const expireDialogVisible = ref(false);
const expireSubmitting = ref(false);
const expireDoc = ref(null);
const expireAt = ref('');
let searchTimer = null;

const canManageDocs = computed(() => Number(authStore.userInfo?.role) === 1);

const activeUploadTasks = computed(() =>
  Array.from(documentStore.uploadingTasks.entries()).map(([taskId, task]) => ({
    ...task,
    taskId: Number(taskId),
    docId: Number(task.docId)
  }))
);

const getDocId = (row) => row?.id ?? row?.docId ?? row?.documentId;

const getDocTitle = (row) => row?.title || row?.name || row?.fileName || '未命名文档';

const getFileType = (row) => {
  const fileType = row?.fileType ?? row?.file_type ?? '';
  return fileType ? String(fileType).toUpperCase() : '-';
};

const getDocStatus = (row) => row?.status || 'PENDING';

const getExpireAt = (row) => row?.expireAt ?? row?.expire_at ?? null;

const getCreatedAt = (row) => row?.createdAt ?? row?.created_at ?? row?.uploadTime ?? null;

const getCurrentVersion = (row) => row?.currentVersion || row?.activeVersion || {};

const getVersionNo = (row) =>
  row?.versionNo ?? row?.version_no ?? row?.currentVersionNo ?? row?.current_version_no ?? null;

const getRowVersionText = (row) => {
  const versionNo = getVersionNo(row) ?? getVersionNo(getCurrentVersion(row));
  return versionNo ? `v${versionNo}` : '-';
};

const getVersionText = (row) => {
  const versionNo = getVersionNo(row);
  return versionNo ? `v${versionNo}` : '-';
};

const getFileSize = (row) => row?.fileSize ?? row?.file_size ?? 0;

const isVersionActive = (row) => {
  const active = row?.isActive ?? row?.is_active;
  return active === true || active === 1;
};

const normalizeList = (data) => {
  if (Array.isArray(data)) {
    return data;
  }

  return data?.records || data?.list || data?.items || [];
};

const normalizeDownloadUrl = (data) => {
  if (typeof data === 'string') {
    return data;
  }

  return data?.url || data?.downloadUrl || data?.presignedUrl || '';
};

const formatDateTime = (value) => {
  if (!value) {
    return '-';
  }

  const date = dayjs(value);
  return date.isValid() ? date.format('YYYY-MM-DD HH:mm') : value;
};

const formatExpireAt = (value) => (value ? formatDateTime(value) : '永不过期');

const formatDatePickerValue = (value) => {
  if (!value) {
    return '';
  }

  const date = dayjs(value);
  return date.isValid() ? date.format('YYYY-MM-DD HH:mm:ss') : value;
};

const formatFileSize = (value) => {
  const bytes = Number(value);

  if (!Number.isFinite(bytes) || bytes < 0) {
    return '-';
  }

  if (bytes < 1024) {
    return `${bytes} B`;
  }

  const units = ['KB', 'MB', 'GB'];
  let sizeValue = bytes / 1024;
  let unitIndex = 0;

  while (sizeValue >= 1024 && unitIndex < units.length - 1) {
    sizeValue /= 1024;
    unitIndex += 1;
  }

  return `${sizeValue >= 10 ? sizeValue.toFixed(0) : sizeValue.toFixed(1)} ${units[unitIndex]}`;
};

const loadDocuments = async () => {
  loading.value = true;

  try {
    await documentStore.loadDocList({
      keyword: keyword.value.trim(),
      page: page.value,
      size: size.value
    });
  } finally {
    loading.value = false;
  }
};

const beforeUpload = (file) => {
  const fileName = file?.name?.toLowerCase() || '';
  const valid = fileName.endsWith('.pdf') || fileName.endsWith('.docx');

  if (!valid) {
    ElMessage.error('仅支持上传 PDF 或 DOCX 文档');
  }

  return valid;
};

const uploadDocument = async ({ file, onError, onSuccess }) => {
  const formData = new FormData();
  formData.append('file', file);
  uploading.value = true;

  try {
    const data = await documentApi.uploadDoc(formData);
    onSuccess(data);
  } catch (error) {
    onError(error);
  } finally {
    uploading.value = false;
    uploadRef.value?.clearFiles();
  }
};

const handleUploadSuccess = async (response) => {
  const docId = response?.docId ?? response?.documentId;
  const taskId = response?.taskId;

  if (!docId || !taskId) {
    ElMessage.warning('上传成功，但未返回任务编号');
    await loadDocuments();
    return;
  }

  documentStore.startUploadTask(docId, taskId);
  ElMessage.success('文档已上传，正在处理');
  await loadDocuments();
};

const handleUploadError = (error) => {
  ElMessage.error(error?.message || '文档上传失败');
};

const handleDownload = async (row) => {
  const docId = getDocId(row);

  if (!docId) {
    return;
  }

  const data = await documentApi.getDownloadUrl(docId);
  const url = normalizeDownloadUrl(data);

  if (!url) {
    ElMessage.warning('未获取到下载链接');
    return;
  }

  window.open(url, '_blank', 'noopener');
};

const openVersionDialog = async (row) => {
  const docId = getDocId(row);

  if (!docId) {
    return;
  }

  versionDialogVisible.value = true;
  versionLoading.value = true;

  try {
    const data = await documentApi.getVersionList(docId);
    versionList.value = normalizeList(data).slice(0, 5);
  } finally {
    versionLoading.value = false;
  }
};

const openExpireDialog = (row) => {
  expireDoc.value = row;
  expireAt.value = formatDatePickerValue(getExpireAt(row));
  expireDialogVisible.value = true;
};

const handleSubmitExpire = async () => {
  const docId = getDocId(expireDoc.value);

  if (!docId) {
    return;
  }

  expireSubmitting.value = true;

  try {
    await documentApi.setExpireAt(docId, {
      expireAt: expireAt.value || null
    });
    ElMessage.success('过期时间已更新');
    expireDialogVisible.value = false;
    await loadDocuments();
  } finally {
    expireSubmitting.value = false;
  }
};

const handleDelete = async (row) => {
  const docId = getDocId(row);

  if (!docId) {
    return;
  }

  await documentApi.deleteDoc(docId);
  ElMessage.success('文档已删除');

  if (documentStore.docList.length === 1 && page.value > 1) {
    page.value -= 1;
  }

  await loadDocuments();
};

const handlePageChange = () => {
  loadDocuments();
};

const handleSizeChange = () => {
  page.value = 1;
  loadDocuments();
};

const handleTaskProgress = ({ taskId, status, progress }) => {
  documentStore.updateTaskProgress(taskId, status, progress);
};

const handleTaskDone = async ({ taskId }) => {
  documentStore.removeTask(taskId);
  await loadDocuments();
};

const handleTaskFailed = ({ taskId, errorMsg }) => {
  documentStore.removeTask(taskId);
  ElMessage.error(errorMsg || '文档处理失败');
  loadDocuments();
};

watch(keyword, () => {
  if (searchTimer) {
    window.clearTimeout(searchTimer);
  }

  searchTimer = window.setTimeout(() => {
    page.value = 1;
    loadDocuments();
  }, 500);
});

onMounted(() => {
  loadDocuments();
});

onUnmounted(() => {
  if (searchTimer) {
    window.clearTimeout(searchTimer);
  }
});
</script>

<style scoped>
.document-view {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #ffffff;
}

.document-toolbar {
  min-height: 68px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--color-border);
  padding: 0 28px;
}

.document-toolbar h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 20px;
  font-weight: 700;
  line-height: 1.3;
}

.search-bar {
  border-bottom: 1px solid var(--color-border);
  padding: 16px 28px;
  background: var(--color-bg-secondary);
}

.search-bar :deep(.el-input) {
  max-width: 420px;
}

.upload-task-panel {
  display: grid;
  gap: 12px;
  border-bottom: 1px solid var(--color-border);
  padding: 14px 28px 18px;
  background: #ffffff;
}

.upload-task-panel__header {
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
}

.document-table-wrap {
  min-height: 0;
  flex: 1;
  overflow: auto;
  padding: 0 28px;
}

.document-table-wrap :deep(.el-table) {
  --el-table-header-bg-color: #ffffff;
  --el-table-row-hover-bg-color: rgba(16, 163, 127, 0.06);
}

.doc-title {
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

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid var(--color-border);
  padding: 14px 28px;
}

:deep(.el-dialog__body) {
  padding-top: 8px;
}

:deep(.el-date-editor.el-input) {
  width: 100%;
}

@media (max-width: 900px) {
  .document-toolbar,
  .search-bar,
  .document-table-wrap,
  .pagination-wrap,
  .upload-task-panel {
    padding-left: 18px;
    padding-right: 18px;
  }

  .document-toolbar {
    align-items: flex-start;
    flex-direction: column;
    padding-top: 16px;
    padding-bottom: 16px;
  }

  .search-bar :deep(.el-input) {
    max-width: none;
  }

  .pagination-wrap {
    justify-content: flex-start;
    overflow-x: auto;
  }
}
</style>
