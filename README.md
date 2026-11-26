# SQ-Nuclear

SQ-Nuclear is a large technology mod for Minecraft 1.21.1 (NeoForge) about nuclear power, from the first uranium ore
to fusion. You build multiblock plants, keep their cooling loops running, send the power over your own grid and
handle what is left over: spent fuel, radiation and contamination. If things go wrong, a reactor can melt down or
explode, and for those who want it there are nuclear bombs, from a small one to the Tsar Bomba.

The mod works on its own. Create, CC:Tweaked, Mekanism and other mods are optional and only add compatibility.

## Features

- **Fuel cycle:** uranium and other ores, ore mill, leaching, UF6 and enrichment lines, fuel rods, spent fuel,
  reprocessing and storage in a large Castor cask.
- **Reactors and power:** fuel rod drives as reactor cores, cooling loops with pumps and coolants, steam turbines with
  shaft couplings, cooling towers, heavy water, solar modules and collectors, fusion as the last stage.
- **Power grid:** cables, transformers, poles and overhead lines, large batteries with charge thresholds, grid
  isolators.
- **Radiation:** dose and contamination for players and the world, shielding blocks, hazmat suits, Geiger counter,
  decontamination showers and docks, fallout after explosions.
- **Explosions:** reactor meltdowns and explosions, three nuclear bombs with crater, blast wave, ash, fallout and
  mushroom cloud. All yields and limits are set in the server config, and bombs can be turned off completely.
- **Assemblers and crafting:** multiblock assemblers with their own recipe types, a particle assembler, machine kits
  that place a whole plant and give the kit back when it is taken down.
- **Data network:** data cables, consoles, terminals, instruments, screen walls, control desks and sensors to watch
  and control your plants.
- **Equipment:** exoskeleton with modules, nano bot swarms that mine, harvest and build, robo pets, tools.
- **Compendium:** an in-game guide with a progression tree that shows every plant, its recipes and what comes next.
- **Multiplayer:** machine ownership and access rights (`/nsqaccess`, `/nsqteam`), titles (`/nsqtitle`), a player
  exchange and difficulty presets.

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.234 or newer |
| GeckoLib | 4.9.3 or newer (required) |

Optional: Create 6.0.x, CC:Tweaked, Mekanism 10.7+, Create: Power Grid, Sable / Create Aeronautics, JEI, Jade.
Without them every machine still works; with them SQ-Nuclear adds recipes, peripherals or physics for them.
Sable Companion (MIT) is bundled inside the jar.

## Running on a server

1. Install NeoForge 21.1.234 or newer for Minecraft 1.21.1 on the server.
2. Put `nuclearsq-<version>.jar` and GeckoLib into the server's `mods` folder.
3. Every player needs the **same** SQ-Nuclear version and GeckoLib on the client. The mod is required on both sides.
4. Start the server once. The settings are created in `config/nuclearsq-server.toml`
   and sent to the clients. Change them while the server is stopped.
5. Optional mods (Create, CC:Tweaked, Mekanism, ...) must be installed on the server and on every client if you use
   them.

Settings worth checking before you open the server:

- `bomb` section: turn nuclear bombs off or lower their yield.
- `explosion` section: whether explosions remove blocks, how many crater blocks are removed per tick, and how many
  chunks one explosion may load. Lower these values on weaker servers.
- `difficulty` section: preset `RELAXED`, `NORMAL` or `HARDCORE` and a factor for radiation uptake.
- `assembler` section: how many chunks an assembler may keep loaded.
- `nanobots` section: range and speed of the nano bot swarms.

Render distances, shader shadows and the look of terminals are set per player in the client config
(`config/nuclearsq-client.toml`).

## Building from source

You need Java 21.

```
./gradlew build
```

The jar ends up in `build/libs/`. `./gradlew runClient` starts a development client, and
`./gradlew runGameTestServer -Pwith_sable=false` runs the game tests.

## Use of AI

This mod is developed with the assistance of Claude.ai.
AI was/will be used in the following areas:

- Code debugging / Error analysis and correction for java and json files
- Learning aid! understanding blockbench model structures and minecraft modding
- Development / quickly testing and adapting ideas through feedback

My opinion, AI is not bad as long as you use it the right way.
AI is a tool, not the creator.
All designs, decisions, features come from me.
Development remains a manual, creative process where AI helps overcome hurdles faster and supports learning.

## Credits and licenses

- SQ-Nuclear by SideQuestChris, released under the MIT License (see `LICENSE`).
- Ore block textures by superhatosh, used with permission under the MIT License.
- Translations: Ukrainian by zakharfsk, Simplified Chinese by yhk037, Hungarian by N. Kristóf.
- RTX Remix compatibility by LithicDev, contributed under the MIT License.
- Compendium examples page, 3D view with port markers and compendium hotkeys by zakharfsk, contributed under the MIT License.
- Developed with the assistance of Claude.ai by Anthropic (see "Use of AI").
- [GeckoLib](https://github.com/bernie-g/geckolib) (MIT) for animated machine models.
- Sable Companion by RyanHCode and Ocelot (MIT), bundled in the jar with its license.
- Parts of the build setup come from the NeoForged MDK (MIT, see `TEMPLATE_LICENSE.txt`).

Source code: [github.com/SideQuestServer/SQ-Nuclear](https://github.com/SideQuestServer/SQ-Nuclear). Bug reports and questions: [Discord](https://discord.gg/9DUcANyAvV).
