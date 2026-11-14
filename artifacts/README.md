# 分发产物

这个目录里是**刻意纳入版本库**的构建产物，方便直接拿链接发给玩家 / 丢进服务器。
日常构建输出仍在各自被 `.gitignore` 排除的 `dist/` 里，发布时把产物复制过来。

## 当前版本（26.3）

| 文件 | 用途 | 大小 | SHA1 |
| --- | --- | --- | --- |
| `ChatPrefix-1.5.1-26.3.jar` | 插件本体，放进服务器的 `plugins/`；v1.5.1 修复：CE 接管资源包后 `pack.detect: auto` 误判「没装包」导致彩虹只剩逐消息相位（CE 不走 Paper 的资源包 API，状态事件收不到，现在 shader-merged=true 时 auto 直接判作装了包）；同时修复 `<image:...>` 对真实 CE 的反射通道（paper-plugin 隔离类加载器 + CE 26.x shaded Component） | 54747 B | `22a6f96aa1867714a292bd25642f17a51a52989a` |
| `ce-resources/chatprefix-rainbow/` | 彩虹着色器的 **CraftEngine 自托管版**（整个文件夹复制到服务器的 `plugins/CraftEngine/resources/` 下） | 36 个文件 | 文件与下面的 zip 内容一致 |
| `ChatPrefix-Rainbow-1.3.0-CE.zip` | 上面那个文件夹的 zip 版（解压后得到 `chatprefix-rainbow/`，方便下载） | 39097 B | `ca80316b915790ac56620368ae93af0b004b6bb7` |

独立的传统资源包 zip（`ChatPrefix-Rainbow-1.3.0.zip`）已废弃并移除：装了 CE 的服务器资源包
槽位被 CE 占用，独立包发不出去；没装 CE 的服务器彩虹走逐消息相位，本来也不需要它。
资源包源码在 `../resourcepack/`，本身就是 CE 包结构（`pack.yml` + `resourcepack/assets/...`）。

**装了 CraftEngine 的服务器用文件夹方式（推荐，不再需要单独发 zip）**：把
`ce-resources/chatprefix-rainbow/` 整个文件夹复制到服务器的 `plugins/CraftEngine/resources/`
下，执行 `/ce reload all`（或重启），CE 会把这些着色器合并进它生成并自动托管的资源包；
然后把插件配置的 `craftengine.shader-merged` 改成 `true`，彩虹前缀就会走魔法色分支，
玩家看到的就是原地流动的彩虹。CE 侧无需改任何配置（`exclude-core-shaders` 默认就是 false）。

**这一版是 26.3 专用的。** 服务端要 Paper/Leaf 26.3 + Java 25；还在 1.21.11 上的服务器请用
v1.2.1 的 jar 与 1.2.0 的包，两者不能混用（`api-version`、字节码版本、着色器文件名都变了）。

* 插件：`api-version: '26.3'`、`version: 1.5.1-26.3`、Java 25 字节码。1.4.0 新增 CraftEngine 集成；1.5.0 新增称号池与 Tab 同步：
  与 CraftEngine 的可选集成 —— 前缀/维度标签里可直接写 `<image:命名空间:id[:行:列]>` 渲染成
  CE 图片图标（`/cp set Steve <image:default:icons:0:0>&c管理员`），全程反射调用 CE 公开 API，
  没装 CE 的服务器行为不变；新增 `/cp ce` 状态指令与 `craftengine` 配置节。
  既有功能口径不变：`/cp set` 的 `--rainbow` 开关、`/cp rainbow` 子命令、`/cp pack` 诊断。
* 资源包：仍是 1.3.0 的着色器，本版未改（26.3 重写了文本渲染管道，旧版覆盖的 `rendertype_text*`
  四个着色器已合并为 `core/text.vsh` / `core/text.fsh`，本包覆盖这两个文件（取自上游 Text Effects
  的 `26_3/` overlay），CE 生成的 `pack.mcmeta` 资源包格式为 `97.1`。从 1.5.0 起资源包是
  **纯 CraftEngine 自托管**：源码目录就是 CE 包结构，不再产出独立 zip。
  与 1.2.0 的着色器不再字节一致，CE 重新生成资源包后客户端会自动重新下载。

## 怎么用这些文件

**插件**：把 jar 丢进服务端 `plugins/`，重启。启动日志里会打印版本与资源包分支状态，
首次启动会生成 `plugins/ChatPrefix/config.yml`。

**资源包**（可选，用于「真正原地流动」的彩虹前缀，纯 CraftEngine 自托管）：

把 `ce-resources/chatprefix-rainbow/` 整个文件夹复制到服务器的 `plugins/CraftEngine/resources/`
下，执行 `/ce reload all`（或重启），CE 会把这些着色器合并进它生成并自动托管的资源包并自动
下发给玩家；然后把插件配置的 `craftengine.shader-merged` 改成 `true`，彩虹前缀就会走魔法色
分支，玩家看到的就是原地流动的彩虹。CE 侧无需改任何配置（`exclude-core-shaders` 默认就是
false），也不需要 `server.properties` 的资源包地址和 sha1。装了包的玩家，插件把它看到的前缀
整段刷成 `#F8F85C`，由 `core/text` 着色器渲染成原地滚动的彩虹；没装到包的玩家不受影响，
仍是逐消息相位彩虹。

**发彩虹前缀**：

```text
/cp set aaa_ovo 炒鸡大牛 --rainbow      →  [炒鸡大牛] 一直变色的彩虹
/cp rainbow aaa_ovo off                 →  关掉彩虹，文字不动
```

**排查**：进游戏执行 `/cp pack`（资源包判定细节）或 `/cp ce`（CraftEngine 集成状态），
前者会打印插件版本、三项 `pack.*` 配置、最近一次资源包状态事件、`hasResourcePack()`、
`getResourcePackStatus()`、最终判定，以及你自己前缀的原文、彩虹开关，和这条前缀会不会走魔法色；
后者打印是否检测到 CE、`craftengine.mode/missing/shader-merged` 与反射通道状态。
客户端手动装包时服务端看不到，这种情形要把 `pack.detect` 改成 `always`。

## 怎么重新生成

```powershell
cd ..\26.3
.\build.ps1                 # -> dist\ChatPrefix-1.5.0-26.3.jar（需要 JDK 25+）

cd ..\resourcepack
.\build-pack.ps1            # -> dist\ce-resources\chatprefix-rainbow\（CE 自托管文件夹）
                            # -> dist\ChatPrefix-Rainbow-1.3.0-CE.zip（上面文件夹的 zip）

cd ..
.\tests\run-tests.ps1       # 100 条离线断言
```

zip 不可字节复现（内嵌时间戳），重新打包后内容应与现有产物一致；如需替换 Release 附件，
用内容对比确认即可。

## 注意

* 资源包覆盖 `core/text.*` 核心着色器，**与任何同样覆盖该着色器的资源包互斥**。
* 资源包由 CE 生成 `pack.mcmeta`，格式 97.1，只适用于 Minecraft 26.3；
  26.4 的 snapshot 已再次改动着色器，换版本必须重新从上游对应 overlay 移植。
* 资源包派生于 TheSalt_ 的 Text Effects（MIT），`../resourcepack/LICENSE-Text_Effects.txt`
  必须随包保留。
