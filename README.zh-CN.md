# ChatPrefix v1.4.0 — Paper 26.3 聊天前缀与维度标签插件

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
| 服务端 | Paper 26.3（Paper API；不是纯 Spigot/Bukkit）；Leaf 26.3 同样可用 |
| Java | 25 或更高（26.3 起服务端最低 Java 25） |
| 插件版本 | `1.4.0-26.3` |

编译目标是 `paper-api-26.3`（构建号 241），用的是 `io.papermc.paper.event.player.AsyncChatEvent` 与 Adventure 5.2.0
组件，不碰 NMS，因此在 Paper 26.x 的小版本更新里照常可用。刻意不依赖 PlaceholderAPI；唯一的可选集成是
CraftEngine（见下文），全程走反射、只在 `plugin.yml` 里登记 `softdepend` —— 没装 CE 的服务器行为与以前完全一致。

> 这是给 26.3 的构建。还在 1.21.11 上的服务器请用 v1.2.1 的 jar，两者不能互换（`api-version` 与字节码版本都不同）。

## 安装

1. 把 `ChatPrefix-1.4.0-26.3.jar` 放进服务端的 `plugins/` 目录。
2. 重启服务器（或 `/reload confirm`）。首次启动会生成 `plugins/ChatPrefix/config.yml` 与 `prefixes.yml`。
3. 给某位玩家设置前缀：`/chatprefix set aaa_ovo 本服`。
4. 想要“一直变色”的彩虹前缀：`/chatprefix set aaa_ovo 炒鸡大牛 --rainbow`。

> 不要和别的聊天格式化插件（或计分板队伍格式）叠着用而不检查结果：最后设置聊天渲染器的插件会赢，
> 服务端的 `paper-global.yml` 或其他聊天插件的 `format` 也会盖掉本插件拼出来的这一行。

## 指令

全部挂在 `chatprefix` 一条指令下（别名 `cp`），需要 `chatprefix.admin` 权限（默认给 op）。

| 指令 | 作用 |
| --- | --- |
| `/cp set <玩家> [--rainbow] <文字>` | 设置前缀。输入 `本服`，聊天里显示 `[本服]`；加 `--rainbow` 就是彩虹前缀。 |
| `/cp rainbow <玩家> on\|off` | 只开关彩虹，不改动前缀文字。 |
| `/cp remove <玩家>` | 取消前缀，之后该玩家不显示前缀段。 |
| `/cp get <玩家>` | 查看已存的前缀（含彩虹开关），并按聊天里的实际效果渲染出来。 |
| `/cp list` | 列出所有已设置的前缀（最多 100 条），彩虹前缀带 `（彩虹）` 标记。 |
| `/cp pack` | 打印你自己的资源包判定细节（刷新前缀的彩虹没生效时先跑这个）。 |
| `/cp ce` | 打印 CraftEngine 集成状态（前缀里的 `<image:...>` 图片没显示时先跑这个）。 |
| `/cp reload` | 重载 `config.yml` 与 `prefixes.yml`，不用重启服务器。 |
| `/cp help` | 显示用法。 |

玩家参数可以是在线玩家，也可以是服务器还缓存着的离线玩家（上线过一次的）；名字支持 Tab 补全，
`prefixes.yml` 里有记录但当前离线的名字也能补出来。

### 彩虹开关（`--rainbow`）

最省事的写法是在 `/cp set` 里加一个开关，前缀原文不必自己写任何标签：

```text
/cp set Steve 炒鸡大牛 --rainbow      →  [炒鸡大牛] 一直变色的彩虹
/cp set Steve --rainbow 炒鸡大牛      →  同上，开关写在玩家名后面也行
/cp set Steve 炒鸡大牛 彩虹           →  同上，中文关键字也认
/cp rainbow Steve off                →  关掉彩虹，文字本身不动
```

规则：

