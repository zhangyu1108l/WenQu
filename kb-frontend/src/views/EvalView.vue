<template>
  <div class="eval-view">
    <header class="page-header">
      <div>
        <h2>Ragas 评估</h2>
        <p>维护评估用例，触发评估任务，并查看批次指标。</p>
      </div>
      <el-button :loading="evalRunning" type="primary" @click="handleRunEval">
        <el-icon v-if="!evalRunning">
          <VideoPlay />
        </el-icon>
        <span>{{ evalRunning ? '评估中...' : '运行评估' }}</span>
      </el-button>
    </header>

    <section v-if="evalRunning" class="eval-progress">
      <div>
        <strong>{{ evalStatusText }}</strong>
        <span>任务进度通过 /api/tasks/{taskId}/status 轮询</span>
      </div>
      <el-progress :percentage="evalProgress" :stroke-width="8" />
    </section>

    <section class="metric-grid">
      <article
        v-for="metric in latestMetricCards"
        :key="metric.key"
        class="metric-card"
      >
        <span>{{ metric.shortLabel }}</span>
        <strong>{{ formatScore(metric.value) }}</strong>
        <small>{{ metric.description }}</small>
      </article>
    </section>

    <section class="eval-card">
      <el-tabs v-model="activeTab" class="eval-tabs">
        <el-tab-pane label="评估用例" name="cases">
          <div class="case-layout">
            <section class="case-form-card">
              <header class="panel-head">
                <h3>新增用例</h3>
              </header>
              <el-form
                ref="caseFormRef"
                class="case-form"
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

                <el-button
                  :loading="caseSubmitting"
                  type="primary"
                  @click="submitCase"
                >
                  <el-icon v-if="!caseSubmitting">
                    <Plus />
                  </el-icon>
                  <span>提交用例</span>
                </el-button>
              </el-form>
            </section>

            <section class="table-wrap">
              <header class="panel-head">
                <h3>评估用例</h3>
              </header>
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

                <el-table-column label="操作" width="100" fixed="right">
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
          <section class="table-wrap table-wrap--full">
            <header class="panel-head">
              <h3>批次历史</h3>
            </header>
            <el-table
              v-loading="batchLoading"
              :data="batchList"
              empty-text="暂无评估批次"
              :row-key="getBatchId"
              stripe
            >
              <el-table-column label="批次 ID" width="110">
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

              <el-table-column label="四项均值" min-width="560">
                <template #default="{ row }">
                  <div class="score-list">
                    <span
                      v-for="metric in metrics"
                      :key="metric.key"
                      class="score-item"
                    >
                      <span class="score-name">{{ metric.shortLabel }}</span>
                      <el-tag
                        effect="light"
                        size="small"
                        :type="getScoreTagType(getMetricValue(row, metric.avgKeys))"
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

              <el-table-column label="操作" width="100" fixed="right">
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
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="detailDialogVisible" title="批次详情" width="1080px">
      <section v-loading="detailLoading" class="detail-body">
        <div class="metric-grid metric-grid--dialog">
          <article
            v-for="metric in detailMetricCards"
            :key="metric.key"
            class="metric-card"
          >
            <span>{{ metric.label }}</span>
            <small>{{ metric.description }}</small>
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
            :label="metric.shortLabel"
            min-width="150"
          >
            <template #default="{ row }">
              <el-tag
                effect="light"
                :type="getScoreTagType(getMetricValue(row, metric.scoreKeys))"
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
import { ElMessage } from 'element-plus';
import { Delete, Plus, VideoPlay, View } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import * as evalApi from '../api/eval';
import { useTaskPoller } from '../composables/useTaskPoller';

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

const { pollTask } = useTaskPoller();

const activeTab = ref('cases');
const caseFormRef = ref(null);
const caseLoading = ref(false);
const batchLoading = ref(false);
const detailLoading = ref(false);
const caseSubmitting = ref(false);
const evalRunning = ref(false);
const evalProgress = ref(0);
const evalStatusText = ref('等待任务开始');
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
  {
    key: 'faithfulness',
    label: 'Faithfulness（忠实度）',
    shortLabel: 'Faithfulness',
    description: '回答是否忠实于检索内容',
    avgKeys: ['avgFaithfulness', 'avg_faithfulness'],
    scoreKeys: ['faithfulness']
  },
  {
    key: 'answerRelevancy',
    label: 'Answer Relevancy（切题度）',
    shortLabel: 'Answer Relevancy',
    description: '回答是否切中问题',
    avgKeys: ['avgAnswerRelevancy', 'avg_answer_relevancy'],
    scoreKeys: ['answerRelevancy', 'answer_relevancy']
  },
  {
    key: 'contextRecall',
    label: 'Context Recall（召回率）',
    shortLabel: 'Context Recall',
    description: '标准答案是否被检索内容覆盖',
    avgKeys: ['avgContextRecall', 'avg_context_recall'],
    scoreKeys: ['contextRecall', 'context_recall']
  },
  {
    key: 'contextPrecision',
    label: 'Context Precision（精确率）',
    shortLabel: 'Context Precision',
    description: '检索内容是否有效',
    avgKeys: ['avgContextPrecision', 'avg_context_precision'],
    scoreKeys: ['contextPrecision', 'context_precision']
  }
];

