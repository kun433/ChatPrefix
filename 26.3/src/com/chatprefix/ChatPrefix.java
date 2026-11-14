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
import com.chatprefix.craftengine.CraftEngineBridge;
import com.chatprefix.tab.TabSync;
import com.chatprefix.title.TitleStore;

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
    private volatile TitleStore titleStore;
    private volatile TabSync tabSync;

    /** 渲染时实际使用的前缀：文本 + 是否按彩虹渲染。 */
    public record ResolvedPrefix(String text, boolean rainbow) {
    }

    /**
     * 解析「这个玩家此刻应该显示什么前缀」：正在使用的称号优先（定义被删时回落），
     * 否则用手动前缀；两者都没有返回 null（不显示前缀段）。
     */
    public static ResolvedPrefix resolvePrefix(PrefixStore.Entry entry, TitleStore titles) {
        if (entry == null) {
            return null;
        }
        if (entry.title() != null && titles != null) {
            TitleStore.Title title = titles.title(entry.title());
            if (title != null) {
                return new ResolvedPrefix(title.text(), title.rainbow() || entry.rainbow());
            }
        }
        if (entry.prefix() == null || entry.prefix().isBlank()) {
            return null;
        }
        return new ResolvedPrefix(entry.prefix(), entry.rainbow());
    }

    /**
     * 玩家最近一次上报的资源包状态。客户端把包手动放进 resourcepacks 文件夹时服务端是看不到的，
     * 但只要服务器下发过包，这个事件就会带着真实结果回来，比 Player#hasResourcePack() 可靠。
     */
    private final Map<UUID, PlayerResourcePackStatusEvent.Status> packStatus = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new PrefixStore(new File(getDataFolder(), "prefixes.yml"));
        titleStore = new TitleStore(new File(getDataFolder(), "titles.yml"));
        reloadAll();

        getServer().getPluginManager().registerEvents(this, this);

        // Tab/头顶前缀同步：注册一次监听，开关变化在 update() 里逐次检查
        tabSync = new TabSync(this);
        getServer().getPluginManager().registerEvents(tabSync, this);
        tabSync.refreshAll();

        PluginCommand command = getCommand("chatprefix");
        if (command == null) {
            getLogger().severe("plugin.yml 里没有注册 chatprefix 指令，插件无法正常工作。");
        } else {
            PrefixCommand executor = new PrefixCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        // 26.3 起 getDescription() 被标为过时，Paper 的替代是 getPluginMeta()
        boolean ce = CraftEngineBridge.pluginPresent();
        getLogger().info("已启用 ChatPrefix " + getPluginMeta().getVersion()
                + "：前缀 " + store.size() + " 条，称号 " + titleStore.titleCount() + " 个，模板 " + settings.format()
                + "；Tab 同步 " + (settings.tabEnabled() ? "开" : "关")
                + "；资源包分支 " + (settings.packEnabled()
                        ? "开（检测 " + settings.packDetect() + "，魔法色 " + settings.packMagicColor() + "）"
                        : "关")
                + "；CraftEngine " + (ce ? "已检测到" + (ceActive(settings) ? "（集成已启用）" : "（集成按配置关闭）")
                        : "未安装"));
    }

    @Override
    public void onDisable() {
        PrefixStore current = store;
        if (current != null) {
            try {
                current.save();
            } catch (IOException ex) {
                getLogger().warning("关闭时写入 prefixes.yml 失败：" + ex.getMessage());
            }
        }
        TabSync sync = tabSync;
        if (sync != null) {
            sync.cleanup();
        }
    }

    public TitleStore titles() {
        return titleStore;
    }

    public TabSync tabSync() {
        return tabSync;
    }

    /** 重读 config.yml 与 prefixes.yml，供 /chatprefix reload 使用。 */
    public void reloadAll() {
        reloadConfig();
        ChatSettings loaded = ChatSettings.load(getConfig());
        this.settings = loaded;
        // CE 集成随配置定型：mode/missing/shader-merged 改了之后 reload 即可生效
        this.formatter = new ChatFormatter(loaded, CraftEngineBridge.imageTagResolver(
                ceActive(loaded), "literal".equalsIgnoreCase(loaded.ceMissing())));
        this.store.load();
        if (titleStore != null) {
            titleStore.load();
        }
        if (tabSync != null) {
            tabSync.refreshAll();
        }
    }

    /**
     * CraftEngine 集成当前是否生效。
     *
     * <p>{@code craftengine.mode}: on 恒为 true；off 恒为 false；auto 看插件在不在——
     * plugin.yml 里已经 softdepend CraftEngine，走到这里 CE 要么先启动了，要么压根没装。
     */
    public static boolean ceActive(ChatSettings s) {
        if (s == null) {
            return false;
        }
        String mode = s.ceMode();
        if ("on".equalsIgnoreCase(mode)) {
            return true;
        }
        if ("off".equalsIgnoreCase(mode)) {
            return false;
        }
        return CraftEngineBridge.pluginPresent();
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
        final PrefixStore.Entry entry = store.get(player.getUniqueId());
        // 称号优先于手动前缀；解析结果当场定型
        final ChatPrefix.ResolvedPrefix resolved = resolvePrefix(entry, titleStore);
        final String prefixRaw = resolved == null ? null : resolved.text();
        final boolean prefixRainbow = resolved != null && resolved.rainbow();
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
                    rainbowPhase, packViewer(viewer), prefixRainbow);
        });
    }

    /**
     * 这个接收者是否应按「装了资源包」渲染。
     *
     * <p>detect 取 auto 时： CraftEngine 接管资源包且 shader-merged 为 true 时直接判作装了
     * （见 {@link #packViewer(Player)}）；否则用事件上报结果与 Bukkit/Paper 的 API 综合判断。
     * 取 always 时当所有人都装了；取 never 时一律当作没装。
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
        if (!(viewer instanceof Player player)) {
            // 控制台等非玩家接收者：CE 接管时资源包对所有人有效，否则只在 always 时算装了
            return "always".equalsIgnoreCase(policy) || (ceActive(current) && current.ceShaderMerged());
        }
        return packViewer(player);
    }

    /** 同一套判定的玩家版本，/cp pack 复用。 */
    public boolean packViewer(Player player) {
        ChatSettings current = settings;
        if (current == null || !current.packEnabled()) {
            return false;
        }
        // CraftEngine 占着服务器的资源包（而 ChatPrefix 的彩虹着色器不在里面）时，
        // 「装了包」不等于「有着色器」：此时跳过魔法色分支，继续用逐消息相位彩虹。
        if (ceActive(current) && !current.ceShaderMerged()) {
            return false;
        }
        String policy = current.packDetect();
        if ("never".equalsIgnoreCase(policy)) {
            return false;
        }
        // CraftEngine 在登录配置阶段自己发资源包数据包（不走 Paper 的 setResourcePack），
        // PlayerResourcePackStatusEvent 不会触发、hasResourcePack() 恒为 false —— 用 Paper API
        // 检测永远判「没装包」。shader-merged=true 时 CE 的下发本身就是「装了包」的保证
        // （默认 kick-if-declined 会踢掉拒绝包的玩家），auto 也直接判作装了。
        if (ceActive(current) && current.ceShaderMerged()) {
            return true;
        }
        if ("always".equalsIgnoreCase(policy)) {
            return true;
        }
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
     * 当前时间对应的彩虹相位（1-10）。关掉动画时恒为 0（= 不替换裸标签，保持静态彩虹）；
     * 10 格为一整圈，所以每过 10/speed 秒循环一次。
     *
     * <p>返回 1-10 而不是 0-9：{@link Text#parse} 把 0 当作「调用方不想动动画」的哨兵值，
     * 如果直接用 0-9，每转一圈就会有一帧漏掉相位替换，那一条消息的彩虹会猛地跳回静态相位。
     * 10 与 0 在 MiniMessage 里视觉等价（都是一整圈），所以循环依旧连续。
     */
    private int rainbowPhase() {
        ChatSettings current = settings;
        if (current == null || !current.prefixRainbowAnimate()) {
            return 0;
        }
        double steps = System.currentTimeMillis() / 1000.0 * current.prefixRainbowSpeed();
        return (int) Math.floorMod((long) Math.floor(steps), 10L) + 1;
    }
}
