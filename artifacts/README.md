# 分发产物

这个目录里是**刻意纳入版本库**的构建产物，方便直接拿链接发给玩家 / 丢进服务器。
日常构建输出仍在各自被 `.gitignore` 排除的 `dist/` 里，发布时把产物复制过来。

## 当前版本（26.3）

| 文件 | 用途 | 大小 | SHA1 |
| --- | --- | --- | --- |
| `ChatPrefix-1.4.0-26.3.jar` | 插件本体，放进服务器的 `plugins/` | 36294 B | `e5d7425b2cdaec7993241092a7d4515970c5b6e0` |
| `ChatPrefix-Rainbow-1.3.0.zip` | 彩虹前缀资源包，发给玩家 | 36895 B | `d1037d36b3f5c197a47b4657ed52b80d365a25bf` |

**这一版是 26.3 专用的。** 服务端要 Paper/Leaf 26.3 + Java 25；还在 1.21.11 上的服务器请用
v1.2.1 的 jar 与 1.2.0 的包，两者不能混用（`api-version`、字节码版本、着色器文件名都变了）。

* 插件：`api-version: '26.3'`、`version: 1.4.0-26.3`、Java 25 字节码。1.4.0 新增：
  与 CraftEngine 的可选集成 —— 前缀/维度标签里可直接写 `<image:命名空间:id[:行:列]>` 渲染成
  CE 图片图标（`/cp set Steve <image:default:icons:0:0>&c管理员`），全程反射调用 CE 公开 API，
  没装 CE 的服务器行为不变；新增 `/cp ce` 状态指令与 `craftengine` 配置节。
  既有功能口径不变：`/cp set` 的 `--rainbow` 开关、`/cp rainbow` 子命令、`/cp pack` 诊断。
* 资源包：仍是 1.3.0 的包，本版未改（26.3 重写了文本渲染管道，旧版覆盖的 `rendertype_text*`
  四个着色器已合并为 `core/text.vsh` / `core/text.fsh`，本包覆盖这两个文件（取自上游 Text Effects
  的 `26_3/` overlay），`pack.mcmeta` 锁在资源包格式 `97.1`。因为文件内容整体不同，zip 与 1.2.0
  不再字节一致，`resource-pack-sha1` 必须同步更新，否则客户端会因为哈希不符拒绝加载。
  装了 CE 的服务器上，这个包的着色器要么合并进 CE 的资源包，要么彩虹自动回落到逐消息相位（见插件 README）。

## 怎么用这两个文件

**插件**：把 jar 丢进服务端 `plugins/`，重启。启动日志里会打印版本与资源包分支状态，
首次启动会生成 `plugins/ChatPrefix/config.yml`。

**资源包**（可选，用于「真正原地流动」的彩虹前缀）：

```properties
# server.properties
resource-pack=https://github.com/kun433/ChatPrefix/releases/download/v1.4.0/ChatPrefix-Rainbow-1.3.0.zip
resource-pack-sha1=d1037d36b3f5c197a47b4657ed52b80d365a25bf
```

`resource-pack-sha1` 必须是**上面那个 SHA1 的小写十六进制**，写错客户端会拒绝加载。
想用固定的 raw 链接也可以：

```properties
resource-pack=https://raw.githubusercontent.com/kun433/ChatPrefix/v1.4.0/artifacts/ChatPrefix-Rainbow-1.3.0.zip
```
装包后，插件对装了包的玩家把它看到的前缀整段刷成 `#F8F85C`，由 `core/text` 着色器渲染成
原地滚动的彩虹；没装包的玩家不受影响，仍是逐消息相位彩虹。

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
.\build.ps1                 # -> dist\ChatPrefix-1.4.0-26.3.jar（需要 JDK 25+）

cd ..\resourcepack
.\build-pack.ps1            # -> dist\ChatPrefix-Rainbow-1.3.0.zip，并打印 sha1

cd ..
.\tests\run-tests.ps1       # 100 条离线断言
```

重新生成后如果 SHA1 变了，记得同步更新 `server.properties` 里的 `resource-pack-sha1`
和本文件里的表格。

## 注意

* 资源包覆盖 `core/text.*` 核心着色器，**与任何同样覆盖该着色器的资源包互斥**。
* 资源包 `pack.mcmeta` 的范围锁在 `min_format`/`max_format` = 97.1，只适用于 Minecraft 26.3；
  26.4 的 snapshot 已再次改动着色器，换版本必须重新从上游对应 overlay 移植。
* 资源包派生于 TheSalt_ 的 Text Effects（MIT），`../resourcepack/LICENSE-Text_Effects.txt`
  必须随包保留。
