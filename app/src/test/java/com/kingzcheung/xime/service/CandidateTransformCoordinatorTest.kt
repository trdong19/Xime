package com.kingzcheung.xime.service

import com.kingzcheung.xime.plugin.core.js.CandidateTransformItem
import com.kingzcheung.xime.rime.RimeCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 候选词变换映射（CandidateTransformCoordinator.buildDisplay 纯函数）：
 * - 引用项（engine_index）→ 平行 CandidateAction.engine + comment 覆盖
 * - 插件项（text）→ CandidateAction.plugin，点击直接上屏
 * - 语义校验：越界/重复 engine_index 丢弃、空 text 丢弃、总数上限截断
 * - 空结果 → null（不干预）
 */
class CandidateTransformCoordinatorTest {

    private val engine = listOf(
        RimeCandidate("你好", "nǐ hǎo"),
        RimeCandidate("你好吗", ""),
        RimeCandidate("拟好", "nǐ hǎo"),
    )

    @Test
    fun `引用项映射引擎候选并保留平行动作`() {
        val result = CandidateTransformCoordinator.buildDisplay(
            items = listOf(
                CandidateTransformItem(engineIndex = 2, text = null, comment = null),
                CandidateTransformItem(engineIndex = 0, text = null, comment = null),
            ),
            engineCandidates = engine,
        )!!
        assertEquals(listOf("拟好", "你好"), result.candidates.map { it.text })
        assertEquals(listOf(2, 0), result.actions.map { it.engineIndex })
        // 引用项仍显示引擎注释
        assertEquals("nǐ hǎo", result.candidates[0].comment)
        assertFalse(result.actions[0].isPluginCandidate)
    }

    @Test
    fun `引用项 comment 覆盖仅影响显示`() {
        val result = CandidateTransformCoordinator.buildDisplay(
            items = listOf(CandidateTransformItem(engineIndex = 0, text = null, comment = "覆盖注释")),
            engineCandidates = engine,
        )!!
        assertEquals("覆盖注释", result.candidates[0].comment)
        // commitText 不用于引擎引用
        assertEquals("", result.actions[0].commitText)
    }

    @Test
    fun `插件候选映射为直接上屏动作`() {
        val result = CandidateTransformCoordinator.buildDisplay(
            items = listOf(CandidateTransformItem(engineIndex = null, text = "18500000000", comment = "手机号")),
            engineCandidates = engine,
        )!!
        assertEquals("18500000000", result.candidates[0].text)
        assertEquals("手机号", result.candidates[0].comment)
        val action = result.actions[0]
        assertTrue(action.isPluginCandidate)
        assertEquals(-1, action.engineIndex)
        assertEquals("18500000000", action.commitText)
    }

    @Test
    fun `混合列表保持插件给定的顺序`() {
        val result = CandidateTransformCoordinator.buildDisplay(
            items = listOf(
                CandidateTransformItem(engineIndex = 0, text = null, comment = null),
                CandidateTransformItem(engineIndex = null, text = "快捷短语", comment = null),
                CandidateTransformItem(engineIndex = 1, text = null, comment = null),
            ),
            engineCandidates = engine,
        )!!
        assertEquals(listOf("你好", "快捷短语", "你好吗"), result.candidates.map { it.text })
        assertEquals(listOf(0, -1, 1), result.actions.map { it.engineIndex })
    }

    @Test
    fun `越界与重复 engine_index 丢弃`() {
        val result = CandidateTransformCoordinator.buildDisplay(
            items = listOf(
                CandidateTransformItem(engineIndex = 3, text = null, comment = null),
                CandidateTransformItem(engineIndex = -1, text = null, comment = null),
                CandidateTransformItem(engineIndex = 0, text = null, comment = null),
                CandidateTransformItem(engineIndex = 0, text = null, comment = null),
            ),
            engineCandidates = engine,
        )!!
        assertEquals(1, result.candidates.size)
        assertEquals(0, result.actions[0].engineIndex)
    }

    @Test
    fun `空 text 与全部非法项`() {
        val result = CandidateTransformCoordinator.buildDisplay(
            items = listOf(
                CandidateTransformItem(engineIndex = null, text = "", comment = null),
                CandidateTransformItem(engineIndex = null, text = null, comment = "x"),
                CandidateTransformItem(engineIndex = 99, text = null, comment = null),
            ),
            engineCandidates = engine,
        )
        assertNull(result)
    }

    @Test
    fun `总数上限截断`() {
        val items = (0 until 30).map { CandidateTransformItem(engineIndex = null, text = "c$it", comment = null) }
        val result = CandidateTransformCoordinator.buildDisplay(items = items, engineCandidates = engine, maxCandidates = 20)!!
        assertEquals(20, result.candidates.size)
        assertEquals(20, result.actions.size)
        assertEquals("c19", result.candidates[19].text)
    }

    // ---------- T9 响应折叠（foldT9Response） ----------

    private val t9Engine = listOf(
        RimeCandidate("你好", "ni hao"),
        RimeCandidate("拟好", "ni hao"),
        RimeCandidate(" hello", "hello"),
    )

    @Test
    fun `T9 引擎引用项注释覆盖按文本收集`() {
        val outcome = CandidateTransformCoordinator.foldT9Response(
            items = listOf(CandidateTransformItem(engineIndex = 0, text = null, comment = "nǐ hǎo")),
            engineCandidates = t9Engine,
        )
        assertEquals(mapOf("你好" to "nǐ hǎo"), outcome.commentOverrides)
        assertTrue(outcome.injections.isEmpty())
    }

