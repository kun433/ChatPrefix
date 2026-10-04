package com.chatprefix;

import java.io.File;
import java.io.IOException;

import com.chatprefix.chat.ChatFormatter;
import com.chatprefix.chat.ChatSettings;
import com.chatprefix.chat.PrefixStore;
import com.chatprefix.command.PrefixCommand;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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

        getLogger().info("已启用：前缀 " + store.size() + " 条，模板 " + settings.format());
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

        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component name = useDisplayName && sourceDisplayName != null
                    ? sourceDisplayName
                    : current.name(source.getName());
            return current.render(prefixRaw, environment, worldName, name, message);
        });
    }
}
