# 分发产物

这个目录里是**刻意纳入版本库**的构建产物，方便直接拿链接发给玩家 / 丢进服务器。
日常构建输出仍在各自被 `.gitignore` 排除的 `dist/` 里，发布时把产物复制过来。

## 当前版本

| 文件 | 用途 | 大小 | SHA1 |
| --- | --- | --- | --- |
| `ChatPrefix-1.2.1-1.21.11.jar` | 插件本体，放进服务器的 `plugins/` | 28103 B | `b8b3b75e52b6e1706201885a580206065e72bd81` |
| `ChatPrefix-Rainbow-1.2.0.zip` | 彩虹前缀资源包，发给玩家 | 39245 B | `40c5896a0b8d3743304f8f5a4ed1c2cd21cfd88a` |

版本不同步是有意的：**1.2.1 只改了插件**（资源包判定加了事件记录、新增 `/cp pack` 诊断命令），
资源包的着色器与配置一个字都没动，所以 zip 与 1.2.0 发布时**字节完全一致**，没必要重新打包。
已装过 1.2.0 资源包的玩家不用重新下载。

1.2.0 的 jar 已被 1.2.1 取代：1.2.0 的 `pack.detect: auto` 只问 API，判不出「客户端手动装包」，
用 `/cp pack` 也看不到判定过程。

## 怎么用这两个文件

**插件**：把 jar 丢进服务端 `plugins/`，重启。启动日志里会打印版本与资源包分支状态，
首次启动会生成 `plugins/ChatPrefix/config.yml`。

**资源包**（可选，用于「真正原地流动」的彩虹前缀）：

```properties
# server.properties
resource-pack=https://github.com/kun433/ChatPrefix/releases/download/v1.2.0/ChatPrefix-Rainbow-1.2.0.zip
resource-pack-sha1=40c5896a0b8d3743304f8f5a4ed1c2cd21cfd88a
```

`resource-pack-sha1` 必须是**上面那个 SHA1 的小写十六进制**，写错客户端会拒绝加载。
想用固定的 raw 链接也可以：

```properties
resource-pack=https://raw.githubusercontent.com/kun433/ChatPrefix/v1.2.0/artifacts/ChatPrefix-Rainbow-1.2.0.zip
```

装包后，插件对装了包的玩家把它看到的前缀整段刷成 `#F8F85C`，由 `rendertype_text` 着色器渲染成
原地滚动的彩虹；没装包的玩家不受影响，仍是逐消息相位彩虹。

**排查**：进游戏执行 `/cp pack`，它会打印插件版本、三项 `pack.*` 配置、最近一次资源包状态事件、
`hasResourcePack()`、`getResourcePackStatus()` 与最终判定。客户端手动装包时服务端看不到，
这种情形要把 `pack.detect` 改成 `always`。

## 怎么重新生成

```powershell
cd ..\1.21.11
.\build.ps1                 # -> dist\ChatPrefix-<版本>-1.21.11.jar

cd ..\resourcepack
.\build-pack.ps1            # -> dist\ChatPrefix-Rainbow-<版本>.zip，并打印 sha1

cd ..
.\tests\run-tests.ps1       # 70 条离线断言
```

重新生成后如果 SHA1 变了，记得同步更新 `server.properties` 里的 `resource-pack-sha1`
和本文件里的表格。

## 注意

* 资源包覆盖 `rendertype_text` 核心着色器，**与任何同样覆盖该着色器的资源包互斥**。
* 资源包 `pack.mcmeta` 的范围锁在 `min_format`/`max_format` = 57–75，即 Minecraft 1.21.6–1.21.11；
  再往后的版本需要按 `../resourcepack/CREDITS.md` 重新核对。
* 资源包派生于 TheSalt_ 的 Text Effects（MIT），`../resourcepack/LICENSE-Text_Effects.txt`
  必须随包保留。
