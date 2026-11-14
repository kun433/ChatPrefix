package com.chatprefix.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.chatprefix.ChatPrefix;
import com.chatprefix.chat.ChatSettings;
import com.chatprefix.chat.PrefixStore;
import com.chatprefix.craftengine.CraftEngineBridge;
import com.chatprefix.title.TitleStore;
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

    private final ChatPrefix plugin;

    public PrefixCommand(ChatPrefix plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 权限分层：use/titles 是玩家自己切称号（默认人人可用），其余需要管理员权限
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        boolean selfService = sub.equals("use") || sub.equals("titles");
        if (!sender.hasPermission(selfService ? "chatprefix.use" : PERMISSION)) {
            reply(sender, Component.text("你没有权限使用这个指令。", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        switch (sub) {
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
            case "title":
                return titleAdmin(sender, label, args);
            case "use":
                return use(sender, label, args);
            case "titles":
                return titlesSelf(sender, label);
            case "preview":
                return preview(sender, label, args);
            case "pack":
                return packDiagnostics(sender);
            case "ce":
                return craftEngineStatus(sender);
            case "reload":
                return reload(sender);
            case "help":
            default:
                help(sender, label);
                return true;
        }
    }

    /** 目标玩家在线时重刷他的 Tab/头顶前缀；离线时静默跳过（上线时会自动刷）。 */
    private void refreshTab(UUID uuid) {
        if (uuid == null) {
            return;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            plugin.tabSync().update(online);
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
        refreshTab(target.uuid());
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
        refreshTab(entry.uuid());
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
        refreshTab(removed.uuid());
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
        ChatPrefix.ResolvedPrefix resolved = ChatPrefix.resolvePrefix(entry, plugin.titles());
        Component shown = resolved == null ? Component.empty()
                : plugin.formatter().prefix(resolved.text(), 1, false, resolved.rainbow());
        String note = entry.title() != null ? "；正在使用称号 " + entry.title() : "";
        reply(sender, Component.text(entry.name() + " 的前缀是 ", NamedTextColor.GRAY)
                .append(shown)
                .append(Component.text("（原文：" + entry.prefix() + "；彩虹："
                        + (resolved != null && resolved.rainbow() ? "开" : "关") + note + "）",
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
            Component prefixPreview = shownOf(entry);
            sender.sendMessage(Component.text(" - ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(entry.name(), NamedTextColor.WHITE))
                    .append(Component.space())
                    .append(prefixPreview)
                    .append(Component.text("  原文：" + entry.prefix()
                            + (entry.title() != null ? "（称号 " + entry.title() + "）" : ""),
                            NamedTextColor.DARK_GRAY)));
        }
        return true;
    }

    /** 按实际生效规则（称号优先）渲染一条前缀的预览，供指令回显。 */
    private Component shownOf(PrefixStore.Entry entry) {
        ChatPrefix.ResolvedPrefix resolved = ChatPrefix.resolvePrefix(entry, plugin.titles());
        return resolved == null ? Component.empty()
                : plugin.formatter().prefix(resolved.text(), 1, false, resolved.rainbow());
    }

    /**
     * /cp title —— 称号池管理（管理员）。
     * grant/revoke 授权与收回；set 强制指定某玩家当前使用的称号（可绕过授权，none 关闭）；
     * list 列出称号池定义；info 查看某玩家（或全部）的授权情况。
     */
    private boolean titleAdmin(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            reply(sender, Component.text("用法：/" + label + " title grant|revoke|set|list|info …"
                    + "（详见 /" + label + " help）", NamedTextColor.RED));
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list": {
                List<TitleStore.Title> titles = plugin.titles().allTitles();
                if (titles.isEmpty()) {
                    reply(sender, Component.text("称号池是空的：在 plugins/ChatPrefix/titles.yml 里定义（改完 /cp reload）。",
                            NamedTextColor.YELLOW));
                    return true;
                }
                reply(sender, Component.text("共 " + titles.size() + " 个称号：", NamedTextColor.GRAY));
                for (TitleStore.Title title : titles) {
                    sender.sendMessage(Component.text(" - ", NamedTextColor.DARK_GRAY)
                            .append(Component.text(title.id(), NamedTextColor.WHITE))
                            .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                            .append(plugin.formatter().prefix(title.text(), 1, false, title.rainbow()))
                            .append(Component.text(title.rainbow() ? "  （彩虹）" : "", NamedTextColor.DARK_GRAY)));
                }
                return true;
            }
            case "grant": {
                if (args.length < 4) {
                    reply(sender, Component.text("用法：/" + label + " title grant <玩家> <称号>", NamedTextColor.RED));
                    return true;
                }
                Target target = resolve(args[2]);
                if (target == null) {
                    reply(sender, Component.text("找不到玩家 " + args[2]
                            + "：他不在线，服务器也没有他的记录。", NamedTextColor.RED));
                    return true;
                }
                if (plugin.titles().grant(target.uuid(), target.name(), args[3])) {
                    if (!save(sender)) {
                        return true;
                    }
                    reply(sender, Component.text("已把称号 ", NamedTextColor.GRAY)
                            .append(plugin.formatter().prefix(plugin.titles().title(args[3]).text(), 1, false,
                                    plugin.titles().title(args[3]).rainbow()))
                            .append(Component.text(" 授权给 " + target.name()
                                    + "。他自己用 /" + label + " use " + args[3].toLowerCase()
                                    + " 就能戴上。", NamedTextColor.GRAY)));
                } else {
                    reply(sender, Component.text("没有这个称号（先在 titles.yml 里定义）或 " + target.name()
                            + " 已经有它的授权了。", NamedTextColor.YELLOW));
                }
                return true;
            }
            case "revoke": {
                if (args.length < 4) {
                    reply(sender, Component.text("用法：/" + label + " title revoke <玩家> <称号>", NamedTextColor.RED));
                    return true;
                }
                Target target = resolve(args[2]);
                if (target == null) {
                    reply(sender, Component.text("找不到玩家 " + args[2] + "。", NamedTextColor.RED));
                    return true;
                }
                String id = args[3].toLowerCase(Locale.ROOT);
                if (!plugin.titles().revoke(target.uuid(), id)) {
                    reply(sender, Component.text(target.name() + " 本来就没有这个称号的授权。", NamedTextColor.YELLOW));
                    return true;
                }
                PrefixStore.Entry entry = plugin.store().get(target.uuid());
                if (entry != null && id.equalsIgnoreCase(entry.title())) {
                    // 正在戴的称号被收回：摘下来，回到手动前缀
                    plugin.store().setTitle(target.uuid(), target.name(), null);
                }
                if (!save(sender)) {
                    return true;
                }
                refreshTab(target.uuid());
                reply(sender, Component.text("已收回 " + target.name() + " 的称号 " + id + "。", NamedTextColor.GRAY));
                return true;
            }
            case "set": {
                if (args.length < 4) {
                    reply(sender, Component.text("用法：/" + label + " title set <玩家> <称号|none>", NamedTextColor.RED));
                    return true;
                }
                Target target = resolve(args[2]);
                if (target == null) {
                    reply(sender, Component.text("找不到玩家 " + args[2] + "。", NamedTextColor.RED));
                    return true;
                }
                String id = args[3];
                TitleStore.Title title = id.equalsIgnoreCase("none") ? null : plugin.titles().title(id);
                if (id.equalsIgnoreCase("none")) {
                    plugin.store().setTitle(target.uuid(), target.name(), null);
                } else {
                    if (title == null) {
                        reply(sender, Component.text("没有这个称号：先在 titles.yml 里定义（改完 /cp reload）。",
                                NamedTextColor.RED));
                        return true;
                    }
                    plugin.store().setTitle(target.uuid(), target.name(), title.id());
                }
                if (!save(sender)) {
                    return true;
                }
                refreshTab(target.uuid());
                PrefixStore.Entry updated = plugin.store().get(target.uuid());
                reply(sender, Component.text("已把 ", NamedTextColor.GRAY)
                        .append(Component.text(target.name(), NamedTextColor.WHITE))
                        .append(Component.text(id.equalsIgnoreCase("none")
                                ? " 的称号摘下（回到手动前缀）" : " 的当前称号设为：", NamedTextColor.GRAY))
                        .append(updated == null ? Component.empty() : shownOf(updated)));
                return true;
            }
            case "info": {
                if (args.length < 3) {
                    List<Map.Entry<UUID, TitleStore.Grant>> grants = plugin.titles().allGrants();
                    if (grants.isEmpty()) {
                        reply(sender, Component.text("还没有任何人被授权称号。", NamedTextColor.YELLOW));
                        return true;
                    }
                    reply(sender, Component.text("共 " + grants.size() + " 个玩家有称号授权：", NamedTextColor.GRAY));
                    for (Map.Entry<UUID, TitleStore.Grant> record : grants) {
                        PrefixStore.Entry entry = plugin.store().get(record.getKey());
                        String current = entry == null ? null : entry.title();
                        sender.sendMessage(Component.text(" - ", NamedTextColor.DARK_GRAY)
                                .append(Component.text(record.getValue().name(), NamedTextColor.WHITE))
                                .append(Component.text("：" + record.getValue().titles()
                                        + (current == null ? "" : "（当前：" + current + "）"),
                                        NamedTextColor.GRAY)));
                    }
                    return true;
                }
                Target target = resolve(args[2]);
                if (target == null) {
                    reply(sender, Component.text("找不到玩家 " + args[2] + "。", NamedTextColor.RED));
                    return true;
                }
                List<TitleStore.Title> owned = plugin.titles().titlesOf(target.uuid());
                PrefixStore.Entry entry = plugin.store().get(target.uuid());
                String current = entry == null ? null : entry.title();
                reply(sender, Component.text(target.name() + " 的称号："
                        + (owned.isEmpty() ? "（无授权）" : "")
                        + owned.stream().map(t -> t.id() + (t.id().equalsIgnoreCase(current) ? "（当前）" : ""))
                                .reduce((a, b) -> a + ", " + b).orElse("")
                        + "；手动前缀：" + (entry == null || entry.prefix().isBlank() ? "（无）" : entry.prefix()),
                        NamedTextColor.GRAY));
                return true;
            }
            default:
                reply(sender, Component.text("未知的 title 子命令，可选 grant / revoke / set / list / info。",
                        NamedTextColor.RED));
                return true;
        }
    }

    /**
     * /cp use &lt;称号|none&gt; —— 玩家在授权范围内自己切换称号（需要 chatprefix.use）。
     * 手动前缀不受影响，用 none 可随时摘下称号回到手动前缀。
     */
    private boolean use(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            reply(sender, Component.text("这条指令要玩家自己在游戏里执行（管理玩家请用 /"
                    + label + " title set）。", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            reply(sender, Component.text("用法：/" + label + " use <称号|none>", NamedTextColor.RED));
            return true;
        }
        String id = args[1];
        UUID uuid = player.getUniqueId();
        if (id.equalsIgnoreCase("none") || id.equals("无")) {
            plugin.store().setTitle(uuid, player.getName(), null);
            if (!save(sender)) {
                return true;
            }
            refreshTab(uuid);
            PrefixStore.Entry entry = plugin.store().get(uuid);
            reply(sender, Component.text("已摘下称号，回到手动前缀：", NamedTextColor.GRAY)
                    .append(entry != null && !entry.prefix().isBlank()
                            ? shownOf(entry)
                            : Component.text("（你没有手动前缀，现在不显示前缀段）", NamedTextColor.GRAY)));
            return true;
        }
        TitleStore.Title title = plugin.titles().title(id);
        if (title == null) {
            reply(sender, Component.text("没有这个称号。用 /" + label + " titles 看看你有哪些。",
                    NamedTextColor.RED));
            return true;
        }
        if (!plugin.titles().hasGrant(uuid, title.id())) {
            reply(sender, Component.text("你还没有被授权这个称号，找管理要吧。", NamedTextColor.RED));
            return true;
        }
        plugin.store().setTitle(uuid, player.getName(), title.id());
        if (!save(sender)) {
            return true;
        }
        refreshTab(uuid);
        reply(sender, Component.text("已戴上称号 ", NamedTextColor.GRAY)
                .append(plugin.formatter().prefix(title.text(), 1, false, title.rainbow()))
                .append(Component.text("。用 /" + label + " use none 可随时摘下。", NamedTextColor.GRAY)));
        return true;
    }

    /** /cp titles —— 看自己被授权了哪些称号、现在戴的是哪个。 */
    private boolean titlesSelf(CommandSender sender, String label) {
        if (!(sender instanceof Player player)) {
            reply(sender, Component.text("这条指令要玩家自己在游戏里执行。", NamedTextColor.RED));
            return true;
        }
        List<TitleStore.Title> owned = plugin.titles().titlesOf(player.getUniqueId());
        PrefixStore.Entry entry = plugin.store().get(player.getUniqueId());
        String current = entry == null ? null : entry.title();
        if (owned.isEmpty()) {
            reply(sender, Component.text("你还没有被授权任何称号。", NamedTextColor.YELLOW));
            return true;
        }
        reply(sender, Component.text("你的称号（用 /" + label + " use <id> 戴上，none 摘下）：", NamedTextColor.GRAY));
        for (TitleStore.Title title : owned) {
            boolean wearing = title.id().equalsIgnoreCase(current);
            sender.sendMessage(Component.text(" - ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(title.id(), wearing ? NamedTextColor.YELLOW : NamedTextColor.WHITE))
                    .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                    .append(plugin.formatter().prefix(title.text(), 1, false, title.rainbow()))
                    .append(Component.text(wearing ? "  ← 正在戴" : "", NamedTextColor.GOLD)));
        }
        return true;
    }

    /**
     * /cp preview &lt;文字&gt; [--rainbow] —— 不落库，只看一段前缀文字渲染出来什么效果。
     * 写 titles.yml 或想试试写法时用，比真的 set 到自己头上省事。
     */
    private boolean preview(CommandSender sender, String label, String[] args) {
        List<String> tokens = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
        boolean rainbow = false;
        while (!tokens.isEmpty() && isRainbowFlag(tokens.get(tokens.size() - 1))) {
            rainbow = true;
            tokens.remove(tokens.size() - 1);
        }
        String text = String.join(" ", tokens).trim();
        if (text.isEmpty()) {
            reply(sender, Component.text("用法：/" + label + " preview <文字> [--rainbow]", NamedTextColor.RED));
            return true;
        }
        reply(sender, Component.text("渲染效果：", NamedTextColor.GRAY)
                .append(plugin.formatter().prefix(text, 1, false, rainbow))
                .append(Component.text("（原文：" + text + "）", NamedTextColor.DARK_GRAY)));
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
        if (ChatPrefix.ceActive(current)) {
            line(sender, "CraftEngine 集成", "开启（shader-merged=" + current.ceShaderMerged()
                    + (current.ceShaderMerged() ? "" : " → 跳过魔法色分支，用逐消息相位彩虹") + "）");
        }

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

    /**
     * /cp ce —— 查看 CraftEngine 集成状态，排查「前缀里的图片图标没显示」。
     */
    private boolean craftEngineStatus(CommandSender sender) {
        ChatSettings current = plugin.settings();
        reply(sender, Component.text("CraftEngine 集成状态：", NamedTextColor.GRAY));
        boolean present = CraftEngineBridge.pluginPresent();
        line(sender, "CraftEngine 插件", present ? "已安装且在线" : "未安装或未启用");
        line(sender, "craftengine.mode", current.ceMode());
        line(sender, "集成生效", ChatPrefix.ceActive(current) ? "是 → <image:...> 标签会调用 CE API 渲染成图片字符" : "否 → 标签按 missing 配置处理");
        line(sender, "craftengine.missing", current.ceMissing() + ("literal".equalsIgnoreCase(current.ceMissing())
                ? "（渲染不了时保留原文本，CE 的网络层拦截或许还能替换）"
                : "（渲染不了时剔除标签）"));
        line(sender, "craftengine.shader-merged", String.valueOf(current.ceShaderMerged()));
        line(sender, "反射通道", CraftEngineBridge.reflectionReady() ? "就绪" : (present ? "未就绪（CE 版本 API 有变化？）" : "未就绪（CE 未安装，正常）"));
        sender.sendMessage(Component.text("  示例：", NamedTextColor.DARK_GRAY)
                .append(Component.text("/cp set 玩家 <image:default:icons:0:0>&c管理员", NamedTextColor.WHITE))
                .append(Component.text("  —— id 与 CE 图片配置的 命名空间:id:行:列 一致", NamedTextColor.GRAY)));
        return true;
    }

    private void help(CommandSender sender, String label) {
        boolean self = sender.hasPermission("chatprefix.use");
        boolean admin = sender.hasPermission(PERMISSION);
        if (self) {
            reply(sender, Component.text("称号用法（所有玩家可用）：", NamedTextColor.GRAY));
            sender.sendMessage(usage(label + " titles", "看看你被授权了哪些称号"));
            sender.sendMessage(usage(label + " use <称号|none>", "戴上 / 摘下称号；手动前缀不受影响"));
        }
        if (!admin) {
            return;
        }
        reply(sender, Component.text("管理用法（需要 op 权限）：", NamedTextColor.GRAY));
        sender.sendMessage(usage(label + " set <玩家> [--rainbow] <文字>", "设置前缀；加 --rainbow 前缀就是一直变色的彩虹"));
        sender.sendMessage(usage(label + " rainbow <玩家> on|off", "不改文字，只开关某位玩家前缀的彩虹"));
        sender.sendMessage(usage(label + " remove <玩家>", "取消前缀，之后聊天里不显示前缀段"));
        sender.sendMessage(usage(label + " get <玩家>", "查看某位玩家当前的前缀与称号"));
        sender.sendMessage(usage(label + " list", "列出所有已设置的前缀"));
        sender.sendMessage(usage(label + " title list", "列出称号池定义（titles.yml，改完 /cp reload）"));
        sender.sendMessage(usage(label + " title grant <玩家> <称号>", "把称号授权给玩家"));
        sender.sendMessage(usage(label + " title revoke <玩家> <称号>", "收回称号授权（正在戴的会被摘下）"));
        sender.sendMessage(usage(label + " title set <玩家> <称号|none>", "直接指定玩家当前戴的称号（可绕过授权）"));
        sender.sendMessage(usage(label + " title info [玩家]", "查看授权情况"));
        sender.sendMessage(usage(label + " use <称号|none>", "管理员也能替自己切换"));
        sender.sendMessage(usage(label + " preview <文字> [--rainbow]", "预览一段前缀文字的渲染效果，不落库"));
        sender.sendMessage(usage(label + " pack", "看你自己的资源包判定细节（彩虹没生效时先跑这个）"));
        sender.sendMessage(usage(label + " ce", "看 CraftEngine 集成状态（前缀里的图片图标没显示时跑这个）"));
        sender.sendMessage(usage(label + " reload", "重载 config.yml、prefixes.yml 与 titles.yml"));
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
     * 先看在线玩家，再看服务器缓存的离线玩家，最后看称号授权记录（被授过称号但从未设置
     * 前缀的玩家不在 PrefixStore 里）。不用 Bukkit.getOfflinePlayer(String)：
     * 它在正版模式下会在主线程发起网络查询。
     */
    private Target resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return new Target(online.getUniqueId(), online.getName());
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            String cachedName = cached.getName();
            return new Target(cached.getUniqueId(), cachedName == null ? name : cachedName);
        }
        UUID fromGrant = plugin.titles().findGrantUuidByName(name);
        if (fromGrant != null) {
            return new Target(fromGrant, name);
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("chatprefix.use")) {
                options.add("titles");
                options.add("use");
            }
            if (sender.hasPermission(PERMISSION)) {
                options.add("set");
                options.add("rainbow");
                options.add("remove");
                options.add("get");
                options.add("list");
                options.add("title");
                options.add("preview");
                options.add("pack");
                options.add("ce");
                options.add("reload");
                options.add("help");
            }
            return filter(options, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        // 玩家自己切称号时，补全自己被授权的称号 id
        if (args.length == 2 && (sub.equals("use") || sub.equals("rainbow"))
                && !(sub.equals("rainbow") && sender.hasPermission(PERMISSION))) {
            if (sender instanceof Player player) {
                List<String> ids = new ArrayList<>();
                for (TitleStore.Title title : plugin.titles().titlesOf(player.getUniqueId())) {
                    ids.add(title.id());
                }
                ids.add("none");
                return filter(ids, args[1]);
            }
            return List.of();
        }
        if (args.length == 2 && isPlayerArgument(sub)) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            for (PrefixStore.Entry entry : plugin.store().all()) {
                if (entry.name() != null && !names.contains(entry.name())) {
                    names.add(entry.name());
                }
            }
            for (TitleStore.Grant grant : plugin.titles().grantsByName()) {
                if (grant.name() != null && !names.contains(grant.name())) {
                    names.add(grant.name());
                }
            }
            return filter(names, args[1]);
        }
        if (args.length == 2 && sub.equals("title")) {
            return filter(List.of("grant", "revoke", "set", "list", "info"), args[1]);
        }
        if (args.length == 3 && sub.equals("title") && !args[1].equalsIgnoreCase("list")) {
            return completePlayerNames(args[2]);
        }
        if (args.length == 4 && sub.equals("title")
                && (args[1].equalsIgnoreCase("grant") || args[1].equalsIgnoreCase("set"))) {
            List<String> ids = new ArrayList<>();
            for (TitleStore.Title title : plugin.titles().allTitles()) {
                ids.add(title.id());
            }
            if (args[1].equalsIgnoreCase("set")) {
                ids.add("none");
            }
            return filter(ids, args[3]);
        }
        if (args.length == 3 && sub.equals("rainbow")) {
            return filter(List.of("on", "off"), args[2]);
        }
        if (args.length >= 3 && sub.equals("set")) {
            return filter(List.of("--rainbow"), args[args.length - 1]);
        }
        return List.of();
    }

    private static List<String> completePlayerNames(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return filter(names, prefix);
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
