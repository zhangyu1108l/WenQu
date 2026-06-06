<template>
  <div class="document-view">
    <header class="page-header">
      <div>
        <h2>文档管理</h2>
        <p>上传、版本、下载、过期时间与异步处理进度均来自后端接口。</p>
      </div>

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
          <span>上传 PDF / DOCX</span>
        </el-button>
      </el-upload>
    </header>

    <section class="status-grid">
      <article
        v-for="stat in docStatusSummary"
        :key="stat.label"
        class="status-card"
      >
        <span>{{ stat.label }}</span>
        <strong>{{ stat.value }}</strong>
        <div class="status-progress">
          <i :style="{ width: `${stat.percent}%`, background: stat.color }" />
        </div>
        <small>{{ stat.description }}</small>
      </article>
    </section>

    <section v-if="activeUploadTasks.length" class="upload-task-panel">
      <header class="panel-head">
        <div>
          <h3>文档处理任务</h3>
          <p>仅轮询上传接口返回的 taskId：/api/tasks/{taskId}/status</p>
        </div>
      </header>

      <TaskProgressBar
        v-for="task in activeUploadTasks"
        :key="task.taskId"
        :doc-id="task.docId"
        :initial-progress="task.progress"
        :initial-status="task.status"
        :task-id="task.taskId"
        @done="handleTaskDone"
        @failed="handleTaskFailed"
        @progress="handleTaskProgress"
      />
    </section>

    <section class="document-card">
      <header class="panel-head">
        <div>
          <h3>知识库文档</h3>
          <p>支持按后端 keyword 参数筛选</p>
        </div>

        <el-input
          v-model="keyword"
          class="keyword-input"
          clearable
          placeholder="搜索文档名称"
          :prefix-icon="Search"
        />
      </header>

      <el-table
        v-loading="loading"
        :data="documentStore.docList"
        empty-text="暂无文档"
        row-key="id"
        stripe
      >
        <el-table-column label="文档名称" min-width="240">
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

        <el-table-column label="操作" :width="canManageDocs ? 360 : 220" fixed="right">
          <template #default="{ row }">
            <el-space :size="4" wrap>
              <el-button link type="primary" @click="openDetailDialog(row)">
                <el-icon>
                  <View />
                </el-icon>
                <span>详情</span>
              </el-button>

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
    </section>

    <el-dialog v-model="detailDialogVisible" title="文档详情" width="640px">
      <el-descriptions
        v-loading="detailLoading"
        border
        :column="2"
        size="large"
      >
        <el-descriptions-item label="文档名称" :span="2">
          {{ getDocTitle(detailDoc) }}
        </el-descriptions-item>
        <el-descriptions-item label="文件类型">
          {{ getFileType(detailDoc) }}
        </el-descriptions-item>
        <el-descriptions-item label="处理状态">
          <DocStatusBadge :status="getDocStatus(detailDoc)" />
        </el-descriptions-item>
        <el-descriptions-item label="当前版本">
          {{ getRowVersionText(detailDoc) }}
        </el-descriptions-item>
        <el-descriptions-item label="文件大小">
          {{ formatFileSize(getFileSize(getCurrentVersion(detailDoc))) }}
        </el-descriptions-item>
        <el-descriptions-item label="过期时间">
          {{ formatExpireAt(getExpireAt(detailDoc)) }}
        </el-descriptions-item>
        <el-descriptions-item label="上传时间">
          {{ formatDateTime(getCreatedAt(detailDoc)) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

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
  UploadFilled,
  View
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
const detailDialogVisible = ref(false);
const detailLoading = ref(false);
const detailDoc = ref(null);
const versionDialogVisible = ref(false);
const versionLoading = ref(false);
const versionList = ref([]);
const expireDialogVisible = ref(false);
const expireSubmitting = ref(false);
const expireDoc = ref(null);
const expireAt = ref('');
let searchTimer = null;

const canManageDocs = computed(() => [0, 1].includes(Number(authStore.userInfo?.role)));

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

const getDocStatus = (row) => String(row?.status || 'PENDING').toUpperCase();

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

const documents = computed(() => documentStore.docList || []);

const docStatusSummary = computed(() => {
  const total = documents.value.length || 1;
  const count = (status) =>
    documents.value.filter((doc) => getDocStatus(doc) === status).length;
  const pending = documents.value.filter((doc) =>
    ['PENDING', 'PARSING', 'EMBEDDING'].includes(getDocStatus(doc))
  ).length;

  return [
    {
      label: '处理中任务',
      value: pending,
      percent: Math.min(100, (pending / total) * 100),
      color: '#1769ff',
      description: 'PENDING / PARSING / EMBEDDING'
    },
    {
      label: 'READY 文档',
      value: count('READY'),
      percent: Math.min(100, (count('READY') / total) * 100),
      color: '#16b978',
      description: '可被 RAG 检索引用'
    },
    {
      label: 'FAILED',
      value: count('FAILED'),
      percent: Math.min(100, (count('FAILED') / total) * 100),
      color: '#f05252',
      description: '失败原因由任务 errorMsg 展示'
    }
  ];
});

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

const openDetailDialog = async (row) => {
  const docId = getDocId(row);

  if (!docId) {
    return;
  }

  detailDoc.value = row;
  detailDialogVisible.value = true;
  detailLoading.value = true;

  try {
    detailDoc.value = await documentApi.getDocDetail(docId);
  } finally {
    detailLoading.value = false;
  }
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
  min-height: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 18px;
}

.page-header,
.document-card,
.upload-task-panel,
.status-card {
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

.page-header p,
.panel-head p {
  margin: 8px 0 0;
  color: var(--color-text-tertiary);
  font-size: 13px;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.status-card {
  padding: 18px;
}

.status-card span {
  color: #667085;
  font-size: 13px;
  font-weight: 800;
}

.status-card strong {
  display: block;
  margin-top: 10px;
  color: var(--color-text-primary);
  font-size: 34px;
  font-weight: 900;
  line-height: 1;
}

.status-card small {
  display: block;
  margin-top: 8px;
  color: var(--color-text-tertiary);
  font-size: 12px;
}

.status-progress {
  height: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: #edf2f7;
  margin-top: 12px;
}

.status-progress i {
  display: block;
  height: 100%;
  min-width: 4px;
  border-radius: inherit;
}

.upload-task-panel {
  padding: 16px 18px;
}

.upload-task-panel :deep(.task-progress-bar) {
  margin-top: 12px;
}

.document-card {
  overflow: hidden;
}

.panel-head {
  min-height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--color-border);
  padding: 0 18px;
}

.panel-head h3 {
  font-size: 18px;
}

.keyword-input {
  width: 260px;
}

.doc-title {
  color: var(--color-text-primary);
  font-weight: 800;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid var(--color-border);
  padding: 14px 18px;
}

@media (max-width: 980px) {
  .status-grid {
    grid-template-columns: 1fr;
  }

  .page-header,
  .panel-head {
    align-items: flex-start;
    flex-direction: column;
    padding: 16px 18px;
  }

  .keyword-input {
    width: 100%;
  }
}
</style>
