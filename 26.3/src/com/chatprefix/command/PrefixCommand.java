package com.chatprefix.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.chatprefix.ChatPrefix;
import com.chatprefix.chat.ChatSettings;
import com.chatprefix.chat.PrefixStore;
import com.chatprefix.util.Text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * /chatprefix —— op 给玩家设置聊天前缀。
 *
 * <p>set / remove / get / list / reload / help。
 */
public final class PrefixCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "chatprefix.admin";
    private static final String PREFIX = "[聊天前缀] ";
    private static final int LIST_LIMIT = 100;

    private static final List<String> SUBCOMMANDS = List.of("set", "rainbow", "remove", "get", "list", "pack", "reload", "help");

    private final ChatPrefix plugin;

    public PrefixCommand(ChatPrefix plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            reply(sender, Component.text("你没有权限使用这个指令。", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set":
                return set(sender, label, args);
            case "rainbow":
                return rainbow(sender, label, args);
            case "remove":
            case "del":
            case "delete":
                return remove(sender, label, args);
            case "get":
                return get(sender, label, args);
            case "list":
                return list(sender);
            case "pack":
                return packDiagnostics(sender);
            case "reload":
                return reload(sender);
            case "help":
            default:
                help(sender, label);
                return true;
        }
    }

    /**
     * /cp set &lt;玩家&gt; [--rainbow] &lt;文字&gt;
     *
     * <p>彩虹开关写在玩家名后面（也可以在末尾补一个），支持 {@code --rainbow} / {@code -r} /
     * {@code rainbow} / {@code 彩虹} 四种写法。开关只影响「这个前缀按彩虹渲染」，不改动文字本身。
     */
    private boolean set(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            reply(sender, Component.text("用法：/" + label + " set <玩家> [--rainbow] <文字>", NamedTextColor.RED));
            return true;
        }
        Target target = resolve(args[1]);
        if (target == null) {
            reply(sender, Component.text("找不到玩家 " + args[1]
                    + "：他不在线，服务器也没有他的记录。让他先上线一次再设置。", NamedTextColor.RED));
            return true;
        }
        List<String> tokens = new ArrayList<>(Arrays.asList(args).subList(2, args.length));
        if (tokens.size() == 1 && isRainbowFlag(tokens.get(0))) {
            reply(sender, Component.text("开关后面还需要前缀文字：/" + label + " set <玩家> --rainbow <文字>",
                    NamedTextColor.RED));
            return true;
        }
        boolean rainbow = false;
        // 前置开关：/cp set Steve --rainbow 本服
        while (tokens.size() > 1 && isRainbowFlag(tokens.get(0))) {
            rainbow = true;
            tokens.remove(0);
        }
        // 后置开关：/cp set Steve 本服 --rainbow（至少给文字留一个 token，避免把「彩虹」这种前缀吞掉）
        while (tokens.size() > 1 && isRainbowFlag(tokens.get(tokens.size() - 1))) {
            rainbow = true;
            tokens.remove(tokens.size() - 1);
        }
        String text = String.join(" ", tokens).trim();
        int visible = Text.visibleLength(text);
        int max = plugin.settings().prefixMaxLength();
        if (visible == 0) {
            reply(sender, Component.text("前缀不能是空白。要取消前缀请用 /" + label + " remove " + target.name(), NamedTextColor.RED));
            return true;
        }
        if (max > 0 && visible > max) {
            reply(sender, Component.text("前缀太长了：可见长度 " + visible + " 个字符，上限 " + max
                    + "（可改 config.yml 的 prefix.max-length，填 0 表示不限制）。", NamedTextColor.RED));
            return true;
        }
        plugin.store().set(target.uuid(), target.name(), text, rainbow);
        if (!save(sender)) {
            return true;
        }
        reply(sender, Component.text("已把 ", NamedTextColor.GRAY)
                .append(Component.text(target.name(), NamedTextColor.WHITE))
                .append(Component.text(" 的前缀设为 ", NamedTextColor.GRAY))
                // 回显走逐消息相位彩虹，让人直接看到「这就是彩虹」，而不是资源包的魔法色
                .append(plugin.formatter().prefix(text, 1, false, rainbow))
                .append(Component.text("（原文：" + text + "，彩虹：" + (rainbow ? "开" : "关") + "）",
                        NamedTextColor.DARK_GRAY)));
        if (rainbow && !plugin.settings().prefixAllowMiniMessage()) {
            reply(sender, Component.text("注意：config.yml 的 prefix.allow-minimessage 是 false，"
                    + "尖括号会被当作普通文字，彩虹开关不会生效。", NamedTextColor.YELLOW));
        }
        return true;
    }

    /** 彩虹开关的四种写法（不区分大小写）。 */
    private static boolean isRainbowFlag(String token) {
        if (token == null) {
            return false;
        }
        switch (token.toLowerCase(Locale.ROOT)) {
            case "--rainbow":
            case "-r":
            case "rainbow":
            case "彩虹":
                return true;
            default:
                return false;
        }
    }

    /** /cp rainbow &lt;玩家&gt; on|off —— 不改文字，只反转彩虹开关。 */
    private boolean rainbow(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            reply(sender, Component.text("用法：/" + label + " rainbow <玩家> on|off", NamedTextColor.RED));
            return true;
        }
        Target target = resolve(args[1]);
        PrefixStore.Entry entry = target != null
                ? plugin.store().get(target.uuid())
                : plugin.store().findByName(args[1]);
        if (entry == null) {
            reply(sender, Component.text(args[1] + " 还没有设置前缀，先 /" + label + " set 再开彩虹。", NamedTextColor.YELLOW));
            return true;
        }
        boolean on;
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "on":
            case "true":
            case "开":
                on = true;
                break;
            case "off":
            case "false":
            case "关":
                on = false;
                break;
            default:
                reply(sender, Component.text("第二个参数只能是 on 或 off。", NamedTextColor.RED));
                return true;
        }
        plugin.store().set(entry.uuid(), entry.name(), entry.prefix(), on);
        if (!save(sender)) {
            return true;
        }
        reply(sender, Component.text("已把 ", NamedTextColor.GRAY)
                .append(Component.text(entry.name(), NamedTextColor.WHITE))
                .append(Component.text(" 的前缀彩虹设为 " + (on ? "开" : "关") + "：", NamedTextColor.GRAY))
                .append(plugin.formatter().prefix(entry.prefix(), 1, false, on)));
        if (on && !plugin.settings().prefixAllowMiniMessage()) {
            reply(sender, Component.text("注意：prefix.allow-minimessage 是 false，彩虹开关不会生效。", NamedTextColor.YELLOW));
        }
        return true;
    }

    private boolean remove(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            reply(sender, Component.text("用法：/" + label + " remove <玩家>", NamedTextColor.RED));
            return true;
        }
        String name = args[1];
        Target target = resolve(name);
        PrefixStore.Entry removed;
        if (target != null) {
            removed = plugin.store().remove(target.uuid());
        } else {
            PrefixStore.Entry found = plugin.store().findByName(name);
            removed = found == null ? null : plugin.store().remove(found.uuid());
        }
        if (removed == null) {
            reply(sender, Component.text(name + " 本来就没有设置前缀。", NamedTextColor.YELLOW));
            return true;
        }
        if (!save(sender)) {
            return true;
        }
        reply(sender, Component.text("已删除 ", NamedTextColor.GRAY)
                .append(Component.text(removed.name(), NamedTextColor.WHITE))
                .append(Component.text(" 的前缀（原为 ", NamedTextColor.GRAY))
                .append(plugin.formatter().prefix(removed.prefix()))
                .append(Component.text("），聊天里不再显示前缀段。", NamedTextColor.GRAY)));
        return true;
    }

    private boolean get(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            reply(sender, Component.text("用法：/" + label + " get <玩家>", NamedTextColor.RED));
            return true;
        }
        String name = args[1];
        Target target = resolve(name);
        PrefixStore.Entry entry = target != null
                ? plugin.store().get(target.uuid())
                : plugin.store().findByName(name);
        if (entry == null) {
            reply(sender, Component.text(name + " 没有设置前缀，聊天里不显示前缀段。", NamedTextColor.YELLOW));
            return true;
        }
        reply(sender, Component.text(entry.name() + " 的前缀是 ", NamedTextColor.GRAY)
                .append(plugin.formatter().prefix(entry.prefix(), 1, false, entry.rainbow()))
                .append(Component.text("（原文：" + entry.prefix() + "；彩虹：" + (entry.rainbow() ? "开" : "关") + "）",
                        NamedTextColor.DARK_GRAY)));
        return true;
    }

    private boolean list(CommandSender sender) {
        List<PrefixStore.Entry> all = plugin.store().all();
        if (all.isEmpty()) {
            reply(sender, Component.text("目前没有任何玩家设置前缀。", NamedTextColor.YELLOW));
            return true;
        }
        reply(sender, Component.text("共 " + all.size() + " 条前缀：", NamedTextColor.GRAY));
        int shown = 0;
        for (PrefixStore.Entry entry : all) {
            if (shown++ >= LIST_LIMIT) {
                reply(sender, Component.text("…还有 " + (all.size() - LIST_LIMIT) + " 条未显示。", NamedTextColor.DARK_GRAY));
                break;
            }
            sender.sendMessage(Component.text(" - ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(entry.name(), NamedTextColor.WHITE))
                    .append(Component.space())
                    .append(plugin.formatter().prefix(entry.prefix(), 1, false, entry.rainbow()))
                    .append(Component.text("  原文：" + entry.prefix() + (entry.rainbow() ? "（彩虹）" : ""),
                            NamedTextColor.DARK_GRAY)));
        }
        return true;
    }

    /**
     * /cp pack —— 把「你这个客户端算不算装了资源包」的每一步都摊开，
     * 用来排查「装了包但前缀没变成原地彩虹」。
     */
    private boolean packDiagnostics(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            reply(sender, Component.text("这条要看执行者自己的客户端状态，请在游戏里执行。", NamedTextColor.RED));
            return true;
        }
        ChatSettings current = plugin.settings();
        reply(sender, Component.text("资源包判定诊断：", NamedTextColor.GRAY));

        // 26.3 起 getDescription() 被标为过时，Paper 的替代是 getPluginMeta()
        line(sender, "插件版本", plugin.getPluginMeta().getVersion());
        line(sender, "pack.enabled", String.valueOf(current.packEnabled()));
        line(sender, "pack.detect", current.packDetect());
        line(sender, "pack.magic-color", current.packMagicColor());

        PlayerResourcePackStatusEvent.Status tracked = plugin.trackedPackStatus(player.getUniqueId());
        line(sender, "最近一次状态事件", tracked == null ? "（从未上报，客户端手动装包时这是正常的）" : tracked.name());
        line(sender, "Player#hasResourcePack()", String.valueOf(player.hasResourcePack()));
        PlayerResourcePackStatusEvent.Status api = player.getResourcePackStatus();
        line(sender, "Player#getResourcePackStatus()", api == null ? "null" : api.name());

        boolean viewer = plugin.packViewer(player);
        line(sender, "最终判定", viewer
                ? "算作装了包 → 前缀会刷成魔法色交给着色器"
                : "算作没装包 → 走逐消息相位彩虹（想强制走资源包就把 pack.detect 改成 always）");

        String prefix = plugin.store().prefixOf(player.getUniqueId());
        PrefixStore.Entry own = plugin.store().get(player.getUniqueId());
        line(sender, "你当前的前缀原文", prefix == null ? "（没设置）" : prefix);
        line(sender, "彩虹开关", own == null ? "（没设置前缀）" : (own.rainbow() ? "开" : "关"));
        line(sender, "该前缀是否写了裸 <rainbow>", String.valueOf(Text.wantsRainbow(prefix)));
        line(sender, "该前缀会不会走魔法色", String.valueOf(viewer && own != null
                && (own.rainbow() || Text.wantsRainbow(prefix))));
        return true;
    }

    private static void line(CommandSender sender, String key, String value) {
        sender.sendMessage(Component.text("  " + key + "：", NamedTextColor.DARK_GRAY)
                .append(Component.text(value, NamedTextColor.WHITE)));
    }

    private boolean reload(CommandSender sender) {
        plugin.reloadAll();
        reply(sender, Component.text("已重载 config.yml 与 prefixes.yml，当前 " + plugin.store().size() + " 条前缀。", NamedTextColor.GREEN));
        return true;
    }

    private void help(CommandSender sender, String label) {
        reply(sender, Component.text("用法（需要 op 权限）：", NamedTextColor.GRAY));
        sender.sendMessage(usage(label + " set <玩家> [--rainbow] <文字>", "设置前缀；加 --rainbow 前缀就是一直变色的彩虹"));
        sender.sendMessage(usage(label + " rainbow <玩家> on|off", "不改文字，只开关某位玩家前缀的彩虹"));
        sender.sendMessage(usage(label + " remove <玩家>", "取消前缀，之后聊天里不显示前缀段"));
        sender.sendMessage(usage(label + " get <玩家>", "查看某位玩家当前的前缀"));
        sender.sendMessage(usage(label + " list", "列出所有已设置的前缀"));
        sender.sendMessage(usage(label + " pack", "看你自己的资源包判定细节（彩虹没生效时先跑这个）"));
        sender.sendMessage(usage(label + " reload", "重载 config.yml 与 prefixes.yml"));
    }

    private static Component usage(String usage, String description) {
        return Component.text(" /" + usage + " ", NamedTextColor.YELLOW)
                .append(Component.text("—— " + description, NamedTextColor.GRAY));
    }

    private boolean save(CommandSender sender) {
        try {
            plugin.store().save();
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("写入 prefixes.yml 失败：" + ex.getMessage());
            reply(sender, Component.text("内存里已生效，但写入 prefixes.yml 失败：" + ex.getMessage(), NamedTextColor.RED));
            return false;
        }
    }

    private void reply(CommandSender sender, Component message) {
        sender.sendMessage(Component.text(PREFIX, NamedTextColor.GOLD).append(message));
    }

    /** 已经解析到的目标玩家。 */
    private record Target(UUID uuid, String name) {
    }

    /**
     * 先看在线玩家，再看服务器缓存的离线玩家。
     * 不用 Bukkit.getOfflinePlayer(String)：它在正版模式下会在主线程发起网络查询。
     */
    private static Target resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return new Target(online.getUniqueId(), online.getName());
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            String cachedName = cached.getName();
            return new Target(cached.getUniqueId(), cachedName == null ? name : cachedName);
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && isPlayerArgument(args[0])) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            for (PrefixStore.Entry entry : plugin.store().all()) {
                if (entry.name() != null && !names.contains(entry.name())) {
                    names.add(entry.name());
                }
            }
            return filter(names, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("rainbow")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            for (PrefixStore.Entry entry : plugin.store().all()) {
                if (entry.name() != null && !names.contains(entry.name())) {
                    names.add(entry.name());
                }
            }
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("rainbow")) {
            return filter(List.of("on", "off"), args[2]);
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("set")) {
            return filter(List.of("--rainbow"), args[args.length - 1]);
        }
        return List.of();
    }

    private static boolean isPlayerArgument(String sub) {
        String value = sub.toLowerCase(Locale.ROOT);
        return value.equals("set") || value.equals("remove") || value.equals("del")
                || value.equals("delete") || value.equals("get");
    }

    private static List<String> filter(List<String> options, String prefix) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(needle)) {
                result.add(option);
            }
        }
        return result;
    }
}
