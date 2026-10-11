# ChatPrefix v1.2.1 — Paper 1.21.11 聊天前缀与维度标签插件

[English](README.md) | [中文](README.zh-CN.md)

在聊天栏里把 op 用指令分配的前缀放到玩家名前面，后面跟上这名玩家当前所在的维度。

```text
[末地]玩家1 » 你好呀
[大佬][主世界]Steve » 你好呀
[下界]Alex » 有人吗
```

* 前缀（`[本服]`）由 op 用指令 **按玩家** 设置，存在 `plugins/ChatPrefix/prefixes.yml` 里。
* 维度标签（`[末地]` / `[下界]` / `[主世界]`）**所有玩家都有**，始终显示。
* 没有设置前缀的玩家就不显示前缀那一整段，不会留下占位文字。

## 环境要求

| 项 | 值 |
| --- | --- |
| 服务端 | Paper 1.21.11（Paper API；不是纯 Spigot/Bukkit） |
| Java | 21 或更高 |
| 插件版本 | `1.2.1-1.21.11` |

编译目标是 `paper-api-1.21.11`，用的是 `io.papermc.paper.event.player.AsyncChatEvent` 与 Adventure
组件，不碰 NMS，因此在 Paper 1.21.x 的小版本更新里照常可用。刻意不依赖 PlaceholderAPI 或任何其他插件。

## 安装

1. 把 `ChatPrefix-1.2.1-1.21.11.jar` 放进服务端的 `plugins/` 目录。
2. 重启服务器（或 `/reload confirm`）。首次启动会生成 `plugins/ChatPrefix/config.yml` 与 `prefixes.yml`。
3. 给某位玩家设置前缀：`/chatprefix set aaa_ovo 本服`。

> 不要和别的聊天格式化插件（或计分板队伍格式）叠着用而不检查结果：最后设置聊天渲染器的插件会赢，
> 服务端的 `paper-global.yml` 或其他聊天插件的 `format` 也会盖掉本插件拼出来的这一行。

## 指令

全部挂在 `chatprefix` 一条指令下（别名 `cp`），需要 `chatprefix.admin` 权限（默认给 op）。

| 指令 | 作用 |
| --- | --- |
| `/cp set <玩家> <文字>` | 设置前缀。输入 `本服`，聊天里显示 `[本服]`。 |
| `/cp remove <玩家>` | 取消前缀，之后该玩家不显示前缀段。 |
| `/cp get <玩家>` | 查看已存的前缀，并按聊天里的实际效果渲染出来。 |
| `/cp list` | 列出所有已设置的前缀（最多 100 条）。 |
| `/cp pack` | 打印你自己的资源包判定细节（刷新前缀的彩虹没生效时先跑这个）。 |
| `/cp reload` | 重载 `config.yml` 与 `prefixes.yml`，不用重启服务器。 |
| `/cp help` | 显示用法。 |

玩家参数可以是在线玩家，也可以是服务器还缓存着的离线玩家（上线过一次的）；名字支持 Tab 补全，
`prefixes.yml` 里有记录但当前离线的名字也能补出来。

### 前缀文字的写法

`/cp set` 里**不要**自己加外层方括号：方括号来自配置里的 `prefix.wrapper`，默认是 `[{text}]`。因此：

| 你输入 | 聊天里显示 |
| --- | --- |
| `/cp set Steve 本服` | `[本服]` |
| `/cp set Steve &c大佬` | 红色的 `[大佬]` |
| `/cp set Steve <gradient:#ff0000:#0000ff>VIP` | 渐变色的 `[VIP]` |
| `/cp set Steve [夜猫子]` | `[[夜猫子]]` —— 文字自带方括号，又套了一层 |

两种颜色写法都支持：

* 传统 `&` 代码：`&c` 红、`&l` 粗体、`&r` 重置；`&&` 表示一个字面 `&`。
* MiniMessage 标签：`<red>`、`<bold>`、`<gradient:#ff0000:#0000ff>`，也支持 `<#ff8800>` 这种十六进制。

