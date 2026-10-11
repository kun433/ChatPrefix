package com.chatprefix.chat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.chatprefix.util.Text;

import net.kyori.adventure.text.Component;
import org.bukkit.World;

/**
 * 纯函数式的聊天行渲染：给定前缀原文、维度、玩家名与消息，产出一整行组件。
 *
 * <p>不读取 Bukkit 运行期状态（维度通过参数传入），因此可以脱离服务器做离线断言。
 * 默认模板 {prefix}{dimension}{name}{separator}{message} 对应：
 * {@code [本服][末地]aaa_ovo » 第二个末地城}
 */
public final class ChatFormatter {

    private static final Pattern TOKEN = Pattern.compile("\\{([a-z_]+)\\}");

    private final ChatSettings settings;

    public ChatFormatter(ChatSettings settings) {
        this.settings = settings;
    }

    public ChatSettings settings() {
        return settings;
    }

    /** 用真实玩家 ID 生成名字组件，并按 name.color 上色（留空则不改动）。 */
    public Component name(String playerId) {
        return Text.tint(Component.text(playerId == null ? "" : playerId), settings.nameColor());
    }

    /**
     * 渲染一整行聊天。
     *
     * @param prefixRaw   op 设置的前缀原文；null 表示没设置，改用配置里的默认（默认空 = 不显示前缀段）
     * @param environment 发送消息时玩家所在维度
     * @param worldName   世界名，用于自定义维度的标签
     * @param name        名字组件（调用方决定用真实 ID 还是显示名）
     * @param message     玩家消息原文组件，保持原样、不再解析
     */
    public Component render(String prefixRaw, World.Environment environment, String worldName,
                            Component name, Component message) {
        return render(prefixRaw, environment, worldName, name, message, 0, false);
    }

    /**
     * 渲染一整行聊天，并指定彩虹相位。
     *
     * @param rainbowPhase 0-9 的整数相位，只有前缀里的裸 &lt;rainbow&gt; 会受影响；0 表示不动
     */
    public Component render(String prefixRaw, World.Environment environment, String worldName,
                            Component name, Component message, int rainbowPhase) {
        return render(prefixRaw, environment, worldName, name, message, rainbowPhase, false);
    }

    /**
     * 渲染一整行聊天，并区分接收者是否装了资源包。
     *
     * @param rainbowPhase 0-9 的整数相位，只有前缀里的裸 &lt;rainbow&gt; 会受影响；0 表示不动
     * @param packViewer   这个接收者是否装了资源包（装了则彩虹交给着色器，见 {@link #prefix})
     */
    public Component render(String prefixRaw, World.Environment environment, String worldName,
                            Component name, Component message, int rainbowPhase, boolean packViewer) {
        Map<String, Component> parts = new LinkedHashMap<>();
        parts.put("prefix", prefix(prefixRaw, rainbowPhase, packViewer));
        parts.put("dimension", dimension(environment, worldName));
        parts.put("name", name == null ? Component.empty() : name);
        parts.put("separator", separator());
        parts.put("message", message == null ? Component.empty() : message);
        return template(settings.format(), parts);
    }

    public Component prefix(String prefixRaw) {
        return prefix(prefixRaw, 0, false);
    }

    public Component prefix(String prefixRaw, int rainbowPhase) {
        return prefix(prefixRaw, rainbowPhase, false);
    }

