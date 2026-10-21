# 字阶 Token 约定（lint 约定，人工 review 执行）

> 体检批 C（v0.68.91）立约。此页是 review 约定而非 CI 门禁：新代码评审时按此核对。

## 规则

1. **新代码的字号一律取自 `AILauncherType`**（`ui/theme/Type.kt`，7 档语义字阶），禁止再新增裸写的 `fontSize = N.sp`。
2. 取用形态二选一：
   - 整档取用：`Text(..., style = AILauncherType.Body)`——同时带走该档的字重/行高，适合新写文本；
   - 只取字号：`fontSize = AILauncherType.Body.fontSize`——其余属性另有来源时（如已有 TextStyle 或单独指定 weight/lineHeight），只把字号锚到 token。
3. 7 档对照：Display 32 / Title 20 / Headline 17 / Body 15 / Callout 14 / Footnote 13 / Caption 12（sp）。低于 Caption 的字号不再新增（中文可读性下限）。
4. **存量裸写随文件被拆分/大改时顺手迁移**，不做全仓一次性大扫除（体检 ⑦ 的既定策略）。迁移只许映射到**等值档**；无等值档的历史值（如 10.5/12.5/16.sp）保留裸写，并在该批报告中标注清单，不许为凑 token 悄悄改字号。
5. 评审检查点：diff 里出现新的 `fontSize = \d` 裸写数字即打回（等值档存在时）；`style =` 引入的字重/行高变化必须在 PR 说明里声明为有意设计变更，不许混在重构批里。

## 背景

- 体检 ⑦ 实测（2026-10-22）：全仓裸 `fontSize` 157 处，token 定义自 v0.68.66 就有但引用为零。
- 批 C 首次迁移：随 PetZone 拆分，PetCards.kt / TodaySection.kt 内 24 处等值替换、14 处无等值档保留（清单见 v0.68.91 发版报告）。
