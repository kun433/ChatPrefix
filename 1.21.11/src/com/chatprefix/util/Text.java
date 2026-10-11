package com.chatprefix.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

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

    /** 裸 &lt;rainbow&gt; 标签；带参数的 &lt;rainbow:2&gt; 不匹配，保持用户自己指定的静态相位。 */
    private static final Pattern BARE_RAINBOW = Pattern.compile("(?i)<rainbow\\s*>");

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
        return parse(raw, allowMiniMessage, 0);
    }

    /**
     * 解析一段文本，并可把裸 &lt;rainbow&gt; 换成带相位的 &lt;rainbow:N&gt;，让彩虹随时间流动。
     *
     * @param rainbowPhase 0-9 的整数相位（MiniMessage 只接受 0-10，1 单位 = 36°，10 = 一整圈）；
     *                     0 表示不动。只替换不带参数的 &lt;rainbow&gt;，&lt;rainbow:2&gt; 这类写法原样保留，
     *                     免得把用户自己指定的固定相位也改掉。
     */
    public static Component parse(String raw, boolean allowMiniMessage, int rainbowPhase) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        // 顺序很重要：先按需把 < > 转义成普通字符，再转换 & 颜色代码。
        // 反过来会把 legacyToTags 刚生成的 <red> 也一起转义掉，&c 就失效了。
        String converted = allowMiniMessage ? raw : MM.escapeTags(raw);
        converted = legacyToTags(converted);
        if (allowMiniMessage && rainbowPhase != 0) {
            converted = BARE_RAINBOW.matcher(converted)
                    .replaceAll("<rainbow:" + Math.floorMod(rainbowPhase, 10) + ">");
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

    /** 文本里是否含裸 &lt;rainbow&gt; 标签：调用方用它判断「这个前缀想要彩虹」。 */
    public static boolean wantsRainbow(String raw) {
        return raw != null && BARE_RAINBOW.matcher(raw).find();
    }

    /**
     * 强制整棵树的颜色（**覆盖**节点已有颜色），其它样式保留。
     *
     * <p>与 {@link #tint} 的区别：tint 只补「本身没有颜色」的节点，forceColor 是全部改掉。
     * 用于把整个前缀刷成资源包约定的魔法色——着色器要求每个字都是那个精确颜色。
     */
    public static Component forceColor(Component component, TextColor color) {
        if (component == null || color == null) {
            return component;
        }
        Component styled = component.color(color);
        if (component.children().isEmpty()) {
            return styled;
        }
        List<Component> children = new ArrayList<>(component.children().size());
        for (Component child : component.children()) {
            children.add(forceColor(child, color));
        }
        return styled.children(children);
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
        return tint(component, color(colorName));
    }

    /** 同 {@link #tint(Component, String)}，但直接接收已经解析好的颜色，便于按文字颜色回填。 */
    public static Component tint(Component component, TextColor color) {
        if (component == null || color == null) {
            return component;
        }
        Component styled = component.colorIfAbsent(color);
        if (component.children().isEmpty()) {
            return styled;
        }
        List<Component> children = new ArrayList<>(component.children().size());
        for (Component child : component.children()) {
            children.add(tint(child, color));
        }
        return styled.children(children);
    }

    /**
     * 组件树里第一个显式颜色：先看节点自身，再按子节点顺序往下找。
     * 整棵树都没有颜色时返回 null。用来让前缀外层方括号跟随文字颜色。
     */
    public static TextColor firstColor(Component component) {
        if (component == null) {
            return null;
        }
        if (component.color() != null) {
            return component.color();
        }
        for (Component child : component.children()) {
            TextColor found = firstColor(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 组件树里最后一个显式颜色：从最后一个子节点往前找；整棵树都没有颜色时返回 null。 */
    public static TextColor lastColor(Component component) {
        if (component == null) {
            return null;
        }
        List<Component> children = component.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            TextColor found = lastColor(children.get(i));
            if (found != null) {
                return found;
            }
        }
        return component.color();
    }
}
