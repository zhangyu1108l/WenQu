<template>
  <div v-if="displayChunks.length" class="source-chunk-list">
    <div class="source-chunk-heading">引用来源</div>

    <article
      v-for="chunk in displayChunks"
      :key="chunk.key"
      class="source-chunk-card"
      :title="chunk.contentText"
    >
      <span class="source-chunk-card__index">{{ chunk.index }}</span>
      <span class="source-chunk-card__title">{{ chunk.headingPath }}</span>
      <span v-if="chunk.pageNo" class="source-chunk-card__page">P{{ chunk.pageNo }}</span>
      <span class="source-chunk-card__score">{{ chunk.scoreText }}</span>
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

const displayChunks = computed(() =>
  props.chunks.map((chunk, index) => {
    const score = Number(chunk?.score);
    const headingPath = chunk?.headingPath || chunk?.heading_path || '来源片段';
    const pageNo = chunk?.pageNo ?? chunk?.page_no;

    return {
      key: chunk?.chunkId ?? chunk?.chunk_id ?? chunk?.id ?? `${headingPath}-${index}`,
      index: index + 1,
      headingPath,
      pageNo,
      contentText: String(chunk?.content ?? ''),
      scoreText: Number.isFinite(score) ? score.toFixed(2) : '-'
    };
  })
);
</script>

<style scoped>
.source-chunk-list {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
}

.source-chunk-heading {
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 800;
  line-height: 1.4;
}

.source-chunk-card {
  min-width: 0;
  max-width: 240px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid #d8e4f8;
  border-radius: 8px;
  background: #fbfdff;
  color: var(--color-text-secondary);
  padding: 0 8px 0 6px;
  transition: border-color 0.16s ease, background-color 0.16s ease, color 0.16s ease;
}

.source-chunk-card:hover {
  border-color: var(--color-primary-tint);
  background: #ffffff;
  color: var(--color-primary);
}

.source-chunk-card__index {
  width: 17px;
  height: 17px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: 11px;
  font-weight: 800;
  line-height: 1;
}

.source-chunk-card__title {
  min-width: 0;
  overflow: hidden;
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-chunk-card__page,
.source-chunk-card__score {
  flex: 0 0 auto;
  color: var(--color-text-tertiary);
  font-size: 12px;
}

@media (max-width: 760px) {
  .source-chunk-card {
    max-width: 100%;
  }
}
</style>