外层方括号自己也会上色（`prefix.color` 留空时的规则）：

* 文字带了颜色 → 方括号跟着变：前括号取文字的**首个**颜色，后括号取**末个**颜色。
  所以 `/cp set Steve &c大佬` 得到的是整段红色的 `[大佬]`，而不是白括号配红字；
  `&c红&f白` 这种多色前缀则是 `[红白]`，两边括号各自跟色。
* 文字完全没颜色 → 方括号保持原样，不额外上色（和以前一致）。
* config 里配了 `prefix.color` → 方括号和「本身没颜色」的文字一起统一上成该颜色；
  `prefix.color` 留空才会走上面「跟随文字」的规则。

如果希望 `<`、`>` 保持普通字符，把 `prefix.allow-minimessage` 设为 `false`。
前缀长度由 `prefix.max-length` 限制（默认 24 个可见字符，按去掉颜色代码后的字符数算，填 `0` 表示不限制）。

#### 彩虹与其它多彩写法

MiniMessage 自带的多彩标签都能直接用（下表颜色是实测值，不是推算）：

| 写法 | 聊天里显示 |
| --- | --- |
| `<rainbow>管理员` | 逐字彩虹：管 `#FF0000`、理 `#7FFF00`、员 `#00FFFF` |
| `<rainbow:2>管理员` | 相位偏移的彩虹（起点 hue 往前挪） |
| `<rainbow:!>管理员` | 反向彩虹 |
| `<gradient:#ffb300:#ff3d00>管理员` | 两色线性插值 |
| `<pride>管理员` | 骄傲旗配色 |

两个实测出来的硬限制，写配置时要留意：

* `<rainbow:N>` 的 N **只能是 0–10 的整数**（1 单位 = 36°，10 = 一整圈）。写 `<rainbow:0.5>` 会失效，
  客户端会把 `<rainbow:0.5>` 原样显示出来；写负数则直接报错。
* **Minecraft 聊天行发出后客户端就定死了，插件无法原地改那一行的颜色**。所以真正的“动画”只能靠
  逐条消息推进相位：每发一条消息颜色往前滚一格。

`prefix.rainbow.animate: true`（默认）时，前缀里的裸 `<rainbow>` 会被插件换成带当前相位的
`<rainbow:N>`，于是每条消息的彩虹起点都不同；`speed` 控制每秒推进多少格（10 格 = 一整圈）。
写 `<rainbow:2>` 这种**带参数**的写法不受影响，永远是固定相位。

## 配置

`plugins/ChatPrefix/config.yml`：

```yaml
# 一整行聊天，占位符：{prefix} {dimension} {name} {separator} {message}
format: "{prefix}{dimension}{name}{separator}{message}"

prefix:
  default: ""            # 没设置前缀时显示什么；空串 = 这一整段不显示
  wrapper: "[{text}]"    # {text} 是 op 输入的原文字
  color: ""              # 前缀默认颜色；空串 = 方括号跟随文字颜色，见「前缀文字的写法」
  rainbow:
    animate: true        # 前缀里的裸 <rainbow> 是否随时间流动（每条消息相位不同）
    speed: 1.0           # 每秒推进多少格相位；10 格 = 一整圈
  allow-minimessage: true
  max-length: 24         # 可见字符数上限，0 = 不限制

dimension:
  wrapper: "[{label}]"
  labels:
    OVERWORLD: "主世界"
    NETHER: "下界"
    THE_END: "末地"
  custom-label: "{world}"   # 数据包/模组维度用它，{world} 是世界名
  colors:
    OVERWORLD: "green"
    NETHER: "red"
    THE_END: "light_purple"
    CUSTOM: "yellow"

name:
  use-display-name: false   # true = 用服务端显示名（昵称插件、队伍前缀会改变它）
  color: ""                 # 空串 = 不改动名字颜色（保留队伍颜色）

separator:
  text: " » "
  color: "gray"

# 资源包侧的真·原地彩虹；见「资源包：真·原地彩虹」。关掉则一律走逐消息相位彩虹。
pack:
  enabled: true
  magic-color: "#F8F85C"   # 必须与资源包 _config.glsl 里 TEXT_EFFECT 的触发色一致
  detect: auto             # auto = 事件 + API 判定；always = 当所有人都装了；never = 一律当作没装
```