* 开关只是「这个前缀按彩虹渲染」，**不会改动前缀原文**，随时可以用 `/cp rainbow` 开关或再 `set` 一次覆盖。
* 开关存在 `prefixes.yml` 的 `rainbow: true`，重启、重载都保留。
* 前缀原文里已经写了 `<rainbow>` 或 `<rainbow:2>` 时以原文为准 —— 手写的固定相位不会被开关改掉。
* `prefix.allow-minimessage: false` 时尖括号只是普通字符，开关不会生效（`/cp set` 会当场提醒）。
* 开关打开后，前缀吃哪条动画路径由资源包决定：装了包 → 客户端原地流动的彩虹；没装包 → 每条消息往前滚一格。
  `--rainbow` 之前**必须**自己手写 `<rainbow>` 才有彩虹，这就是之前“装不装材质包都一样”的原因。

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

日常不需要手写标签：`/cp set Steve 炒鸡大牛 --rainbow` 的等效结果就是前缀原文前面多了个 `<rainbow>`，
后面的判定（相位、资源包、固定相位优先）完全一致。

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

# CraftEngine 集成：把 CE 里的自定义图片用作聊天图标；见「与 CraftEngine 集成」一节。
craftengine:
  mode: auto            # auto = 检测到 CE 在线时启用；on = 强制启用；off = 关闭
  missing: hide         # hide = 剧除渲染不了的 <image:...> 标签；literal = 保留原文本
  shader-merged: false  # 只有把 ChatPrefix 的彩虹着色器合并进服务器资源包后才改 true
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

## 与 CraftEngine 集成：图片前缀（可选）

服务器同时装了 CraftEngine（CE）时，前缀可以直接用 CE 的自定义图片当彩色聊天图标。ChatPrefix
通过反射调用 CE 的公开 API：没有编译期或硬性运行时依赖，没装 CE 的服务器行为与以前完全一致。

在任何 ChatPrefix 会解析的文本里都能写图片引用 —— 前缀、`prefix.default`、维度标签、分隔符、
以及 `format` 模板里的静态文字：

| 你输入 | 聊天里显示 |
| --- | --- |
| `/cp set Steve <image:default:icons:0:0>&c管理员` | CE 图片 `default:icons` 的 (0, 0) 格 + 红色「管理员」 |
| `/cp set Steve <image:default:crown>&6&l服主` | 单格图片 `default:crown` + 金色粗体「服主」 |
| `/cp set Steve <image:default:icons>&c管理员` | 精灵图的第一格 + 红色「管理员」 |

id 写法与 CE 自己的 `<image:...>` 标签一致（`命名空间:id[:行[:列]]`），所以在 CE 里已经定义好的图片
可以直接拿过来用。渲染由 ChatPrefix 自己完成（调用 `CraftEngineImages.byId(...)`），玩家侧需要
CE 的资源包 —— CE 会自动下发，所以在 CE 服务器上不需要任何额外配置。

渲染不了的标签按 `craftengine.missing` 回退：

* `hide`（默认）—— 剧除标签，不会把无法渲染的 `<image:...>` 原文露出来。
* `literal` —— 保留原文本；CE 在线时它的网络包拦截（system-chat，默认开启）可能仍会在网络层替换掉。

CE 在线但图片 id 不存在、以及带格式参数的复杂标签（如 `<image:default:icons:0:0:'<!shadow>'>`）
同样走这套回退；前缀长度限制把可渲染的图片按 1 个可见字符计。

说明：

* `/cp ce` 打印集成状态：是否检测到 CE、`mode`、`missing`，反射通道是否就绪；前缀里的图片没显示时先跑它。
* CE 的其它网络标签（`<shift:N>`、`<global:...>`、`<papi:...>`）只是原样透传；是否替换由 CE 的
  包拦截决定，与本插件无关。
* `craftengine.mode: off` 关闭整个集成；即使 CE 在线，标签也只按 `missing` 处理。

### CE 服务器上的彩虹前缀

CE 占用着服务器的资源包槽位，而 ChatPrefix 的彩虹着色器不在里面。集成生效时（mode 为 `auto`/`on`）
且 `shader-merged: false`，插件会自动跳过魔法色着色器分支：彩虹前缀继续用逐消息相位流动，完全
不需要资源包。如果你把 `resourcepack/` 里的 `core/text.*` 着色器合并进了自己的 CE 包，把
`craftengine.shader-merged` 改成 `true` 即可恢复原地彩虹。

## 资源包：真·原地彩虹（可选）

