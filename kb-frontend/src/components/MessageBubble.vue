<template>
  <div
    class="message-row"
    :class="{ 'is-user': isUserMessage, 'is-assistant': !isUserMessage }"
  >
    <div v-if="!isUserMessage" class="message-avatar" aria-hidden="true">AI</div>

    <div class="message-content">
      <div class="message-bubble">
        <div v-if="isUserMessage" class="message-text">
          {{ message?.content }}
        </div>

        <div v-else class="message-markdown" v-html="renderedContent" />

        <span
          v-if="!isUserMessage && isStreaming"
          class="message-cursor"
        />
      </div>

      <SourceChunkCard
        v-if="!isUserMessage && sourceChunks.length"
        class="message-sources"
        :chunks="sourceChunks"
        :query="sourceQuery"
      />
    </div>

    <div v-if="isUserMessage" class="message-avatar is-user-avatar" aria-hidden="true">我</div>
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
  gap: 10px;
  width: 100%;
}

.message-row.is-user {
  justify-content: flex-end;
}

.message-row.is-assistant {
  justify-content: flex-start;
}

.message-content {
  max-width: min(78%, var(--content-max-width));
}

.message-bubble {
  position: relative;
  border-radius: 8px;
  padding: 13px 15px;
  font-size: 15px;
  line-height: 1.7;
  word-break: break-word;
}

.is-user .message-bubble {
  border: 1px solid #dbe8ff;
  background: var(--color-user-bubble);
  color: var(--color-text-primary);
}

.is-assistant .message-bubble {
  border: 1px solid var(--color-border);
  background: #ffffff;
  color: var(--color-text-primary);
  box-shadow: 0 10px 26px rgba(16, 24, 40, 0.05);
}

.message-avatar {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 50%;
  background: linear-gradient(135deg, #4f7cff, #23cce7);
  color: #ffffff;
  font-size: 11px;
  font-weight: 760;
  line-height: 1;
  box-shadow: 0 8px 18px rgba(63, 109, 246, 0.2);
}

.is-user-avatar {
  background: #f2f5f9;
  color: var(--color-text-secondary);
  box-shadow: none;
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
  margin-top: 10px;
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
    max-width: calc(100% - 38px);
  }
}
</style>
