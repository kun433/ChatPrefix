package com.chatprefix.craftengine;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;

/**
 * 与 CraftEngine（CE）的可选集成桥：全部走反射，不引入任何编译期依赖。
 *
 * <p>CE 的图片系统把 PNG 映射成自定义字体字符（见 CE 文档「配置 → 图像」），并把这些字符
 * 打进它自己生成、自动下发的资源包。本桥在渲染前缀时调用 CE 的公开 API
 * {@code CraftEngineImages.byId(Key)} 把 {@code <image:命名空间:id[:行:列]>} 标签
 * 渲染成对应的字符组件，于是 op 可以把 CE 里做好的彩色图标直接用作聊天前缀。
 *
 * <p>三条渲染通道（按优先级）：
 * <ol>
 *   <li>本桥直调 CE API：即时渲染，不依赖 CE 的网络层配置；</li>
 *   <li>带格式参数等复杂标签原样保留在文本里，交给 CE 的网络包拦截（默认开
 *       {@code network.intercept-packets.system-chat}）替换；</li>
 *   <li>CE 不在线时按 config 的 {@code craftengine.missing} 剔除或保留原文本。</li>
 * </ol>
 *
 * <p>两个关键实现约束（针对真实 CE 服务端验证过，CE 26.9.2）：
 * <ul>
 *   <li>CE 是 paper-plugin（隔离类加载器），ChatPrefix 自己的类加载器看不到 CE 的类，
 *       必须拿到 CE 的 {@link org.bukkit.plugin.Plugin} 实例后用它的类加载器解析；</li>
 *   <li>CE 26.x 起 {@code Image.componentAt} 返回 CE 自带（shaded）的 adventure Component，
 *       不能强转回 kyori Component；改用 {@code Image.codepointAt} 拿码点，再用
 *       {@code BitmapImage.font()} 里的字体 Key 用 kyori API 原样重建
 *       {@code Component.text(char).font(key)}（与 CE 的 componentAt 逐字段等价）。</li>
 * </ul>
 *
 * <p>所有反射调用都在渲染线程发生（聊天渲染是异步的），Method 对象只解析一次；
 * CE 重载后图片码位变化也能跟随，因为每次渲染都现查 CE 的注册表。
 */
public final class CraftEngineBridge {

    /** CraftEngine 在 Bukkit 里的插件名（其 plugin.yml 的 name）。 */
    public static final String PLUGIN_NAME = "CraftEngine";

    private static final Object INIT_LOCK = new Object();

    /** 反射句柄只解析一次；解析失败后不再重试（CE 的 API 形状变了就当没有集成）。 */
    private static volatile boolean resolved;
    private static volatile boolean usable;
    private static Method keyOf;
    private static Method imagesById;
    private static Method imageCodepointAt;
    private static Method fontManagerInstance;
    private static Method imageByIdValue;
    /** 从 Image 实现类拿字体 Key 的访问器（BitmapImage.font()；ReferenceImage 要先 image() 退一层），按具体类缓存。 */
    private static final java.util.Map<Class<?>, FontAccessor> FONT_ACCESSORS = new java.util.HashMap<>();

    /** <image:...> 标签体内允许的 id 片段；用于区分「简单形式」与带格式参数的复杂形式。 */
    private static final Pattern ID_PART = Pattern.compile("[A-Za-z0-9_\\-.]+");
    /** 精灵图行号/列号。 */
    private static final Pattern NUM_PART = Pattern.compile("\\d+");

    private CraftEngineBridge() {
    }

