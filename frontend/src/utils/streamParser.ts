/** StreamParser — 状态机驱动的流式 Markdown 块解析器
 *
 *  将流式内容按语义边界（标题、代码围栏、段落等）切分成独立块。
 *  已完成块冻结不再变，仅未完成块随新数据更新。
 */

export interface StreamBlock {
  id: string
  type: 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6' | 'code' | 'p' | 'list' | 'blockquote' | 'table' | 'hr'
  content: string
  complete: boolean
  lang?: string
}

// 行首块级匹配
const HEADING_RE = /^(#{1,6})\s/
const FENCE_RE   = /^```/
const HR_RE      = /^(-{3,}|\*{3,}|_{3,})\s*$/
const BQ_RE      = /^>\s/
const LIST_RE    = /^(\s*[-*+]\s|\s*\d+\.\s)/
const TABLE_RE   = /^\|/
/** 所有能触发新块的起始 pattern（供段落收束用） */
const BLOCK_START_RE = /^(#{1,6}\s|```|>\s|[-*+]\s|\d+\.\s|\||---)/

let COUNTER = 0
function nextId() { return `sb_${++COUNTER}` }

/**
 * 将一段 Markdown 文本解析为 Block 列表。
 * 每行按状态机分类，只有「到达末尾且无后续行」的块标记为 incomplete。
 * 调用者每次传入完整累积文本即可，先前已完成的块会因后续有行而保持 complete。
 */
export function parseBlocks(text: string): StreamBlock[] {
  const blocks: StreamBlock[] = []
  const lines = text.split('\n')
  let i = 0

  while (i < lines.length) {
    const line = lines[i]

    // 空行跳过
    if (line === '') { i++; continue }

    // ── 标题 ──
    const hm = line.match(HEADING_RE)
    if (hm) {
      blocks.push({ id: nextId(), type: `h${hm[1].length}` as StreamBlock['type'], content: line, complete: true })
      i++; continue
    }

    // ── 代码围栏 ──
    if (FENCE_RE.test(line)) {
      const lang = line.slice(3).trim()
      const raw: string[] = [line]
      i++
      let closed = false
      while (i < lines.length) {
        raw.push(lines[i])
        if (FENCE_RE.test(lines[i])) { closed = true; i++; break }
        i++
      }
      blocks.push({ id: nextId(), type: 'code', content: raw.join('\n'), complete: closed, lang: lang || undefined })
      continue
    }

    // ── 分割线 ──
    if (HR_RE.test(line)) {
      blocks.push({ id: nextId(), type: 'hr', content: line, complete: true })
      i++; continue
    }

    // ── 引用 ──
    if (BQ_RE.test(line)) {
      const raw: string[] = [line]; i++
      while (i < lines.length && BQ_RE.test(lines[i])) { raw.push(lines[i]); i++ }
      blocks.push({ id: nextId(), type: 'blockquote', content: raw.join('\n'), complete: i < lines.length })
      continue
    }

    // ── 列表 ──
    if (LIST_RE.test(line)) {
      const raw: string[] = [line]; i++
      while (i < lines.length && LIST_RE.test(lines[i])) { raw.push(lines[i]); i++ }
      blocks.push({ id: nextId(), type: 'list', content: raw.join('\n'), complete: i < lines.length })
      continue
    }

    // ── 表格 ──
    if (TABLE_RE.test(line)) {
      const raw: string[] = [line]; i++
      while (i < lines.length && TABLE_RE.test(lines[i])) { raw.push(lines[i]); i++ }
      blocks.push({ id: nextId(), type: 'table', content: raw.join('\n'), complete: i < lines.length })
      continue
    }

    // ── 段落（兜底） ──
    const raw: string[] = [line]; i++
    while (i < lines.length) {
      const nxt = lines[i]
      // 遇到空行或新的块起始标记 → 段落结束
      if (nxt === '' || BLOCK_START_RE.test(nxt)) break
      raw.push(nxt)
      i++
    }
    blocks.push({ id: nextId(), type: 'p', content: raw.join('\n'), complete: i < lines.length })
  }

  return blocks
}
