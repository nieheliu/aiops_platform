<template>
  <div v-if="content" class="markdown-viewer" v-html="safeHtml"></div>
  <el-empty v-else description="暂无 AI 诊断报告" />
</template>

<script setup>
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

const props = defineProps({
  content: {
    type: String,
    default: '',
  },
})

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: true,
  highlight(str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code>${hljs.highlight(str, { language: lang, ignoreIllegals: true }).value}</code></pre>`
      } catch {
        return ''
      }
    }
    return `<pre class="hljs"><code>${md.utils.escapeHtml(str)}</code></pre>`
  },
})

const safeHtml = computed(() => DOMPurify.sanitize(md.render(props.content || '')))
</script>

<style scoped>
.markdown-viewer {
  color: #1f2937;
  line-height: 1.75;
}

.markdown-viewer :deep(h1),
.markdown-viewer :deep(h2),
.markdown-viewer :deep(h3) {
  margin: 20px 0 12px;
  color: #111827;
  line-height: 1.35;
}

.markdown-viewer :deep(h1) {
  font-size: 26px;
}

.markdown-viewer :deep(h2) {
  padding-left: 10px;
  border-left: 4px solid #2563eb;
  font-size: 22px;
}

.markdown-viewer :deep(h3) {
  font-size: 18px;
}

.markdown-viewer :deep(p) {
  margin: 10px 0;
}

.markdown-viewer :deep(ul),
.markdown-viewer :deep(ol) {
  padding-left: 24px;
}

.markdown-viewer :deep(blockquote) {
  margin: 14px 0;
  padding: 12px 16px;
  border-left: 4px solid #93c5fd;
  border-radius: 8px;
  background: #eff6ff;
  color: #475569;
}

.markdown-viewer :deep(code) {
  padding: 2px 6px;
  border-radius: 6px;
  background: #f1f5f9;
  color: #be123c;
}

.markdown-viewer :deep(pre.hljs) {
  overflow: auto;
  padding: 16px;
  border-radius: 12px;
  background: #0f172a;
}

.markdown-viewer :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.markdown-viewer :deep(table) {
  width: 100%;
  margin: 16px 0;
  border-collapse: collapse;
}

.markdown-viewer :deep(th),
.markdown-viewer :deep(td) {
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
}

.markdown-viewer :deep(th) {
  background: #f8fafc;
  font-weight: 700;
}
</style>
