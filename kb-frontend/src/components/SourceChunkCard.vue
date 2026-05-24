<template>
  <div v-if="displayChunks.length" class="source-chunk-list">
    <article
      v-for="chunk in displayChunks"
      :key="chunk.key"
      class="source-chunk-card"
    >
      <div class="source-chunk-card__header">
        <span class="source-chunk-card__title">{{ chunk.headingPath }}</span>
        <span v-if="chunk.pageNo" class="source-chunk-card__page">
          第 {{ chunk.pageNo }} 页
        </span>
      </div>

      <p class="source-chunk-card__content" v-html="chunk.highlightedContent" />

      <div class="source-chunk-card__score">
        相关度 {{ chunk.scoreText }}
      </div>
    </article>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  chunks: {
    type: Array,
    default: () => []
  },
  query: {
    type: String,
    default: ''
  }
});

const escapeHtml = (value) =>
  String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

const escapeRegExp = (value) => String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

const queryWords = computed(() => {
  const words = props.query
    .trim()
    .split(/\s+/)
    .filter(Boolean);

  return [...new Set(words)].sort((a, b) => b.length - a.length);
});

const highlightContent = (content) => {
  const rawContent = String(content ?? '');
  const preview = rawContent.length > 180 ? `${rawContent.slice(0, 180)}...` : rawContent;
  const safePreview = escapeHtml(preview);

  if (!queryWords.value.length) {
    return safePreview;
  }

  const pattern = queryWords.value.map(escapeRegExp).join('|');
  return safePreview.replace(new RegExp(`(${pattern})`, 'gi'), '<mark>$1</mark>');
};

const displayChunks = computed(() =>
  props.chunks.map((chunk, index) => {
    const score = Number(chunk?.score);
    const headingPath = chunk?.headingPath || chunk?.heading_path || '来源片段';
    const pageNo = chunk?.pageNo ?? chunk?.page_no;

    return {
      key: chunk?.chunkId ?? chunk?.chunk_id ?? chunk?.id ?? `${headingPath}-${index}`,
      headingPath,
      pageNo,
      highlightedContent: highlightContent(chunk?.content),
      scoreText: Number.isFinite(score) ? score.toFixed(2) : '-'
    };
  })
);
</script>

<style scoped>
.source-chunk-list {
  display: grid;
  gap: 10px;
}

.source-chunk-card {
  border: 1px solid var(--color-border);
  border-left: 4px solid var(--color-primary);
  border-radius: 8px;
  background: var(--color-bg-secondary);
  padding: 12px 14px;
}

.source-chunk-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
}

.source-chunk-card__title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-chunk-card__page {
  flex: 0 0 auto;
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 400;
}

.source-chunk-card__content {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.source-chunk-card__content :deep(mark) {
  border-radius: 3px;
  background: #fff3bf;
  color: inherit;
  padding: 0 2px;
}

.source-chunk-card__score {
  margin-top: 8px;
  color: var(--color-text-secondary);
  font-size: 12px;
}
</style>
