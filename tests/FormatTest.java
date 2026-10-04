import java.util.ArrayList;
import java.util.List;

import com.chatprefix.chat.ChatFormatter;
import com.chatprefix.chat.ChatSettings;
import com.chatprefix.util.Text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.World;
import org.bukkit.configuration.MemoryConfiguration;

/**
 * 聊天渲染的离线断言：不启动服务器，直接跑 ChatFormatter 与 Text。
 *
 * <p>覆盖的是「显示成什么样」，即整行纯文本、颜色、模板与兜底行为；
 * 真机部分（事件注册、指令、api-version）由 tests/smoke-test.ps1 负责。
 */
public final class FormatTest {

    private static int passed;
    private static int failed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        ChatSettings defaults = ChatSettings.defaults();
        ChatFormatter formatter = new ChatFormatter(defaults);

        System.out.println("== 默认配置（format: {prefix}{dimension}{name}{separator}{message}）==");
        eq("有前缀 + 末地（对照截图）",
                "[本服][末地]aaa_ovo » 第二个末地城",
                plain(formatter.render("&c本服", World.Environment.THE_END, "world_the_end",
                        formatter.name("aaa_ovo"), Component.text("第二个末地城"))));
        eq("没设前缀 + 主世界：不显示前缀段",
                "[主世界]aaa_ovo » 你好呀",
                plain(formatter.render(null, World.Environment.NORMAL, "world",
                        formatter.name("aaa_ovo"), Component.text("你好呀"))));
        eq("没设前缀 + 下界",
                "[下界]aaa_ovo » 你好呀",
                plain(formatter.render(null, World.Environment.NETHER, "world_nether",
                        formatter.name("aaa_ovo"), Component.text("你好呀"))));
        eq("没设前缀 + 末地",
                "[末地]aaa_ovo » 你好呀",
                plain(formatter.render(null, World.Environment.THE_END, "world_the_end",
                        formatter.name("aaa_ovo"), Component.text("你好呀"))));
        eq("自定义维度用世界名当标签",
                "[resource]aaa_ovo » 你好呀",
                plain(formatter.render(null, World.Environment.CUSTOM, "resource",
                        formatter.name("aaa_ovo"), Component.text("你好呀"))));
        eq("维度为 null 时也按自定义维度兜底",
                "[resource]aaa_ovo » 你好呀",
                plain(formatter.render(null, null, "resource",
                        formatter.name("aaa_ovo"), Component.text("你好呀"))));
        eq("前缀只有空白 = 没设置",
                "[末地]aaa_ovo » 你好呀",
                plain(formatter.render("   ", World.Environment.THE_END, "world_the_end",
                        formatter.name("aaa_ovo"), Component.text("你好呀"))));
        eq("MiniMessage 标签前缀",
                "[本服][末地]aaa_ovo » 你好呀",
                plain(formatter.render("<yellow>本服", World.Environment.THE_END, "world_the_end",
                        formatter.name("aaa_ovo"), Component.text("你好呀"))));
        eq("& 与 § 两种写法等价",
                plain(formatter.render("&c本服", World.Environment.NORMAL, "world", Component.text("x"), Component.text("m"))),
                plain(formatter.render("\u00a7c本服", World.Environment.NORMAL, "world", Component.text("x"), Component.text("m"))));

        System.out.println();
        System.out.println("== 玩家内容不被解析 ==");
        eq("玩家消息里的尖括号保持原样",
                "[末地]aaa_ovo » 看这个 <red> 标签",
                plain(formatter.render(null, World.Environment.THE_END, "world_the_end",
                        formatter.name("aaa_ovo"), Component.text("看这个 <red> 标签"))));
        eq("玩家名里的 & 与 < 不被当作代码",
                "[末地]A&B<C> » hi",
                plain(formatter.render(null, World.Environment.THE_END, "world_the_end",
                        formatter.name("A&B<C>"), Component.text("hi"))));
        eq("前缀里的 && 表示一个字面 &（后跟的字母不再是颜色代码）",
                "[&cA][末地]aaa_ovo » hi",
                plain(formatter.render("&&cA", World.Environment.THE_END, "world_the_end",
                        formatter.name("aaa_ovo"), Component.text("hi"))));