聊天行一旦发出，客户端就把它定死了，插件无法原地改色 —— 所以 `prefix.rainbow` 做出来的「流动」
本质是逐条消息推进相位。想要**真正的原地动画**，只能让客户端的内核着色器参与。

`resourcepack/` 里是一个裁剪版资源包，派生自 TheSalt_ 的 Text Effects（MIT）。它覆盖
`core/text.vsh` / `core/text.fsh` 核心着色器，把颜色 `#F8F85C` 渲染成持续滚动的彩虹。

> 26.3 重写了文本渲染管道（旧版的 `rendertype_text` / `rendertype_text_intensity*` 四个着色器已合并成
> 单一的 `core/text.*`，改用 `#include <minecraft:...>` 与显式 `layout(location)`），
> 所以这个包**只适用于 26.3**：旧包装到 26.3 上不会报错，但彩虹直接失效（覆盖的文件名已经不存在了）。

```powershell
cd resourcepack
.\build-pack.ps1     # 产物：dist\ChatPrefix-Rainbow-1.3.0.zip，同时打印 sha1
```

1.3.0 的资源包与 1.2.0 **不再字节一致**，升过级的服要重新下发（`resource-pack-sha1` 也得换）：

* 触发色改用 `TEXT_EFFECT_WITH_SHADOW`。聊天行默认带阴影，只匹配主色的话彩虹旁边会留一圈暗黄的阴影。
* `apply_effect.glsl` 的彩虹分支对阴影层乘 0.25，否则阴影会变成与正文一样亮的一层，看起来像重影。

生效链路：

1. 把 zip 下发给玩家（`server.properties` 的 `resource-pack=` + `resource-pack-sha1=`，
   或用 Paper 的资源包配置）。
2. 插件对**装了包的玩家**把它看到的前缀整段刷成 `#F8F85C`，由着色器渲染成原地流动的彩虹；
   **没装包的玩家完全不受影响**，仍是逐消息相位彩虹。
3. 只有「裸 `<rainbow>`」或「`--rainbow` 开关」才会走这条分支；`<rainbow:2>` 这种带相位的写法
   表示「要固定彩虹」，两条路径一致。

判定玩家装没装包用 `pack.detect`：`auto`（默认，用 Bukkit/Paper API）、`always`（当所有人都装了，
服务器强制下发时用）、`never`（一律当作没装）。

注意：

* 这个包覆盖 `core/text.*`，**与任何同样覆盖该着色器的资源包互斥**。
* `pack.mcmeta` 的范围锁在 `min_format`/`max_format` = 97.1，即 Minecraft 26.3（1.21.9 起资源包格式改成 主.次 两段）。
* 许可为 MIT，`resourcepack/LICENSE-Text_Effects.txt` 必须随包保留；派生说明见
  `resourcepack/CREDITS.md`。
* 已做的静态校核：核心着色器与上游 26.3 版逐字节一致，`#include` 依赖全部能在包里或原版 26.3 里解析；
  相对上游只多两处改动（`_config.glsl` 只留一条映射、彩虹分支对阴影层乘 0.25）。
  但客户端里的实际画面仍需进服看一眼，离线测试覆盖不到这一步。

### 装了包但彩虹没有变化？先跑 `/cp pack`

判定链路上有两个容易踩的点：

1. **服务端必须跑 1.3.0 或更高的插件**。`--rainbow` 开关是 1.3.0 新增的；资源包判定本身从 1.2.1 起才可靠（会同时记录客户端上报的资源包状态事件）。如果 `plugins/ChatPrefix/config.yml`
   里根本找不到 `pack:` 段，那就是在跑旧 jar。
2. **客户端手动装包，服务端看不到**。把 zip 自己丢进 `resourcepacks` 文件夹时，服务器没有任何
   途径知道，`auto` 必然判为「没装」。这种情形把 `pack.detect` 改成 `always`。

`/cp pack` 会把每一步摊开：插件版本、三项 `pack.*` 配置、最近一次资源包状态事件、
`hasResourcePack()`、`getResourcePackStatus()`、最终判定，以及你自己前缀的原文、彩虹开关，
和这条前缀到底会不会走魔法色。

看到的现象能直接区分问题：

