// =============================================
// 拼音声调标注（candidate_transform 插件）
//
// 作用：把候选注释中的无声调拼音转成带声调形式（ni hao → nǐ hǎo）。
// 实现：host 调用 plugin.transform.candidates(req)，对每个候选：
//   1. comment 含数字调（ni3）→ 按声调标位规则精确转符号调；
//   2. comment 无调且音节数与候选汉字数对齐 → 逐字查内置字表（pypinyin 标准
//      读音），与注释音节比对一致才标注（多音字不匹配时保守原样）；
//   3. 已带调 / 非拼音内容 → 原样返回。
// 纯内存同步计算，无网络、无 IO；变换失败/不匹配一律回退原始候选。
// =============================================

import { TONE_LINES } from './libs/tone-data';

// ---------- 数据表 ----------
// 字 -> [无调, 数字调尾数, 符号调]
const CHAR_TABLE: Map<string, [string, number, string]> = new Map();
for (const line of TONE_LINES.split('\n')) {
  const parts = line.split('|');
  if (parts.length !== 4) continue;
  const t3 = parts[2].slice(-1);
  CHAR_TABLE.set(parts[0], [parts[1], t3 >= '1' && t3 <= '5' ? Number(t3) : 0, parts[3]]);
}

// ---------- 声调符号 ----------
const TONE_MARKS: Record<string, string[]> = {
  a: ['ā', 'á', 'ǎ', 'à'],
  o: ['ō', 'ó', 'ǒ', 'ò'],
  e: ['ē', 'é', 'ě', 'è'],
  i: ['ī', 'í', 'ǐ', 'ì'],
  u: ['ū', 'ú', 'ǔ', 'ù'],
  v: ['ǖ', 'ǘ', 'ǚ', 'ǜ'],
};

/** 调号应标的字符位置：a 优先 → o/e 取后出现的 → 否则最后出现的 i/u/v（iu→u、ui→i）。 */
function toneMarkIndex(sy: string): number {
  let ai = -1, oi = -1, ei = -1, lastGlide = -1;
  for (let i = 0; i < sy.length; i++) {
    const c = sy[i];
    if (c === 'a') { if (ai < 0) ai = i; }
    else if (c === 'o') oi = i;
    else if (c === 'e') ei = i;
    else if (c === 'i' || c === 'u' || c === 'v') lastGlide = i;
  }
  if (ai >= 0) return ai;
  if (oi >= 0 || ei >= 0) return Math.max(oi, ei);
  return lastGlide;
}

/** 无调音节 + 声调(1-4，5/0=轻声不标) → 带调音节（v 转ü）。 */
function applyTone(sy: string, tone: number): string {
  const normalized = sy.replace(/v/g, 'ü');
  if (tone < 1 || tone > 4) return normalized;
  const bare = sy;
  const idx = toneMarkIndex(bare);
  if (idx < 0) return normalized;
  const marks = TONE_MARKS[bare[idx]];
  if (!marks) return normalized;
  return bare.slice(0, idx) + marks[tone - 1] + bare.slice(idx + 1).replace(/v/g, 'ü');
}

// ---------- 判定工具 ----------
const TONE_CHAR_RE = /[\u0100-\u01ff\u1e00-\u1eff]/; // 带调拉丁字母（含拼音扩展）
const HAN_RE = /[\u4e00-\u9fff]/;
const SYL_RE = /^[a-z]+[1-5]?$/;

// ---------- 注释转带调 ----------
function toneComment(text: string, comment: string): string | null {
  if (!comment || TONE_CHAR_RE.test(comment)) return null;
  const tokens = comment.split(/[\s'’·\-]+/).filter(Boolean);
  if (!tokens.length) return null;
  const chars = Array.from(text).filter((c) => HAN_RE.test(c));

  // 数字调 / 无调混合逐 token 处理；无调音节尝试与汉字对齐
  const aligned = tokens.length === chars.length;
  let changed = false;
  const out: string[] = [];
  for (let i = 0; i < tokens.length; i++) {
    const tk = tokens[i].toLowerCase();
    if (!SYL_RE.test(tk)) { out.push(tokens[i]); continue; }
    const m = tk.match(/^([a-z]+)([1-5])$/);
    if (m) {
      const t = applyTone(m[1], Number(m[2]));
      if (t !== tokens[i]) changed = true;
      out.push(t);
      continue;
    }
    // 无调：对齐的汉字查表
    const ch = aligned ? chars[i] : '';
    const entry = ch ? CHAR_TABLE.get(ch) : undefined;
    if (!entry || entry[0] !== tk) { out.push(tokens[i]); continue; }
    const t = entry[2];
    if (t !== tokens[i]) changed = true;
    out.push(t);
  }
  if (!changed) return null;
  // 保留原分隔样式：按 token 重新以空格连接（Rime 注释惯例）
  return out.join(' ');
}

// ---------- 插件定义 ----------
const plugin = definePlugin({
  transform: {
    candidates(req: XimeTransformRequest): XimeTransformResponse | null {
      if (req.asciiMode) return null;
      const items: Array<{ engineIndex: number; comment: string }> = [];
      const list = req.candidates;
      for (let i = 0; i < list.length && i < 20; i++) {
        const c = list[i];
        if (!c.comment) continue;
        const t = toneComment(c.text, c.comment);
        if (t && t !== c.comment) items.push({ engineIndex: i, comment: t });
      }
      if (!items.length) return null;
      return { candidates: items };
    },
  },
});

export default plugin;
