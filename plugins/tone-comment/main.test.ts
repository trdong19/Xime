// 拼音声调标注插件测试（xipm test 运行，内嵌 QuickJS，无需真机）。

function req(cands: Array<{ text: string; comment?: string }>, ascii = false): any {
  return { inputText: '', preedit: '', asciiMode: ascii, candidates: cands };
}

function run(cands: Array<{ text: string; comment?: string }>, ascii = false): any {
  const p = (globalThis as any).plugin;
  return p.transform.candidates(req(cands, ascii));
}

test("数字调精确转换：ni3 hao3 → nǐ hǎo", () => {
  const r = run([{ text: "你好", comment: "ni3 hao3" }]);
  assert.ok(r, "应有输出");
  assert.equal(r.candidates[0].engineIndex, 0);
  assert.equal(r.candidates[0].comment, "nǐ hǎo");
});

test("无调对齐字表：ni hao + 你好 → nǐ hǎo", () => {
  const r = run([{ text: "你好", comment: "ni hao" }]);
  assert.ok(r, "应有输出");
  assert.equal(r.candidates[0].comment, "nǐ hǎo");
});

test("多音字按注释对齐：行 xing → xíng", () => {
  const r = run([{ text: "行", comment: "xing" }]);
  assert.ok(r);
  assert.equal(r.candidates[0].comment, "xíng");
});

test("多音字对齐失败保守原样：行 hang 不乱标", () => {
  const r = run([{ text: "行", comment: "hang" }]);
  assert.equal(r, null, "字表默认读音 xing ≠ hang，应不干预");
});

test("轻声/无声调可标：的 de → de 不变则不干预", () => {
  const r = run([{ text: "的", comment: "de" }]);
  assert.equal(r, null, "de 无调号变化，不应输出");
});

test("英文注释不干预", () => {
  const r = run([{ text: "hello", comment: "hello" }]);
  assert.equal(r, null);
});

test("已带调注释不重复处理", () => {
  const r = run([{ text: "你好", comment: "nǐ hǎo" }]);
  assert.equal(r, null);
});

test("asciiMode 短路", () => {
  const r = run([{ text: "你好", comment: "ni3 hao3" }], true);
  assert.equal(r, null);
});

test("ü 声调：lv3 → lǚ", () => {
  const r = run([{ text: "旅", comment: "lv3" }]);
  assert.ok(r);
  assert.equal(r.candidates[0].comment, "lǚ");
});

test("iu/ui 标后：liu2 → liú、gui4 → guì", () => {
  const r = run([
    { text: "流", comment: "liu2" },
    { text: "贵", comment: "gui4" },
  ]);
  assert.ok(r);
  assert.equal(r.candidates[0].comment, "liú");
  assert.equal(r.candidates[1].comment, "guì");
});