说明：

* 颜色可以写命名颜色（`gray`、`red`、`gold`、`light_purple` …）或 `#rrggbb`。
* `prefix.color` / `name.color` 只给**本身没有颜色**的部分补色。前缀写成 `&c大佬` 时文字始终是红色：
  `prefix.color` 留空则方括号跟着文字变红，配成 `gold` 则方括号用 gold（显式配置优先）。
* `name.color` 默认留空是有意的：给名字上色会盖掉计分板队伍颜色。
* `prefix.default` 留空即「没有前缀就什么都不显示」；把它改成 `滚木`，没设置前缀的玩家就会显示 `[滚木]`。
* 玩家名与聊天内容从不做 MiniMessage 解析，玩家无法通过聊天注入格式或点击事件；
  只有配置文本和 op 输入的前缀会被解析。
* 如果 `prefix.wrapper` 里漏写了 `{text}`，前缀文字会不加包裹直接显示，而不是被悄悄丢掉。

## 资源包：真·原地彩虹（可选）

聊天行一旦发出，客户端就把它定死了，插件无法原地改色 —— 所以 `prefix.rainbow` 做出来的「流动」
本质是逐条消息推进相位。想要**真正的原地动画**，只能让客户端的内核着色器参与。

`resourcepack/` 里是一个裁剪版资源包，派生自 TheSalt_ 的 Text Effects（MIT）。它覆盖
`rendertype_text` 核心着色器，把颜色 `#F8F85C` 渲染成持续滚动的彩虹。

```powershell
cd resourcepack
.\build-pack.ps1     # 产物：dist\ChatPrefix-Rainbow-1.2.0.zip，同时打印 sha1
```

资源包内容自 1.2.0 起没再改动，所以它停在 1.2.0；插件版本的推进与它无关。

生效链路：

1. 把 zip 下发给玩家（`server.properties` 的 `resource-pack=` + `resource-pack-sha1=`，
   或用 Paper 的资源包配置）。
2. 插件对**装了包的玩家**把它看到的前缀整段刷成 `#F8F85C`，由着色器渲染成原地流动的彩虹；
   **没装包的玩家完全不受影响**，仍是逐消息相位彩虹。
3. 只有裸 `<rainbow>` 才会走这条分支；`<rainbow:2>` 这种带相位的写法表示「要固定彩虹」，两条路径一致。

判定玩家装没装包用 `pack.detect`：`auto`（默认，用 Bukkit/Paper API）、`always`（当所有人都装了，
服务器强制下发时用）、`never`（一律当作没装）。

注意：

* 这个包覆盖 `rendertype_text`，**与任何同样覆盖该着色器的资源包互斥**。
* `pack.mcmeta` 的范围锁在 `min_format`/`max_format` = 57–75，即 Minecraft 1.21.6–1.21.11。
* 许可为 MIT，`resourcepack/LICENSE-Text_Effects.txt` 必须随包保留；派生说明见
  `resourcepack/CREDITS.md`。
* **尚未做真机验证**：着色器接口与原版 1.21.11 已逐项比对通过（见 CREDITS.md），
  但「玩家装上包后实际看到的画面」需要有人进服确认，离线测试覆盖不到这一步。

### 装了包但彩虹没有变化？先跑 `/cp pack`

判定链路上有两个容易踩的点：

1. **服务端必须跑 1.2.1 或更高的插件**。1.2.0 也有这条分支，但 `auto` 判定只问 API，
   不够可靠；1.2.1 起会同时记录客户端上报的资源包状态事件。如果 `plugins/ChatPrefix/config.yml`
   里根本找不到 `pack:` 段，那就是在跑旧 jar。