        System.out.println();
        System.out.println("== 异常输入不能把整行搞崩 ==");
        eqContains("未闭合的尖括号仍能显示文字", "未闭合",
                plain(formatter.render("<未闭合", World.Environment.THE_END, "world_the_end",
                        formatter.name("aaa_ovo"), Component.text("hi"))));
        eq("结尾孤零零的 & 原样保留",
                "[&][末地]aaa_ovo » hi",
                plain(formatter.render("&", World.Environment.THE_END, "world_the_end",
                        formatter.name("aaa_ovo"), Component.text("hi"))));
        eq("前缀里的 {} 不会被当成占位符",
                "[{name}][末地]aaa_ovo » hi",
                plain(formatter.render("{name}", World.Environment.THE_END, "world_the_end",
                        formatter.name("aaa_ovo"), Component.text("hi"))));

        System.out.println();
        System.out.println("== 颜色 ==");
        Component colored = formatter.render("&c(本服)", World.Environment.THE_END, "world_the_end",
                formatter.name("aaa_ovo"), Component.text("hi"));
        eq("带颜色代码的前缀文字",
                "[(本服)][末地]aaa_ovo » hi",
                plain(colored));
        eqColor("前缀文字 &c = red", NamedTextColor.RED, colorOf(colored, "(本服)"));
        eqColor("末地标签 = light_purple", NamedTextColor.LIGHT_PURPLE, colorOf(colored, "末地"));
        eqColor("分隔符 = gray", NamedTextColor.GRAY, colorOf(colored, " » "));
        eqColor("没配 prefix.color 时方括号不上色", null, colorOf(formatter.prefix("&c(本服)"), "["));

        Component tinted = new ChatFormatter(settings("prefix.color", "gold", "prefix.max-length", 24))
                .render("本服", World.Environment.NORMAL, "world", formatter.name("aaa_ovo"), Component.text("hi"));
        eqColor("prefix.color 生效时不覆盖文字自带颜色", NamedTextColor.GOLD, colorOf(tinted, "本服"));
        eqColor("维度默认绿色保留", NamedTextColor.GREEN, colorOf(tinted, "主世界"));

        System.out.println();
        System.out.println("== 模板与配置项 ==");
        ChatFormatter custom = new ChatFormatter(settings(
                "format", "{dimension}{name}: {message}",
                "prefix.max-length", 24));
        eq("自定义 format",
                "[末地]aaa_ovo: 你好",
                plain(custom.render("本服", World.Environment.THE_END, "world_the_end",
                        custom.name("aaa_ovo"), Component.text("你好"))));

        eq("prefix.default 可作为兜底",
                "[游客][末地]aaa_ovo » hi",
                plain(new ChatFormatter(settings("prefix.default", "游客", "prefix.max-length", 24))
                        .render(null, World.Environment.THE_END, "world_the_end",
                                formatter.name("aaa_ovo"), Component.text("hi"))));

        eq("wrapper 改成 {text} 时不加方括号",
                "本服[末地]aaa_ovo » hi",
                plain(new ChatFormatter(settings("prefix.wrapper", "{text}", "prefix.max-length", 24))
                        .render("本服", World.Environment.THE_END, "world_the_end",
                                formatter.name("aaa_ovo"), Component.text("hi"))));

        eq("wrapper 忘了写 {text} 也不会丢前缀",
                "本服[末地]aaa_ovo » hi",
                plain(new ChatFormatter(settings("prefix.wrapper", "【】", "prefix.max-length", 24))
                        .render("本服", World.Environment.THE_END, "world_the_end",
                                formatter.name("aaa_ovo"), Component.text("hi"))));

        eq("维度标签可改成别的文字",
                "[End]aaa_ovo » hi",
                plain(new ChatFormatter(settings("dimension.labels.THE_END", "End", "dimension.wrapper", "[{label}]"))
                        .render(null, World.Environment.THE_END, "world_the_end",
                                formatter.name("aaa_ovo"), Component.text("hi"))));

