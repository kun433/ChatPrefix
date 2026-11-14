package com.chatprefix.title;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 称号池：op 在 titles.yml 里预定义一批称号（支持与前缀相同的全部写法，包括 CE 图片和
 * 彩虹标签），再用指令把称号授权给玩家；玩家在授权范围内自己切换当前使用的称号。
 *
 * <p>文件分两节：
 * <pre>
 * titles:
 *   vip:
 *     text: "&amp;6&amp;lVIP"        # 与 /cp set 的文字写法完全一致
 *     rainbow: false          # 可选，这个称号默认按彩虹渲染
 * grants:
 *   "&lt;uuid&gt;":
 *     name: Steve
 *     titles: [vip, boss]     # 授权列表；当前使用哪一个记录在 prefixes.yml 的 title 字段
 * </pre>
 *
 * <p>称号 id 不区分大小写：内存里统一按小写存放与查找，写进文件时保留小写形式。
 */
public final class TitleStore {

    /** 一个称号的定义；id 为小写。 */
    public record Title(String id, String text, boolean rainbow) {
    }

    /** 一个玩家的授权记录；name 只用于回显。titles 是有序集合，按授权先后排列。 */
    public record Grant(String name, LinkedHashSet<String> titles) {
    }

    private final File file;
    private final Map<String, Title> titles = new ConcurrentHashMap<>();
    private final Map<UUID, Grant> grants = new ConcurrentHashMap<>();

    public TitleStore(File file) {
        this.file = file;
    }

    /** 从磁盘读取；文件不存在视为空。 */
    public void load() {
        titles.clear();
        grants.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection defs = yaml.getConfigurationSection("titles");
        if (defs != null) {
            for (String id : defs.getKeys(false)) {
                ConfigurationSection section = defs.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                String text = section.getString("text");
                if (text == null || text.isBlank()) {
                    continue;
                }
                String key = id.toLowerCase();
                titles.put(key, new Title(key, text, section.getBoolean("rainbow", false)));
            }
        }
        ConfigurationSection granted = yaml.getConfigurationSection("grants");
        if (granted != null) {
            for (String key : granted.getKeys(false)) {
                ConfigurationSection section = granted.getConfigurationSection(key);
                UUID uuid = parseUuid(key);
                if (section == null || uuid == null) {
                    continue;
                }
                List<String> list = section.getStringList("titles");
                LinkedHashSet<String> ids = new LinkedHashSet<>();
                for (String id : list) {
                    ids.add(id.toLowerCase());
                }
                grants.put(uuid, new Grant(section.getString("name", key), ids));
            }
        }
    }

    /** 全量写回磁盘（量很小，直接同步写）。 */
    public void save() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Title title : allTitles()) {
            String path = "titles." + title.id();
            yaml.set(path + ".text", title.text());
            if (title.rainbow()) {
                yaml.set(path + ".rainbow", true);
            }
        }
        for (Map.Entry<UUID, Grant> entry : grants.entrySet()) {
            String path = "grants." + entry.getKey();
            yaml.set(path + ".name", entry.getValue().name());
            if (!entry.getValue().titles().isEmpty()) {
                yaml.set(path + ".titles", new ArrayList<>(entry.getValue().titles()));
            }
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("无法创建目录 " + parent);
        }
        yaml.save(file);
    }

    /** 按小写 id 查称号定义；不存在返回 null。 */
    public Title title(String id) {
        return id == null ? null : titles.get(id.toLowerCase());
    }

    /** 全部称号定义，按 id 排序，供 /cp title list 使用。 */
    public List<Title> allTitles() {
        List<Title> list = new ArrayList<>(titles.values());
        list.sort((a, b) -> a.id().compareTo(b.id()));
        return list;
    }

    public boolean hasGrant(UUID uuid, String id) {
        Grant grant = uuid == null ? null : grants.get(uuid);
        return grant != null && id != null && grant.titles().contains(id.toLowerCase());
    }

    /**
     * 授权一个称号；称号必须已定义。已授权过时返回 false（幂等）。
     */
    public boolean grant(UUID uuid, String name, String id) {
        Title title = title(id);
        if (uuid == null || title == null) {
            return false;
        }
        Grant grant = grants.get(uuid);
        if (grant == null) {
            grant = new Grant(name == null ? uuid.toString() : name, new LinkedHashSet<>());
            grants.put(uuid, grant);
        } else if (name != null && !name.isBlank()) {
            grant = new Grant(name, grant.titles());
            grants.put(uuid, grant);
        }
        return grant.titles().add(title.id());
    }

    /**
     * 收回一个称号。返回是否真的收回了（本来就没有返回 false）。
     * 调用方负责检查该玩家当前是否正使用这个称号并做相应处理。
     */
    public boolean revoke(UUID uuid, String id) {
        Grant grant = uuid == null ? null : grants.get(uuid);
        if (grant == null || id == null) {
            return false;
        }
        return grant.titles().remove(id.toLowerCase());
    }

    /**
     * 玩家被授权、且称号仍有定义的列表（按授权顺序）。
     * 定义已被删除的授权残留在文件里但不显示，重新定义同名称号后自动恢复。
     */
    public List<Title> titlesOf(UUID uuid) {
        Grant grant = uuid == null ? null : grants.get(uuid);
        if (grant == null) {
            return List.of();
        }
        List<Title> result = new ArrayList<>(grant.titles().size());
        for (String id : grant.titles()) {
            Title title = titles.get(id);
            if (title != null) {
                result.add(title);
            }
        }
        return result;
    }

    /** 全部授权记录，按玩家名排序，供 /cp title info 与 tab 补全使用。 */
    public List<Map.Entry<UUID, Grant>> allGrants() {
        List<Map.Entry<UUID, Grant>> list = new ArrayList<>(grants.entrySet());
        list.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                a.getValue().name() == null ? "" : a.getValue().name(),
                b.getValue().name() == null ? "" : b.getValue().name()));
        return list;
    }

    /** 只取授权记录本身（不带 uuid），按玩家名排序，供补全玩家名列表。 */
    public List<Grant> grantsByName() {
        List<Grant> list = new ArrayList<>();
        for (Map.Entry<UUID, Grant> entry : allGrants()) {
            list.add(entry.getValue());
        }
        return list;
    }

    /** 按玩家名（不区分大小写）查授权记录的 uuid；没有返回 null。 */
    public UUID findGrantUuidByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (Map.Entry<UUID, Grant> entry : grants.entrySet()) {
            String granted = entry.getValue().name();
            if (granted != null && granted.equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public int titleCount() {
        return titles.size();
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
