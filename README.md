# glowmod

a client side fabric mod that scans your render distance for blocks and highlights them with colored outlines. also adds glow effects to players and mobs. everything is configurable from a built-in gui.

---

## features

- **block outlines** — add any block to a whitelist and it'll get a colored outline whenever it's in your render distance. each block can have its own custom color
- **entity glow** — applies the glow effect to players and mobs. separate color pickers for each, plus a whitelist so you can pick exactly which mobs glow
- **sleek gui** — press `g` to open it. has four pages: glow, outlines, performance, and settings. supports multiple accent color themes and an adjustable opacity
- **performance controls** — tune chunk scan rate, outline refresh interval, glow range, and max glowing entities so the mod doesn't tank your fps

---

## how to use

1. press `g` in-game to open the gui (rebindable in the settings page)
2. go to the **outlines** tab and search for a block, then click it to add it to the whitelist
3. go to the **glow** tab to enable player/mob glow and add specific mobs
4. tweak the sliders on the **performance** tab if you notice any lag
5. everything saves automatically

---

## config

config file is at `.minecraft/config/glowmod.json`. you can edit it manually or just use the in-game gui — both work fine.

---

## requirements

- minecraft 1.21.1+
- fabric loader 0.18.0+
- fabric api

---

## building

```
./gradlew build
```

output jar is in `build/libs/`.