        eq("维度标签留空则不显示维度段",
                "aaa_ovo » hi",
                plain(new ChatFormatter(settings("dimension.labels.THE_END", "", "dimension.wrapper", "[{label}]"))
                        .render(null, World.Environment.THE_END, "world_the_end",
                                formatter.name("aaa_ovo"), Component.text("hi"))));

        eq("format 里的未知占位符原样输出",
                "[末地]{nick} aaa_ovo » hi",
                plain(new ChatFormatter(settings("format", "{dimension}{nick} {name}{separator}{message}"))
                        .render(null, World.Environment.THE_END, "world_the_end",
                                formatter.name("aaa_ovo"), Component.text("hi"))));

        eq("prefix.allow-minimessage=false 时尖括号按普通字符",
                "[<red>本服][末地]aaa_ovo » hi",
                plain(new ChatFormatter(settings("prefix.allow-minimessage", false))
                        .render("<red>本服", World.Environment.THE_END, "world_the_end",
                                formatter.name("aaa_ovo"), Component.text("hi"))));

        eqColor("name.color = aqua", NamedTextColor.AQUA,
                new ChatFormatter(settings("name.color", "aqua")).name("aaa_ovo").color());

        System.out.println();
        System.out.println("== 长度与颜色工具 ==");
        eqInt("visibleLength(&c[大佬])", 4, Text.visibleLength("&c[大佬]"));
        eqInt("visibleLength(<red>[大佬])", 4, Text.visibleLength("<red>[大佬]"));
        eqInt("visibleLength(§l§a猪)", 1, Text.visibleLength("\u00a7l\u00a7a猪"));
        eqInt("visibleLength(null)", 0, Text.visibleLength(null));
        eqColor("Text.color(gray)", NamedTextColor.GRAY, Text.color("gray"));
        eqColor("Text.color(LIGHT_PURPLE 小写)", NamedTextColor.LIGHT_PURPLE, Text.color("light_purple"));
        eqColor("Text.color(#ff0000)", TextColor.color(0xff0000), Text.color("#ff0000"));
        eqColor("Text.color(乱写的返回 null)", null, Text.color("这不是颜色"));

        System.out.println();
        System.out.println("===== 结果：" + passed + " 通过 / " + failed + " 失败 =====");
        for (String failure : failures) {
            System.out.println("  " + failure);
        }
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static ChatSettings settings(Object... pairs) {
        MemoryConfiguration cfg = new MemoryConfiguration();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            cfg.set((String) pairs[i], pairs[i + 1]);
        }
        return ChatSettings.load(cfg);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** 找到内容等于 content 的文本节点，返回它的颜色（applyFallbackStyle 会把它写进节点）。 */
    private static TextColor colorOf(Component root, String content) {
        if (root.children().isEmpty()) {
            if (plain(root).equals(content)) {
                return root.style().color();
            }
            return null;
        }
        for (Component child : root.children()) {
            TextColor found = colorOf(child, content);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void eq(String label, String expected, String actual) {
        if (expected.equals(actual)) {
            passed++;
            System.out.println("  [OK]   " + label + "  ->  " + actual);
        } else {
            failed++;
            String message = "[FAIL] " + label + "：期望 <" + expected + ">，实际 <" + actual + ">";
            failures.add(message);
            System.out.println("  [FAIL] " + label + "：期望 <" + expected + ">，实际 <" + actual + ">");
        }
    }

    private static void eqContains(String label, String needle, String actual) {
        if (actual.contains(needle)) {
            passed++;
            System.out.println("  [OK]   " + label + "  ->  " + actual);
        } else {
            failed++;
            String message = "[FAIL] " + label + "：<" + actual + "> 里找不到 <" + needle + ">";
            failures.add(message);
            System.out.println("  [FAIL] " + label + "：<" + actual + "> 里找不到 <" + needle + ">");
        }
    }

    private static void eqInt(String label, int expected, int actual) {
        eq(label, String.valueOf(expected), String.valueOf(actual));
    }

    private static void eqColor(String label, TextColor expected, TextColor actual) {
        String expectedText = expected == null ? "无" : expected.asHexString();
        String actualText = actual == null ? "无" : actual.asHexString();
        eq(label, expectedText, actualText);
    }
}