2. **客户端手动装包，服务端看不到**。把 zip 自己丢进 `resourcepacks` 文件夹时，服务器没有任何
   途径知道，`auto` 必然判为「没装」。这种情形把 `pack.detect` 改成 `always`。

`/cp pack` 会把每一步摊开：插件版本、三项 `pack.*` 配置、最近一次资源包状态事件、
`hasResourcePack()`、`getResourcePackStatus()`、最终判定，以及你自己前缀的原文。

看到的现象能直接区分问题：

| 聊天里看到 | 说明 |
| --- | --- |
| 前缀是**纯色**黄绿（`#F8F85C`） | 魔法色生效了，但客户端没装成包 → 包没加载成功，或 `detect` 判错了 |
| 前缀是**固定彩虹** | 魔法色没生效，插件走了逐消息相位那条路径 → 看 `/cp pack` 的最终判定 |
| 前缀**在原地滚动** | 成功 |

## 从源码构建

只需要 JDK，不需要 Maven/Gradle。

```powershell
cd 1.21.11
.\build.ps1          # 产物：dist\ChatPrefix-1.2.1-1.21.11.jar
```

`build.ps1` 用 JDK 21 编译（`--release 21`），把 `build/classes` 与 `resources/*` 打进 jar。
第三方 jar 放在 `1.21.11/lib/`，**不会**被打进插件：

| jar | 用途 |
| --- | --- |
| `paper-api-1.21.11.jar` | 服务端 API（仅编译期） |
| `adventure-api-4.26.1.jar`、`adventure-key-4.26.1.jar` | 文本组件 |
| `adventure-text-minimessage-4.26.1.jar` | MiniMessage 解析 |
| `adventure-text-serializer-plain-4.26.1.jar` | 纯文本输出，离线测试用 |
| `examination-api-1.3.0.jar`、`bungeecord-chat-1.21.jar` | API 的传递依赖 |
| `guava-33.3.1-jre.jar`、`failureaccess-1.0.2.jar` | 离线读取 `config.yml` 需要 |

从 `https://repo.papermc.io/repository/maven-public/` 下载（paper-api 快照在
`io/papermc/paper/paper-api/1.21.11-R0.1-SNAPSHOT/`，取时间戳最新的那个 jar）。

## 测试

```powershell
cd tests
.\run-tests.ps1      # 70 条离线断言，不需要服务器
.\smoke-test.ps1     # 在 .testserver\12111 起真实 Paper 1.21.11，用控制台喂指令验证
```

`run-tests.ps1` 用 `ChatFormatter` 渲染聊天行，断言精确的纯文本与颜色，覆盖：有/无前缀、三种维度、
自定义维度、`&` 与 MiniMessage 解析、方括号跟随文字颜色、彩虹相位流动、资源包魔法色分支、
颜色优先级、自定义模板、长度计算，以及「玩家消息与玩家名不被解析」。

`smoke-test.ps1` 首次运行会下载 Paper 1.21.11 服务端 jar（若本机已有缓存的原版 jar 会自动复用），
装上插件并启动服务器，再从控制台日志核对：插件是否启用、`/cp list|get|remove|set|reload` 是否符合预期、
`config.yml` 与 `prefixes.yml` 是否按预期生成与写回。

### 验证状态（2026-10-11）

* 离线断言 70/70 通过（8 条覆盖方括号跟随文字颜色，9 条覆盖彩虹相位流动，14 条覆盖资源包魔法色分支）。
* 真机 Paper 1.21.11（build 132）+ Java 21：`api-version: '1.21'` 通过加载，插件正常启用，所有指令都有正确回应，
  `prefixes.yml` 能正确往返中文前缀与 `&` 颜色代码。
* 尚未验证：真人玩家在客户端里看到的聊天行样式。控制台驱动的冒烟测试造不出真实玩家消息，
  这一步需要有人进服打一句话。
