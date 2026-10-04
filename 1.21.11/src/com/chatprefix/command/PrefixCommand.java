package com.chatprefix.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.chatprefix.ChatPrefix;
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

/**
 * /chatprefix —— op 给玩家设置聊天前缀。
 *
 * <p>set / remove / get / list / reload / help。
 */
public final class PrefixCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "chatprefix.admin";
    private static final String PREFIX = "[聊天前缀] ";
    private static final int LIST_LIMIT = 100;

    private static final List<String> SUBCOMMANDS = List.of("set", "remove", "get", "list", "reload", "help");

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
            case "remove":
            case "del":
            case "delete":
                return remove(sender, label, args);
            case "get":
                return get(sender, label, args);
            case "list":
                return list(sender);
            case "reload":
                return reload(sender);
            case "help":
            default:
                help(sender, label);
                return true;
        }
    }

    private boolean set(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            reply(sender, Component.text("用法：/" + label + " set <玩家> <文字>", NamedTextColor.RED));
            return true;
        }
        Target target = resolve(args[1]);
        if (target == null) {
            reply(sender, Component.text("找不到玩家 " + args[1]
                    + "：他不在线，服务器也没有他的记录。让他先上线一次再设置。", NamedTextColor.RED));
            return true;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
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
        plugin.store().set(target.uuid(), target.name(), text);
        if (!save(sender)) {
            return true;
        }
        reply(sender, Component.text("已把 ", NamedTextColor.GRAY)
                .append(Component.text(target.name(), NamedTextColor.WHITE))
                .append(Component.text(" 的前缀设为 ", NamedTextColor.GRAY))
                .append(plugin.formatter().prefix(text))
                .append(Component.text("（原文：" + text + "）", NamedTextColor.DARK_GRAY)));
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
                .append(plugin.formatter().prefix(entry.prefix()))
                .append(Component.text("（原文：" + entry.prefix() + "）", NamedTextColor.DARK_GRAY)));
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
                    .append(plugin.formatter().prefix(entry.prefix()))
                    .append(Component.text("  原文：" + entry.prefix(), NamedTextColor.DARK_GRAY)));
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        plugin.reloadAll();
        reply(sender, Component.text("已重载 config.yml 与 prefixes.yml，当前 " + plugin.store().size() + " 条前缀。", NamedTextColor.GREEN));
        return true;
    }

    private void help(CommandSender sender, String label) {
        reply(sender, Component.text("用法（需要 op 权限）：", NamedTextColor.GRAY));
        sender.sendMessage(usage(label + " set <玩家> <文字>", "设置前缀，外层方括号由插件自动加上（写 本服 即显示 [本服]）"));
        sender.sendMessage(usage(label + " remove <玩家>", "取消前缀，之后聊天里不显示前缀段"));
        sender.sendMessage(usage(label + " get <玩家>", "查看某位玩家当前的前缀"));
        sender.sendMessage(usage(label + " list", "列出所有已设置的前缀"));
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
