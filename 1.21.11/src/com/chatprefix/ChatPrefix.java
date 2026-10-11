package com.chatprefix;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.chatprefix.chat.ChatFormatter;
import com.chatprefix.chat.ChatSettings;
import com.chatprefix.chat.PrefixStore;
import com.chatprefix.command.PrefixCommand;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ChatPrefix：在聊天栏里把 op 分配的前缀与玩家所在维度拼到名字前面。
 *
 * <p>默认显示成 {@code [本服][末地]aaa_ovo » 第二个末地城}；
 * 没被设置前缀的玩家不显示前缀段，维度段所有玩家都有。
 */
public final class ChatPrefix extends JavaPlugin implements Listener {

    /** 聊天事件在异步线程读这些字段，指令在主线程替换它们，因此用 volatile。 */
    private volatile PrefixStore store;
    private volatile ChatFormatter formatter;
    private volatile ChatSettings settings;

    /**
     * 玩家最近一次上报的资源包状态。客户端把包手动放进 resourcepacks 文件夹时服务端是看不到的，
     * 但只要服务器下发过包，这个事件就会带着真实结果回来，比 Player#hasResourcePack() 可靠。
     */
    private final Map<UUID, PlayerResourcePackStatusEvent.Status> packStatus = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new PrefixStore(new File(getDataFolder(), "prefixes.yml"));
        reloadAll();

        getServer().getPluginManager().registerEvents(this, this);

        PluginCommand command = getCommand("chatprefix");
        if (command == null) {
            getLogger().severe("plugin.yml 里没有注册 chatprefix 指令，插件无法正常工作。");
        } else {
            PrefixCommand executor = new PrefixCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("已启用 ChatPrefix " + getDescription().getVersion()
                + "：前缀 " + store.size() + " 条，模板 " + settings.format()
                + "；资源包分支 " + (settings.packEnabled()
                        ? "开（检测 " + settings.packDetect() + "，魔法色 " + settings.packMagicColor() + "）"
                        : "关"));
    }

    @Override
    public void onDisable() {
        PrefixStore current = store;
        if (current == null) {
            return;
        }
        try {
            current.save();
        } catch (IOException ex) {
            getLogger().warning("关闭时写入 prefixes.yml 失败：" + ex.getMessage());
        }
    }

    /** 重读 config.yml 与 prefixes.yml，供 /chatprefix reload 使用。 */
    public void reloadAll() {
        reloadConfig();
        ChatSettings loaded = ChatSettings.load(getConfig());
        this.settings = loaded;
        this.formatter = new ChatFormatter(loaded);
        this.store.load();
    }

    public PrefixStore store() {
        return store;
    }

    public ChatFormatter formatter() {
        return formatter;
    }

    public ChatSettings settings() {
        return settings;
    }

    /**
     * 接管聊天行的渲染：只替换「怎么显示」，不动玩家消息本身，
     * 因此 1.19+ 的聊天签名照旧有效，玩家消息里的内容不会被重新解析。
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        // 在消息发出的这一刻定型：之后玩家传送、前缀被改，都不会影响已经发出的这条消息
        final World.Environment environment = world.getEnvironment();
        final String worldName = world.getName();
        final String prefixRaw = store.prefixOf(player.getUniqueId());
        final ChatFormatter current = formatter;
        final boolean useDisplayName = settings.nameUseDisplayName();
        // 相位在消息发出的这一刻定型，同一条消息对所有接收者一致
        final int rainbowPhase = rainbowPhase();

        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component name = useDisplayName && sourceDisplayName != null
                    ? sourceDisplayName
                    : current.name(source.getName());
            // 装没装资源包是按接收者算的：装了的人看到着色器做的原地彩虹，没装的回落逐消息相位彩虹
            return current.render(prefixRaw, environment, worldName, name, message,
                    rainbowPhase, packViewer(viewer));
        });
    }

    /**
     * 这个接收者是否应按「装了资源包」渲染。
     *
     * <p>detect 取 auto 时用事件上报结果与 Bukkit/Paper 的 API 综合判断；取 always 时当所有人都装了
     * （服务器强制下发资源包、或客户端手动装包服务端看不到时可以用它）；取 never 时一律当作没装。
     */
    public boolean packViewer(Audience viewer) {
        ChatSettings current = settings;
        if (current == null || !current.packEnabled()) {
            return false;
        }
        String policy = current.packDetect();
        if ("never".equalsIgnoreCase(policy)) {
            return false;
        }
        if ("always".equalsIgnoreCase(policy)) {
            return true;
        }
        if (!(viewer instanceof Player player)) {
            return false;
        }
        return packViewer(player);
    }

    /** 同一套判定的玩家版本，/cp pack 复用。 */
    public boolean packViewer(Player player) {
        PlayerResourcePackStatusEvent.Status tracked = packStatus.get(player.getUniqueId());
        if (tracked != null) {
            // 事件是最新的真实结果，优先信它；还在下载中的状态则继续问 API
            if (tracked == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
                return true;
            }
            if (tracked != PlayerResourcePackStatusEvent.Status.ACCEPTED
                    && tracked != PlayerResourcePackStatusEvent.Status.DOWNLOADED) {
                return false;
            }
        }
        return player.hasResourcePack()
                || player.getResourcePackStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED;
    }

    /** 最近一次上报的资源包状态；没上报过返回 null。 */
    public PlayerResourcePackStatusEvent.Status trackedPackStatus(UUID playerId) {
        return playerId == null ? null : packStatus.get(playerId);
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        if (status == null) {
            packStatus.remove(event.getPlayer().getUniqueId());
        } else {
            packStatus.put(event.getPlayer().getUniqueId(), status);
        }
    }

    /** 退出时清掉记录，避免记录跟着 UUID 一直留着。 */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        packStatus.remove(event.getPlayer().getUniqueId());
    }

    /**
     * 当前时间对应的彩虹相位（0-9）。关掉动画时恒为 0；
     * 10 格为一整圈，所以每过 10/speed 秒循环一次。
     */
    private int rainbowPhase() {
        ChatSettings current = settings;
        if (current == null || !current.prefixRainbowAnimate()) {
            return 0;
        }
        double steps = System.currentTimeMillis() / 1000.0 * current.prefixRainbowSpeed();
        return (int) Math.floorMod((long) Math.floor(steps), 10L);
    }
}