const latestBatch = computed(() => batchList.value[0] || null);

const latestMetricCards = computed(() =>
  metrics.map((metric) => ({
    ...metric,
    value: getMetricValue(latestBatch.value, metric.avgKeys)
  }))
);

const detailMetricCards = computed(() =>
  metrics.map((metric) => ({
    ...metric,
    value: getMetricValue(selectedBatch.value, metric.avgKeys)
  }))
);

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
  row?.id ??
  row?.resultId ??
  row?.result_id ??
  `${row?.batchId || row?.batch_id || ''}-${row?.evalCaseId || row?.eval_case_id || ''}`;

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

  if (!caseList.value.length) {
    ElMessage.warning('请先新增评估用例');
    activeTab.value = 'cases';
    return;
  }

  evalRunning.value = true;
  evalProgress.value = 0;
  evalStatusText.value = '等待任务开始';
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

    stopEvalPoll = pollTask(
      taskId,
      async () => {
        evalRunning.value = false;
        evalProgress.value = 100;
        evalStatusText.value = '评估完成';
        stopEvalPoll = null;
        activeTab.value = 'reports';
        await loadBatches();
        ElMessage.success('评估完成');
      },
      (message) => {
        evalRunning.value = false;
        stopEvalPoll = null;
        evalStatusText.value = message || '评估失败';
        ElMessage.error(evalStatusText.value);
      },
      (result) => {
        evalProgress.value = Math.max(0, Math.min(Number(result?.progress) || 0, 100));
        evalStatusText.value = getStatusLabel(String(result?.status || 'RUNNING').toUpperCase());
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
    const data = await evalApi.getBatchDetail(batchId);
    selectedBatch.value = data?.batch || data?.batchInfo || data?.evalBatch || data || row;
    detailResultList.value = normalizeDetailResults(data);
  } finally {
    detailLoading.value = false;
  }
};

onMounted(() => {
  loadCases();
  loadBatches();
});

onUnmounted(() => {
  stopEvalPoll?.();
});
</script>

<style scoped>
.eval-view {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: var(--color-bg-secondary);
  padding: 18px;
}

.page-header,
.eval-progress,
.eval-card,
.case-form-card,
.table-wrap,
.metric-card {
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

.eval-progress {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  align-items: center;
  gap: 18px;
  padding: 16px 18px;
}

.eval-progress strong {
  display: block;
  color: var(--color-text-primary);
  font-size: 14px;
}

.eval-progress span {
  display: block;
  margin-top: 6px;
  color: var(--color-text-tertiary);
  font-size: 12px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-grid--dialog {
  margin-bottom: 16px;
}

.metric-card {
  min-height: 118px;
  padding: 16px;
}

.metric-card span {
  color: #667085;
  font-size: 13px;
  font-weight: 800;
}

.metric-card small {
  display: block;
  margin-top: 8px;
  color: var(--color-text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}

.metric-card strong {
  display: block;
  margin-top: 12px;
  color: var(--color-text-primary);
  font-size: 30px;
  font-weight: 900;
  line-height: 1;
}

.eval-card {
  overflow: hidden;
}

.eval-tabs {
  padding: 0 18px 18px;
}

.eval-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

.case-layout {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 16px;
}

.case-form-card,
.table-wrap {
  overflow: hidden;
}

.panel-head {
  min-height: 58px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--color-border);
  padding: 0 18px;
}

.panel-head h3 {
  font-size: 18px;
}

.case-form {
  padding: 16px 18px 18px;
}

.text-cell {
  display: -webkit-box;
  overflow: hidden;
  color: #475467;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.text-cell--strong {
  color: var(--color-text-primary);
  font-weight: 800;
}

.batch-id {
  color: #175cd3;
  font-weight: 900;
}

.score-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.score-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid #dbe7fb;
  border-radius: 8px;
  background: #fbfdff;
  padding: 3px 6px;
}

.score-name {
  color: #667085;
  font-size: 12px;
  font-weight: 800;
}

.detail-body {
  min-height: 420px;
}

@media (max-width: 1180px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .case-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }

  .page-header,
  .eval-progress {
    align-items: flex-start;
    grid-template-columns: 1fr;
    flex-direction: column;
    padding: 16px 18px;
  }
}
</style>
