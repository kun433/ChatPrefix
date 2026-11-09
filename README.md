# ChatPrefix v1.4.0 — Chat Prefix and Dimension Tag for Paper 26.3

[English](README.md) | [中文](README.zh-CN.md)

Puts an operator-assigned prefix in front of a player's name in chat, followed by the dimension the
player is in.

```text
[末地]Aleax » hello
[大佬][主世界]Steve » hello
```

* The prefix (`[本服]`) is assigned per player by an operator with a command, and is stored in
  `plugins/ChatPrefix/prefixes.yml`.
* The dimension tag (`[末地]` / `[下界]` / `[主世界]`) is added for **every** player, always.
* A player with no prefix simply shows no prefix segment — nothing is printed in its place.

## Requirements

| Item | Value |
| --- | --- |
| Server | Paper 26.3 (Paper API, not Spigot/Bukkit-only); Leaf 26.3 works too |
| Java | 25 or newer (26.3 servers require Java 25) |
| Plugin version | `1.4.0-26.3` |

The plugin is built against `paper-api-26.3` (build 241) and uses
`io.papermc.paper.event.player.AsyncChatEvent` plus Adventure 5.2.0 components. It does not use NMS,
so it keeps working across Paper 26.x patch releases. There is deliberately no dependency on
PlaceholderAPI. The only optional integration is CraftEngine (see [below](#craftengine-integration-image-prefixes-optional)),
connected purely through reflection with a `softdepend` entry — on a server without CE nothing changes.

> This is the 26.3 build. Servers still on 1.21.11 should keep the v1.2.1 jar; the two are not
> interchangeable (`api-version` and the bytecode level both differ).

## Install

1. Drop `ChatPrefix-1.4.0-26.3.jar` into the server's `plugins/` folder.
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
| `/cp set <player> [--rainbow] <text>` | Sets the player's prefix. Type `本服` and chat shows `[本服]`; add `--rainbow` for a rainbow prefix. |
| `/cp rainbow <player> on\|off` | Toggles the rainbow without touching the prefix text. |
| `/cp remove <player>` | Clears the prefix; the player then shows no prefix segment. |
| `/cp get <player>` | Shows the stored prefix (and its rainbow switch), rendered as it appears in chat. |
| `/cp list` | Lists every stored prefix (up to 100 lines); rainbow entries are marked `（彩虹）`. |
| `/cp pack` | Prints your own resource-pack detection details (run this first when the rainbow does nothing). |
| `/cp ce` | Prints the CraftEngine integration status (run this when an `<image:...>` prefix shows nothing). |
| `/cp reload` | Reloads `config.yml` and `prefixes.yml` without restarting the server. |
| `/cp help` | Shows the usage summary. |

The player argument accepts an online player, or an offline player the server still has cached
(someone who has joined at least once). Names are also tab-completed, including players that only
exist in `prefixes.yml`.

### The rainbow switch (`--rainbow`)

The short form: add one flag to `/cp set` and the prefix text needs no tags at all.

```text
/cp set Steve 炒鸡大牛 --rainbow      ->  [炒鸡大牛], a rainbow that keeps changing colour
/cp set Steve --rainbow 炒鸡大牛      ->  same, flag right after the player name
/cp set Steve 炒鸡大牛 彩虹           ->  same, the Chinese keyword works too
/cp rainbow Steve off                ->  turn the rainbow off, text unchanged
```

Rules:

* The flag only means "render this prefix as a rainbow"; it **never edits the prefix text**, so
  `/cp rainbow` or another `set` can flip it at any time.
* It is stored as `rainbow: true` in `prefixes.yml` and survives restarts and reloads.
* If the text already contains `<rainbow>` or `<rainbow:2>`, the text wins — a hand-written fixed
  phase is never rewritten by the switch.
* With `prefix.allow-minimessage: false` angle brackets are literal characters, so the switch cannot
  work (`/cp set` tells you right away).
* Which animation path a flagged prefix takes depends on the pack: with the pack, the client scrolls
  the rainbow in place; without it, the phase advances once per message. Before this switch, a
  rainbow required writing `<rainbow>` yourself — that is why the pack used to change nothing.

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
  Multi-colour tags work too: `<rainbow>` (one hue per character), `<rainbow:2>` (phase shift),
  `<rainbow:!>` (reversed), `<pride>` and `<gradient:...>`.

The wrapper brackets are coloured as well (this is what happens when `prefix.color` is empty):

* The text carries a colour → the brackets follow it: the opening bracket takes the text's **first**
  colour and the closing bracket its **last** colour. So `/cp set Steve &c大佬` renders a fully red
  `[大佬]` instead of white brackets around a red word, and `&cRED&fWHITE` renders `[REDWHITE]`
  with each bracket matching its neighbour.
* The text has no colour at all → the brackets stay exactly as they were.
* `prefix.color` is set → the brackets and any uncoloured text take that colour; leaving
  `prefix.color` empty is what enables the "follow the text" rule above.

Set `prefix.allow-minimessage: false` if you want `<` and `>` to stay literal characters.
Prefix length is limited by `prefix.max-length` (24 visible characters by default, counting
characters after colour codes are stripped; `0` disables the limit).

#### Rainbow and the other multi-colour tags

MiniMessage's own multi-colour tags work out of the box (the colours below were measured, not
interpolated):

| You type | Chat shows |
| --- | --- |
| `<rainbow>管理员` | one hue per character: 管 `#FF0000`, 理 `#7FFF00`, 员 `#00FFFF` |
| `<rainbow:2>管理员` | the same rainbow, phase-shifted |
| `<rainbow:!>管理员` | reversed rainbow |
| `<gradient:#ffb300:#ff3d00>管理员` | two-colour linear gradient |
| `<pride>管理员` | pride flag colours |

Two hard limits, both measured against the bundled MiniMessage version:

* The `N` in `<rainbow:N>` **must be an integer from 0 to 10** (1 unit = 36°, 10 = a full turn).
  `<rainbow:0.5>` silently fails and the client prints the literal text `<rainbow:0.5>`; a negative
  value throws.
* **A chat line is frozen the moment it is sent — no plugin can recolour it in place.** So real
  animation can only come from advancing the phase per message: every new message rotates the
  rainbow by one step.

With `prefix.rainbow.animate: true` (the default) the plugin rewrites a bare `<rainbow>` in the
prefix into `<rainbow:N>` using the current phase, so each message starts at a different hue;
`speed` sets how many steps advance per second (10 steps = one full turn). Tags that carry an
argument, such as `<rainbow:2>`, are left alone and stay at a fixed phase.

## Configuration

`plugins/ChatPrefix/config.yml`:

```yaml
# The whole chat line. Placeholders: {prefix} {dimension} {name} {separator} {message}
format: "{prefix}{dimension}{name}{separator}{message}"

prefix:
  default: ""            # shown when a player has no prefix; "" = print nothing at all
  wrapper: "[{text}]"    # {text} is what the operator typed
  color: ""              # default colour for the prefix; "" = brackets follow the text colour
  rainbow:
    animate: true        # let a bare <rainbow> in the prefix drift with time (phase changes per message)
    speed: 1.0           # phase steps per second; 10 steps = one full turn
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

# Resource-pack side: a genuinely in-place rainbow; see the "Resource pack" section below.
# Turn it off to always use the per-message phase drift instead.
pack:
  enabled: true
  magic-color: "#F8F85C"   # must match the TEXT_EFFECT trigger colour in the pack's _config.glsl
  detect: auto             # auto = events + API; always = assume everyone has it; never = assume nobody

# CraftEngine integration: use CE's custom images as chat icons. See the section below.
craftengine:
  mode: auto            # auto = enable when CE is detected; on = always; off = never
  missing: hide         # hide = drop unrenderable <image:...> tags; literal = keep the raw text
  shader-merged: false  # true only if you merged ChatPrefix's rainbow shader into the server's resource pack
```

Notes:

* A colour value may be a named colour (`gray`, `red`, `gold`, `light_purple`, …) or `#rrggbb`.
* `prefix.color` / `name.color` only fill in parts that have **no** colour of their own. A prefix
  written as `&c大佬` is always red: with `prefix.color` empty the brackets turn red too, while
  `prefix.color: gold` paints the brackets gold (explicit config wins).
* `name.color` is empty by default on purpose: colouring the name would override scoreboard team
  colours.
* `{prefix}` with `prefix.default: ""` produces nothing — that is how "no prefix, show nothing"
  is expressed. Setting `prefix.default: "滚木"` would print `[滚木]` for players without a prefix.
* Player names and chat messages are never parsed as MiniMessage, so a player cannot inject
  formatting or click events through chat. Only config values and operator-typed prefixes are parsed.
* If `prefix.wrapper` is missing its `{text}` placeholder, the prefix text is printed unwrapped
  instead of being silently dropped.

## CraftEngine integration: image prefixes (optional)

If the server also runs CraftEngine (CE), prefixes can use CE's custom images as colourful chat
icons. ChatPrefix talks to CE's public API through reflection: there is no compile-time or hard
runtime dependency, and on a server without CE everything behaves exactly as before.

Write an image reference anywhere ChatPrefix parses text — the prefix, `prefix.default`, dimension
labels, the separator, and literal text inside `format`:

| You type | Chat shows |
| --- | --- |
| `/cp set Steve <image:default:icons:0:0>&c管理员` | the cell (0, 0) of CE's image `default:icons`, then a red 管理员 |
| `/cp set Steve <image:default:crown>&6&l服主` | the single-cell image `default:crown`, then a bold gold 服主 |
| `/cp set Steve <image:default:icons>&c管理员` | the first cell of the sprite, then a red 管理员 |

The id syntax is the same as CE's own `<image:...>` tag (`namespace:id[:row[:column]]`), so whatever
images you have already defined for CE can be reused directly. Rendering happens inside ChatPrefix
itself (a call to `CraftEngineImages.byId(...)`), and each player needs CE's resource pack — which CE
already sends automatically, so on a CE server this needs no extra setup.

Tags ChatPrefix cannot render fall back per `craftengine.missing`:

* `hide` (default) — the tag is removed, so an unreachable tag never appears as raw `<image:...>` text.
* `literal` — the raw text stays; when CE is installed, its network-layer interception (system chat,
  enabled by default) may still replace it there.

`hide`/`literal` also apply when CE is installed but the image id does not exist, and to complex tags
with a format parameter (e.g. `<image:default:icons:0:0:'<!shadow>'>`); the prefix length limit counts
a renderable image as one visible character.

Notes:

* `/cp ce` prints the integration status: whether CE was detected, `mode`, `missing`, and whether the
  reflection channel is up. Run it when an `<image:...>` prefix shows nothing.
* Other CE network tags (`<shift:N>`, `<global:...>`, `<papi:...>`) are simply passed through as text;
  whether CE replaces them is decided by CE's packet interception, not by this plugin.
* `craftengine.mode: off` disables the whole integration; the tag handling then follows `missing` even
  if CE is installed.

### Rainbow prefixes on a CE server: let CE self-host the shader

CE occupies the server's resource-pack slot, and ChatPrefix's rainbow shader is not part of it. With
the integration active (mode `auto`/`on`) and `shader-merged: false`, the plugin skips the
magic-colour shader branch automatically: rainbow prefixes keep using the per-message phase drift,
which needs no resource pack at all.

To get the in-place rainbow on a CE server, hand the shaders to CE instead of shipping a separate zip:

1. Copy the whole `resourcepack/dist/ce-resources/chatprefix-rainbow/` folder into the server's
   `plugins/CraftEngine/resources/` directory (`artifacts/ce-resources/` holds the same folder, and
   the release ships it as `ChatPrefix-Rainbow-1.3.0-CE.zip`; all three are identical);
2. Run `/ce reload all` (or restart); CE merges the shaders into the pack it generates and pushes
   automatically;
3. Set `craftengine.shader-merged: true` to bring the magic-colour path back.

No CE-side configuration is needed (`exclude-core-shaders` is false by default, so the shaders are
kept), and players install nothing. Details are in the "Resource pack" section below.

## Resource pack: a genuinely animated rainbow (optional)

A chat line is frozen the moment the client receives it, so no plugin can recolour it in place —
which means the "flow" you get from `prefix.rainbow` is really the phase advancing once per message.
Real in-place animation needs the client's core shaders to take part.

`resourcepack/` holds a trimmed pack derived from TheSalt_'s Text Effects (MIT). It overrides the
`core/text.vsh` / `core/text.fsh` core shader and renders the colour `#F8F85C` as a continuously
scrolling rainbow.

> 26.3 rewrote the text rendering pipeline: the four old `rendertype_text*` shaders collapsed into a
> single `core/text.*` pair, now using `#include <minecraft:...>` and explicit `layout(location)`.
> This pack is therefore **26.3 only** — an old pack on 26.3 does not error out, it simply stops
> working, because the files it overrides no longer exist.

```powershell
cd resourcepack
.\build-pack.ps1     # -> dist\ChatPrefix-Rainbow-1.3.0.zip, and prints the sha1;
                     # also emits dist\ce-resources\chatprefix-rainbow\ (the CE self-hosted folder)
```

### On a server with CraftEngine: let CE self-host it (recommended, no zip needed)

CE merges everything under `resources/<pack>/resourcepack/` into the resource pack it generates and
hosts itself — shaders included, verbatim (`exclude-core-shaders` is false by default). So:

1. Copy the whole `resourcepack/dist/ce-resources/chatprefix-rainbow/` folder into the server's
   `plugins/CraftEngine/resources/` directory.
2. Run `/ce reload all` (or restart); CE rebuilds its resource pack and pushes it to players
   automatically.
3. Set `craftengine.shader-merged: true` in the plugin config so rainbow prefixes take the
   magic-colour branch again.

After that the `server.properties` pack URL and sha1 are no longer needed (CE takes over), and
players don't install anything themselves. The folder is byte-identical to the zip shaders (same
sources, same build script); it just drops `pack.mcmeta` (CE writes its own) and adds CE's
`pack.yml`. Note: if you also run the full Text_Effects pack, both provide `include/_config.glsl`
and will collide — keep one of them.

