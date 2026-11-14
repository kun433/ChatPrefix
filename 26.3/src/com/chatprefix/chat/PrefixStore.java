package com.chatprefix.chat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 玩家前缀的持久化存储：UUID → 前缀原文（可含颜色代码），落在插件数据目录的 prefixes.yml。
 *
 * <p>底层用 ConcurrentHashMap：聊天事件在异步线程读，指令在主线程写，两侧都要安全。
 */
public final class PrefixStore {

    /**
     * 一条前缀记录；name 只用于回显与查找，不参与匹配逻辑。
     *
     * @param rainbow 彩虹开关（{@code /cp set ... --rainbow}）。true 时前缀按彩虹渲染，
     *                原文里不需要写 {@code <rainbow>}；与原文里手写的标签是「或」的关系。
     * @param title   当前使用的称号 id（titles.yml 里的定义）；null 表示未用称号，
     *                此时显示 prefix。prefix 为空串表示没有手动前缀（只用称号）。
     */
    public record Entry(UUID uuid, String name, String prefix, boolean rainbow, String title) {

        /** 兼容旧代码的构造：没有称号。 */
        public Entry(UUID uuid, String name, String prefix, boolean rainbow) {
            this(uuid, name, prefix, rainbow, null);
        }
    }

    private final File file;
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public PrefixStore(File file) {
        this.file = file;
    }

    /** 从磁盘读取；文件不存在视为空。 */
    public void load() {
        entries.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            String prefix = section.getString("prefix");
            String title = section.getString("title");
            // 只有称号、没有手动前缀的记录也要保留（prefix 空串 + title 非空）
            if ((prefix == null || prefix.isBlank()) && (title == null || title.isBlank())) {
                continue;
            }
            UUID uuid = parseUuid(key);
            if (uuid == null) {
                continue;
            }
            entries.put(uuid, new Entry(uuid, section.getString("name", key),
                    prefix == null ? "" : prefix,
                    section.getBoolean("rainbow", false),
                    title == null || title.isBlank() ? null : title));
        }
    }

    /** 全量写回磁盘（量很小，直接同步写）。 */
    public void save() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Entry entry : all()) {
            String path = "players." + entry.uuid();
            yaml.set(path + ".name", entry.name());
            yaml.set(path + ".prefix", entry.prefix());
            // 关着就不写，老配置文件不会平白多出一行 false
            if (entry.rainbow()) {
                yaml.set(path + ".rainbow", true);
            }
            if (entry.title() != null) {
                yaml.set(path + ".title", entry.title());
            }
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("无法创建目录 " + parent);
        }
        yaml.save(file);
    }

    public Entry get(UUID uuid) {
        return uuid == null ? null : entries.get(uuid);
    }

    /** 返回前缀原文，未设置返回 null。 */
    public String prefixOf(UUID uuid) {
        Entry entry = get(uuid);
        return entry == null ? null : entry.prefix();
    }

    public void set(UUID uuid, String name, String prefix) {
        set(uuid, name, prefix, false);
    }

    /** /cp set 的写入：直接指定前缀原文，并清掉称号选择（显式覆盖）。 */
    public void set(UUID uuid, String name, String prefix, boolean rainbow) {
        entries.put(uuid, new Entry(uuid, name, prefix, rainbow, null));
    }

    /**
     * 更新当前使用的称号 id（null = 回到手动前缀）。
     * 记录不存在时创建一条只有称号的记录（prefix 为空串）。
     */
    public Entry setTitle(UUID uuid, String name, String titleId) {
        Entry current = entries.get(uuid);
        Entry updated = current == null
                ? new Entry(uuid, name == null ? uuid.toString() : name, "", false, titleId)
                : new Entry(current.uuid(), name != null && !name.isBlank() ? name : current.name(),
                        current.prefix(), current.rainbow(), titleId);
        entries.put(uuid, updated);
        return updated;
    }

    /** 删除并返回被删掉的记录，没有则返回 null。 */
    public Entry remove(UUID uuid) {
        return uuid == null ? null : entries.remove(uuid);
    }

    /** 按玩家名查找（不区分大小写），用于给已离线且不在缓存里的玩家改名或删除。 */
    public Entry findByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (Entry entry : entries.values()) {
            if (entry.name() != null && entry.name().equalsIgnoreCase(name)) {
                return entry;
            }
        }
        return null;
    }

    /** 全部记录，按玩家名排序，供 /chatprefix list 使用。 */
    public List<Entry> all() {
        List<Entry> list = new ArrayList<>(entries.values());
        list.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                a.name() == null ? "" : a.name(), b.name() == null ? "" : b.name()));
        return list;
    }

    public int size() {
        return entries.size();
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