    @Test
    fun `T9 注释空或与原注释相同的引用项不产生覆盖`() {
        val outcome = CandidateTransformCoordinator.foldT9Response(
            items = listOf(
                CandidateTransformItem(engineIndex = 0, text = null, comment = null),
                CandidateTransformItem(engineIndex = 1, text = null, comment = "ni hao"),
            ),
            engineCandidates = t9Engine,
        )
        assertTrue(outcome.commentOverrides.isEmpty())
        assertTrue(outcome.injections.isEmpty())
    }

    @Test
    fun `T9 越界引擎索引丢弃不崩溃`() {
        val outcome = CandidateTransformCoordinator.foldT9Response(
            items = listOf(
                CandidateTransformItem(engineIndex = 99, text = null, comment = "越界"),
                CandidateTransformItem(engineIndex = -1, text = null, comment = "负数"),
            ),
            engineCandidates = t9Engine,
        )
        assertTrue(outcome.commentOverrides.isEmpty())
    }

    @Test
    fun `T9 插件自有项按锚点折叠为注入`() {
        val outcome = CandidateTransformCoordinator.foldT9Response(
            items = listOf(
                CandidateTransformItem(engineIndex = 0, text = null, comment = null),
                CandidateTransformItem(engineIndex = null, text = "快捷短语", comment = "快捷"),
                CandidateTransformItem(engineIndex = null, text = "另一条", comment = null),
            ),
            engineCandidates = t9Engine,
        )
        // 引用项无新注释 → 无覆盖；自有项按锚点折叠
        assertTrue(outcome.commentOverrides.isEmpty())
        assertEquals(2, outcome.injections.size)
        assertEquals("快捷短语", outcome.injections[0].snapshot.text)
        assertEquals("快捷", outcome.injections[0].snapshot.comment)
        assertEquals(0, outcome.injections[0].anchorEngineIndex)
        assertEquals("另一条", outcome.injections[1].snapshot.text)
        assertEquals("", outcome.injections[1].snapshot.comment)
        assertEquals(0, outcome.injections[1].anchorEngineIndex)
    }

    @Test
    fun `T9 同文本多候选保留首个覆盖`() {
        val outcome = CandidateTransformCoordinator.foldT9Response(
            items = listOf(
                CandidateTransformItem(engineIndex = 0, text = null, comment = "nǐ hǎo"),
                CandidateTransformItem(engineIndex = 1, text = null, comment = "nǐ hǎo?"),
            ),
            engineCandidates = t9Engine,
        )
        // 你好(0) 与 拟好(1) 文本不同 → 各自覆盖
        assertEquals(
            mapOf("你好" to "nǐ hǎo", "拟好" to "nǐ hǎo?"),
            outcome.commentOverrides,
        )
    }

    @Test
    fun `T9 混合列表锚点跟随最近一次引擎引用`() {
        val outcome = CandidateTransformCoordinator.foldT9Response(
            items = listOf(
                CandidateTransformItem(engineIndex = 1, text = null, comment = null),
                CandidateTransformItem(engineIndex = null, text = "跟在拟好后", comment = null),
                CandidateTransformItem(engineIndex = null, text = "跟在拟好后2", comment = null),
            ),
            engineCandidates = t9Engine,
        )
        assertEquals(2, outcome.injections.size)
        assertEquals(1, outcome.injections[0].anchorEngineIndex)
        assertEquals(1, outcome.injections[1].anchorEngineIndex)
    }

    @Test
    fun `T9 同文本候选 putIfAbsent 保留首个覆盖`() {
        val sameText = listOf(
            RimeCandidate("重", "chong2"),
            RimeCandidate("重", "zhong4"),
        )
        val outcome = CandidateTransformCoordinator.foldT9Response(
            items = listOf(
                CandidateTransformItem(engineIndex = 0, text = null, comment = "chóng"),
                CandidateTransformItem(engineIndex = 1, text = null, comment = "zhòng"),
            ),
            engineCandidates = sameText,
        )
        assertEquals(mapOf("重" to "chóng"), outcome.commentOverrides)
    }

    @Test
    fun `T9 前导空格文本作为精确 key`() {
        // t9Engine[2] 原注释 "hello"；覆盖用不同值，key 应保留前导空格不 trim
        val outcome = CandidateTransformCoordinator.foldT9Response(
            items = listOf(
                CandidateTransformItem(engineIndex = 2, text = null, comment = "注释测试"),
            ),
            engineCandidates = t9Engine,
        )
        assertEquals(mapOf(" hello" to "注释测试"), outcome.commentOverrides)
    }

    @Test
    fun `T9 组合场景：注释覆盖与候选注入同时存在`() {
        val outcome = CandidateTransformCoordinator.foldT9Response(
            items = listOf(
                CandidateTransformItem(engineIndex = 0, text = null, comment = "nǐ hǎo"),
                CandidateTransformItem(engineIndex = null, text = "快捷短语", comment = "快捷"),
                CandidateTransformItem(engineIndex = 1, text = null, comment = "nǐ hǎo"),
            ),
            engineCandidates = t9Engine,
        )
        // 两个引擎引用项各自覆盖（文本不同）
        assertEquals(
            mapOf("你好" to "nǐ hǎo", "拟好" to "nǐ hǎo"),
            outcome.commentOverrides,
        )
        // 自有项锚点跟随其前最近的引擎引用（index=0）
        assertEquals(1, outcome.injections.size)
        assertEquals("快捷短语", outcome.injections[0].snapshot.text)
        assertEquals(0, outcome.injections[0].anchorEngineIndex)
    }

    @Test
    fun `空响应返回 null 不干预`() {
        assertNull(
            CandidateTransformCoordinator.buildDisplay(items = emptyList(), engineCandidates = engine)
        )
    }
}
