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
        Map<String, Component> parts = new LinkedHashMap<>();
        parts.put("prefix", prefix(prefixRaw));
        parts.put("dimension", dimension(environment, worldName));
        parts.put("name", name == null ? Component.empty() : name);
        parts.put("separator", separator());
        parts.put("message", message == null ? Component.empty() : message);
        return template(settings.format(), parts);
    }

    /** 前缀段；未设置或配置为空白时不显示。 */
    public Component prefix(String prefixRaw) {
        String raw = prefixRaw == null ? settings.prefixDefault() : prefixRaw;
        if (raw == null || raw.isBlank()) {
            return Component.empty();
        }
        Component body = Text.parse(raw, settings.prefixAllowMiniMessage());
        String wrapper = settings.prefixWrapper();
        if (wrapper == null || !wrapper.contains("{text}")) {
            // 包裹格式写错时宁可直接显示文字，也不要让 op 设置的前缀凭空消失
            wrapper = "{text}";
        }
        return Text.tint(template(wrapper, Map.of("text", body)), settings.prefixColor());
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