### Without CE: the classic zip route

The 1.3.0 pack is **no longer byte-identical** to 1.2.0, so upgraded servers must redistribute it
(and update `resource-pack-sha1`):

* The trigger now uses `TEXT_EFFECT_WITH_SHADOW`. Chat lines are drawn with a shadow; matching only
  the main colour left a dark yellow shadow around the rainbow.
* The rainbow branch in `apply_effect.glsl` scales the shadow layer by 0.25; otherwise the shadow
  would be as bright as the text and look like a ghost image.

How it fits together:

1. Deliver the zip to players (via `server.properties` `resource-pack=` plus `resource-pack-sha1=`,
   or Paper's resource-pack settings).
2. For every player who has the pack, the plugin repaints the whole prefix they see with `#F8F85C`
   and the shader turns it into an in-place scrolling rainbow. **Players without the pack are
   unaffected** and keep the per-message phase rainbow.
3. Only a bare `<rainbow>` or a `--rainbow` prefix takes this branch. A tag with an explicit phase
   such as `<rainbow:2>` means "a fixed rainbow", consistently in both paths.

Whether a player has the pack is decided by `pack.detect`: `auto` (the default — it first looks at
the resource-pack status events the client reports, then falls back to the Bukkit/Paper API),
`always` (assume everyone has it — useful when the server forces the pack, or while you are testing
by hand), or `never` (assume nobody does).

Caveats:

* The pack overrides `core/text.*`, so it is **mutually exclusive with any other pack that
  overrides that shader**.
* `pack.mcmeta` pins `min_format`/`max_format` to 97.1, i.e. Minecraft 26.3 (resource pack formats
  became major.minor from 1.21.9 on).
* Licensed MIT; `resourcepack/LICENSE-Text_Effects.txt` must ship with the pack. See
  `resourcepack/CREDITS.md` for exactly what was trimmed.
* Static checks done: the core shaders are byte-identical to upstream's 26.3 version, and every
  `#include` resolves either inside the pack or from vanilla 26.3. Two changes sit on top of
  upstream (`_config.glsl` keeps a single mapping, and the rainbow branch scales the shadow layer
  by 0.25). What a real player actually sees still needs somebody to join and look; offline tests
  cannot cover that.

### Pack installed but the rainbow still does not move? Run `/cp pack`

Two easy traps sit on that path:

1. **The server must run plugin 1.3.0 or newer.** The `--rainbow` switch is new in 1.3.0, and the
   pack detection itself is only reliable from 1.2.1 on (it also records the resource-pack status
   events the client reports). If `plugins/ChatPrefix/config.yml` has no `pack:` section at all,
   the server is running an old jar.
2. **A pack installed by hand on the client is invisible to the server.** If you just drop the zip
   into the client's `resourcepacks` folder, nothing tells the server about it, so `auto` will
   always say "not installed" — set `pack.detect: always` for that case.

`/cp pack` prints every step: plugin version, the three `pack.*` settings, the last resource-pack
status event, `hasResourcePack()`, `getResourcePackStatus()`, the final verdict, and your own raw
prefix text.

What you see in chat tells you which problem it is:

| In chat you see | Meaning |
| --- | --- |
| A **solid** yellow-green prefix (`#F8F85C`) | The magic colour is applied but the client has no pack → the pack failed to load, or detection got it wrong |
| A **static** rainbow prefix | The magic colour was not applied; the plugin took the per-message phase path → read the verdict from `/cp pack` |
| A prefix that **scrolls in place** | Working |

## Build from source

Only a JDK is needed — there is no Maven/Gradle build.

```powershell
cd 26.3
.\build.ps1          # -> dist\ChatPrefix-1.4.0-26.3.jar
```

`build.ps1` compiles with JDK 25 (`--release 25`) and packs `build/classes` plus `resources/*`
into the jar. The 26.3 paper-api is class file version 69, so **javac 21 cannot read it** — JDK 25
or newer is required. Third-party jars are expected in `26.3/lib/` and are **not** bundled:

| Jar | Why |
| --- | --- |
| `paper-api-26.3.build.241-stable.jar` | server API (compile only) |
| `adventure-api-5.2.0.jar`, `adventure-key-5.2.0.jar` | text components |
| `adventure-text-minimessage-5.2.0.jar` | MiniMessage parsing |
| `adventure-text-serializer-plain-5.2.0.jar` | plain-text output, used by the offline test |
| `bungeecord-chat-1.21.jar` | transitive dep of the API (26.3 no longer needs examination-api) |
| `guava-33.6.0-jre.jar`, `failureaccess-1.0.2.jar` | needed on the classpath to read `config.yml` offline |

Download them from `https://repo.papermc.io/repository/maven-public/`. From 26.x on, paper-api uses
build-numbered versions instead of `-R0.1-SNAPSHOT`, so the path is
`io/papermc/paper/paper-api/26.3.build.241-stable/`.

## Tests

```powershell
cd tests
.\run-tests.ps1      # 100 offline assertions, no server needed
.\smoke-test.ps1     # boots a real Paper 26.3 in .testserver\263 and drives it via console (opt-in)
```

`run-tests.ps1` renders chat lines with `ChatFormatter` and asserts the exact plain text and colours
for: prefix set/unset, all three dimensions, custom dimensions, `&`/MiniMessage parsing, the
`--rainbow` switch, bracket colour following the text, the rainbow phase drift, the resource-pack
magic-colour branch, colour precedence, custom templates, length accounting, the CraftEngine
fallback behaviour, and the guarantee that player messages and player names are not parsed.

`smoke-test.ps1` downloads the Paper 26.3 server jar on first run (it reuses an already cached
vanilla jar if one is present), installs the plugin, starts the server, and checks from the console
log that the plugin enabled, that `/cp list|get|remove|set|reload` behave as expected, and that
`config.yml` / `prefixes.yml` were created and written back correctly. It is opt-in and not run by
default.

### Verification status (2026-11-04, 26.3 migration)

* 85/85 offline assertions pass — the same suite as the 1.21.11 build; only the dependencies and the
  target version changed, the number of cases did not.
* Compiles with `javac --release 25` against `paper-api-26.3.build.241-stable` with **no errors**
  (silent under the default `-Xlint:-options`; `-Xlint:all` only adds classfile-annotation warnings
  from adventure-api's own `@Contract`, unrelated to this source). `getDescription()` is deprecated
  in 26.3, so both call sites now use `getPluginMeta().getVersion()`.
* `plugin.yml`: `version: 1.3.0-26.3`, `api-version: '26.3'` (Paper documents 26.3 as the highest
  valid value).
* Resource pack: core shaders are byte-identical to upstream Text Effects' `26_3/` overlay, every
  `#include` resolves against the pack or vanilla 26.3, and `pack.mcmeta` is pinned to 97.1.
* Not yet verified: a real server boot (plugin load and command replies) and what a player actually
  sees. Both need a live server and client; run `tests\smoke-test.ps1` and join the game if you want
  that confirmation.
