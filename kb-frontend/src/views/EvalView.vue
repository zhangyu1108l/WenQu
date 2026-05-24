<template>
  <div v-if="isAllowed" class="eval-view">
    <header class="eval-toolbar">
      <h2>Ragas 评估中心</h2>
    </header>

    <section class="eval-content">
      <el-tabs v-model="activeTab" class="eval-tabs">
        <el-tab-pane label="评估用例" name="cases">
          <div class="tab-panel">
            <div class="action-bar">
              <el-button type="primary" :loading="evalRunning" @click="handleRunEval">
                <el-icon v-if="!evalRunning">
                  <VideoPlay />
                </el-icon>
                <span>{{ evalRunning ? '评估中...' : '运行全量评估' }}</span>
              </el-button>
            </div>

            <el-collapse v-model="caseFormCollapse" class="case-create-collapse">
              <el-collapse-item title="新增用例" name="create">
                <el-form
                  ref="caseFormRef"
                  :model="caseForm"
                  :rules="caseRules"
                  label-position="top"
                >
                  <el-form-item label="问题" prop="question">
                    <el-input
                      v-model="caseForm.question"
                      maxlength="1000"
                      placeholder="请输入评估问题"
                      show-word-limit
                      :rows="4"
                      type="textarea"
                    />
                  </el-form-item>

                  <el-form-item label="标准答案" prop="groundTruth">
                    <el-input
                      v-model="caseForm.groundTruth"
                      maxlength="3000"
                      placeholder="请输入标准答案"
                      show-word-limit
                      :rows="5"
                      type="textarea"
                    />
                  </el-form-item>

                  <div class="form-actions">
                    <el-button
                      type="primary"
                      :loading="caseSubmitting"
                      @click="submitCase"
                    >
                      <el-icon v-if="!caseSubmitting">
                        <Plus />
                      </el-icon>
                      <span>提交</span>
                    </el-button>
                  </div>
                </el-form>
              </el-collapse-item>
            </el-collapse>

            <section class="table-wrap">
              <el-table
                v-loading="caseLoading"
                :data="caseList"
                empty-text="暂无评估用例"
                :row-key="getCaseId"
                stripe
              >
                <el-table-column label="问题" min-width="260">
                  <template #default="{ row }">
                    <span class="text-cell text-cell--strong">{{ getQuestion(row) }}</span>
                  </template>
                </el-table-column>

                <el-table-column label="标准答案摘要" min-width="320">
                  <template #default="{ row }">
                    <span class="text-cell">{{ getSummary(getGroundTruth(row), 96) }}</span>
                  </template>
                </el-table-column>

                <el-table-column label="创建时间" min-width="170">
                  <template #default="{ row }">
                    {{ formatDateTime(getCreatedAt(row)) }}
                  </template>
                </el-table-column>

                <el-table-column label="删除" width="100" fixed="right">
                  <template #default="{ row }">
                    <el-popconfirm
                      cancel-button-text="取消"
                      confirm-button-text="删除"
                      title="确认删除该评估用例？"
                      width="190"
                      @confirm="handleDeleteCase(row)"
                    >
                      <template #reference>
                        <el-button
                          link
                          type="danger"
                          :loading="Boolean(caseDeletingMap[getCaseId(row)])"
                        >
                          <el-icon v-if="!caseDeletingMap[getCaseId(row)]">
                            <Delete />
                          </el-icon>
                          <span>删除</span>
                        </el-button>
                      </template>
                    </el-popconfirm>
                  </template>
                </el-table-column>
              </el-table>
            </section>
          </div>
        </el-tab-pane>

        <el-tab-pane label="评估报告" name="reports">
          <div class="tab-panel">
            <section class="table-wrap table-wrap--flush">
              <el-table
                v-loading="batchLoading"
                :data="batchList"
                empty-text="暂无评估批次"
                :row-key="getBatchId"
                stripe
              >
                <el-table-column label="批次ID" width="110">
                  <template #default="{ row }">
                    <span class="batch-id">#{{ getBatchId(row) || '-' }}</span>
                  </template>
                </el-table-column>

                <el-table-column label="用例数" width="100">
                  <template #default="{ row }">
                    {{ getCaseCount(row) }}
                  </template>
                </el-table-column>

                <el-table-column label="状态" width="120">
                  <template #default="{ row }">
                    <el-tag :type="getStatusTagType(getStatus(row))" effect="light">
                      {{ getStatusLabel(getStatus(row)) }}
                    </el-tag>
                  </template>
                </el-table-column>

                <el-table-column label="四项均值得分" min-width="430">
                  <template #default="{ row }">
                    <div class="score-list">
                      <span
                        v-for="metric in metrics"
                        :key="metric.key"
                        class="score-item"
                      >
                        <span class="score-name">{{ metric.label }}</span>
                        <el-tag
                          :type="getScoreTagType(getMetricValue(row, metric.avgKeys))"
                          effect="light"
                          size="small"
                        >
                          {{ formatScore(getMetricValue(row, metric.avgKeys)) }}
                        </el-tag>
                      </span>
                    </div>
                  </template>
                </el-table-column>

                <el-table-column label="创建时间" min-width="170">
                  <template #default="{ row }">
                    {{ formatDateTime(getCreatedAt(row)) }}
                  </template>
                </el-table-column>

                <el-table-column label="查看" width="100" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openBatchDetail(row)">
                      <el-icon>
                        <View />
                      </el-icon>
                      <span>查看</span>
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </section>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog
      v-model="detailDialogVisible"
      title="批次详情"
      width="1080px"
    >
      <section v-loading="detailLoading" class="detail-body">
        <div class="metric-grid">
          <article
            v-for="metric in detailMetricCards"
            :key="metric.key"
            class="metric-card"
          >
            <span class="metric-name">{{ metric.label }}</span>
            <strong>{{ formatScore(metric.value) }}</strong>
            <el-progress
              :percentage="getScorePercentage(metric.value)"
              :show-text="false"
              :status="getProgressStatus(metric.value)"
              :stroke-width="8"
            />
          </article>
        </div>

        <el-table
          :data="detailResultList"
          empty-text="暂无评估明细"
          :row-key="getResultId"
          stripe
        >
          <el-table-column label="问题" min-width="250">
            <template #default="{ row }">
              <span class="text-cell text-cell--strong">{{ getResultQuestion(row) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="模型回答摘要" min-width="280">
            <template #default="{ row }">
              <span class="text-cell">{{ getSummary(getModelAnswer(row), 90) }}</span>
            </template>
          </el-table-column>

          <el-table-column
            v-for="metric in metrics"
            :key="metric.key"
            :label="metric.label"
            min-width="150"
          >
            <template #default="{ row }">
              <el-tag
                :type="getScoreTagType(getMetricValue(row, metric.scoreKeys))"
                effect="light"
              >
                {{ formatScore(getMetricValue(row, metric.scoreKeys)) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Delete, Plus, VideoPlay, View } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import * as evalApi from '../api/eval';
import { useTaskPoller } from '../composables/useTaskPoller';
import { useAuthStore } from '../stores/auth';

const ROLE_VALUE_MAP = {
  SUPER_ADMIN: 0,
  TENANT_ADMIN: 1,
  USER: 2
};

const STATUS_LABEL_MAP = {
  PENDING: '待评估',
  RUNNING: '评估中',
  DONE: '已完成',
  FAILED: '失败'
};

const STATUS_TAG_TYPE_MAP = {
  PENDING: 'info',
  RUNNING: 'warning',
  DONE: 'success',
  FAILED: 'danger'
};

const router = useRouter();
const authStore = useAuthStore();
const { pollTask } = useTaskPoller();

const activeTab = ref('cases');
const caseFormCollapse = ref(['create']);
const caseFormRef = ref(null);
const caseLoading = ref(false);
const batchLoading = ref(false);
const detailLoading = ref(false);
const caseSubmitting = ref(false);
const evalRunning = ref(false);
const detailDialogVisible = ref(false);
const caseList = ref([]);
const batchList = ref([]);
const selectedBatch = ref(null);
const detailResultList = ref([]);
const caseDeletingMap = reactive({});
let stopEvalPoll = null;

const caseForm = reactive({
  question: '',
  groundTruth: ''
});

const caseRules = {
  question: [{ required: true, message: '请输入评估问题', trigger: 'blur' }],
  groundTruth: [{ required: true, message: '请输入标准答案', trigger: 'blur' }]
};

const metrics = [
  // Faithfulness（忠实度）：AI回答是否忠实于检索内容。
  {
    key: 'faithfulness',
    label: 'Faithfulness',
    avgKeys: ['avgFaithfulness', 'avg_faithfulness'],
    scoreKeys: ['faithfulness']
  },
  // Answer Relevancy（相关性）：AI回答是否切题。
  {
    key: 'answerRelevancy',
    label: 'Answer Relevancy',
    avgKeys: ['avgAnswerRelevancy', 'avg_answer_relevancy'],
    scoreKeys: ['answerRelevancy', 'answer_relevancy']
  },
  // Context Recall（召回率）：标准答案是否被检索覆盖。
  {
    key: 'contextRecall',
    label: 'Context Recall',
    avgKeys: ['avgContextRecall', 'avg_context_recall'],
    scoreKeys: ['contextRecall', 'context_recall']
  },
  // Context Precision（精准度）：检索内容是否都有用。
  {
    key: 'contextPrecision',
    label: 'Context Precision',
    avgKeys: ['avgContextPrecision', 'avg_context_precision'],
    scoreKeys: ['contextPrecision', 'context_precision']
  }
];

const currentRole = computed(() => normalizeRole(authStore.userInfo?.role));
const isAllowed = computed(() => currentRole.value === 0 || currentRole.value === 1);

const detailMetricCards = computed(() =>
  metrics.map((metric) => ({
    ...metric,
    value: getMetricValue(selectedBatch.value, metric.avgKeys)
  }))
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

  return data?.records || data?.list || data?.items || data?.rows || [];
};

const normalizeDetailResults = (data) =>
  normalizeList(
    data?.results ||
      data?.resultList ||
      data?.evalResults ||
      data?.details ||
      data?.records ||
      data?.items ||
      []
  );

const getFirstValue = (row, keys) => {
  if (!row) {
    return null;
  }

  for (const key of keys) {
    if (row[key] !== null && row[key] !== undefined) {
      return row[key];
    }
  }

  return null;
};

const getCaseId = (row) => row?.id ?? row?.caseId ?? row?.case_id;

const getBatchId = (row) => row?.id ?? row?.batchId ?? row?.batch_id;

const getResultId = (row) =>
  row?.id ?? row?.resultId ?? row?.result_id ?? `${row?.batchId || row?.batch_id || ''}-${row?.evalCaseId || row?.eval_case_id || ''}`;

const getQuestion = (row) => row?.question || '-';

const getGroundTruth = (row) => row?.groundTruth ?? row?.ground_truth ?? '';

const getResultQuestion = (row) =>
  row?.question || row?.caseQuestion || row?.case_question || row?.evalCase?.question || '-';

const getModelAnswer = (row) =>
  row?.modelAnswer ?? row?.model_answer ?? row?.answer ?? '';

const getCreatedAt = (row) => row?.createdAt ?? row?.created_at ?? null;

const getCaseCount = (row) => row?.caseCount ?? row?.case_count ?? 0;

const getStatus = (row) => String(row?.status || 'PENDING').toUpperCase();

const getStatusLabel = (status) => STATUS_LABEL_MAP[status] || status;

const getStatusTagType = (status) => STATUS_TAG_TYPE_MAP[status] || 'info';

const getMetricValue = (row, keys) => getFirstValue(row, keys);

const normalizeScore = (value) => {
  const score = Number(value);
  return Number.isFinite(score) ? score : null;
};

const getScoreTagType = (value) => {
  const score = normalizeScore(value);

  if (score === null) {
    return 'info';
  }

  // 指标颜色阈值来自 Ragas 业界经验：>= 0.8 为绿色优秀，0.6~0.8 为橙色可关注，< 0.6 为红色且通常需要优化分块策略。
  if (score >= 0.8) {
    return 'success';
  }

  if (score >= 0.6) {
    return 'warning';
  }

  return 'danger';
};

const getProgressStatus = (value) => {
  const tagType = getScoreTagType(value);

  if (tagType === 'success') {
    return 'success';
  }

  if (tagType === 'warning') {
    return 'warning';
  }

  if (tagType === 'danger') {
    return 'exception';
  }

  return undefined;
};

const getScorePercentage = (value) => {
  const score = normalizeScore(value);

  if (score === null) {
    return 0;
  }

  return Math.round(Math.min(Math.max(score, 0), 1) * 100);
};

const formatScore = (value) => {
  const score = normalizeScore(value);
  return score === null ? '-' : score.toFixed(2);
};

const formatDateTime = (value) => {
  if (!value) {
    return '-';
  }

  const date = dayjs(value);
  return date.isValid() ? date.format('YYYY-MM-DD HH:mm') : value;
};

const getSummary = (value, maxLength = 80) => {
  const text = String(value || '').replace(/\s+/g, ' ').trim();

  if (!text) {
    return '-';
  }

  return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text;
};

const resetCaseForm = () => {
  caseForm.question = '';
  caseForm.groundTruth = '';
  caseFormRef.value?.clearValidate();
};

const loadCases = async () => {
  caseLoading.value = true;

  try {
    const data = await evalApi.getCaseList();
    caseList.value = normalizeList(data);
  } finally {
    caseLoading.value = false;
  }
};

const loadBatches = async () => {
  batchLoading.value = true;

  try {
    const data = await evalApi.getBatchList();
    batchList.value = normalizeList(data);
  } finally {
    batchLoading.value = false;
  }
};

const submitCase = async () => {
  await caseFormRef.value?.validate();
  caseSubmitting.value = true;

  try {
    await evalApi.createCase({
      question: caseForm.question.trim(),
      groundTruth: caseForm.groundTruth.trim()
    });
    ElMessage.success('评估用例已新增');
    resetCaseForm();
    await loadCases();
  } finally {
    caseSubmitting.value = false;
  }
};

const handleDeleteCase = async (row) => {
  const caseId = getCaseId(row);

  if (!caseId) {
    return;
  }

  caseDeletingMap[caseId] = true;

  try {
    await evalApi.deleteCase(caseId);
    ElMessage.success('评估用例已删除');
    await loadCases();
  } finally {
    caseDeletingMap[caseId] = false;
  }
};

const handleRunEval = async () => {
  if (evalRunning.value) {
    return;
  }

  evalRunning.value = true;
  stopEvalPoll?.();
  stopEvalPoll = null;

  try {
    const data = await evalApi.runEval();
    const taskId = data?.taskId ?? data?.task_id;

    if (!taskId) {
      evalRunning.value = false;
      ElMessage.warning('未获取到评估任务编号');
      return;
    }

    // 评估会执行完整 RAG 与 Ragas 指标计算，属于耗时异步任务；前端通过 taskId 轮询 async_task 状态来感知完成或失败。
    stopEvalPoll = pollTask(
      taskId,
      async () => {
        evalRunning.value = false;
        stopEvalPoll = null;
        await loadBatches();
        ElMessage.success('评估完成');
      },
      () => {
        evalRunning.value = false;
        stopEvalPoll = null;
        ElMessage.error('评估失败');
      }
    );
  } catch {
    evalRunning.value = false;
  }
};

const openBatchDetail = async (row) => {
  const batchId = getBatchId(row);

  if (!batchId) {
    return;
  }

  detailDialogVisible.value = true;
  detailLoading.value = true;
  selectedBatch.value = row;
  detailResultList.value = [];

  try {
    // 详情弹窗的数据来自 getBatchDetail 接口：包含批次均值和每条 eval_result 的四项指标。
    const data = await evalApi.getBatchDetail(batchId);
    selectedBatch.value = data?.batch || data?.batchInfo || data?.evalBatch || data || row;
    detailResultList.value = normalizeDetailResults(data);
  } finally {
    detailLoading.value = false;
  }
};

onMounted(() => {
  if (!isAllowed.value) {
    ElMessage.warning('仅管理员可访问评估中心');
    router.replace('/chat');
    return;
  }

  loadCases();
  loadBatches();
});

onUnmounted(() => {
  stopEvalPoll?.();
});
</script>

<style scoped>
.eval-view {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #ffffff;
}

.eval-toolbar {
  height: 68px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--color-border);
  padding: 0 28px;
}

.eval-toolbar h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 20px;
  font-weight: 700;
  line-height: 1.3;
}

.eval-content {
  min-height: 0;
  flex: 1;
  padding: 0 28px 24px;
  overflow: auto;
}

.eval-tabs {
  min-height: 100%;
}

.tab-panel {
  display: grid;
  gap: 18px;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  padding-top: 6px;
}

.case-create-collapse {
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
}

.case-create-collapse :deep(.el-collapse-item__header) {
  color: var(--color-text-primary);
  font-weight: 600;
}

.case-create-collapse :deep(.el-collapse-item__content) {
  padding-bottom: 18px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
}

.table-wrap {
  min-height: 0;
  overflow: auto;
}

.table-wrap--flush {
  padding-top: 6px;
}

.table-wrap :deep(.el-table) {
  --el-table-header-bg-color: #ffffff;
  --el-table-row-hover-bg-color: rgba(16, 163, 127, 0.06);
}

.text-cell {
  display: -webkit-box;
  overflow: hidden;
  color: var(--color-text-secondary);
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.text-cell--strong {
  color: var(--color-text-primary);
  font-weight: 600;
}

.batch-id {
  color: var(--color-text-primary);
  font-family: ui-monospace, SFMono-Regular, Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
  font-weight: 600;
}

.score-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(180px, 1fr));
  gap: 8px 14px;
}

.score-item {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.score-name {
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.el-dialog__body) {
  padding-top: 8px;
}

.detail-body {
  display: grid;
  gap: 18px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  min-width: 0;
  display: grid;
  gap: 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 14px;
  background: #ffffff;
}

.metric-name {
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-card strong {
  color: var(--color-text-primary);
  font-size: 24px;
  font-weight: 700;
  line-height: 1.1;
}

@media (max-width: 1100px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .eval-toolbar,
  .eval-content {
    padding-left: 18px;
    padding-right: 18px;
  }

  .eval-toolbar {
    height: auto;
    align-items: flex-start;
    flex-direction: column;
    padding-top: 16px;
    padding-bottom: 16px;
  }

  .action-bar,
  .form-actions {
    justify-content: flex-start;
  }

  .score-list,
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
