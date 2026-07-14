import { marked } from 'marked'
import hljs from 'highlight.js/lib/common'

const renderer = {
  code({ text, lang }: { text: string; lang?: string }) {
    const language = lang || ''
    try {
      if (language && hljs.getLanguage(language)) {
        const result = hljs.highlight(text, { language })
        return `<pre class="hljs"><code class="language-${language}">${result.value}</code></pre>`
      }
    } catch {
      // fall through
    }
    const escaped = text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
    return `<pre class="hljs"><code>${escaped}</code></pre>`
  },
}

marked.use({
  renderer: renderer as any,
  gfm: true,
  breaks: false,
})

export function renderMarkdown(md: string): string {
  return marked.parse(md, { async: false }) as string
}
