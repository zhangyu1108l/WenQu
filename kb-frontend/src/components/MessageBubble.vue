<template>
  <div
    class="message-row"
    :class="{ 'is-user': isUserMessage, 'is-assistant': !isUserMessage }"
  >
    <div class="message-content">
      <div class="message-bubble">
        <div
          v-if="isUserMessage"
          class="message-text"
        >
          {{ message?.content }}
        </div>
        <!--
          DeepSeek 的回答天然可能包含 Markdown（代码块、列表、强调等），引入并配置 marked 后，
          可以把 AI 文本渲染成更接近文档阅读体验的 HTML。
        -->
        <div
          v-else
          class="message-markdown"
          v-html="renderedContent"
        />
        <!-- isStreaming 用于区分已完成消息和正在生成的消息，流式生成时显示闪烁光标。 -->
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

// marked 在组件内统一开启 GFM 和换行支持，保证列表、代码块和普通换行都能稳定渲染。
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

const sourceChunks = computed(() => props.message?.sourceChunks || props.message?.source_chunks || []);

const sourceQuery = computed(() => props.message?.query || props.message?.question || '');
</script>

<style scoped>
.message-row {
  display: flex;
  width: 100%;
}

.message-row.is-user {
  justify-content: flex-end;
}

.message-row.is-assistant {
  justify-content: flex-start;
}

.message-content {
  max-width: min(76%, var(--content-max-width));
}

.message-bubble {
  position: relative;
  border-radius: 8px;
  padding: 12px 14px;
  font-size: 15px;
  line-height: 1.7;
  word-break: break-word;
}

.is-user .message-bubble {
  background: var(--color-user-bubble);
  color: #ffffff;
}

.is-assistant .message-bubble {
  border: 1px solid var(--color-border);
  background: #ffffff;
  color: var(--color-text-primary);
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
  background: var(--color-bg-secondary);
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
    max-width: 92%;
  }
}
</style>
