package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** PinyinTone.annotateWith 纯逻辑测试（字表注入）。 */
class PinyinToneTest {

    private val tbl = mapOf(
        "你" to arrayOf("ni", "ni3", "nǐ"),
        "好" to arrayOf("hao", "hao3", "hǎo"),
        "的" to arrayOf("de", "de", "de"),
        "重" to arrayOf("zhong", "zhong4", "zhòng"),
        "行" to arrayOf("xing", "xing2", "xíng"),
        "嗯" to arrayOf("n", "n2", "ń"),
        "旅" to arrayOf("lv", "lv3", "lǚ"),
        "流" to arrayOf("liu", "liu2", "liú"),
        "贵" to arrayOf("gui", "gui4", "guì"),
    )

    /** 与 annotateWith 同源的音节映射 fixture */
    private val toneSyllable = tbl.entries.associate { (ch, arr) ->
        val digit = arr[1].lastOrNull()?.takeIf { it in '1'..'5' } ?: '0'
        (arr[0] + digit) to arr[2]
    }

    @Test
    fun `数字调精确转换`() {
        assertEquals("nǐ hǎo", PinyinTone.annotateWith(tbl, toneSyllable = toneSyllable, text = "你好", "ni3 hao3"))
    }

    @Test
    fun `无调对齐字表标注`() {
        assertEquals("nǐ hǎo", PinyinTone.annotateWith(tbl, toneSyllable = toneSyllable, text = "你好", "ni hao"))
    }

    @Test
    fun `多音字读音不一致保守原样`() {
        // 行 注释 xing（默认读音匹配 → 标注）
        assertEquals("xíng", PinyinTone.annotateWith(tbl, toneSyllable = toneSyllable, text = "行", "xing"))
        // 注释 hang（多音字另一读音，字表默认 xing 不一致 → 原样）
        assertNull(PinyinTone.annotateWith(tbl, toneSyllable = toneSyllable, text = "行", "hang"))
    }

    @Test
    fun `轻声不标调`() {
        assertNull(PinyinTone.annotateWith(tbl, toneSyllable = toneSyllable, text = "的", "de"))
    }

    @Test
    fun `英文注释不标注`() {
        assertNull(PinyinTone.annotateWith(tbl, toneSyllable = toneSyllable, text = "hello", "hello"))
    }

    @Test
    fun `已带调注释原样`() {
        assertNull(PinyinTone.annotateWith(tbl, toneSyllable = toneSyllable, text = "你好", "nǐ hǎo"))
    }

    @Test
    fun `iu ui 标在后元音`() {
        assertEquals("liú", PinyinTone.annotateWith(tbl, toneSyllable = toneSyllable, text = "流", "liu2"))
        assertEquals("guì", PinyinTone.annotateWith(tbl, toneSyllable = toneSyllable, text = "贵", "gui4"))
    }

    @Test
    fun `v 转 ü`() {
        assertEquals("lǚ", PinyinTone.annotateWith(tbl, toneSyllable = toneSyllable, text = "旅", "lv3"))
    }

    @Test
    fun `特殊音节 嗯`() {
        println("诊断: tbl嗯=" + tbl["嗯"]?.contentToString())
        println("诊断: aligned判定 tokens=1 chars=" + "嗯".filter { it.code in 0x4E00..0x9FFF }.length)
        val r = PinyinTone.annotateWith(tbl, toneSyllable = toneSyllable, text = "嗯", "n2")
        println("诊断: annotateWith=" + r)
        assertEquals("ń", r)
    }

    @Test
    fun `多字词部分对齐失败则整条原样`() {
        // "你好吗" 3 字 vs 2 音节 → 对齐失败，"吗"不在表 → 原样
        assertNull(PinyinTone.annotateWith(tbl, toneSyllable = toneSyllable, text = "你好吗", "ni hao"))
    }
}
