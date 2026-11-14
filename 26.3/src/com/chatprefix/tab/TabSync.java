package com.chatprefix.tab;

import java.util.UUID;

import com.chatprefix.ChatPrefix;
import com.chatprefix.chat.ChatSettings;
import com.chatprefix.chat.PrefixStore;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * 把前缀同步到 Tab 列表与头顶名牌：用主计分板上的队伍（Team prefix）实现。
 *
 * <p>做法与大多数前缀插件一致——为每个「有可显示前缀」的玩家维护一个专属队伍，
 * 前缀段渲染成组件写进 {@code Team#prefix(Component)}；没有前缀的玩家从队伍里摘除，
 * 空队伍注销。队伍名固定为 {@code cp + UUID 前 14 个十六进制位}（16 字符，兼容旧客户端）。
 *
 * <p>注意：主计分板队伍是全局唯一的，如果还装了 LuckPerms / TAB 这类同样接管队伍前缀的
 * 插件，两边会互相覆盖——用哪个就关掉另一个（本功能可用 {@code tab.enabled: false} 关闭）。
 * 已在别的插件队伍里的玩家不会被抢走，本同步对它保持沉默。
 */
public final class TabSync implements Listener {

    private static final String TEAM_PREFIX = "cp";

    private final ChatPrefix plugin;

    public TabSync(ChatPrefix plugin) {
        this.plugin = plugin;
    }

    /** 按当前配置与存储刷新一个玩家的 Tab/头顶前缀；任何时候都可以安全调用。 */
    public void update(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        ChatSettings settings = plugin.settings();
        if (settings == null || !settings.tabEnabled()) {
            removePlayer(player.getUniqueId(), player.getName());
            return;
        }
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = teamName(player.getUniqueId());
        Team team = scoreboard.getTeam(teamName);
        ChatPrefix.ResolvedPrefix resolved = plugin.resolvePrefix(
                plugin.store().get(player.getUniqueId()), plugin.titles());

        Component prefix;
        if (resolved == null) {
            // 没有前缀：开维度同步时至少显示维度段，否则完全退出队伍
            if (!settings.tabShowDimension()) {
                removeTeam(team, player.getName());
                return;
            }
            prefix = dimension(player);
            if (prefix == Component.empty()) {
                removeTeam(team, player.getName());
                return;
            }
        } else {
            prefix = plugin.formatter().prefix(resolved.text(), 0, false, resolved.rainbow());
            if (settings.tabShowDimension()) {
                Component dimension = dimension(player);
                if (prefix == Component.empty()) {
                    prefix = dimension;
                } else {
                    prefix = prefix.append(dimension);
                }
            }
            if (prefix == Component.empty()) {
                removeTeam(team, player.getName());
                return;
            }
        }
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }
        team.prefix(prefix);
        joinTeam(team, player.getName());
    }

    /** 全部在线玩家重刷；启动与 /cp reload 后调用。 */
    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    /** 关闭/重载时清掉本插件注册的所有队伍，不碰别的插件的。 */
    public void cleanup() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : scoreboard.getTeams()) {
            if (isOurs(team.getName())) {
                team.unregister();
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        update(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (plugin.settings() != null && plugin.settings().tabShowDimension()) {
            update(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removePlayer(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    private Component dimension(Player player) {
        return plugin.formatter().dimension(
                player.getWorld().getEnvironment(), player.getWorld().getName());
    }

    private void joinTeam(Team team, String name) {
        if (team.hasEntry(name)) {
            return;
        }
        Team current = team.getScoreboard().getEntryTeam(name);
        if (current != null && !isOurs(current.getName())) {
            // 别的插件（如 LuckPerms）在管这个玩家的队伍，不抢
            return;
        }
        team.addEntry(name);
    }

    private void removeTeam(Team team, String name) {
        if (team != null) {
            team.removeEntry(name);
            if (team.getEntries().isEmpty()) {
                team.unregister();
            }
        }
    }

    private void removePlayer(UUID uuid, String name) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        removeTeam(scoreboard.getTeam(teamName(uuid)), name);
    }

    private static String teamName(UUID uuid) {
        return TEAM_PREFIX + uuid.toString().replace("-", "").substring(0, 14);
    }

    private static boolean isOurs(String teamName) {
        return teamName.length() == TEAM_PREFIX.length() + 14 && teamName.startsWith(TEAM_PREFIX);
    }
}
