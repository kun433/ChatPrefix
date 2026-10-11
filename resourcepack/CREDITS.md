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

* `assets/minecraft/shaders/core/` 下 1.21.6–1.21.11 这一代用到的 8 个核心着色器
  （`rendertype_text` / `_intensity` / `_intensity_see_through` / `_see_through` 的 `.vsh` + `.fsh`），
  取自上游的 `1_21_6/` overlay。
* `assets/minecraft/shaders/include/` 下的公共管线文件（30 个 `.glsl`），
  其中 `spin_effect.glsl` 与 `text_effects_utils.glsl` 用的是上游 `1_21_6/` 的版本。

**改写**

* `assets/minecraft/shaders/include/_config.glsl` 被裁到只剩一条映射：
  `#F8F85C → apply_rainbow()`。除这个颜色外，任何颜色都不会触发特效。

**删除**

* 上游的其它版本分支：`1_21_2/`、`26_1/`、`26_2/`、`26_3/`，以及 base 里给旧格式（pack_format 7–35）用的核心着色器。
  本包只面向 1.21.6–1.21.11。
* 上游的 `pack.png`、`CONFIGURATION.md`、`README.md`。

## 为什么没有把特效代码也删掉

上游把全部特效写在一个约 400 行的调度器 `apply_effect.glsl` 里，并由 8 个核心着色器无条件
`#moj_import` 十几个特效文件。**只裁 `_config.glsl` 就能让它们全部无法触发**（行为上只剩彩虹），
而真正删除那些代码需要改写那 400 行调度器与 8 个着色器分支，且没有 Minecraft 客户端就无法编译验证——
一旦写坏，玩家看到的是整片文字异常。因此这一版选择只裁配置、不动管线。

如果你确实要连代码一起清掉，那一步需要配合一次真机验证（见 README 的验证清单）。

## 适用范围

`pack.mcmeta` 把范围限定为 `min_format`/`max_format` = **57–75**，即 Minecraft 1.21.6 至 1.21.11。
设成 75 而不是上游的 100，是为了避免在 26.x（format 76+）上套用这一代着色器——那一代上游换了新的
overlay 实现，硬套会出问题。将来要支持 1.21.11 之后的小版本时，把这两个数字一起往上调，并重新验证。
