# 分发产物

这个目录里是**刻意纳入版本库**的构建产物，方便直接拿链接发给玩家 / 丢进服务器。
日常构建输出仍在各自被 `.gitignore` 排除的 `dist/` 里，发布时把产物复制到此处。

## 当前版本：1.2.0

| 文件 | 用途 | 大小 | SHA1 |
| --- | --- | --- | --- |
| `ChatPrefix-1.2.0-1.21.11.jar` | 插件本体，放进服务器的 `plugins/` | 26002 B | `f3fe0bb88ad2c12b4c4f9d0367ce36df9d31722d` |
| `ChatPrefix-Rainbow-1.2.0.zip` | 彩虹前缀资源包，发给玩家 | 39245 B | `40c5896a0b8d3743304f8f5a4ed1c2cd21cfd88a` |

SHA256：

```text
ChatPrefix-1.2.0-1.21.11.jar     bb02487521c4b4f9ad819b69708577fcdcc4260dd3ed3951efb087235762900d
ChatPrefix-Rainbow-1.2.0.zip     4629486d4068eb803e831c7425bb9e124e989c6a7e5a1231402b6423c1bbd156
```

## 怎么用这两个文件

**插件**：把 jar 丢进服务端 `plugins/`，重启。首次启动会生成 `plugins/ChatPrefix/config.yml`。

**资源包**（可选，用于「真正原地流动」的彩虹前缀）：

```properties
# server.properties
resource-pack=https://raw.githubusercontent.com/kun433/ChatPrefix/v1.2.0/artifacts/ChatPrefix-Rainbow-1.2.0.zip
resource-pack-sha1=40c5896a0b8d3743304f8f5a4ed1c2cd21cfd88a
```

`resource-pack-sha1` 必须是**上面那个 SHA1 的小写十六进制**，写错客户端会拒绝加载。

装包后，插件对装了包的玩家把它看到的前缀整段刷成 `#F8F85C`，由 `rendertype_text` 着色器渲染成
原地滚动的彩虹；没装包的玩家不受影响，仍是逐消息相位彩虹。相关配置见 `pack.enabled` /
`pack.magic-color` / `pack.detect`。

## 怎么重新生成

```powershell
cd ..\1.21.11
.\build.ps1                 # -> dist\ChatPrefix-1.2.0-1.21.11.jar

cd ..\resourcepack
.\build-pack.ps1            # -> dist\ChatPrefix-Rainbow-1.2.0.zip，并打印 sha1

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