| 聊天里看到 | 说明 |
| --- | --- |
| 前缀是**纯色**黄绿（`#F8F85C`） | 魔法色生效了，但客户端没装成包 → 包没加载成功，或 `detect` 判错了 |
| 前缀是**固定彩虹** | 魔法色没生效，插件走了逐消息相位那条路径 → 看 `/cp pack` 的最终判定 |
| 前缀**在原地滚动** | 成功 |
| 前缀**根本没变** | 这个前缀没开 `--rainbow`，也没写 `<rainbow>`，本来就不是彩虹 |

## 从源码构建

只需要 JDK，不需要 Maven/Gradle。

```powershell
cd 26.3
.\build.ps1          # 产物：dist\ChatPrefix-1.4.0-26.3.jar
```

`build.ps1` 用 JDK 25 编译（`--release 25`），把 `build/classes` 与 `resources/*` 打进 jar。
26.3 的 paper-api 是 class 版本 69，**javac 21 读不了**，必须用 25 以上的 JDK。
第三方 jar 放在 `26.3/lib/`，**不会**被打进插件：

| jar | 用途 |
| --- | --- |
| `paper-api-26.3.build.241-stable.jar` | 服务端 API（仅编译期） |
| `adventure-api-5.2.0.jar`、`adventure-key-5.2.0.jar` | 文本组件 |
| `adventure-text-minimessage-5.2.0.jar` | MiniMessage 解析 |
| `adventure-text-serializer-plain-5.2.0.jar` | 纯文本输出，离线测试用 |
| `bungeecord-chat-1.21.jar` | API 的传递依赖（26.3 已不再依赖 examination-api） |
| `guava-33.6.0-jre.jar`、`failureaccess-1.0.2.jar` | 离线读取 `config.yml` 需要 |

从 `https://repo.papermc.io/repository/maven-public/` 下载（paper-api 从 26.x 起改用带构建号的版本号，
路径是 `io/papermc/paper/paper-api/26.3.build.241-stable/`，不再有 `-R0.1-SNAPSHOT`）。

## 测试

```powershell
cd tests
.\run-tests.ps1      # 100 条离线断言，不需要服务器
.\smoke-test.ps1     # 在 .testserver\263 起真实 Paper 26.3，用控制台喂指令验证（默认不跑）
```

`run-tests.ps1` 用 `ChatFormatter` 渲染聊天行，断言精确的纯文本与颜色，覆盖：有/无前缀、三种维度、
自定义维度、`&` 与 MiniMessage 解析、彩虹开关（`--rainbow`）、方括号跟随文字颜色、彩虹相位流动、
资源包魔法色分支、颜色优先级、自定义模板、长度计算、CraftEngine 回退行为，以及「玩家消息与玩家名
不被解析」。

`smoke-test.ps1` 首次运行会下载 Paper 26.3 服务端 jar（若本机已有缓存的原版 jar 会自动复用），
装上插件并启动服务器，再从控制台日志核对：插件是否启用、`/cp list|get|remove|set|reload` 是否符合预期、
`config.yml` 与 `prefixes.yml` 是否按预期生成与写回。按约定默认不跑，需要时手动执行。

### 验证状态（2026-11-04，迁移到 26.3）

* 离线断言 **85/85 通过**（与 1.21.11 版同一套用例，改的只是依赖与目标版本；用例数量不变）。
* 编译：`javac --release 25` 对着 `paper-api-26.3.build.241-stable` **零错误**（默认 `-Xlint:-options` 下无任何输出；
  改成 `-Xlint:all` 只会多出 adventure-api 自带的 `@Contract` 类文件批注警告，与插件源码无关）。
  `getDescription()` 在 26.3 被标为过时，两处已换成 `getPluginMeta().getVersion()`。
* `plugin.yml`：`version: 1.3.0-26.3`、`api-version: '26.3'`（Paper 文档写明的合法上限就是 26.3）。
* 资源包：核心着色器取自上游 Text Effects 的 `26_3/` overlay，逐字节未改；
  `#include` 依赖全部能在包内或原版 26.3 里解析；`pack.mcmeta` 锁在 97.1。
* 尚未验证：真机进服（插件加载与指令回显）与真人看到的画面。这两步都要真实服务端与客户端，
  离线环境做不到；如需确认，手动跑 `tests\smoke-test.ps1` 并进服看一眼彩虹。
