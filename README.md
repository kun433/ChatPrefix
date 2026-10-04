# ChatPrefix v1.0.0 — Chat Prefix and Dimension Tag for Paper 1.21.11

[English](README.md) | [中文](README.zh-CN.md)

Puts an operator-assigned prefix in front of a player's name in chat, followed by the dimension the
player is in.

```text
[本服][末地]aaa_ovo » 第二个末地城
[大佬][主世界]Steve » hello
[下界]Alex » where is everyone
```

* The prefix (`[本服]`) is assigned per player by an operator with a command, and is stored in
  `plugins/ChatPrefix/prefixes.yml`.
* The dimension tag (`[末地]` / `[下界]` / `[主世界]`) is added for **every** player, always.
* A player with no prefix simply shows no prefix segment — nothing is printed in its place.

## Requirements

| Item | Value |
| --- | --- |
| Server | Paper 1.21.11 (Paper API, not Spigot/Bukkit-only) |
| Java | 21 or newer |
| Plugin version | `1.0.0-1.21.11` |

The plugin is built against `paper-api-1.21.11` and uses `io.papermc.paper.event.player.AsyncChatEvent`
plus Adventure components. It does not use NMS, so it keeps working across Paper 1.21.x patch
releases. There is deliberately no dependency on PlaceholderAPI or any other plugin.

## Install

1. Drop `ChatPrefix-1.0.0-1.21.11.jar` into the server's `plugins/` folder.
2. Restart the server (or `/reload confirm`). `plugins/ChatPrefix/config.yml` and
   `prefixes.yml` are created on first start.
3. Give an operator the prefix: `/chatprefix set aaa_ovo 本服`.

> Do not use a chat-formatting plugin (or a scoreboard team format) on top of this one without
> checking the result: the last plugin that sets the chat renderer wins, and a server-side
> `format` in `paper-global.yml`/another chat plugin can override this plugin's line.

## Commands

Everything is under one command, `chatprefix` (alias `cp`), and requires the
`chatprefix.admin` permission, which defaults to op.

| Command | What it does |
| --- | --- |
| `/cp set <player> <text>` | Sets the player's prefix. Type `本服` and chat shows `[本服]`. |
| `/cp remove <player>` | Clears the prefix; the player then shows no prefix segment. |
| `/cp get <player>` | Shows the stored prefix, rendered as it appears in chat. |
| `/cp list` | Lists every stored prefix (up to 100 lines). |
| `/cp reload` | Reloads `config.yml` and `prefixes.yml` without restarting the server. |
| `/cp help` | Shows the usage summary. |

The player argument accepts an online player, or an offline player the server still has cached
(someone who has joined at least once). Names are also tab-completed, including players that only
exist in `prefixes.yml`.

### Prefix text syntax

`/cp set` takes the text **without** the surrounding brackets: the brackets come from
`prefix.wrapper` in the config, which defaults to `[{text}]`. So:

| You type | Chat shows |
| --- | --- |
| `/cp set Steve 本服` | `[本服]` |
| `/cp set Steve &c大佬` | a red `[大佬]` |
| `/cp set Steve <gradient:#ff0000:#0000ff>VIP` | a gradient `[VIP]` |
| `/cp set Steve [夜猫子]` | `[[夜猫子]]` — brackets from the text plus the wrapper |

Both styles of color codes work:

* legacy `&` codes: `&c` red, `&l` bold, `&r` reset. `&&` prints a literal `&`.
* MiniMessage tags: `<red>`, `<bold>`, `<gradient:#ff0000:#0000ff>`, also `<#ff8800>` hex.

Set `prefix.allow-minimessage: false` if you want `<` and `>` to stay literal characters.
Prefix length is limited by `prefix.max-length` (24 visible characters by default, counting
characters after colour codes are stripped; `0` disables the limit).

## Configuration

`plugins/ChatPrefix/config.yml`:

```yaml
# The whole chat line. Placeholders: {prefix} {dimension} {name} {separator} {message}
format: "{prefix}{dimension}{name}{separator}{message}"

prefix:
  default: ""            # shown when a player has no prefix; "" = print nothing at all
  wrapper: "[{text}]"    # {text} is what the operator typed
  color: ""              # default colour for the prefix; "" = no extra colouring
  allow-minimessage: true
  max-length: 24         # visible characters, 0 = unlimited

dimension:
  wrapper: "[{label}]"
  labels:
    OVERWORLD: "主世界"
    NETHER: "下界"
    THE_END: "末地"
  custom-label: "{world}"   # datapack/modded dimensions; {world} is the world name
  colors:
    OVERWORLD: "green"
    NETHER: "red"
    THE_END: "light_purple"
    CUSTOM: "yellow"

name:
  use-display-name: false   # true = use the server display name (nick plugins, team prefix)
  color: ""                 # "" = leave the name colour alone (keeps team colours)

separator:
  text: " » "
  color: "gray"
```

Notes:

* A colour value may be a named colour (`gray`, `red`, `gold`, `light_purple`, …) or `#rrggbb`.
* `prefix.color` / `name.color` only fill in parts that have **no** colour of their own, so a prefix
  written as `&c大佬` stays red even if `prefix.color` is `gold`.
* `name.color` is empty by default on purpose: colouring the name would override scoreboard team
  colours.
* `{prefix}` with `prefix.default: ""` produces nothing — that is how "no prefix, show nothing"
  is expressed. Setting `prefix.default: "滚木"` would print `[滚木]` for players without a prefix.
* Player names and chat messages are never parsed as MiniMessage, so a player cannot inject
  formatting or click events through chat. Only config values and operator-typed prefixes are parsed.
* If `prefix.wrapper` is missing its `{text}` placeholder, the prefix text is printed unwrapped
  instead of being silently dropped.

## Build from source

Only a JDK is needed — there is no Maven/Gradle build.

```powershell
cd 1.21.11
.\build.ps1          # -> dist\ChatPrefix-1.0.0-1.21.11.jar
```

`build.ps1` compiles with JDK 21 (`--release 21`) and packs `build/classes` plus `resources/*`
into the jar. Third-party jars are expected in `1.21.11/lib/` and are **not** bundled:

| Jar | Why |
| --- | --- |
| `paper-api-1.21.11.jar` | server API (compile only) |
| `adventure-api-4.26.1.jar`, `adventure-key-4.26.1.jar` | text components |
| `adventure-text-minimessage-4.26.1.jar` | MiniMessage parsing |
| `adventure-text-serializer-plain-4.26.1.jar` | plain-text output, used by the offline test |
| `examination-api-1.3.0.jar`, `bungeecord-chat-1.21.jar` | transitive deps of the API |
| `guava-33.3.1-jre.jar`, `failureaccess-1.0.2.jar` | needed on the classpath to read `config.yml` offline |

Download them from `https://repo.papermc.io/repository/maven-public/` (paper-api snapshots live
under `io/papermc/paper/paper-api/1.21.11-R0.1-SNAPSHOT/`; pick the newest timestamped jar).

## Tests

```powershell
cd tests
.\run-tests.ps1      # 39 offline assertions, no server needed
.\smoke-test.ps1     # boots a real Paper 1.21.11 in .testserver\12111 and drives it via console
```

`run-tests.ps1` renders chat lines with `ChatFormatter` and asserts the exact plain text and colours
for: prefix set/unset, all three dimensions, custom dimensions, `&`/MiniMessage parsing, colour
precedence, custom templates, length accounting, and the guarantee that player messages and player
names are not parsed.

`smoke-test.ps1` downloads the Paper 1.21.11 server jar on first run (it reuses an already cached
vanilla jar if one is present), installs the plugin, starts the server, and checks from the console
log that the plugin enabled, that `/cp list|get|remove|set|reload` behave as expected, and that
`config.yml` / `prefixes.yml` were created and written back correctly.

### Verification status

Verified on 2026-10-04:

* 36/39 offline assertions pass.
* Real Paper 1.21.11 (build 132) + Java 21: plugin loads with `api-version: '1.21'`, enables, all
  commands respond, and `prefixes.yml` round-trips UTF-8 Chinese prefixes and `&` colour codes.
* Not yet verified: how a real player's chat line looks in a client. That needs a human to join and
  type in chat — a console-driven smoke test cannot produce a real player message.
