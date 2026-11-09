# CREDITS / 来源说明

本资源包是 [TheSalt's Text Effects](https://github.com/TheSalts/Text_Effects) 的**裁剪派生版**，
用于给 ChatPrefix 插件的前缀提供「原地流动的彩虹」效果。

## 上游

| 项 | 值 |
| --- | --- |
| 项目 | TheSalts/Text_Effects |
| 版本 | v1.1.8（`pack.mcmeta` 里的 description） |
| 提交 | `e1f565cc3ec5a37053b0b94b118c4ccaa424f163` |
| 作者 | TheSalt_ |
| 许可 | MIT License，Copyright (c) 2026 TheSalt_ |

完整许可原文见同目录的 `LICENSE-Text_Effects.txt`。按 MIT 要求，再分发时必须保留该声明。

## 本版做了哪些裁剪

**保留**

* `assets/minecraft/shaders/core/` 下 26.3 用到的 2 个核心着色器（`core/text.vsh` + `core/text.fsh`），
  取自上游的 `26_3/` overlay。26.3 重写了文本管道：旧的 4 个 `rendertype_text*` 合并成一对
  `core/text.*`，改用 `#include <minecraft:...>`（不再是 `#moj_import`）与显式 `layout(location)`，
  GUI / 透视 / 灰度 / OIT 都跑到同一份文件里用 `#ifdef` 区分。
* `assets/minecraft/shaders/include/` 下的公共管线文件（30 个 `.glsl`），全部取自上游 `26_3/` overlay，
  其中 `coverage.glsl` 是上游为 26.3 新加的（原版 26.3 没有这个 include）。
  另有本包自己的 `_config.glsl`，合计 31 个。

**改写**

* `assets/minecraft/shaders/include/_config.glsl` 被裁到只剩一条映射：
  `#F8F85C → apply_rainbow()`。除这个颜色外，任何颜色都不会触发特效。
* 1.3.0 起这条映射改用 `TEXT_EFFECT_WITH_SHADOW`：聊天行默认带阴影，只匹配正文的话
  彩虹旁边会留一圈暗黄的阴影（阴影是主色 * 0.25 的单独一层）。
* 1.3.0 起 `apply_effect.glsl` 的 `flagRainbow` 分支对阴影层乘 0.25。
  上游的 `applyHueColor` 只输出满亮度色相，不乘的话阴影会和正文一样亮，看起来像重影。
  这是本包相对上游的**唯一一处管线代码改动**（2 行）。

**删除**

* 上游的其它版本分支：`1_21_2/`、`1_21_6/`、`26_1/`、`26_2/`，以及 base 里给旧格式（pack_format 7–35）用的核心着色器。
  本包只面向 26.3。
* 上游的 `pack.png`、`CONFIGURATION.md`、`README.md`。

## 为什么没有把特效代码也删掉

上游把全部特效写在一个约 400 行的调度器 `apply_effect.glsl` 里，并由核心着色器无条件
`#include` 十几个特效文件。**只裁 `_config.glsl` 就能让它们全部无法触发**（行为上只剩彩虹），
而真正删除那些代码需要改写那 400 行调度器与着色器分支，且没有 Minecraft 客户端就无法编译验证——
一旦写坏，玩家看到的是整片文字异常。因此这一版只在彩虹分支上做了必要修改，其余管线不动。

如果你确实要连代码一起清掉，那一步需要配合一次真机验证（见 README 的验证清单）。

## 适用范围

`pack.mcmeta` 把范围限定为 `min_format`/`max_format` = **97.1**，即 Minecraft 26.3。
从 1.21.9 起资源包格式改成「主.次」两段（26.3 = 97.1），并且只有 `min_format`/`max_format`
能带小数——`pack_format` 保持整数。锁死在 97.1 而不是往上兼容，是因为这一套是 26.3 的文本管道：
26.4 的 snapshot 已经又改了 shader，未来版本要重新从上游对应 overlay 移植一次。
