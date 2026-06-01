import MarkdownIt from 'markdown-it'
import github from 'markdown-it-github'

interface MdToken { content: string; info: string }

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
})

// GFM: tables, strikethrough, task lists, autolink, etc.
md.use(github)

// override fence with dark code block
md.renderer.rules.fence = (tokens: MdToken[], idx: number) => {
  const t = tokens[idx]
  const code = md.utils.escapeHtml(t.content)
  const lang = t.info ? ` language-${md.utils.escapeHtml(t.info)}` : ''
  return `<pre class="code-block"><code class="${lang}">${code}</code></pre>`
}

// override inline code
md.renderer.rules.code_inline = (tokens: MdToken[], idx: number) => {
  const t = tokens[idx]
  return `<code class="inline-code">${md.utils.escapeHtml(t.content)}</code>`
}

// add table styles
md.renderer.rules.table_open = () => '<table class="md-table">\n'
md.renderer.rules.thead_open = () => '<thead>\n'
md.renderer.rules.tbody_open = () => '<tbody>\n'
md.renderer.rules.tr_open = () => '<tr>\n'
md.renderer.rules.th_open = (tokens: MdToken[], idx: number) => {
  const align = tokens[idx].info
  const style = align ? ` style="text-align:${align}"` : ''
  return `<th${style}>`
}
md.renderer.rules.td_open = (tokens: MdToken[], idx: number) => {
  const align = tokens[idx].info
  const style = align ? ` style="text-align:${align}"` : ''
  return `<td${style}>`
}

export default md
