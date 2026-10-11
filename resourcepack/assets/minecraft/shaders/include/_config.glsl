// ============================================================
// ChatPrefix 彩虹前缀 —— 裁剪版配置
// ============================================================
// 这里只保留一条映射：触发色 #F8F85C → 持续滚动的彩虹。
// 插件把这个颜色用在「装了本资源包」的玩家所看到的前缀文字上，
// 客户端就会把那段文字渲染成原地流动的彩虹（不再依赖每条消息推进相位）。
//
// 文件位置是 Text Effects 官方留的扩展点：text_effects_utils.glsl 单点 import 它。
// 除本文件列出的映射外，任何颜色都不会触发出特效。
//
// 语法：TEXT_EFFECT(rgb(R, G, B)) { apply_effect(); }
//   - 触发色分量建议能被 4 整除；两个触发色的差值不得小于 4
//     （阴影判定会把每个分量除以 4 取整，太近会串效果）。
//   - TEXT_EFFECT 只作用于正文；TEXT_EFFECT_WITH_SHADOW 连阴影一起。
//   - apply_color(...) 可以把显示色改成与触发色不同的值。
//   - apply_rainbow() 使用默认速度；要给速度就写 apply_rainbow(1500.0)。
// 需要别的特效（shake / wavy / aurora / neon …）时，
// 从上游 Text Effects 的 _config.glsl 把对应条目贴回来即可，见 CREDITS.md。
// ============================================================

// Rainbow (#F8F85C)
// 触发色必须与 ChatPrefix 的 config.yml 里 pack.magic-color 保持一致
TEXT_EFFECT(rgb(0xF8F85C)) {
    apply_rainbow();
}