    /**
     * 前缀段；未设置或配置为空白时不显示。
     *
     * <p>上色优先级：文字自带的颜色代码 &gt; prefix.color &gt; 方括号跟随相邻文字颜色。
     * 即 {@code /cp set Steve &c大佬} 会得到整段红色的 {@code [大佬]}：文字是红的，
     * 外层方括号跟着变红（前括号取文字首个颜色，后括号取末个颜色），而不是白括号配红字。
     *
     * <p>{@code rainbowPhase} 非 0 时，文字里的裸 {@code <rainbow>} 会被换成带相位的
     * {@code <rainbow:N>}，于是每条消息的彩虹起点不同，看起来像在流动。
     *
     * <p>{@code packViewer} 为 true 表示这个接收者装了资源包：此时若前缀本来就写了裸
     * {@code <rainbow>}，整段（含方括号）会被刷成资源包约定的魔法色，交由 rendertype_text
     * 着色器做**原地**流动；没装包的接收者仍走上面的逐消息相位彩虹。
     */
    public Component prefix(String prefixRaw, int rainbowPhase, boolean packViewer) {
        String raw = prefixRaw == null ? settings.prefixDefault() : prefixRaw;
        if (raw == null || raw.isBlank()) {
            return Component.empty();
        }
        Component body = Text.parse(raw, settings.prefixAllowMiniMessage(), rainbowPhase);
        String wrapper = settings.prefixWrapper();
        String configured = settings.prefixColor();
        if (wrapper == null || !wrapper.contains("{text}")) {
            // 包裹格式写错时宁可直接显示文字，也不要让 op 设置的前缀凭空消失
            return magicColor(raw, packViewer, Text.tint(body, configured));
        }
        if (configured != null && !configured.isBlank()) {
            // prefix.color 已明确指定：整段（含方括号）统一上色，文字自带的颜色仍然优先
            return magicColor(raw, packViewer, Text.tint(template(wrapper, Map.of("text", body)), configured));
        }
        int first = wrapper.indexOf("{text}");
        if (first != wrapper.lastIndexOf("{text}")) {
            // wrapper 里写了多个 {text} 属于异常配置，退回「只管文字、不动括号」
            return magicColor(raw, packViewer, template(wrapper, Map.of("text", body)));
        }
        // 没配 prefix.color：方括号跟随相邻文字的颜色，文字本身没有颜色时保持不变
        int end = first + "{text}".length();
        Component segment = Component.empty()
                .append(Text.tint(Text.parse(wrapper.substring(0, first), true), Text.firstColor(body)))
                .append(body)
                .append(Text.tint(Text.parse(wrapper.substring(end), true), Text.lastColor(body)));
        return magicColor(raw, packViewer, segment);
    }

    /**
     * 装了资源包、且这个前缀本来就写了裸 {@code <rainbow>} 时，把整段刷成魔法色交着色器处理；
     * 否则原样返回。只有裸 {@code <rainbow>} 算数，{@code <rainbow:2>} 这种显式相位表示「要固定彩虹」，
     * 与逐消息相位那条规则保持一致。
     */
    private Component magicColor(String raw, boolean packViewer, Component segment) {
        if (!packViewer || !settings.packEnabled() || !Text.wantsRainbow(raw)) {
            return segment;
        }
        return Text.forceColor(segment, Text.color(settings.packMagicColor()));
    }

    /** 维度段：主世界 / 下界 / 末地 / 自定义世界名，所有玩家都会有。 */
    public Component dimension(World.Environment environment, String worldName) {
        String label = settings.dimensionLabel(environment, worldName);
        if (label == null || label.isBlank()) {
            return Component.empty();
        }
        Component body = Text.parse(label, true);
        return Text.tint(template(settings.dimensionWrapper(), Map.of("label", body)),
                settings.dimensionColor(environment));
    }

    /** 名字与消息之间的分隔符。 */
    public Component separator() {
        Component body = Text.parse(settings.separatorText(), true);
        return Text.tint(body, settings.separatorColor());
    }

    /**
     * 通用模板替换：{token} 换成对应组件，其余文本按配置解析规则解析（允许 MiniMessage）；
     * 未知占位符原样输出，避免配置里写错一个词就整行消失。
     */
    private Component template(String pattern, Map<String, Component> parts) {
        if (pattern == null || pattern.isEmpty()) {
            return Component.empty();
        }
        Component result = Component.empty();
        Matcher matcher = TOKEN.matcher(pattern);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                result = result.append(Text.parse(pattern.substring(last, matcher.start()), true));
            }
            Component replacement = parts.get(matcher.group(1));
            result = result.append(replacement == null ? Component.text(matcher.group()) : replacement);
            last = matcher.end();
        }
        if (last < pattern.length()) {
            result = result.append(Text.parse(pattern.substring(last), true));
        }
        return result;
    }
}
