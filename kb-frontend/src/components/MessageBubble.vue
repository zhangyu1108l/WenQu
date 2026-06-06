<template>
  <div
    class="message-row"
    :class="{ 'is-user': isUserMessage, 'is-assistant': !isUserMessage }"
  >
    <div class="message-content">
      <div v-if="!isUserMessage" class="message-meta">
        <span class="message-role">WenQu AI</span>
      </div>

      <div class="message-line">
        <div class="message-bubble">
          <div v-if="isUserMessage" class="message-text">
            {{ message?.content }}
          </div>

          <div v-else class="message-markdown" v-html="renderedContent" />

          <span
            v-if="!isUserMessage && isStreaming"
            class="message-cursor"
          />

          <SourceChunkCard
            v-if="!isUserMessage && sourceChunks.length"
            class="message-sources"
            :chunks="sourceChunks"
            :query="sourceQuery"
          />
        </div>

        <span v-if="isUserMessage" class="message-role message-role--inline">我</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { marked } from 'marked';
import SourceChunkCard from './SourceChunkCard.vue';

const props = defineProps({
  message: {
    type: Object,
    required: true
  },
  isStreaming: {
    type: Boolean,
    default: false
  }
});

marked.setOptions({
  gfm: true,
  breaks: true
});

const isUserMessage = computed(() => props.message?.role === 'user' || props.message?.role === 0);

const renderedContent = computed(() =>
  marked.parse(String(props.message?.content ?? ''), {
    async: false
  })
);

const sourceChunks = computed(() => {
  const chunks = props.message?.sourceChunks || props.message?.source_chunks || [];

  if (Array.isArray(chunks)) {
    return chunks;
  }

  try {
    return JSON.parse(chunks);
  } catch {
    return [];
  }
});

const sourceQuery = computed(() => props.message?.query || props.message?.question || '');
</script>

<style scoped>
.message-row {
  display: flex;
  align-items: flex-start;
  width: 100%;
  margin-bottom: 26px;
}

.message-row.is-user {
  justify-content: flex-end;
}

.message-row.is-assistant {
  justify-content: flex-start;
}

.message-content {
  width: min(100%, calc(var(--content-max-width) + 140px));
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.is-user .message-content {
  width: auto;
  max-width: min(58%, 560px);
  align-items: flex-end;
  margin-left: auto;
}

.is-assistant .message-content {
  margin-right: auto;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--color-text-tertiary);
  font-size: 15px;
  line-height: 1.4;
}

.message-meta::before {
  color: #8b7cff;
  content: '✦';
  font-size: 22px;
  line-height: 1;
}

.message-role {
  color: var(--color-text-primary);
  font-size: 15px;
  font-weight: 800;
  line-height: 1;
}

.message-role--inline {
  flex: 0 0 auto;
  color: var(--color-text-primary);
  font-size: 15px;
  padding: 0 2px;
}

.message-line {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.is-user .message-line {
  justify-content: flex-end;
}

.message-bubble {
  position: relative;
  border-radius: 8px;
  padding: 18px 20px;
  font-size: 15px;
  line-height: 1.7;
  word-break: break-word;
}

.is-user .message-bubble {
  border: 1px solid #dbe8ff;
  background: #eaf2ff;
  color: var(--color-text-primary);
  padding: 12px 16px;
  box-shadow: none;
}

.is-assistant .message-bubble {
  border: 1px solid #d7dfea;
  border-left: 3px solid var(--color-primary);
  background: #ffffff;
  color: var(--color-text-primary);
  box-shadow: 0 14px 30px rgba(16, 24, 40, 0.04);
}

.message-text {
  white-space: pre-wrap;
}

.message-markdown {
  color: inherit;
}

.message-markdown :deep(p) {
  margin: 0 0 10px;
}

.message-markdown :deep(p:last-child),
.message-markdown :deep(ul:last-child),
.message-markdown :deep(ol:last-child),
.message-markdown :deep(pre:last-child) {
  margin-bottom: 0;
}

.message-markdown :deep(ul),
.message-markdown :deep(ol) {
  margin: 0 0 10px;
  padding-left: 22px;
}

.message-markdown :deep(code) {
  border-radius: 4px;
  background: #f6f8fa;
  padding: 2px 5px;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 0.92em;
}

.message-markdown :deep(pre) {
  overflow-x: auto;
  border-radius: 8px;
  background: #1f2937;
  color: #f9fafb;
  padding: 12px;
}

.message-markdown :deep(pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
}

.message-cursor {
  display: inline-block;
  width: 7px;
  height: 1.15em;
  margin-left: 3px;
  vertical-align: -2px;
  background: var(--color-primary);
  animation: cursor-blink 1s steps(2, start) infinite;
}

.message-sources {
  margin: 18px -20px -18px;
  border-top: 1px solid var(--color-border);
  padding: 14px 20px;
}

@keyframes cursor-blink {
  0%,
  45% {
    opacity: 1;
  }

  46%,
  100% {
    opacity: 0;
  }
}

@media (max-width: 768px) {
  .message-content {
    width: 100%;
    max-width: 100%;
  }

  .is-user .message-content {
    max-width: 86%;
  }

  .message-line {
    gap: 8px;
  }
}
</style>
