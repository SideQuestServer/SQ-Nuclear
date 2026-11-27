# Changelog

## 0.99.6.2

Minecraft 1.21.1 · NeoForge 21.1.234

### Added
- Compendium tab **Calculator**: raw material calculator for a whole list, including vanilla items.
- Crafting terminal as wall panel.
- Ores spawn in normal veins with 2 to 4 poor ore per vein. New poor ore blocks and nuggets, also for iron, copper and gold (new chunks only).
- New large turbine 19x9x42 with data port, spinning shaft and more detail.

### Changed
- Blast furnace smelts everything that becomes ingots (ores, raw blocks, zinc, modded ores).
- Doors render from a greater distance.
- Solar: movement reworked.
- Create shaft output and SU translator: 8 SU per FE/t.
- Old turbines are dismantled on load and refunded as a crate with their materials.

### Fixed
- Lost drill rigs and distillers: dismantled machines return the crate with the correct kit.
- Storage crate: item pipes pull all item types, not just the first.
- Item pipes: items no longer get stuck in the line, pipes no longer clog up with a single item type.
- Heavy Assembler: with a recipe template set, item pipes also fill empty grid slots.
- Missing port type texts (large water, acid, high voltage).
- Blast furnace lava flows down on the sides.
- Dark shadow after old turbines were removed.

## 0.99.5-beta.1

Minecraft 1.21.1 · NeoForge 21.1.234

### Added
- **Conduit Frame**: a wall feed-through for pipes and cables with a selectable look.
- Command `/nsqteam achievements shared|individual`: team members share achievements or keep their own.
- Test build commands `/nuclearsq testbuild primary`, `extended` and `full` place a ready primary loop, the extended primary loop or the full cycle. They run on a real battery and turbine, no creative blocks.
- Compendium: new pages for the extended primary loop and the full cycle.
- Optional EMC values for ProjectE.
- The extended test build has a Cooling Tower, the full one a Cooling Tower XL.
- Ukrainian translation by zakharfsk, Simplified Chinese translation by yhk037.
- 17 repainted ore textures by superhatosh.
- KubeJS support for recipes: ingredients are visible and all custom recipe types have schemas.
- Containment: hydraulic water and feedwater from the treatment plant now have their own ports to the left of the bunker door.

### Changed
- Power generators rebalanced:
  - Solar Field 2,400 FE/t with a 1,000,000 FE buffer, Solar Panel 69 FE/t, Solar Collector 312 FE/t. Panel and collector have a small buffer, collector and field follow the sun.
  - Small Wind Turbine, Wind Turbine and Wind Power Plant four times as strong: 352, 1,760 and 7,040 FE/t.
  - Coal Generator 720 FE/t (3,200 with output cards).
  - Fuel Cell 15,000 FE/t, Gas Generator 10,000 FE/t, Radioisotope Generator (RTG) 3,600, 6,400 and 9,000 FE/t.
- Power cables move four times as much: Basic 4,096, Reinforced 32,768, Hardened 524,288, Superconducting 33,554,432 FE/t.
- Power cables fill all connected devices at the same time instead of one after another.
- Batteries charge evenly in parallel and in series. Batteries in series now charge together and only give energy until they are balanced.
- Emergency Cooling runs permanently while it has power. Boron is only added on a trigger or in manual mode. It pumps 320 instead of 80 mB/t, and so does its Containment feed-through.
- Containment feed-throughs no longer have a throughput limit.
- The Containment power port shows the real throughput instead of its own consumption.
- The Main Coolant Pump reports the real amount it moves.
- Inner Containment ports moved one field inward. Pipes no longer sink into the wall.
- Doors open away from the player who placed them.
- Plate Mill is cheaper (1 control board, 4 reactor casings, 4 steel sheets, 1 brass bearing) and finishes a run in 40 instead of 100 ticks.
- Machines are cheaper without Create: brass bearing, andesite casing, copper coil, Quantum Module, Quantum Computer, reinforced and hardened pressure chambers, Fusion parts and kits that use pressure chambers.
- The HARDCORE preset keeps the old, higher costs. NORMAL and RELAXED use the new costs. Assembler, JEI and the Compendium show the costs from the server config.
- The Compendium raw material list now stops at ingots, coal and redstone instead of showing ores.
- Cables go straight over outer block edges instead of forming a junction box.
- Cable supports go to the ceiling when it is closer than the floor (up to 8 blocks).
- Cables that run out of a wall hang from the ceiling.
- Cable supports are at least 4 blocks apart.
- The Cooling Tower evaporates much less water (one twentieth). The dryer passes dry steam from the separator straight through.
- The missing computing power hint now appears in chat instead of the action bar, so it is no longer cut off.

### Fixed
- Pipes no longer send fluid into dead-end branches.
- The Compendium fits again at GUI scale 3 and 4.
- Cables running out of a wall no longer show extra wall brackets up to the ceiling.

## 0.99.0-beta.1

Hello and welcome to SQ-Nuclear!

This is the first public release of the mod for Minecraft 1.21.1 (NeoForge). It brings fuel rod driven reactors, turbines, cooling loops, radiation, a data network and much more.

This is a beta, so expect bugs and balance changes. Please report problems on GitHub or on our Discord.

Detailed changes will be listed here from the next version on.
