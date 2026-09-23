<script setup lang="ts">
import { computed } from 'vue'
import { marked, type RendererObject } from 'marked'
import hljs from 'highlight.js'

const props = defineProps<{ content: string }>()

marked.setOptions({
  breaks: true,
  gfm: true,
})

const renderer: RendererObject = {
  code({ text, lang }) {
    const language = lang && hljs.getLanguage(lang) ? lang : ''
    const highlighted = language
      ? hljs.highlight(text, { language }).value
      : hljs.highlightAuto(text).value
    return `<div class="code-block-wrapper">
      <div class="code-block-header">
        <span class="code-lang">${language || 'text'}</span>
        <button class="code-copy-btn" onclick="(function(btn){
          navigator.clipboard.writeText(btn.parentElement.nextElementSibling.textContent);
          btn.textContent='已复制';
          setTimeout(()=>btn.textContent='复制',1500);
        })(this)">复制</button>
      </div>
      <pre><code class="hljs ${language ? `language-${language}` : ''}">${highlighted}</code></pre>
    </div>`
  },
}

marked.use({ renderer })

const html = computed(() => marked.parse(props.content || '') as string)
</script>

<template>
  <div class="md-renderer" v-html="html"></div>
</template>

<style lang="scss" scoped>
.md-renderer {
  line-height: 1.75;
  font-family: var(--nm-font);
  color: var(--nm-ink);
}

.md-renderer :deep(p) {
  margin: 0.5em 0;
}

.md-renderer :deep(p:first-child) {
  margin-top: 0;
}

.md-renderer :deep(strong) {
  font-weight: 700;
}

.md-renderer :deep(code:not(.hljs)) {
  padding: 2px 6px;
  border-radius: var(--nm-r-xs);
  background: var(--nm-accent-soft);
  color: var(--nm-ink);
  font-family: var(--nm-mono);
  font-size: 0.875em;
}

.md-renderer :deep(pre) {
  margin: 12px 0;
  border-radius: var(--nm-r);
  overflow: hidden;
  background: var(--nm-surface-3);
  border: 1px solid var(--nm-line);
}

.md-renderer :deep(.code-block-wrapper) {
  border-radius: var(--nm-r);
  overflow: hidden;
  margin: 12px 0;
  border: 1px solid var(--nm-line);
  background: var(--nm-surface-3);
}

.md-renderer :deep(.code-block-header) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 14px;
  background: var(--nm-surface-2);
  border-bottom: 1px solid var(--nm-line);
  font-size: 12px;
}

.md-renderer :deep(.code-lang) {
  color: var(--nm-ink-3);
  font-family: var(--nm-mono);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  font-weight: 600;
}

.md-renderer :deep(.code-copy-btn) {
  padding: 2px 10px;
  border: 1px solid var(--nm-line-strong);
  border-radius: var(--nm-r-xs);
  background: transparent;
  color: var(--nm-ink-3);
  cursor: pointer;
  font-size: 12px;
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.md-renderer :deep(.code-copy-btn:hover) {
  background: var(--nm-accent-soft);
  border-color: var(--nm-accent-line);
  color: var(--nm-accent);
}

/* 代码块：背景与正文都取自语义令牌，浅色/深色都可读 */
.md-renderer :deep(pre code.hljs) {
  display: block;
  padding: 16px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.6;
  font-family: var(--nm-mono);
  background: transparent;
  color: var(--nm-ink-2);
}

/* highlight.js 语法着色令牌化（不再依赖 highlight.js 的浅色主题表） */
.md-renderer :deep(.hljs-comment),
.md-renderer :deep(.hljs-quote) {
  color: var(--nm-ink-4);
  font-style: italic;
}

.md-renderer :deep(.hljs-keyword),
.md-renderer :deep(.hljs-selector-tag),
.md-renderer :deep(.hljs-literal),
.md-renderer :deep(.hljs-doctag),
.md-renderer :deep(.hljs-meta-keyword) {
  color: var(--nm-accent);
}

.md-renderer :deep(.hljs-string),
.md-renderer :deep(.hljs-regexp),
.md-renderer :deep(.hljs-addition),
.md-renderer :deep(.hljs-attribute),
.md-renderer :deep(.hljs-template-tag) {
  color: var(--nm-success);
}

.md-renderer :deep(.hljs-number),
.md-renderer :deep(.hljs-symbol),
.md-renderer :deep(.hljs-bullet),
.md-renderer :deep(.hljs-link),
.md-renderer :deep(.hljs-meta) {
  color: var(--nm-warning);
}

.md-renderer :deep(.hljs-title),
.md-renderer :deep(.hljs-section),
.md-renderer :deep(.hljs-name),
.md-renderer :deep(.hljs-selector-id),
.md-renderer :deep(.hljs-selector-class) {
  color: var(--nm-cyan);
}

.md-renderer :deep(.hljs-type),
.md-renderer :deep(.hljs-built_in),
.md-renderer :deep(.hljs-title.function_),
.md-renderer :deep(.hljs-title.class_) {
  color: var(--nm-danger);
}

.md-renderer :deep(.hljs-variable),
.md-renderer :deep(.hljs-template-variable),
.md-renderer :deep(.hljs-attr),
.md-renderer :deep(.hljs-params) {
  color: var(--nm-ink-2);
}

.md-renderer :deep(.hljs-tag) {
  color: var(--nm-ink-3);
}

.md-renderer :deep(.hljs-deletion) {
  color: var(--nm-danger);
}

.md-renderer :deep(.hljs-emphasis) {
  font-style: italic;
}

.md-renderer :deep(.hljs-strong) {
  font-weight: 700;
}

.md-renderer :deep(ul),
.md-renderer :deep(ol) {
  padding-left: 1.5em;
  margin: 0.5em 0;
}

.md-renderer :deep(li) {
  margin: 0.25em 0;
}

.md-renderer :deep(h1),
.md-renderer :deep(h2),
.md-renderer :deep(h3),
.md-renderer :deep(h4) {
  margin: 1em 0 0.5em;
  font-weight: 700;
  color: var(--nm-ink);
}

.md-renderer :deep(h1) { font-size: 1.4em; }
.md-renderer :deep(h2) { font-size: 1.25em; }
.md-renderer :deep(h3) { font-size: 1.1em; }

.md-renderer :deep(blockquote) {
  margin: 12px 0;
  padding: 10px 14px;
  border-left: 3px solid var(--nm-cyan);
  background: var(--nm-cyan-soft);
  border-radius: 0 var(--nm-r) var(--nm-r) 0;
  color: var(--nm-ink-2);
}

.md-renderer :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 13px;
}

.md-renderer :deep(th),
.md-renderer :deep(td) {
  padding: 8px 12px;
  border: 1px solid var(--nm-line);
  text-align: left;
}

.md-renderer :deep(th) {
  background: var(--nm-surface-3);
  color: var(--nm-ink);
  font-weight: 600;
}

.md-renderer :deep(hr) {
  margin: 16px 0;
  border: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--nm-line-strong), transparent);
}

.md-renderer :deep(a) {
  color: var(--nm-cyan);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.md-renderer :deep(img) {
  max-width: 100%;
  border-radius: var(--nm-r);
  margin: 12px 0;
}
</style>
