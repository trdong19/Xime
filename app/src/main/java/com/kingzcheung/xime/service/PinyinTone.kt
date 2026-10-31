package com.kingzcheung.xime.service

import android.content.Context

/**
 * 内置拼音声调标注（2026-10-30）：候选注释的无调拼音转带调显示（ni hao → nǐ hǎo）。
 *
 * 数据：assets/pinyin_tone.tsv（CJK 基本区 20992 字，pypinyin 生成：汉字/无调/数字调/符号调）。
 * 纯内存查表、零网络；[init] 幂等，service onCreate 调用一次。
 *
 * 标注规则（与已弃用的 tone_comment 插件同口径）：
 *  - 注释带数字调（ni3）→ 按声调标位规则精确转符号调；
 *  - 注释无调且音节数与候选汉字数对齐 → 逐字查表（字的默认读音与注释音节一致才标注，
 *    多音字读音不一致时保守原样）；
 *  - 已带调 / 非拼音内容（英文、数字）→ 原样返回 null。
 */
object PinyinTone {

    /** 字 -> [无调, 数字调, 符号调] */
    private val table = HashMap<String, Array<String>>(24500)
    @Volatile
    private var initialized = false

    /** 声调符号表（v 表示 ü） */
    private val TONE_MARKS = mapOf(
        'a' to arrayOf("ā", "á", "ǎ", "à"),
        'o' to arrayOf("ō", "ó", "ǒ", "ò"),
        'e' to arrayOf("ē", "é", "ě", "è"),
        'i' to arrayOf("ī", "í", "ǐ", "ì"),
        'u' to arrayOf("ū", "ú", "ǔ", "ù"),
        'v' to arrayOf("ǖ", "ǘ", "ǚ", "ǜ"),
    )

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            runCatching {
                context.assets.open("pinyin_tone.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (line in lines) {
                        val parts = line.split('\t')
                        if (parts.size == 4 && parts[0] != "汉字") {
                            table[parts[0]] = arrayOf(parts[1], parts[2], parts[3])
                        }
                    }
                }
                initialized = true
            }
        }
    }

    fun isReady(): Boolean = initialized

    /** 调号应标的字符位置：a 优先 → o/e 取后出现的 → 否则最后出现的 i/u/v（liú/guì）。 */
    private fun toneMarkIndex(syllable: String): Int {
        var ai = -1; var oe = -1; var ei = -1; var glide = -1
        for (i in syllable.indices) {
            when (val c = syllable[i]) {
                'a' -> if (ai < 0) ai = i
                'o' -> oe = i
                'e' -> ei = i
                'i', 'u', 'v' -> glide = i
            }
        }
        if (ai >= 0) return ai
        if (oe >= 0 || ei >= 0) return maxOf(oe, ei)
        return glide
    }

    /** 无调音节 + 声调(1-4；5/0 轻声不标) → 带调音节（v→ü）。 */
    private fun applyTone(syllable: String, tone: Int): String {
        val normalized = syllable.replace("v", "ü")
        if (tone < 1 || tone > 4) return normalized
        val idx = toneMarkIndex(syllable)
        if (idx < 0) return normalized
        val marks = TONE_MARKS[syllable[idx]] ?: return normalized
        return syllable.substring(0, idx) + marks[tone - 1] + syllable.substring(idx + 1).replace("v", "ü")
    }

    private val TONE_CHAR_RE = Regex("[\\u0100-\\u01ff\\u1e00-\\u1eff]")
    private val HAN_RE = Regex("[\\u4e00-\\u9fff]")
    private val SYL_RE = Regex("^[a-z]+[1-5]?$")

    /**
     * 候选注释声调标注。[text] 候选文本（对齐用），[comment] 引擎注释。
     * 返回带调注释；无需标注/无法标注返回 null（调用方保留原注释）。
     */
    fun annotate(text: String, comment: String): String? =
        if (!initialized) null else annotateWith(table, text, comment)

    /** 纯函数版本（可 JVM 单测）：[tbl] 为 字 -> [无调, 数字调, 符号调]。 */
    internal fun annotateWith(tbl: Map<String, Array<String>>, text: String, comment: String): String? {
        if (comment.isEmpty() || TONE_CHAR_RE.containsMatchIn(comment)) return null
        val tokens = comment.split(' ', '\'', '·', '-', '’').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val chars = text.filter { HAN_RE.matches(it.toString()) }
        val aligned = tokens.size == chars.length
        var changed = false
        val out = ArrayList<String>(tokens.size)
        for (i in tokens.indices) {
            val tk = tokens[i].lowercase()
            if (!SYL_RE.matches(tk)) { out.add(tokens[i]); continue }
            val m = SYL_RE.matchEntire(tk)!!
            val digit = tk.last()
            if (digit in '1'..'5') {
                val base = tk.dropLast(1)
                val tone = digit - '0'
                // 优先对齐字表的符号调（覆盖 ń 等无元音的特殊音节，规则算不出）；
                // 无对齐/表缺时回退规则计算
                val t = if (aligned) {
                    val ch = chars.getOrNull(i) ?: ""
                    tbl[ch]?.takeIf { it[0] == base && it[1].last() == digit }?.get(2)
                        ?: applyTone(base, tone)
                } else applyTone(base, tone)
                if (t != tokens[i]) changed = true
                out.add(t)
                continue
            }
            // 无调：与对齐的汉字查表（默认读音与注释一致才标）
            val ch = if (aligned && i < chars.length) chars[i].toString() else ""
            val entry = if (ch.isEmpty()) null else tbl[ch]
            if (entry == null || entry[0] != tk) { out.add(tokens[i]); continue }
            if (entry[2] != tokens[i]) changed = true
            out.add(entry[2])
        }
        if (!changed) return null
        return out.joinToString(" ")
    }
}
