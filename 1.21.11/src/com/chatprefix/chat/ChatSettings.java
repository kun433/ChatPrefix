package com.chatprefix.chat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.World;
import org.bukkit.configuration.Configuration;

/**
 * 从 config.yml 读出的显示设置。所有字段在构造时定型，运行期只读；
 * 读取方式与 {@link #defaults()} 共用同一套默认值，保证配置文件缺项时行为可预期。
 */
public final class ChatSettings {

    private final String format;
    private final String prefixDefault;
    private final String prefixWrapper;
    private final String prefixColor;
    private final boolean prefixAllowMiniMessage;
    private final int prefixMaxLength;
    private final String dimensionWrapper;
    private final String dimensionCustomLabel;
    private final Map<String, String> dimensionLabels;
    private final Map<String, String> dimensionColors;
    private final boolean nameUseDisplayName;
    private final String nameColor;
    private final String separatorText;
    private final String separatorColor;

    private ChatSettings(String format, String prefixDefault, String prefixWrapper, String prefixColor,
                         boolean prefixAllowMiniMessage, int prefixMaxLength, String dimensionWrapper,
                         String dimensionCustomLabel, Map<String, String> dimensionLabels,
                         Map<String, String> dimensionColors, boolean nameUseDisplayName, String nameColor,
                         String separatorText, String separatorColor) {
        this.format = format;
        this.prefixDefault = prefixDefault;
        this.prefixWrapper = prefixWrapper;
        this.prefixColor = prefixColor;
        this.prefixAllowMiniMessage = prefixAllowMiniMessage;
        this.prefixMaxLength = prefixMaxLength;
        this.dimensionWrapper = dimensionWrapper;
        this.dimensionCustomLabel = dimensionCustomLabel;
        this.dimensionLabels = Map.copyOf(dimensionLabels);
        this.dimensionColors = Map.copyOf(dimensionColors);
        this.nameUseDisplayName = nameUseDisplayName;
        this.nameColor = nameColor;
        this.separatorText = separatorText;
        this.separatorColor = separatorColor;
    }

    /** 内置默认值，等价于 config.yml 全部缺项时的结果（用空 Configuration 走同一套读取逻辑）。 */
    public static ChatSettings defaults() {
        return load(new org.bukkit.configuration.MemoryConfiguration());
    }

    public static ChatSettings load(Configuration cfg) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("OVERWORLD", cfg.getString("dimension.labels.OVERWORLD", "主世界"));
        labels.put("NETHER", cfg.getString("dimension.labels.NETHER", "下界"));
        labels.put("THE_END", cfg.getString("dimension.labels.THE_END", "末地"));

        Map<String, String> colors = new LinkedHashMap<>();
        colors.put("OVERWORLD", cfg.getString("dimension.colors.OVERWORLD", "green"));
        colors.put("NETHER", cfg.getString("dimension.colors.NETHER", "red"));
        colors.put("THE_END", cfg.getString("dimension.colors.THE_END", "light_purple"));
        colors.put("CUSTOM", cfg.getString("dimension.colors.CUSTOM", "yellow"));

        return new ChatSettings(
                cfg.getString("format", "{prefix}{dimension}{name}{separator}{message}"),
                cfg.getString("prefix.default", ""),
                cfg.getString("prefix.wrapper", "[{text}]"),
                cfg.getString("prefix.color", ""),
                cfg.getBoolean("prefix.allow-minimessage", true),
                Math.max(0, cfg.getInt("prefix.max-length", 24)),
                cfg.getString("dimension.wrapper", "[{label}]"),
                cfg.getString("dimension.custom-label", "{world}"),
                labels,
                colors,
                cfg.getBoolean("name.use-display-name", false),
                cfg.getString("name.color", ""),
                cfg.getString("separator.text", " » "),
                cfg.getString("separator.color", "gray"));
    }

    /** 整行模板，占位符：{prefix} {dimension} {name} {separator} {message}。 */
    public String format() {
        return format;
    }

    /** 未设置前缀的玩家显示什么；空串表示这一整段不显示。 */
    public String prefixDefault() {
        return prefixDefault;
    }

    /** 前缀外层包裹，{text} 是 op 输入的原文字。 */
    public String prefixWrapper() {
        return prefixWrapper;
    }

    /** 前缀默认颜色；空串表示不额外上色。 */
    public String prefixColor() {
        return prefixColor;
    }

    public boolean prefixAllowMiniMessage() {
        return prefixAllowMiniMessage;
    }

    public int prefixMaxLength() {
        return prefixMaxLength;
    }

    public String dimensionWrapper() {
        return dimensionWrapper;
    }

    public boolean nameUseDisplayName() {
        return nameUseDisplayName;
    }

    public String nameColor() {
        return nameColor;
    }

    public String separatorText() {
        return separatorText;
    }

    public String separatorColor() {
        return separatorColor;
    }

    /** 维度标签文案；主世界 / 下界 / 末地之外的维度用 custom-label 模板（{world} 为世界名）。 */
    public String dimensionLabel(World.Environment environment, String worldName) {
        String key = key(environment);
        String label = key == null ? null : dimensionLabels.get(key);
        if (label != null) {
            return label;
        }
        return dimensionCustomLabel.replace("{world}", worldName == null ? "" : worldName);
    }

    /** 维度标签颜色；自定义维度取 colors.CUSTOM。 */
    public String dimensionColor(World.Environment environment) {
        String key = key(environment);
        return dimensionColors.getOrDefault(key == null ? "CUSTOM" : key, "");
    }

    private static String key(World.Environment environment) {
        if (environment == null) {
            return null;
        }
        switch (environment) {
            case NORMAL:
                return "OVERWORLD";
            case NETHER:
                return "NETHER";
            case THE_END:
                return "THE_END";
            default:
                return null;
        }
    }
}