    /** CraftEngine 插件当前是否在线（未安装 CE 的服务器恒为 false）。 */
    public static boolean pluginPresent() {
        try {
            return Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 把一张 CE 图片渲染成聊天字符组件。
     *
     * @param namespacedId {@code ns:id}；只写 {@code id} 时按 CE 的「按 value 查」规则匹配
     * @param row          精灵图行号（非精灵图传 0）
     * @param col          精灵图列号（非精灵图传 0）
     * @return 渲染出的组件；CE 不在线、图片不存在、坐标越界等任何失败都返回 null
     */
    public static Component image(String namespacedId, int row, int col) {
        if (namespacedId == null || namespacedId.isBlank() || !pluginPresent()) {
            return null;
        }
        if (!ensureResolved()) {
            return null;
        }
        try {
            Object image;
            if (namespacedId.indexOf(':') >= 0) {
                Object key = keyOf.invoke(null, namespacedId);
                image = imagesById.invoke(null, key);
            } else if (imageByIdValue != null && fontManagerInstance != null) {
                Object manager = fontManagerInstance.invoke(null);
                image = imageByIdValue.invoke(manager, namespacedId);
            } else {
                return null;
            }
            if (image == null) {
                return null;
            }
            // CE 26.x 的 componentAt 返回它 shaded 的 adventure Component，不能直接用；
            // 这里复刻它的实现：码点字符 + 图片所属字体（见类注释）。
            int codepoint = (Integer) imageCodepointAt.invoke(image, row, col);
            if (codepoint <= 0) {
                return null;
            }
            return Component.text(new String(Character.toChars(codepoint)))
                    .font(fontKeyOf(image));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 拿图片的字体 Key；拿不到（API 形状又变了）返回 null，退回默认字体。 */
    private static net.kyori.adventure.key.Key fontKeyOf(Object image) {
        try {
            Object fontKey = fontAccessor(image.getClass()).get(image);
            return fontKey == null ? null : net.kyori.adventure.key.Key.key(fontKey.toString());
        } catch (Throwable t) {
            return null;
        }
    }

    @FunctionalInterface
    private interface FontAccessor {
        Object get(Object image) throws Exception;
    }

    private static synchronized FontAccessor fontAccessor(Class<?> imageClass) throws ReflectiveOperationException {
        FontAccessor cached = FONT_ACCESSORS.get(imageClass);
        if (cached != null) {
            return cached;
        }
        FontAccessor accessor;
        try {
            Method font = imageClass.getMethod("font");
            accessor = font::invoke;
        } catch (NoSuchMethodException e) {
            // ReferenceImage 包装真正的图片：先 image() 退一层再 font()
            Method unwrap = imageClass.getMethod("image");
            accessor = img -> {
                Object inner = unwrap.invoke(img);
                return inner == null ? null : fontAccessor(inner.getClass()).get(inner);
            };
        }
        FONT_ACCESSORS.put(imageClass, accessor);
        return accessor;
    }

    /**
     * 构造 {@code <image:...>} 的 MiniMessage 标签解析器。
     *
     * <p>{@code ceActive} 为 false（CE 没装或配置关闭）时标签不会渲染；{@code literalFallback}
     * 决定此时剔除标签（false）还是保留原文本（true）。CE 在线但图片 id 不存在时也走同一套
     * fallback——让 CE 网络层再查一遍没有意义，直接按配置处理更可预期。
     *
     * <p>带格式参数的复杂形式（如 {@code <image:default:icons:0:0:'<!shadow>'>}）不做直调：
     * 参数里的引号与样式交给 CE 的 MiniMessage 处理更完整，因此保留原文本交给 CE 网络层。
     */
    public static TagResolver imageTagResolver(boolean ceActive, boolean literalFallback) {
        return TagResolver.resolver("image", (BiFunction<ArgumentQueue, Context, Tag>) (args, ctx) -> {
            List<String> parts = new ArrayList<>(6);
            try {
                while (args.hasNext()) {
                    parts.add(args.pop().value());
                }
            } catch (RuntimeException ex) {
                parts.clear();
            }
            String rebuilt = "<image:" + String.join(":", parts) + ">";
            if (ceActive && isSimple(parts)) {
                String id = parts.size() == 1 ? parts.get(0) : parts.get(0) + ":" + parts.get(1);
                Component rendered = image(id, intAt(parts, 2), intAt(parts, 3));
                if (rendered != null) {
                    return Tag.selfClosingInserting(rendered);
                }
            }
            if (literalFallback) {
                return Tag.selfClosingInserting(Component.text(rebuilt));
            }
            return Tag.selfClosingInserting(Component.empty());
        });
    }

    /** 简单形式：1-4 段，前两段是 id、后两段是数字（行/列），不带格式参数。 */
    private static boolean isSimple(List<String> parts) {
        if (parts.isEmpty() || parts.size() > 4) {
            return false;
        }
        if (!ID_PART.matcher(parts.get(0)).matches()) {
            return false;
        }
        if (parts.size() >= 2 && !ID_PART.matcher(parts.get(1)).matches()) {
            return false;
        }
        for (int i = 2; i < parts.size(); i++) {
            if (!NUM_PART.matcher(parts.get(i)).matches()) {
                return false;
            }
        }
        return true;
    }

    private static int intAt(List<String> parts, int index) {
        if (parts.size() <= index) {
            return 0;
        }
        try {
            return Integer.parseInt(parts.get(index));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /** 懒解析 CE 的反射句柄；CE 类不在classpath 时保持不可用且不再重试。 */
    private static boolean ensureResolved() {
        if (resolved) {
            return usable;
        }
        synchronized (INIT_LOCK) {
            if (resolved) {
                return usable;
            }
            try {
                // CE 是 paper-plugin（隔离类加载器），必须借 CE 插件自己的类加载器解析
                org.bukkit.plugin.Plugin ce = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
                if (ce == null) {
                    usable = false;
                    resolved = true;
                    return false;
                }
                ClassLoader ceLoader = ce.getClass().getClassLoader();
                Class<?> key = Class.forName("net.momirealms.craftengine.core.util.Key", true, ceLoader);
                Class<?> images = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineImages", true, ceLoader);
                Class<?> image = Class.forName("net.momirealms.craftengine.core.font.Image", true, ceLoader);
                keyOf = key.getMethod("of", String.class);
                imagesById = images.getMethod("byId", key);
                // 不用 componentAt：它返回 CE shaded 的 adventure Component，强转会 ClassCastException
                imageCodepointAt = image.getMethod("codepointAt", int.class, int.class);
                try {
                    Class<?> bukkitFont = Class.forName("net.momirealms.craftengine.bukkit.font.BukkitFontManager", true, ceLoader);
                    fontManagerInstance = bukkitFont.getMethod("instance");
                    imageByIdValue = bukkitFont.getMethod("imageByIdValue", String.class);
                } catch (ReflectiveOperationException ignored) {
                    // 按 value 查是可选通道（CE 26.9 起已移除），缺失时只影响不带命名空间的写法
                }
                usable = true;
            } catch (Throwable t) {
                usable = false;
            }
            resolved = true;
            return usable;
        }
    }

    /** 供测试与 /cp ce 使用的状态说明：反射句柄是否已就绪。 */
    public static boolean reflectionReady() {
        // 直接探测而非只读缓存：没人用过图片标签时也能给出真实结论（/cp ce 用）
        return ensureResolved();
    }
}
