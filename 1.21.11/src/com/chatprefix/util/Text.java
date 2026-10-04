package com.chatprefix.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * 文本解析工具：把「传统颜色代码（&amp;a / §a）」与「MiniMessage 标签（&lt;red&gt;）」统一成一个组件。
 *
 * <p>只用于配置文本和 op 用指令输入的前缀文字；玩家 ID 与玩家聊天内容一律不经过这里，
 * 避免玩家把自己的消息伪装成标签。
 */
public final class Text {

    private static final char SECTION = '\u00a7';
    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** 传统颜色代码 → MiniMessage 标签。 */
    private static final Map<Character, String> LEGACY = new LinkedHashMap<>();

    static {
        LEGACY.put('0', "black");
        LEGACY.put('1', "dark_blue");
        LEGACY.put('2', "dark_green");
        LEGACY.put('3', "dark_aqua");
        LEGACY.put('4', "dark_red");
        LEGACY.put('5', "dark_purple");
        LEGACY.put('6', "gold");
        LEGACY.put('7', "gray");
        LEGACY.put('8', "dark_gray");
        LEGACY.put('9', "blue");
        LEGACY.put('a', "green");
        LEGACY.put('b', "aqua");
        LEGACY.put('c', "red");
        LEGACY.put('d', "light_purple");
        LEGACY.put('e', "yellow");
        LEGACY.put('f', "white");
        LEGACY.put('k', "obfuscated");
        LEGACY.put('l', "bold");
        LEGACY.put('m', "strikethrough");
        LEGACY.put('n', "underlined");
        LEGACY.put('o', "italic");
        LEGACY.put('r', "reset");
    }

    private Text() {
    }

    /**
     * 把 &amp; 与 § 颜色代码转成 MiniMessage 标签，方便习惯旧写法的 op 直接输入 &amp;c[本服]。
     * 连续两个 &amp; 表示一个字面 &amp;。
     */
    public static String legacyToTags(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c == '&' || c == SECTION) && i + 1 < raw.length()) {
                char next = raw.charAt(i + 1);
                if (next == '&' || next == SECTION) {
                    out.append(c);
                    i++;
                    continue;
                }
                String tag = LEGACY.get(Character.toLowerCase(next));
                if (tag != null) {
                    out.append('<').append(tag).append('>');
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * 解析一段文本。
     *
     * @param raw              原文本，支持 &amp; 颜色代码；allowMiniMessage 为 true 时同时支持 &lt;red&gt; 这类标签
     * @param allowMiniMessage false 表示把 &lt; &gt; 当作普通字符，只认 &amp; 颜色代码
     */
    public static Component parse(String raw, boolean allowMiniMessage) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        String converted = legacyToTags(raw);
        if (!allowMiniMessage) {
            converted = MM.escapeTags(converted);
        }
        try {
            return MM.deserialize(converted);
        } catch (RuntimeException ex) {
            try {
                return Component.text(MM.stripTags(converted));
            } catch (RuntimeException ignored) {
                return Component.text(raw);
            }
        }
    }

    /** 去掉颜色代码与标签后的可见长度，用于限制前缀长度。 */
    public static int visibleLength(String raw) {
        if (raw == null) {
            return 0;
        }
        try {
            return MM.stripTags(legacyToTags(raw)).length();
        } catch (RuntimeException ex) {
            return raw.length();
        }
    }

    /** 解析颜色设置：命名颜色（gray / light_purple / gold …）或 #rrggbb；留空或无法识别返回 null。 */
    public static TextColor color(String name) {
        if (name == null) {
            return null;
        }
        String value = name.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.charAt(0) == '#') {
            return TextColor.fromHexString(value);
        }
        return NamedTextColor.NAMES.value(value.toLowerCase(Locale.ROOT));
    }

    /**
     * 给整棵树上「本身没有颜色」的节点套上默认颜色，已有颜色的节点保持不变。
     * 不用 Component#applyFallbackStyle：实测它不会把样式带到子节点，
     * 而这里正好需要 "[末地]" 这种由多个子节点拼出来的段落整体上色。
     */
    public static Component tint(Component component, String colorName) {
        TextColor color = color(colorName);
        if (component == null || color == null) {
            return component;
        }
        Component styled = component.colorIfAbsent(color);
        if (component.children().isEmpty()) {
            return styled;
        }
        List<Component> children = new ArrayList<>(component.children().size());
        for (Component child : component.children()) {
            children.add(tint(child, colorName));
        }
        return styled.children(children);
    }
}
