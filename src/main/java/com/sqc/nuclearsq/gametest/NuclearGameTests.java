package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.kit.KitRegistry;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.NuclearConfig;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.compat.sable.SableCompat;
import com.sqc.nuclearsq.explosion.NuclearExplosion;
import java.util.List;

import com.sqc.nuclearsq.explosion.bomb.FallingBombEntity;
import com.sqc.nuclearsq.explosion.bomb.LargeNuclearBombBlock;
import com.sqc.nuclearsq.explosion.bomb.TsarBombBlock;
import com.sqc.nuclearsq.explosion.bomb.NuclearBombBlock;
import com.sqc.nuclearsq.explosion.AshLayerBlock;
import com.sqc.nuclearsq.registry.BombRegistry;
import com.sqc.nuclearsq.registry.CatalogRegistry;
import com.sqc.nuclearsq.registry.ModFluids;
import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.RodShaft;
import com.sqc.nuclearsq.reactor.FuelRodHeadBlock;
import com.sqc.nuclearsq.machine.big.DriveCore;
import com.sqc.nuclearsq.reactor.ReactorProps;
import com.sqc.nuclearsq.registry.ReactorRegistry;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class NuclearGameTests {
    private NuclearGameTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "bomb_safe", timeoutTicks = 60)
    public static void disarmedBombDoesNotFall(GameTestHelper h) {
        BlockPos p = new BlockPos(7, 8, 7);
        h.setBlock(p, BombRegistry.BOMB.get().defaultBlockState());
        h.runAfterDelay(40, () -> {
            h.assertBlockPresent(BombRegistry.BOMB.get(), p);
            h.assertEntityNotPresent(BombRegistry.FALLING_BOMB.get());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "bomb_redstone", timeoutTicks = 60)
    public static void redstoneArmsBomb(GameTestHelper h) {
        int fuse = NuclearConfig.BOMB_FUSE_SECONDS.get();
        NuclearConfig.BOMB_FUSE_SECONDS.set(20);
        BlockPos p = new BlockPos(7, 4, 7);
        h.setBlock(p, BombRegistry.BOMB.get().defaultBlockState());
        h.setBlock(p.east(), Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(20, () -> {
            NuclearConfig.BOMB_FUSE_SECONDS.set(fuse);
            h.assertBlockProperty(p, NuclearBombBlock.ARMED, true);
            h.assertEntityNotPresent(BombRegistry.FALLING_BOMB.get());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "bomb_disabled", timeoutTicks = 60)
    public static void disabledBombsDoNothing(GameTestHelper h) {
        BlockPos p = new BlockPos(7, 4, 7), probe = new BlockPos(7, 2, 7);
        h.setBlock(p, BombRegistry.BOMB.get().defaultBlockState());
        h.setBlock(probe, Blocks.STONE);
        boolean old = NuclearConfig.BOMBS_ENABLED.get();
        try {
            NuclearConfig.BOMBS_ENABLED.set(false);
            BlockPos abs = h.absolutePos(p);
            ((NuclearBombBlock) BombRegistry.BOMB.get()).setArmed(h.getLevel(), abs, h.getLevel().getBlockState(abs), true, null);
            NuclearExplosion.detonate(h.getLevel(), Vec3.atCenterOf(h.absolutePos(probe.above())), NuclearExplosion.Blast.ofYield(0.001, true));
        } finally {
            NuclearConfig.BOMBS_ENABLED.set(old);
        }
        h.assertBlockProperty(p, NuclearBombBlock.ARMED, false);
        h.runAfterDelay(20, () -> {
            h.assertBlockPresent(Blocks.STONE, probe);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "bomb_drop", timeoutTicks = 260)
    public static void armedBombFallsAndDetonates(GameTestHelper h) {
        double radius = NuclearConfig.REACTOR_YIELD.get();
        double strength = NuclearConfig.BOMB_YIELD.get();
        int falloutMax = NuclearConfig.FALLOUT_MAX_RADIUS.get();
        NuclearConfig.FALLOUT_MAX_RADIUS.set(10);
        int fuse = NuclearConfig.BOMB_FUSE_SECONDS.get();
        NuclearConfig.REACTOR_YIELD.set(0.012);
        NuclearConfig.BOMB_YIELD.set(0.0015);
        NuclearConfig.BOMB_FUSE_SECONDS.set(2);
        BlockPos start = new BlockPos(7, 10, 7);
        boolean physics = SableCompat.hasPhysics();
        h.assertBlockPresent(Blocks.STONE, new BlockPos(7, 3, 7));
        h.setBlock(start, BombRegistry.BOMB.get().defaultBlockState().setValue(NuclearBombBlock.ARMED, true));
        boolean[] fell = {false};
        h.onEachTick(() -> {
            if (!h.getLevel().getEntitiesOfClass(FallingBombEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(20)).isEmpty())
                fell[0] = true;
        });
        boolean[] heldDuringFuse = {false};
        h.runAfterDelay(30, () -> {
            h.assertBlockPresent(BombRegistry.BOMB.get(), start);
            h.assertFalse(fell[0], "bomb fell before the fuse ran out");
            heldDuringFuse[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(heldDuringFuse[0], "fuse check did not run");
            h.assertTrue(physics == SableCompat.hasPhysics(), "Sable physics failed and was disabled");
            if (physics) h.assertFalse(fell[0], "bomb fell as an entity although Sable physics is available");
            else h.assertTrue(fell[0], "bomb never turned into a falling entity");
            h.assertBlockNotPresent(BombRegistry.BOMB.get(), start);
            h.assertEntityNotPresent(BombRegistry.FALLING_BOMB.get());
            h.assertBlockPresent(Blocks.AIR, new BlockPos(7, 3, 7));
            h.assertBlockPresent(Blocks.AIR, new BlockPos(7, 1, 7));
            h.assertBlockPresent(Blocks.STONE, new BlockPos(1, 1, 1));
            NuclearConfig.REACTOR_YIELD.set(radius);
            NuclearConfig.BOMB_YIELD.set(strength);
            NuclearConfig.FALLOUT_MAX_RADIUS.set(falloutMax);
            NuclearConfig.BOMB_FUSE_SECONDS.set(fuse);
        });
    }

    @GameTest(template = FLOOR, batch = "bomb_large_parts", timeoutTicks = 40)
    public static void largeBombIsOneMultiblock(GameTestHelper h) {
        LargeNuclearBombBlock bomb = BombRegistry.LARGE_BOMB.get();
        BlockPos core = h.absolutePos(new BlockPos(6, 4, 6));
        bomb.placeParts(h.getLevel(), core, bomb.defaultBlockState(), Block.UPDATE_ALL);
        List<BlockPos> parts = bomb.parts(core);
        h.assertTrue(parts.size() == 16, "large bomb should have 16 parts, has " + parts.size());
        for (int i = 0; i < parts.size(); i++) {
            BlockState st = h.getLevel().getBlockState(parts.get(i));
            h.assertTrue(st.is(bomb) && st.getValue(LargeNuclearBombBlock.PART) == i, "part " + i + " missing at " + parts.get(i));
            h.assertTrue(bomb.core(parts.get(i), st).equals(core), "part " + i + " does not find its core");
        }
        h.assertTrue(h.getLevel().getBlockEntity(core) != null, "core has no block entity");
        h.getLevel().destroyBlock(parts.get(13), false);
        h.runAfterDelay(2, () -> {
            for (BlockPos p : parts) h.assertTrue(h.getLevel().getBlockState(p).isAir(), "part left over at " + p);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "bomb_tsar_parts", timeoutTicks = 40)
    public static void tsarBombIsOneMultiblock(GameTestHelper h) {
        TsarBombBlock bomb = BombRegistry.TSAR_BOMB.get();
        BlockPos core = h.absolutePos(new BlockPos(6, 4, 6));
        bomb.placeParts(h.getLevel(), core, bomb.defaultBlockState(), Block.UPDATE_ALL);
        List<BlockPos> parts = bomb.parts(core);
        h.assertTrue(parts.size() == 54, "tsar bomb should have 54 parts, has " + parts.size());
        for (int i = 0; i < parts.size(); i++) {
            BlockState st = h.getLevel().getBlockState(parts.get(i));
            h.assertTrue(st.is(bomb) && st.getValue(TsarBombBlock.PART) == i, "part " + i + " missing at " + parts.get(i));
            h.assertTrue(bomb.core(parts.get(i), st).equals(core), "part " + i + " does not find its core");
        }
        h.assertTrue(parts.get(53).equals(core.offset(2, 5, 2)), "last part not at the top corner");
        h.assertTrue(h.getLevel().getBlockEntity(core) != null, "core has no block entity");
        h.getLevel().destroyBlock(parts.get(40), false);
        h.runAfterDelay(2, () -> {
            for (BlockPos p : parts) h.assertTrue(h.getLevel().getBlockState(p).isAir(), "part left over at " + p);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "bomb_tsar_parts", timeoutTicks = 20)
    public static void tsarBombIsTheBiggest(GameTestHelper h) {
        NuclearExplosion.Blast large = NuclearExplosion.Blast.largeBomb(), tsar = NuclearExplosion.Blast.tsarBomb();
        h.assertTrue(tsar.radius() > large.radius(), "tsar crater " + tsar.radius() + " not bigger than " + large.radius());
        h.assertTrue(tsar.maxDamage() > large.maxDamage() && tsar.cloudRange() > large.cloudRange(), "tsar blast not stronger");
        h.assertTrue(BombRegistry.TSAR_BOMB.get().blast().radius() == tsar.radius(), "tsar block uses another blast");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "bomb_large_drop", timeoutTicks = 400)
    public static void largeBombFallsAndDetonates(GameTestHelper h) {
        double radius = NuclearConfig.REACTOR_YIELD.get();
        double strength = NuclearConfig.LARGE_BOMB_YIELD.get();
        int falloutMax = NuclearConfig.FALLOUT_MAX_RADIUS.get();
        NuclearConfig.FALLOUT_MAX_RADIUS.set(10);
        int fuse = NuclearConfig.BOMB_FUSE_SECONDS.get();
        double impact = NuclearConfig.BOMB_IMPACT_SPEED.get();
        NuclearConfig.REACTOR_YIELD.set(0.012);
        NuclearConfig.LARGE_BOMB_YIELD.set(0.0015);
        NuclearConfig.BOMB_FUSE_SECONDS.set(1);
        NuclearConfig.BOMB_IMPACT_SPEED.set(0.2);
        boolean ash = NuclearConfig.ASH_LAYER.get();
        NuclearConfig.ASH_LAYER.set(false);
        LargeNuclearBombBlock bomb = BombRegistry.LARGE_BOMB.get();
        BlockPos core = h.absolutePos(new BlockPos(6, 8, 6));
        boolean physics = SableCompat.hasPhysics();
        bomb.placeParts(h.getLevel(), core, bomb.defaultBlockState().setValue(NuclearBombBlock.ARMED, true), Block.UPDATE_ALL);
        boolean[] fell = {false};
        h.onEachTick(() -> {
            for (FallingBombEntity e : h.getLevel().getEntitiesOfClass(FallingBombEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(20))) {
                fell[0] = true;
                h.assertTrue(e.isLarge(), "falling bomb lost its size");
            }
        });
        h.runAfterDelay(10, () -> h.assertTrue(h.getLevel().getBlockState(core).is(bomb), "large bomb fell before the fuse ran out"));
        h.succeedWhen(() -> {
            h.assertTrue(physics == SableCompat.hasPhysics(), "Sable physics failed and was disabled");
            if (physics) h.assertFalse(fell[0], "bomb fell as an entity although Sable physics is available");
            else h.assertTrue(fell[0], "bomb never turned into a falling entity");
            for (BlockPos p : bomb.parts(core)) h.assertFalse(h.getLevel().getBlockState(p).is(bomb), "part still at start " + p);
            h.assertEntityNotPresent(BombRegistry.FALLING_BOMB.get());
            h.assertBlockPresent(Blocks.AIR, new BlockPos(7, 3, 7));
            h.assertBlockPresent(Blocks.AIR, new BlockPos(7, 2, 7));
            h.assertBlockPresent(Blocks.STONE, new BlockPos(1, 1, 1));
            ExplosionFlowTests.settled();
            NuclearConfig.ASH_LAYER.set(ash);
            NuclearConfig.REACTOR_YIELD.set(radius);
            NuclearConfig.LARGE_BOMB_YIELD.set(strength);
            NuclearConfig.FALLOUT_MAX_RADIUS.set(falloutMax);
            NuclearConfig.BOMB_FUSE_SECONDS.set(fuse);
            NuclearConfig.BOMB_IMPACT_SPEED.set(impact);
        });
    }

    @GameTest(template = FLOOR, batch = "crater", timeoutTicks = 600)
    public static void craterRemovesSphere(GameTestHelper h) {
        BlockPos mid = new BlockPos(7, 3, 7);
        int r = 6;
        BlockPos center = h.absolutePos(mid);
        ExplosionFlowTests.detonate(h, center, new NuclearExplosion.Blast(r, 0.08f, 0f, 1.0, 0, false));
        h.succeedWhen(() -> {
            ExplosionFlowTests.settled();
            int[] carved = {0};
            for (int x = 1; x < 14; x++)
                for (int y = 1; y <= 3; y++)
                    for (int z = 1; z < 14; z++) {
                        BlockPos rel = new BlockPos(x, y, z);
                        BlockPos abs = h.absolutePos(rel);
                        boolean inside = NuclearExplosion.inCrater(r, NuclearExplosion.craterSeed(center), abs.getX(), abs.getZ(),
                                abs.getX() - center.getX(), abs.getY() - center.getY(), abs.getZ() - center.getZ(), 0);
                        if (inside) carved[0] += abs.distSqr(center) > r * r ? 1 : 0;
                        if (!inside && glowing(h.getBlockState(rel))) continue;
                        h.assertBlockPresent(inside ? Blocks.AIR : Blocks.STONE, rel);
                    }
            if (carved[0] < 10) throw new GameTestAssertException("crater rim too small: " + carved[0] + " blocks outside the sphere");
            for (int y = 0; y <= 4; y++) h.getLevel().getChunkSource().getLightEngine().checkBlock(h.absolutePos(new BlockPos(7, y, 7)));
            int sky = h.getLevel().getBrightness(LightLayer.SKY, h.absolutePos(new BlockPos(7, 1, 7)));
            if (sky < 7) throw new GameTestAssertException("crater floor still dark, sky light " + sky);
        });
    }

    private static boolean glowing(BlockState st) {
        return st.is(Blocks.MAGMA_BLOCK) || st.is(Blocks.OBSIDIAN) || st.is(Blocks.BASALT) || st.is(Blocks.TUFF);
    }

    @GameTest(template = FLOOR, batch = "ash", timeoutTicks = 40)
    public static void ashLayerSitsOnGround(GameTestHelper h) {
        BlockPos p = new BlockPos(7, 4, 7);
        h.setBlock(p, CatalogRegistry.ASH.get().defaultBlockState().setValue(AshLayerBlock.LAYERS, 3));
        h.runAfterDelay(5, () -> {
            h.assertBlockProperty(p, AshLayerBlock.LAYERS, 3);
            if (!h.getBlockState(p).getCollisionShape(h.getLevel(), h.absolutePos(p)).isEmpty())
                throw new GameTestAssertException("ash must not block movement");
            h.setBlock(p.below(), Blocks.AIR);
            h.runAfterDelay(2, () -> {
                h.assertBlockPresent(Blocks.AIR, p);
                h.succeed();
            });
        });
    }

    private static BigMachineBlockEntity buildBig(GameTestHelper h, BigMachine m, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) {
            throw new GameTestAssertException(m.id + ": Controller hat kein BlockEntity");
        }
        return be;
    }

    private static void fill(BigMachineBlockEntity be, String port, net.minecraft.world.level.material.Fluid f, int mb) {
        int n = be.tank(port).fill(new FluidStack(f, mb), IFluidHandler.FluidAction.EXECUTE);
        if (n != mb) throw new GameTestAssertException(be.machine().id + "." + port + " nimmt " + n + " statt " + mb + " mB an");
    }

    private static int fillPort(BigMachineBlockEntity be, String port, net.minecraft.world.level.material.Fluid f, int mb) {
        return be.tank(port).fill(new FluidStack(f, mb), IFluidHandler.FluidAction.SIMULATE);
    }

    private static void keepEmergencyFlowing(BigMachineBlockEntity be) {
        net.neoforged.neoforge.fluids.capability.templates.FluidTank water = be.tank("water_in"), out = be.tank("primary_out");
        if (water.getSpace() > 0) water.fill(new FluidStack(Fluids.WATER, water.getSpace()), IFluidHandler.FluidAction.EXECUTE);
        if (out.getFluidAmount() > 4000) out.drain(out.getFluidAmount() - 4000, IFluidHandler.FluidAction.EXECUTE);
    }

    private static void charge(BigMachineBlockEntity be) {
        while (be.energy().receiveEnergy(1000, false) > 0) { }
    }

    private static void expect(BigMachineBlockEntity be, String port, net.minecraft.world.level.material.Fluid f, int mb) {
        FluidStack fs = be.tank(port).getFluid();
        if (fs.getAmount() != mb || (mb > 0 && !fs.is(f))) {
            throw new GameTestAssertException(be.machine().id + "." + port + ": erwartet " + mb + " mB, ist " + fs.getAmount()
                    + " " + fs.getHoverName().getString());
        }
    }

    private static void formed(BigMachineBlockEntity be) {
        if (!be.isFormed()) throw new GameTestAssertException(be.machine().id + " wird nicht als aufgebaut erkannt ("
                + be.matched() + "/" + be.total() + ", Struktur in Drehung 0: " + be.machine().structure.matched(be.getLevel(), be.getBlockPos(), 0) + ")");
    }

    @GameTest(template = FLOOR, batch = "big_tower_large", timeoutTicks = 100)
    public static void largeCoolingTowerCoolsCoolant(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.COOLING_TOWER_LARGE, new BlockPos(1, 4, 1));
        BigMachine.Port in = be.machine().port("coolant_in");
        int evap = be.machine().extra("evaporation_mb_t");
        fill(be, "coolant_in", ModFluids.HOT_COOLANT.source.get(), in.rate() * 10);
        h.runAfterDelay(60, () -> {
            formed(be);
            expect(be, "coolant_in", ModFluids.HOT_COOLANT.source.get(), 0);
            expect(be, "coolant_out", ModFluids.COOLANT.source.get(), (in.rate() - evap) * 10);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_tower_xl", timeoutTicks = 200)
    public static void xlCoolingTowerCoolsCoolant(GameTestHelper h) {
        BlockPos lo = h.absolutePos(new BlockPos(1, 4, 1)), hi = lo.offset(63, 0, 63);
        java.util.List<net.minecraft.world.level.ChunkPos> forced = new java.util.ArrayList<>();
        for (int cx = lo.getX() >> 4; cx <= hi.getX() >> 4; cx++) {
            for (int cz = lo.getZ() >> 4; cz <= hi.getZ() >> 4; cz++) {
                if (h.getLevel().setChunkForced(cx, cz, true)) forced.add(new net.minecraft.world.level.ChunkPos(cx, cz));
            }
        }
        BigMachineBlockEntity be = buildBig(h, BigMachine.COOLING_TOWER_XL, new BlockPos(1, 4, 1));
        BigMachine.Port in = be.machine().port("coolant_in");
        int evap = be.machine().extra("evaporation_mb_t");
        fill(be, "coolant_in", ModFluids.HOT_COOLANT.source.get(), in.rate() * 10);
        h.runAfterDelay(60, () -> {
            formed(be);
            expect(be, "coolant_in", ModFluids.HOT_COOLANT.source.get(), 0);
            expect(be, "coolant_out", ModFluids.COOLANT.source.get(), (in.rate() - evap) * 10);
            BlockPos mid = be.machine().structure.world(be.getBlockPos(), be.formedRot(), 31, 50, 31);
            if (!h.getLevel().getBlockState(mid).isAir()) throw new GameTestAssertException("Inneres nicht leer: " + h.getLevel().getBlockState(mid));
            for (var c : forced) h.getLevel().setChunkForced(c.x, c.z, false);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "reactor_assembler", timeoutTicks = 700)
    public static void reactorAssemblerMachineStates(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.REACTOR_ASSEMBLER, new BlockPos(2, 2, 2));
        var recipe = h.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                com.sqc.nuclearsq.NuclearSQ.MODID, "small_parts_assembly/reactor_assembler_kit_sqc"));
        if (recipe.isEmpty() || !recipe.get().value().getResultItem(h.getLevel().registryAccess()).is(KitRegistry.KITS.get("reactor_assembler").get())) {
            throw new GameTestAssertException("Kleinteil-Assembler-Rezept liefert nicht das Kit");
        }
        h.runAfterDelay(25, () -> {
            formed(be);
            BigCrafter c = be.crafter();
            if (c == null || be.items() == null || be.items().getSlots() != 10) throw new GameTestAssertException("Kein 3x3-Raster mit Ergebnisfeld");
            if (c.status() != BigCrafter.NO_INPUT) throw new GameTestAssertException("leer: NO_INPUT erwartet, ist " + c.status());
            int dia = 1, quartz = 4;
            var zItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("nuclearsq:zirconium_dust_sqc"));
            for (var holder : com.sqc.nuclearsq.assembler.AssemblyRecipe.all(h.getLevel().getRecipeManager())) {
                var zr = holder.value();
                if (!zr.result().is(zItem) || zr.ingredients().stream().noneMatch(n -> n.test(new ItemStack(net.minecraft.world.item.Items.DIAMOND)))) continue;
                for (var need : zr.ingredients()) {
                    if (need.test(new ItemStack(net.minecraft.world.item.Items.DIAMOND))) dia = need.count();
                    else if (need.test(new ItemStack(net.minecraft.world.item.Items.QUARTZ))) quartz = need.count();
                }
                break;
            }
            be.items().insertItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND, dia), false);
            be.items().insertItem(4, new ItemStack(net.minecraft.world.item.Items.QUARTZ, quartz), false);
            be.setEnabled(false);
            h.runAfterDelay(25, () -> {
                if (c.progress() != 0 || !be.items().getStackInSlot(9).isEmpty()) throw new GameTestAssertException("aus: kein Fortschritt erwartet, ist " + c.progress());
                be.setEnabled(true);
                h.runAfterDelay(25, () -> {
                    if (c.status() != BigCrafter.NO_POWER) throw new GameTestAssertException("ohne Strom: NO_POWER erwartet, ist " + c.status());
                    charge(be);
                    int full = be.energy().getEnergyStored();
                    h.runAfterDelay(BigMachine.REACTOR_ASSEMBLER.extra("craft_ticks") * 100 / 91 + 30, () -> {
                        ItemStack out = be.items().getStackInSlot(9);
                        if (!out.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("nuclearsq:zirconium_dust_sqc")))) {
                            throw new GameTestAssertException("Kein Zirkonium-Staub im Ergebnisfeld: " + out);
                        }
                        if (!be.items().getStackInSlot(0).isEmpty() || !be.items().getStackInSlot(4).isEmpty()) throw new GameTestAssertException("Zutaten nicht verbraucht");
                        if (be.energy().getEnergyStored() >= full) throw new GameTestAssertException("kein Strom verbraucht");
                        h.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "reactor_assembler_kit", timeoutTicks = 60)
    public static void reactorAssemblerKitPlaces(GameTestHelper h) {
        MachineKits.Placement pl = MachineKits.placement(MachineKits.def("reactor_assembler"), h.absolutePos(new BlockPos(7, 4, 4)), net.minecraft.core.Direction.SOUTH);
        if (!MachineKits.blocked(h.getLevel(), pl, null).isEmpty()) throw new GameTestAssertException("Platz gilt als belegt");
        MachineKits.place(h.getLevel(), pl);
        h.runAfterDelay(10, () -> {
            if (!(h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity be) || be.machine() != BigMachine.REACTOR_ASSEMBLER) {
                throw new GameTestAssertException("Kein Reaktor-Assembler aufgestellt");
            }
            formed(be);
            BlockPos part = null;
            for (MultiblockStructure.Cell c : pl.cells()) if (!c.pos().equals(pl.anchor())) { part = c.pos(); break; }
            net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel()).gameMode.destroyBlock(part);
            boolean kit = !h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pl.anchor()).inflate(6),
                    e -> e.getItem().is(KitRegistry.KITS.get("reactor_assembler").get())).isEmpty();
            if (!kit) throw new GameTestAssertException("Kit faellt nicht heraus");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "particle_assembler", timeoutTicks = 400)
    public static void particleAssemblerMakesItems(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.PARTICLE_ASSEMBLER, new BlockPos(2, 4, 2));
        var pc = com.sqc.nuclearsq.particle.ParticleClasses.get();
        var up = com.sqc.nuclearsq.particle.ParticleRegistry.UPGRADES;
        boolean[] power = {false};
        h.onEachTick(() -> {
            if (power[0]) charge(be);
        });
        h.runAfterDelay(25, () -> {
            formed(be);
            BigCrafter c = be.crafter();
            var items = be.items();
            if (c == null || items == null || items.getSlots() != com.sqc.nuclearsq.particle.ParticleClasses.SLOTS) {
                throw new GameTestAssertException("Kein Puffer mit 9 Ergebnis- und 4 Upgrade-Feldern");
            }
            if (c.status() != BigCrafter.NO_POWER) throw new GameTestAssertException("ohne Strom: NO_POWER erwartet, ist " + c.status());
            int speed = com.sqc.nuclearsq.particle.ParticleClasses.Upgrade.SPEED.slot();
            if (items.insertItem(speed, new ItemStack(up.get(com.sqc.nuclearsq.particle.ParticleClasses.Upgrade.ENERGY).get()), true).isEmpty()) {
                throw new GameTestAssertException("Tempo-Feld nimmt ein Energie-Upgrade");
            }
            if (be.itemsAt("item_out").insertItem(0, new ItemStack(net.minecraft.world.item.Items.DIRT), true).isEmpty()) {
                throw new GameTestAssertException("Ausgang nimmt Items an");
            }
            ItemStack left = items.insertItem(speed, new ItemStack(up.get(com.sqc.nuclearsq.particle.ParticleClasses.Upgrade.SPEED).get(), 16), false);
            int max = pc.max(com.sqc.nuclearsq.particle.ParticleClasses.Upgrade.SPEED);
            if (items.getStackInSlot(speed).getCount() != max || left.getCount() != 16 - max) {
                throw new GameTestAssertException("Tempo-Feld haelt " + items.getStackInSlot(speed).getCount() + " statt " + max);
            }
            int card = com.sqc.nuclearsq.particle.ParticleClasses.CARD;
            if (items.getSlots() <= card || c.cardTier() != 0) throw new GameTestAssertException("Kein leeres Feld fuer die Klassen-Karte");
            if (items.insertItem(card, new ItemStack(up.get(com.sqc.nuclearsq.particle.ParticleClasses.Upgrade.SPEED).get()), true).isEmpty()) {
                throw new GameTestAssertException("Karten-Feld nimmt ein Upgrade");
            }
            var cardItem = com.sqc.nuclearsq.particle.ParticleRegistry.CARDS.get(1).get();
            if (!items.insertItem(card, new ItemStack(cardItem), true).isEmpty() || items.getSlotLimit(card) != 1) {
                throw new GameTestAssertException("Karten-Feld nimmt die Karte nicht (oder mehr als eine)");
            }
            int time = c.particleTime();
            if (time >= BigMachine.PARTICLE_ASSEMBLER.extra("cycle_ticks")) throw new GameTestAssertException("Tempo-Upgrade verkuerzt nicht: " + time);
            charge(be);
            power[0] = true;
            h.runAfterDelay(time + 40, () -> {
                int found = -1;
                for (int i = 0; i < com.sqc.nuclearsq.particle.ParticleClasses.OUTPUTS; i++) if (!items.getStackInSlot(i).isEmpty()) found = i;
                if (found < 0 || c.cycles() < 1) {
                    throw new GameTestAssertException("Nach einem Durchgang kein Item (Zustand " + c.status() + ", Durchgaenge " + c.cycles() + ")");
                }
                ItemStack got = items.getStackInSlot(found);
                boolean known = false;
                for (var e : pc.classes.get(0).entries()) known |= got.is(com.sqc.nuclearsq.particle.ParticleClasses.resolve(e.item()));
                if (!known || c.lastClass() != 0) throw new GameTestAssertException("Ohne Karte kein Item der leichten Klasse: " + got);
                var out = be.itemsAt("item_out");
                if (out.extractItem(speed, 1, true).getCount() != 0) throw new GameTestAssertException("Ausgang gibt Upgrades her");
                if (out.extractItem(found, 64, false).isEmpty()) throw new GameTestAssertException("Ausgang gibt das Ergebnis nicht her");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "particle_assembler_tables", timeoutTicks = 20)
    public static void particleUpgradeTables(GameTestHelper h) {
        var pc = com.sqc.nuclearsq.particle.ParticleClasses.get();
        int base = BigMachine.PARTICLE_ASSEMBLER.extra("fe_per_tick"), ticks = BigMachine.PARTICLE_ASSEMBLER.extra("cycle_ticks");
        if (base != 3840 || ticks != 160) throw new GameTestAssertException("Grundwerte " + base + " FE/t, " + ticks + " Ticks");
        int[] none = {0, 0, 0, 0}, speed = {8, 0, 0, 0}, energy = {0, 8, 0, 0}, eff = {0, 0, 8, 0}, out = {0, 0, 0, 8};
        if (pc.energy(base, none) != base || pc.time(ticks, none) != ticks) throw new GameTestAssertException("Ohne Upgrades veraendert");
        if (pc.time(ticks, speed) >= ticks || pc.energy(base, speed) <= base) throw new GameTestAssertException("Tempo: kuerzer und teurer erwartet");
        if (pc.energy(base, energy) >= base) throw new GameTestAssertException("Energie-Upgrade spart nichts");
        if (pc.bonus(none) != 0 || pc.bonus(out) <= 0) throw new GameTestAssertException("Produktion ohne Bonus");
        int[] a = pc.chances(none), b = pc.chances(eff);
        int sum = 0;
        for (int v : a) sum += v;
        if (Math.abs(sum - 1000) > 5) throw new GameTestAssertException("Chancen summieren sich zu " + sum);
        if (b[3] <= a[3] || b[0] >= a[0]) throw new GameTestAssertException("Effizienz hebt schwere Klassen nicht an");
        for (int k = 0; k < 3; k++) if (a[k] <= a[k + 1]) throw new GameTestAssertException("Klasse " + k + " nicht haeufiger als " + (k + 1));
        for (int tier = 0; tier <= com.sqc.nuclearsq.particle.ParticleClasses.ALL; tier++) {
            int[] ch = pc.chances(none, tier);
            int total = 0;
            for (int k = 0; k < ch.length; k++) {
                total += ch[k];
                if ((k > tier) != (ch[k] == 0)) throw new GameTestAssertException("Stufe " + tier + ": Klasse " + k + " Chance " + ch[k]);
            }
            if (Math.abs(total - 1000) > 5) throw new GameTestAssertException("Stufe " + tier + ": Summe " + total);
        }
        int[] fe = {3840, 4800, 5760, 7680, 11520};
        for (int tier = 0; tier < fe.length; tier++) {
            if (pc.energy(base, none, tier) != fe[tier]) throw new GameTestAssertException("Stufe " + tier + ": " + pc.energy(base, none, tier) + " statt " + fe[tier] + " FE/t");
        }
        var cards = com.sqc.nuclearsq.particle.ParticleRegistry.CARDS;
        if (cards.size() != 4) throw new GameTestAssertException(cards.size() + " Klassen-Karten statt 4");
        for (int i = 0; i < cards.size(); i++) {
            if (com.sqc.nuclearsq.particle.ParticleClassCardItem.tierOf(new ItemStack(cards.get(i).get())) != i + 1) throw new GameTestAssertException("Karte " + i + " falsche Stufe");
            var r = com.sqc.nuclearsq.assembler.SmallPartsRecipe.forItem(h.getLevel().getRecipeManager(), cards.get(i).get());
            if (r == null) throw new GameTestAssertException("Kein Rezept fuer Karte " + (i + 1));
            if (i > 0) {
                ItemStack prev = new ItemStack(cards.get(i - 1).get());
                if (r.ingredients().stream().noneMatch(n -> n.matches(prev))) throw new GameTestAssertException("Karte " + (i + 1) + " braucht nicht " + prev);
            }
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "assembler_power", timeoutTicks = 20)
    public static void assemblerPowerMatchesReactorParts(GameTestHelper h) {
        if (BigMachine.REACTOR_ASSEMBLER.extra("fe_per_tick") != 1350 || BigMachine.REACTOR_ASSEMBLER.extra("fe_per_tick_reactor") != 3840) {
            throw new GameTestAssertException("Reaktor-Assembler: " + BigMachine.REACTOR_ASSEMBLER.extra("fe_per_tick") + " / "
                    + BigMachine.REACTOR_ASSEMBLER.extra("fe_per_tick_reactor") + " FE/t");
        }
        var regs = h.getLevel().registryAccess();
        List<String> wrong = new java.util.ArrayList<>();
        int reactor = 0;
        for (var holder : h.getLevel().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof com.sqc.nuclearsq.assembler.HeavyAssemblyRecipe r)) continue;
            boolean part = r.getResultItem(regs).is(com.sqc.nuclearsq.assembler.AssemblyRecipe.REACTOR_PARTS);
            if (part) reactor++;
            if (r.energyPerTick() != (part ? 9600 : 5760)) wrong.add(holder.id().getPath() + "=" + r.energyPerTick());
        }
        if (!wrong.isEmpty()) throw new GameTestAssertException("Falsche FE/t: " + String.join(", ", wrong));
        if (reactor < 9) throw new GameTestAssertException("Nur " + reactor + " Rezepte fuer Reaktorteile");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "big_relief_tank", timeoutTicks = 100)
    public static void bigReliefTankCoolsRelief(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.RELIEF_TANK, new BlockPos(3, 4, 3));
        BigMachine.Port in = be.machine().port("relief_in");
        BlockPos port = h.absolutePos(new BlockPos(3 + in.x(), 4 + in.y(), 3 + in.z()));
        h.runAfterDelay(2, () -> {
            formed(be);
            IFluidHandler cap = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, port, null);
            if (cap == null) throw new GameTestAssertException("Abblase-Anschluss hat keine Fluessigkeits-Capability");
            if (cap.fill(new FluidStack(ModFluids.PRIMARY_WATER.source.get(), 100), IFluidHandler.FluidAction.EXECUTE) != 0) {
                throw new GameTestAssertException("Abblase-Anschluss nimmt kaltes Primaerwasser an");
            }
            if (cap.fill(new FluidStack(ModFluids.HOT_PRIMARY_WATER.source.get(), 600), IFluidHandler.FluidAction.EXECUTE) != 600) {
                throw new GameTestAssertException("Abblase-Anschluss nimmt kein heisses Primaerwasser an");
            }
        });
        h.runAfterDelay(20, () -> {
            expect(be, "relief_in", ModFluids.HOT_PRIMARY_WATER.source.get(), 600);
            fill(be, "coolant_in", ModFluids.COOLANT.source.get(), 1000);
        });
        h.runAfterDelay(60, () -> {
            expect(be, "relief_in", ModFluids.HOT_PRIMARY_WATER.source.get(), 0);
            expect(be, "primary_out", ModFluids.PRIMARY_WATER.source.get(), 600);
            expect(be, "coolant_in", ModFluids.COOLANT.source.get(), 400);
            expect(be, "coolant_out", ModFluids.HOT_COOLANT.source.get(), 600);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_relief_tank_cryo", timeoutTicks = 100)
    public static void bigReliefTankCryoCoolsFourfold(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.RELIEF_TANK, new BlockPos(3, 4, 3));
        h.runAfterDelay(2, () -> {
            formed(be);
            fill(be, "coolant_in", ModFluids.CRYO_COOLANT.source.get(), 1000);
            fill(be, "relief_in", ModFluids.HOT_PRIMARY_WATER.source.get(), 600);
        });
        h.runAfterDelay(60, () -> {
            expect(be, "primary_out", ModFluids.PRIMARY_WATER.source.get(), 600);
            expect(be, "coolant_in", ModFluids.CRYO_COOLANT.source.get(), 850);
            expect(be, "coolant_out", ModFluids.HOT_COOLANT.source.get(), 150);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_pressurizer", timeoutTicks = 900)
    public static void bigPressurizerHoldsAndVents(GameTestHelper h) {
        BigMachineBlockEntity drive = rodDrive(h, new BlockPos(2, 4, 2));
        BigMachineBlockEntity pz = buildBig(h, BigMachine.PRESSURIZER, new BlockPos(11, 4, 4));
        fill(pz, "primary_in", ModFluids.PRIMARY_WATER.source.get(), 16000);
        if (fillPort(pz, "primary_in", ModFluids.HOT_PRIMARY_WATER.source.get(), 100) != 0) throw new GameTestAssertException("Ausgleichsleitung nimmt heisses Primaerwasser an");
        h.onEachTick(() -> charge(pz));
        h.runAfterDelay(30, () -> {
            formed(pz);
            if (pz.pressure() <= 0) throw new GameTestAssertException("Druckhalter heizt nicht: " + pz.stateKey() + ", Strom " + pz.energy().getEnergyStored() + ", Primaer " + pz.tank("primary_in").getFluidAmount() + ", Antrieb " + drive.primaryPressure());
            if (!pz.stateKey().equals("low_pressure")) throw new GameTestAssertException("Zustand " + pz.stateKey() + " statt low_pressure");
        });
        h.runAfterDelay(700, () -> {
            float p = pz.pressure();
            if (p < 45 || p > 51) throw new GameTestAssertException("Druck " + p + " % statt etwa 50 %");
            if (Math.abs(drive.primaryPressure() - p) > 1) throw new GameTestAssertException("Antrieb kennt den Druck nicht: " + drive.primaryPressure());
            if (pz.signalOut() < 7 || pz.signalOut() > 8) throw new GameTestAssertException("Druck-Signal " + pz.signalOut());
            pz.setPressure(95);
        });
        h.runAfterDelay(704, () -> {
            if (!pz.stateKey().equals("venting")) throw new GameTestAssertException("Zustand " + pz.stateKey() + " statt venting");
            if (pz.tank("relief_out").getFluidAmount() <= 0 || !pz.tank("relief_out").getFluid().is(ModFluids.HOT_PRIMARY_WATER.source.get())) {
                throw new GameTestAssertException("Druckhalter blaest nicht ab");
            }
        });
        h.runAfterDelay(760, () -> {
            if (pz.venting() || pz.pressure() > 86 || pz.pressure() < 80) throw new GameTestAssertException("Abblasen endet nicht bei 85 %: " + pz.pressure());
            int vented = pz.tank("relief_out").getFluidAmount();
            if (vented < 160 * 8 || vented > 160 * 12) throw new GameTestAssertException("Abgeblasen " + vented + " mB");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_emergency_auto", timeoutTicks = 200)
    public static void emergencyStartsOnLowPressure(GameTestHelper h) {
        BigMachineBlockEntity drive = rodDrive(h, new BlockPos(2, 4, 2));
        BigMachineBlockEntity pz = buildBig(h, BigMachine.PRESSURIZER, new BlockPos(11, 4, 4));
        BigMachineBlockEntity ec = buildBig(h, BigMachine.EMERGENCY_COOLING, new BlockPos(2, 4, 11));
        BigMachineBlockEntity hpi = buildBig(h, BigMachine.HIGH_PRESSURE_INJECTION, new BlockPos(9, 4, 10));
        fill(ec, "water_in", Fluids.WATER, 4000);
        fill(hpi, "primary_in", ModFluids.PRIMARY_WATER.source.get(), 4000);
        if (fillPort(hpi, "primary_in", ModFluids.HOT_PRIMARY_WATER.source.get(), 100) != 0) throw new GameTestAssertException("Einspeisung nimmt heisses Primaerwasser an");
        h.onEachTick(() -> {
            charge(ec);
            charge(hpi);
        });
        h.runAfterDelay(80, () -> {
            formed(pz);
            formed(hpi);
            if (drive.primaryPressure() != 0) throw new GameTestAssertException("Antrieb kennt den Druck nicht: " + drive.primaryPressure());
            if (ec.manual() || hpi.manual()) throw new GameTestAssertException("Handbetrieb an");
            if (ec.tank("primary_out").getFluidAmount() <= 0) throw new GameTestAssertException("Notkuehlung startet nicht bei niedrigem Druck: " + ec.stateKey());
            if (hpi.tank("primary_out").getFluidAmount() <= 0) throw new GameTestAssertException("Hochdruck-Einspeisung startet nicht: " + hpi.stateKey());
            if (!hpi.lowPressureStart()) throw new GameTestAssertException("Einspeisung meldet keinen Auto-Start");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_hp_injection", timeoutTicks = 100)
    public static void highPressureInjectionWaitsThenPumps(GameTestHelper h) {
        BigMachineBlockEntity hpi = buildBig(h, BigMachine.HIGH_PRESSURE_INJECTION, new BlockPos(4, 4, 4));
        fill(hpi, "primary_in", ModFluids.PRIMARY_WATER.source.get(), 16000);
        h.onEachTick(() -> {
            charge(hpi);
            hpi.tank("primary_out").drain(100000, IFluidHandler.FluidAction.EXECUTE);
        });
        h.runAfterDelay(30, () -> {
            formed(hpi);
            if (!hpi.stateKey().equals("standby")) throw new GameTestAssertException("Zustand " + hpi.stateKey() + " statt standby");
            expect(hpi, "primary_in", ModFluids.PRIMARY_WATER.source.get(), 16000);
            hpi.setManual(true);
        });
        h.runAfterDelay(61, () -> {
            int used = 16000 - hpi.tank("primary_in").getFluidAmount();
            if (used < 320 * 25 || used > 320 * 31 || used % 320 != 0) throw new GameTestAssertException("Foerdermenge " + used + " mB");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_pressurizer_empty", timeoutTicks = 100)
    public static void bigPressurizerEmptyStaysLow(GameTestHelper h) {
        BigMachineBlockEntity pz = buildBig(h, BigMachine.PRESSURIZER, new BlockPos(4, 4, 4));
        h.runAfterDelay(60, () -> {
            formed(pz);
            if (pz.pressure() != 0 || pz.venting()) throw new GameTestAssertException("Druck " + pz.pressure());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_steam", timeoutTicks = 100)
    public static void bigSeparatorAndDryerSplitWetSteam(GameTestHelper h) {
        BigMachineBlockEntity sep = buildBig(h, BigMachine.STEAM_SEPARATOR, new BlockPos(2, 4, 2));
        BigMachineBlockEntity dry = buildBig(h, BigMachine.STEAM_DRYER, new BlockPos(8, 4, 2));
        fill(sep, "wet_steam_in", ModFluids.WET_STEAM.source.get(), 400);
        fill(dry, "wet_steam_in", ModFluids.WET_STEAM.source.get(), 400);
        h.runAfterDelay(60, () -> {
            formed(sep);
            formed(dry);
            expect(sep, "steam_out", ModFluids.STEAM.source.get(), 360);
            expect(sep, "water_out", Fluids.WATER, 40);
            expect(dry, "steam_out", ModFluids.STEAM.source.get(), 390);
            expect(dry, "water_out", Fluids.WATER, 10);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_steam_chain", timeoutTicks = 100)
    public static void dryerPassesDrySteamFromSeparator(GameTestHelper h) {
        BigMachineBlockEntity dry = buildBig(h, BigMachine.STEAM_DRYER, new BlockPos(2, 4, 2));
        fill(dry, "wet_steam_in", ModFluids.STEAM.source.get(), 400);
        h.runAfterDelay(60, () -> {
            formed(dry);
            expect(dry, "wet_steam_in", ModFluids.STEAM.source.get(), 0);
            expect(dry, "steam_out", ModFluids.STEAM.source.get(), 400);
            expect(dry, "water_out", Fluids.WATER, 0);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_tower_part_load", timeoutTicks = 100)
    public static void coolingTowerEvaporatesInProportion(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.COOLING_TOWER_LARGE, new BlockPos(1, 4, 1));
        BigMachine.Port in = be.machine().port("coolant_in");
        int evap = be.machine().extra("evaporation_mb_t"), part = in.rate() / 4;
        int[] fed = {0};
        h.onEachTick(() -> {
            if (be.isFormed() && fed[0] < 40) {
                fill(be, "coolant_in", ModFluids.HOT_COOLANT.source.get(), part);
                fed[0]++;
            }
        });
        h.runAfterDelay(80, () -> {
            formed(be);
            if (fed[0] != 40) throw new GameTestAssertException("Testaufbau: nur " + fed[0] + " Ticks gespeist");
            expect(be, "coolant_out", ModFluids.COOLANT.source.get(), part * 40 - evap * 10);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_steam_generator", timeoutTicks = 80)
    public static void bigSteamGeneratorIdlesWithoutPrimary(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.STEAM_GENERATOR, new BlockPos(2, 4, 2));
        fill(be, "feedwater_in", ModFluids.FEEDWATER.source.get(), 1000);
        h.runAfterDelay(40, () -> {
            formed(be);
            expect(be, "wet_steam_out", ModFluids.WET_STEAM.source.get(), 0);
            expect(be, "feedwater_in", ModFluids.FEEDWATER.source.get(), 1000);
            if (!be.stateKey().equals("no_primary")) throw new GameTestAssertException("Zustand " + be.stateKey() + " statt no_primary");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_tube_leak", timeoutTicks = 200)
    public static void steamGeneratorTubeLeak(GameTestHelper h) {
        BigMachineBlockEntity sg = buildBig(h, BigMachine.STEAM_GENERATOR, new BlockPos(2, 4, 2));
        BigMachineBlockEntity sep = buildBig(h, BigMachine.STEAM_SEPARATOR, new BlockPos(12, 4, 2));
        fill(sg, "primary_hot_in", ModFluids.HOT_PRIMARY_WATER.source.get(), 3200);
        fill(sg, "feedwater_in", ModFluids.FEEDWATER.source.get(), 4000);
        sg.startTubeLeak();
        h.runAfterDelay(60, () -> {
            formed(sg);
            if (!sg.stateKey().equals("tube_leak")) throw new GameTestAssertException("Zustand " + sg.stateKey() + " statt tube_leak");
            expect(sg, "wet_steam_out", ModFluids.CONTAMINATED_STEAM.source.get(), 1600);
            fill(sep, "wet_steam_in", ModFluids.CONTAMINATED_STEAM.source.get(), 400);
            if (sg.repair()) throw new GameTestAssertException("Wartung im Betrieb");
            sg.setEnabled(false);
            if (!sg.repair() || sg.tubeLeak() || sg.wearPercent() != 0) throw new GameTestAssertException("Wartung dichtet das Leck nicht");
        });
        h.runAfterDelay(120, () -> {
            formed(sep);
            expect(sep, "steam_out", ModFluids.CONTAMINATED_STEAM.source.get(), 360);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_steam_generator", timeoutTicks = 120)
    public static void bigSteamGeneratorMakesSteam(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.STEAM_GENERATOR, new BlockPos(2, 4, 2));
        BigMachineBlockEntity pre = buildBig(h, BigMachine.STEAM_GENERATOR, new BlockPos(12, 4, 2));
        if (fillPort(be, "primary_hot_in", ModFluids.PRIMARY_WATER.source.get(), 100) != 0) {
            throw new GameTestAssertException("Heiss-Eingang nimmt kaltes Primaerwasser an");
        }
        fill(be, "primary_hot_in", ModFluids.HOT_PRIMARY_WATER.source.get(), 3200);
        fill(be, "feedwater_in", ModFluids.FEEDWATER.source.get(), 4000);
        fill(pre, "primary_hot_in", ModFluids.HOT_PRIMARY_WATER.source.get(), 3200);
        fill(pre, "feedwater_in", ModFluids.PREHEATED_FEEDWATER.source.get(), 4000);
        h.runAfterDelay(60, () -> {
            formed(be);
            formed(pre);
            expect(be, "primary_hot_in", ModFluids.HOT_PRIMARY_WATER.source.get(), 0);
            expect(be, "primary_cold_out", ModFluids.PRIMARY_WATER.source.get(), 3200);
            expect(be, "wet_steam_out", ModFluids.WET_STEAM.source.get(), 1600);
            expect(be, "feedwater_in", ModFluids.FEEDWATER.source.get(), 2400);
            expect(pre, "primary_cold_out", ModFluids.PRIMARY_WATER.source.get(), 3200);
            expect(pre, "wet_steam_out", ModFluids.WET_STEAM.source.get(), 1840);
            h.succeed();
        });
    }

    private static BigMachineBlockEntity primaryLoop(GameTestHelper h, BigMachineBlockEntity drive, BigMachineBlockEntity sg) {
        BigMachineBlockEntity pump = buildBig(h, BigMachine.MAIN_COOLANT_PUMP, new BlockPos(2, 4, 10));
        fill(pump, "primary_in", ModFluids.PRIMARY_WATER.source.get(), 4000);
        h.onEachTick(() -> {
            charge(pump);
            move(drive.tank("primary_hot_out"), sg.tank("primary_hot_in"));
            move(sg.tank("primary_cold_out"), pump.tank("primary_in"));
            move(pump.tank("primary_out"), drive.tank("primary_cold_in"));
        });
        return pump;
    }

    private static void pumping(GameTestHelper h, BigMachineBlockEntity drive) {
        h.onEachTick(() -> drive.addPrimaryFlow(640));
    }

    private static void move(net.neoforged.neoforge.fluids.capability.templates.FluidTank from, net.neoforged.neoforge.fluids.capability.templates.FluidTank to) {
        FluidStack got = from.drain(Math.min(from.getFluidAmount(), to.getSpace()), IFluidHandler.FluidAction.SIMULATE);
        if (!got.isEmpty()) from.drain(to.fill(got, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
    }

    @GameTest(template = FLOOR, batch = "big_steam_generator_power", timeoutTicks = 600)
    public static void pressurizedLoopMakesSteam(GameTestHelper h) {
        BigMachineBlockEntity drive = rodDrive(h, new BlockPos(2, 4, 2));
        BigMachineBlockEntity sg = buildBig(h, BigMachine.STEAM_GENERATOR, new BlockPos(11, 4, 2));
        twoRods(h, drive);
        fill(drive, "primary_cold_in", ModFluids.PRIMARY_WATER.source.get(), 16000);
        fill(sg, "feedwater_in", ModFluids.FEEDWATER.source.get(), 16000);
        primaryLoop(h, drive, sg);
        h.onEachTick(() -> {
            sg.tank("wet_steam_out").drain(100000, IFluidHandler.FluidAction.EXECUTE);
            sg.tank("feedwater_in").fill(new FluidStack(ModFluids.FEEDWATER.source.get(), 16000), IFluidHandler.FluidAction.EXECUTE);
        });
        h.runAfterDelay(25, () -> {
            formed(drive);
            for (int k = 0; k < 9; k++) drive.setShaftLocked(k, true);
            drive.setRodTarget(15);
        });
        h.runAfterDelay(400, () -> {
            formed(sg);
            if (drive.reactorPower() <= 0) throw new GameTestAssertException("Antrieb hat keine Leistung (Hitze " + drive.core().heatPercent() + " %, kalt " + drive.tank("primary_cold_in").getFluidAmount() + ", heiss " + drive.tank("primary_hot_out").getFluidAmount() + ")");
            if (drive.vesselDry()) throw new GameTestAssertException("Durchfluss reisst trotz Dampferzeuger ab");
            if (drive.signalOut() <= 0) throw new GameTestAssertException("Kein Flux-Signal am Antrieb trotz Leistung");
            if (sg.lastMb() < 60) throw new GameTestAssertException("Dampferzeuger macht zu wenig Nassdampf: " + sg.lastMb() + " mB/t");
            drive.scram();
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_pump_dry", timeoutTicks = 600)
    public static void dryMainCoolantPumpStopsDriveFlow(GameTestHelper h) {
        BigMachineBlockEntity drive = rodDrive(h, new BlockPos(2, 4, 2));
        BigMachineBlockEntity pump = buildBig(h, BigMachine.MAIN_COOLANT_PUMP, new BlockPos(2, 4, 10));
        twoRods(h, drive);
        h.onEachTick(() -> {
            charge(pump);
            drive.tank("primary_cold_in").fill(new FluidStack(ModFluids.PRIMARY_WATER.source.get(), 32000), IFluidHandler.FluidAction.EXECUTE);
            drive.tank("primary_hot_out").drain(100000, IFluidHandler.FluidAction.EXECUTE);
        });
        h.runAfterDelay(25, () -> {
            formed(drive);
            formed(pump);
            for (int k = 0; k < 9; k++) drive.setShaftLocked(k, true);
            drive.setRodTarget(15);
        });
        h.runAfterDelay(500, () -> {
            if (drive.pumpFlow() != 0) throw new GameTestAssertException("Trockene Pumpe meldet " + drive.pumpFlow() + " mB/t an den Antrieb");
            if (pump.lastMb() != 0) throw new GameTestAssertException("Trockene Pumpe zeigt " + pump.lastMb() + " mB/t");
            if (!drive.vesselDry()) throw new GameTestAssertException("Antrieb laeuft ohne Umwaelzung weiter");
            drive.scram();
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_steam_generator_dry", timeoutTicks = 600)
    public static void pressurizedLoopStopsWithoutFeedwater(GameTestHelper h) {
        BigMachineBlockEntity drive = rodDrive(h, new BlockPos(2, 4, 2));
        BigMachineBlockEntity sg = buildBig(h, BigMachine.STEAM_GENERATOR, new BlockPos(11, 4, 2));
        twoRods(h, drive);
        fill(drive, "primary_cold_in", ModFluids.PRIMARY_WATER.source.get(), 8000);
        primaryLoop(h, drive, sg);
        h.runAfterDelay(25, () -> {
            formed(drive);
            for (int k = 0; k < 9; k++) drive.setShaftLocked(k, true);
            drive.setRodTarget(15);
        });
        h.runAfterDelay(450, () -> {
            if (!drive.vesselDry()) throw new GameTestAssertException("Kein Abriss ohne Speisewasser (kalt " + drive.tank("primary_cold_in").getFluidAmount() + ")");
            expect(sg, "wet_steam_out", ModFluids.WET_STEAM.source.get(), 0);
            if (sg.tank("primary_cold_out").getFluidAmount() != 0) throw new GameTestAssertException("Primaerwasser ohne Speisewasser abgekuehlt");
            drive.scram();
            h.succeed();
        });
    }

    private static BigMachineBlockEntity rodDrive(GameTestHelper h, BlockPos min) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.CONTROL_ROD_DRIVE, min);
        charge(be);
        fill(be, "hydraulic_in", Fluids.WATER, 4000);
        return be;
    }

    private static void rodAt(BigMachineBlockEntity be, String who, int want) {
        if (be.rod() != want) throw new GameTestAssertException(who + ": Stabstellung " + be.rod() + ", erwartet " + want);
    }

    @GameTest(template = FLOOR, batch = "floor_culling", timeoutTicks = 40)
    public static void formedPartsKeepFloorVisible(GameTestHelper h) {
        java.util.Set<String> ids = new java.util.TreeSet<>(MultiblockStructure.TURBINE.blockIds());
        for (BigMachine m : BigMachine.values()) ids.addAll(m.structure.blockIds());
        BlockPos ground = h.absolutePos(new BlockPos(7, 3, 7)), above = ground.above();
        h.getLevel().setBlock(ground, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        BlockState grass = h.getLevel().getBlockState(ground);
        List<String> bad = new java.util.ArrayList<>();
        for (String id : ids) {
            Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(id));
            BlockState st = b.defaultBlockState();
            if (st.hasProperty(ReactorProps.FORMED)) st = st.setValue(ReactorProps.FORMED, true);
            else if (st.getRenderShape() != net.minecraft.world.level.block.RenderShape.INVISIBLE) continue;
            h.getLevel().setBlock(above, st, 2 | 16);
            if (!Block.shouldRenderFace(grass, h.getLevel(), ground, net.minecraft.core.Direction.UP, above)) bad.add(id);
        }
        h.getLevel().setBlock(above, Blocks.AIR.defaultBlockState(), 2);
        if (!bad.isEmpty()) throw new GameTestAssertException("Boden darunter wird weggeschnitten bei: " + bad);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "floor_culling_all", timeoutTicks = 40)
    public static void partialModelsKeepFloorVisible(GameTestHelper h) {
        BlockPos ground = h.absolutePos(new BlockPos(7, 3, 7)), above = ground.above();
        h.getLevel().setBlock(ground, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        BlockState grass = h.getLevel().getBlockState(ground);
        List<String> bad = new java.util.ArrayList<>();
        for (var e : net.minecraft.core.registries.BuiltInRegistries.BLOCK.entrySet()) {
            var id = e.getKey().location();
            if (!id.getNamespace().equals(NuclearSQ.MODID) || e.getValue() instanceof net.minecraft.world.level.block.LiquidBlock) continue;
            if (!ModelCheck.hasPartialModel(id.getPath())) continue;
            for (BlockState st : e.getValue().getStateDefinition().getPossibleStates()) {
                h.getLevel().setBlock(above, st, 2 | 16);
                if (!Block.shouldRenderFace(grass, h.getLevel(), ground, Direction.UP, above)) {
                    bad.add(id.getPath());
                    break;
                }
            }
        }
        h.getLevel().setBlock(above, Blocks.AIR.defaultBlockState(), 2);
        if (!bad.isEmpty()) throw new GameTestAssertException("Boden darunter wird weggeschnitten bei: " + bad);
        h.succeed();
    }

    private static final class ModelCheck {
        static boolean hasPartialModel(String block) {
            com.google.gson.JsonObject bs = json("blockstates/" + block + ".json");
            if (bs == null) return false;
            List<String> models = new java.util.ArrayList<>();
            if (bs.has("variants")) for (var v : bs.getAsJsonObject("variants").entrySet()) collect(v.getValue(), models);
            if (bs.has("multipart")) for (var part : bs.getAsJsonArray("multipart")) collect(part.getAsJsonObject().get("apply"), models);
            for (String m : models) if (!full(m, 0)) return true;
            return false;
        }

        private static void collect(com.google.gson.JsonElement e, List<String> out) {
            if (e.isJsonArray()) for (var x : e.getAsJsonArray()) out.add(x.getAsJsonObject().get("model").getAsString());
            else out.add(e.getAsJsonObject().get("model").getAsString());
        }

        private static boolean full(String model, int depth) {
            if (!model.contains(":")) model = "minecraft:" + model;
            String ns = model.substring(0, model.indexOf(':')), path = model.substring(model.indexOf(':') + 1);
            if (!ns.equals(NuclearSQ.MODID)) return path.startsWith("block/cube") || path.startsWith("block/orientable");
            com.google.gson.JsonObject m = json("models/" + path + ".json");
            if (m == null || depth > 8) return true;
            if (m.has("loader") && m.get("loader").getAsString().equals("neoforge:obj")) {
                String obj = m.get("model").getAsString();
                return objBottom(obj.substring(obj.indexOf(':') + 1).replaceFirst("^models/", "")) > 0.999;
            }
            if (m.has("loader") && m.get("loader").getAsString().equals(NuclearSQ.MODID + ":cable")) {
                for (var part : m.getAsJsonArray("parts")) {
                    if (!full(part.getAsJsonObject().getAsJsonObject("apply").get("model").getAsString(), depth + 1)) return false;
                }
                return true;
            }
            if (m.has("elements")) {
                for (var el : m.getAsJsonArray("elements")) {
                    var o = el.getAsJsonObject();
                    var from = o.getAsJsonArray("from");
                    var to = o.getAsJsonArray("to");
                    if (!o.has("rotation") && o.getAsJsonObject("faces").has("down") && from.get(1).getAsDouble() == 0
                            && from.get(0).getAsDouble() == 0 && from.get(2).getAsDouble() == 0
                            && to.get(0).getAsDouble() == 16 && to.get(2).getAsDouble() == 16) return true;
                }
                return false;
            }
            return m.has("parent") && full(m.get("parent").getAsString(), depth + 1);
        }

        private static double objBottom(String path) {
            try (var in = NuclearGameTests.class.getResourceAsStream("/assets/" + NuclearSQ.MODID + "/models/" + path)) {
                if (in == null) return 1;
                List<double[]> v = new java.util.ArrayList<>();
                double area = 0;
                for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                    String[] t = line.trim().split("\\s+");
                    if (t[0].equals("v")) v.add(new double[] {Double.parseDouble(t[1]), Double.parseDouble(t[2]), Double.parseDouble(t[3])});
                    if (!t[0].equals("f")) continue;
                    List<double[]> poly = new java.util.ArrayList<>();
                    for (int i = 1; i < t.length; i++) poly.add(v.get(Integer.parseInt(t[i].split("/")[0]) - 1));
                    if (poly.stream().anyMatch(p -> Math.abs(p[1]) > 1e-4)) continue;
                    double a = 0;
                    for (int i = 0; i < poly.size(); i++) {
                        double[] p = poly.get(i), q = poly.get((i + 1) % poly.size());
                        a += p[0] * q[2] - q[0] * p[2];
                    }
                    area += Math.abs(a) / 2;
                }
                return area;
            } catch (java.io.IOException ex) {
                return 1;
            }
        }

        private static com.google.gson.JsonObject json(String path) {
            try (var in = NuclearGameTests.class.getResourceAsStream("/assets/" + NuclearSQ.MODID + "/" + path)) {
                if (in == null) return null;
                return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (java.io.IOException ex) {
                return null;
            }
        }
    }

    @GameTest(template = FLOOR, batch = "compendium_loop", timeoutTicks = 20)
    public static void reactorLoopPageIsComplete(GameTestHelper h) {
        var d = com.sqc.nuclearsq.progression.ReactorLoop.DIAGRAM;
        var nodes = d.nodes();
        int[] order = d.order();
        if (order.length != nodes.size()) h.fail("Reihenfolge hat " + order.length + " statt " + nodes.size() + " Eintraege");
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int k : order) {
            if (k < 0 || k >= nodes.size()) h.fail("Knoten " + k + " gibt es nicht");
            if (!seen.add(k)) h.fail("Knoten " + k + " doppelt in der Reihenfolge");
        }
        java.util.Set<String> kinds = new java.util.HashSet<>();
        java.util.Set<Integer> linked = new java.util.HashSet<>();
        for (var f : d.flows()) {
            if (f.from() < 0 || f.from() >= nodes.size() || f.to() < 0 || f.to() >= nodes.size() || f.from() == f.to())
                h.fail("Leitung " + f.kind() + " verbindet ungueltige Knoten");
            if (f.path().length < 4 || f.path().length % 2 != 0) h.fail("Leitung " + f.kind() + " hat keinen gueltigen Weg");
            kinds.add(f.kind());
            linked.add(f.from());
            linked.add(f.to());
        }
        for (int k = 0; k < nodes.size(); k++) {
            var n = nodes.get(k);
            if (!linked.contains(k)) h.fail(n.key() + " haengt an keiner Leitung");
            if (n.x() < 0 || n.x() > d.width() || n.y() < 0 || n.y() > d.height()) h.fail(n.key() + " liegt ausserhalb der Seite");
        }
        for (String lang : List.of("de_de", "en_us")) {
            com.google.gson.JsonObject l;
            try (var in = NuclearGameTests.class.getResourceAsStream("/assets/" + NuclearSQ.MODID + "/lang/" + lang + ".json")) {
                l = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception ex) {
                throw new net.minecraft.gametest.framework.GameTestAssertException(lang + " nicht lesbar: " + ex);
            }
            for (String k : List.of("compendium.energy_loop.title", "compendium.energy_loop.text", "compendium.loop.closed", "compendium.loop.replay", "compendium.loop.open"))
                if (!l.has(k)) h.fail(lang + ": " + k + " fehlt");
            for (var n : nodes) {
                for (String part : List.of("name", "short", "hint"))
                    if (!l.has("compendium.loop." + n.key() + "." + part)) h.fail(lang + ": compendium.loop." + n.key() + "." + part + " fehlt");
                if (!l.has("compendium." + n.page() + ".title")) h.fail(lang + ": Zielseite " + n.page() + " hat keinen Titel");
            }
            for (String kind : kinds)
                if (!l.has("compendium.loop.pwr_flow." + kind)) h.fail(lang + ": compendium.loop.pwr_flow." + kind + " fehlt");
        }
        for (var n : nodes) {
            var it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, n.item()));
            if (it == net.minecraft.world.item.Items.AIR) h.fail(n.key() + ": Item " + n.item() + " ist nicht registriert");
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "compendium_loop", timeoutTicks = 20)
    public static void primaryLoopPageIsComplete(GameTestHelper h) {
        var nodes = com.sqc.nuclearsq.progression.PrimaryLoop.NODES;
        var flows = com.sqc.nuclearsq.progression.PrimaryLoop.FLOWS;
        int[] order = com.sqc.nuclearsq.progression.PrimaryLoop.ORDER;
        if (order.length != nodes.size()) h.fail("Reihenfolge hat " + order.length + " statt " + nodes.size() + " Eintraege");
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int k : order) {
            if (k < 0 || k >= nodes.size()) h.fail("Knoten " + k + " gibt es nicht");
            if (!seen.add(k)) h.fail("Knoten " + k + " doppelt in der Reihenfolge");
        }
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (var n : nodes) {
            if (!keys.add(n.key())) h.fail("Knoten " + n.key() + " doppelt");
            if (n.x() < 0 || n.x() > com.sqc.nuclearsq.progression.PrimaryLoop.WIDTH || n.y() < 0 || n.y() > com.sqc.nuclearsq.progression.PrimaryLoop.HEIGHT)
                h.fail(n.key() + " liegt ausserhalb der Seite");
        }
        if (keys.contains("pwr_pressurizer")) h.fail("Druckhalter gehoert nicht mehr in den Grundkreis");
        for (String want : List.of("pwr_containment", "pwr_drive", "pwr_steam_generator", "pwr_pump", "pwr_emergency", "pwr_intake", "pwr_treatment", "pwr_separator", "turbine"))
            if (!keys.contains(want)) h.fail("Grundkreis ohne " + want);
        java.util.Set<String> kinds = new java.util.HashSet<>();
        for (var f : flows) {
            if (f.from() < 0 || f.from() >= nodes.size() || f.to() < 0 || f.to() >= nodes.size() || f.from() == f.to())
                h.fail("Leitung " + f.kind() + " verbindet ungueltige Knoten");
            if (f.path().length < 4 || f.path().length % 2 != 0) h.fail("Leitung " + f.kind() + " hat keinen gueltigen Weg");
            kinds.add(f.kind());
        }
        if (!kinds.contains("bigpipe")) h.fail("Grossrohr fehlt im Grundkreis");
        if (!kinds.contains("hydraulic")) h.fail("Hydraulik fehlt im Grundkreis");
        for (String lang : List.of("de_de", "en_us")) {
            com.google.gson.JsonObject l;
            try (var in = NuclearGameTests.class.getResourceAsStream("/assets/" + NuclearSQ.MODID + "/lang/" + lang + ".json")) {
                l = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception ex) {
                throw new net.minecraft.gametest.framework.GameTestAssertException(lang + " nicht lesbar: " + ex);
            }
            for (String k : List.of("compendium.reactor_pwr.title", "compendium.reactor_pwr.text", "compendium.reactor_pwr_loop.title",
                    "compendium.reactor_pwr_loop.text"))
                if (!l.has(k)) h.fail(lang + ": " + k + " fehlt");
            for (var n : nodes) {
                for (String part : List.of("name", "short", "hint"))
                    if (!l.has("compendium.loop." + n.key() + "." + part)) h.fail(lang + ": compendium.loop." + n.key() + "." + part + " fehlt");
                if (!l.has("compendium." + n.page() + ".title")) h.fail(lang + ": Zielseite " + n.page() + " hat keinen Titel");
            }
            for (String kind : kinds)
                if (!l.has("compendium.loop.pwr_flow." + kind)) h.fail(lang + ": compendium.loop.pwr_flow." + kind + " fehlt");
        }
        for (var n : nodes) {
            var it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, n.item()));
            if (it == net.minecraft.world.item.Items.AIR) h.fail(n.key() + ": Item " + n.item() + " ist nicht registriert");
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "compendium_loop", timeoutTicks = 20)
    public static void reactorFluidsPageIsComplete(GameTestHelper h) {
        var fluids = com.sqc.nuclearsq.progression.ReactorFluids.FLUIDS;
        java.util.Set<String> pages = CompendiumPageTests.pageIds();
        if (!pages.contains("reactor_fluids")) h.fail("Seite reactor_fluids fehlt in der Struktur");
        java.util.Set<String> keys = new java.util.HashSet<>(), machines = new java.util.TreeSet<>(), langKeys = new java.util.TreeSet<>();
        List<String> problems = new java.util.ArrayList<>();
        for (var f : fluids) {
            if (!keys.add(f.key())) problems.add("Stoff " + f.key() + " doppelt");
            var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.parse(f.fluid()));
            if (fluid == Fluids.EMPTY) { problems.add(f.key() + ": Fluid " + f.fluid() + " fehlt"); continue; }
            FluidStack fs = new FluidStack(fluid, 1);
            if (f.links().isEmpty()) problems.add(f.key() + ": kein Baum");
            if (!f.fluid().startsWith("minecraft:")) langKeys.add(f.nameKey());
            langKeys.add("compendium.fluids." + f.key() + ".text");
            for (var link : f.links()) {
                if (link.to().isEmpty()) problems.add(f.key() + ": " + link.from().machine() + " fuehrt nirgends hin");
                java.util.List<com.sqc.nuclearsq.progression.ReactorFluids.End> ends = new java.util.ArrayList<>(link.to());
                ends.add(0, link.from());
                for (int i = 0; i < ends.size(); i++) {
                    var e = ends.get(i);
                    boolean out = i == 0;
                    machines.add(e.machine());
                    String page = com.sqc.nuclearsq.progression.ReactorFluids.PAGES.get(e.machine());
                    if (page == null || !pages.contains(page)) problems.add(e.machine() + ": Seite " + page + " fehlt");
                    var it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID,
                            com.sqc.nuclearsq.progression.ReactorFluids.controllerItem(e.machine())));
                    if (it == net.minecraft.world.item.Items.AIR) problems.add(e.machine() + ": Controller-Item fehlt");
                    for (String pn : e.ports()) {
                        langKeys.add(com.sqc.nuclearsq.progression.ReactorFluids.portKey(e.machine(), pn));
                        String where = f.key() + ": " + e.machine() + "." + pn;
                        if (com.sqc.nuclearsq.progression.ReactorFluids.TURBINE.equals(e.machine())) {
                            if (out && !(pn.equals("water") && fluid == ModFluids.EXHAUST_STEAM.source.get())) problems.add(where + " gibt das nicht ab");
                            if (!out && !(pn.equals("steam") && fs.is(com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity.TURBINE_STEAM))) problems.add(where + " nimmt das nicht");
                            continue;
                        }
                        BigMachine m;
                        try {
                            m = BigMachine.valueOf(e.machine().toUpperCase(java.util.Locale.ROOT));
                        } catch (IllegalArgumentException ex) {
                            problems.add(where + ": Maschine unbekannt");
                            continue;
                        }
                        BigMachine.Port port = m.port(pn);
                        if (port == null) { problems.add(where + ": Anschluss fehlt"); continue; }
                        if (out ? !port.out() : !port.in()) problems.add(where + ": falsche Richtung " + port.dir());
                        if (!BigMachineBlockEntity.accepts(m, port).test(fs)) problems.add(where + ": Tank nimmt " + f.fluid() + " nicht");
                    }
                }
            }
        }
        for (String m : machines) langKeys.add("compendium.fluids.m." + m);
        langKeys.addAll(List.of("compendium.reactor_fluids.title", "compendium.reactor_fluids.text", "compendium.fluids.from", "compendium.fluids.to",
                "compendium.fluids.rate", "compendium.loop.open"));
        for (String lang : List.of("de_de", "en_us", "ru_ru")) {
            com.google.gson.JsonObject l;
            try (var in = NuclearGameTests.class.getResourceAsStream("/assets/" + NuclearSQ.MODID + "/lang/" + lang + ".json")) {
                l = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception ex) {
                throw new GameTestAssertException(lang + " nicht lesbar: " + ex);
            }
            for (String k : langKeys) if (!l.has(k)) problems.add(lang + ": " + k + " fehlt");
        }
        if (!problems.isEmpty()) throw new GameTestAssertException(problems.size() + " Probleme: " + String.join("; ", problems));
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "compendium_plant", timeoutTicks = 20)
    public static void plantOverviewIsComplete(GameTestHelper h) {
        var nodes = com.sqc.nuclearsq.progression.PlantOverview.NODES;
        var edges = com.sqc.nuclearsq.progression.PlantOverview.EDGES;
        java.util.Set<String> keys = new java.util.HashSet<>(), cells = new java.util.HashSet<>();
        for (var n : nodes) {
            if (!keys.add(n.key())) h.fail("Knoten " + n.key() + " doppelt");
            if (!cells.add(n.col() + "," + n.row())) h.fail(n.key() + ": Feld " + n.col() + "," + n.row() + " schon belegt");
            var it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, n.item()));
            if (it == net.minecraft.world.item.Items.AIR) h.fail(n.key() + ": Item " + n.item() + " ist nicht registriert");
        }
        List<String> items = new java.util.ArrayList<>();
        for (var n : nodes) items.add(n.item());
        for (var n : com.sqc.nuclearsq.progression.ReactorLoop.NODES) items.add(n.item());
        for (var n : com.sqc.nuclearsq.progression.PrimaryLoop.NODES) items.add(n.item());
        for (var n : com.sqc.nuclearsq.progression.AssemblyTree.NODES) items.add(n.item());
        for (String item : items) {
            if (com.sqc.nuclearsq.progression.PlantOverview.ICON_ONLY.contains(item)) continue;
            for (String suffix : List.of("", "_small")) {
                String path = "/assets/" + NuclearSQ.MODID + "/textures/gui/compendium/machines/" + item + suffix + ".png";
                try (var in = NuclearGameTests.class.getResourceAsStream(path)) {
                    if (in == null) h.fail("Kompendium-Bild fehlt: " + path + " (python tools/gen_compendium_thumbs.py)");
                } catch (java.io.IOException ex) {
                    h.fail(path + " nicht lesbar: " + ex);
                }
            }
        }
        requireStartPower(h, nodes, "Weg zum Strom");
        boolean[] linked = new boolean[nodes.size()];
        for (var e : edges) {
            if (e.from() == e.to()) h.fail("Linie von " + nodes.get(e.from()).key() + " auf sich selbst");
            linked[e.from()] = linked[e.to()] = true;
        }
        for (int k = 0; k < nodes.size(); k++) if (!linked[k]) h.fail(nodes.get(k).key() + ": ohne Linie");
        for (String lang : List.of("de_de", "en_us")) {
            com.google.gson.JsonObject l;
            try (var in = NuclearGameTests.class.getResourceAsStream("/assets/" + NuclearSQ.MODID + "/lang/" + lang + ".json")) {
                l = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception ex) {
                throw new net.minecraft.gametest.framework.GameTestAssertException(lang + " nicht lesbar: " + ex);
            }
            List<String> common = new java.util.ArrayList<>(List.of("compendium.intro_roadmap.title", "compendium.intro_roadmap.text", "compendium.plant.from",
                    "compendium.plant.to", "compendium.plant.open", "compendium.plant.replay", "compendium.plant.reset", "compendium.plant.closed"));
            for (var gr : com.sqc.nuclearsq.progression.PlantOverview.Group.values())
                common.add("compendium.plant.group." + gr.name().toLowerCase(java.util.Locale.ROOT));
            for (String k : common) if (!l.has(k)) h.fail(lang + ": " + k + " fehlt");
            for (var n : nodes) {
                for (String part : List.of("name", "short"))
                    if (!l.has("compendium.plant." + n.key() + "." + part)) h.fail(lang + ": compendium.plant." + n.key() + "." + part + " fehlt");
                if (!l.has("compendium." + n.page() + ".title")) h.fail(lang + ": Zielseite " + n.page() + " hat keinen Titel");
            }
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "compendium_plant", timeoutTicks = 20)
    public static void assemblyTreeIsComplete(GameTestHelper h) {
        var nodes = com.sqc.nuclearsq.progression.AssemblyTree.NODES;
        var edges = com.sqc.nuclearsq.progression.AssemblyTree.EDGES;
        java.util.Set<String> keys = new java.util.HashSet<>(), cells = new java.util.HashSet<>(), items = new java.util.HashSet<>();
        for (var n : nodes) {
            if (!keys.add(n.key())) h.fail("Knoten " + n.key() + " doppelt");
            if (!cells.add(n.col() + "," + n.row())) h.fail(n.key() + ": Feld " + n.col() + "," + n.row() + " schon belegt");
            items.add(n.item());
            var it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, n.item()));
            if (it == net.minecraft.world.item.Items.AIR) h.fail(n.key() + ": Item " + n.item() + " ist nicht registriert");
        }
        for (var id : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) {
            if (!id.getNamespace().equals(NuclearSQ.MODID) || !id.getPath().endsWith("_assembler_controller_sqc")) continue;
            if (!items.contains(id.getPath())) h.fail(id.getPath() + " fehlt im Assembler-Baum");
        }
        for (var n : com.sqc.nuclearsq.progression.PlantOverview.NODES)
            if (n.item().contains("assembler")) h.fail(n.key() + ": Assembler gehoert in den Assembler-Baum, nicht in den Weg zum Strom");
        requireStartPower(h, nodes, "Assembler-Baum");
        boolean[] linked = new boolean[nodes.size()];
        for (var e : edges) {
            if (e.from() == e.to()) h.fail("Linie von " + nodes.get(e.from()).key() + " auf sich selbst");
            if (e.ring()) h.fail("Assembler-Baum hat keinen Kreislauf");
            linked[e.from()] = linked[e.to()] = true;
        }
        for (int k = 0; k < nodes.size(); k++) if (!linked[k]) h.fail(nodes.get(k).key() + ": ohne Linie");
        for (String lang : List.of("de_de", "en_us")) {
            com.google.gson.JsonObject l;
            try (var in = NuclearGameTests.class.getResourceAsStream("/assets/" + NuclearSQ.MODID + "/lang/" + lang + ".json")) {
                l = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception ex) {
                throw new net.minecraft.gametest.framework.GameTestAssertException(lang + " nicht lesbar: " + ex);
            }
            for (String k : List.of("compendium.intro_assembly.title", "compendium.intro_assembly.text", "compendium.toggle.tips", "compendium.toggle.items"))
                if (!l.has(k)) h.fail(lang + ": " + k + " fehlt");
            for (var n : nodes) {
                for (String part : List.of("name", "short"))
                    if (!l.has("compendium.plant." + n.key() + "." + part)) h.fail(lang + ": compendium.plant." + n.key() + "." + part + " fehlt");
                if (!l.has("compendium." + n.page() + ".title")) h.fail(lang + ": Zielseite " + n.page() + " hat keinen Titel");
            }
        }
        h.succeed();
    }

    private static void requireStartPower(GameTestHelper h, List<com.sqc.nuclearsq.progression.PlantOverview.Node> nodes, String tree) {
        java.util.Set<String> items = new java.util.HashSet<>();
        for (var n : nodes) items.add(n.item());
        for (var n : com.sqc.nuclearsq.progression.AchievementTree.NODES)
            if (n.group() == com.sqc.nuclearsq.progression.PlantOverview.Group.START && !items.contains(n.item()))
                h.fail(n.item() + " fehlt im " + tree);
    }

    @GameTest(template = FLOOR, batch = "compendium_plant", timeoutTicks = 20)
    public static void secretWeaponsTreeIsComplete(GameTestHelper h) {
        var nodes = com.sqc.nuclearsq.progression.SecretWeaponsTree.NODES;
        var edges = com.sqc.nuclearsq.progression.SecretWeaponsTree.EDGES;
        java.util.Set<String> keys = new java.util.HashSet<>(), cells = new java.util.HashSet<>(), items = new java.util.HashSet<>();
        for (var n : nodes) {
            if (!keys.add(n.key())) h.fail("Knoten " + n.key() + " doppelt");
            if (!cells.add(n.col() + "," + n.row())) h.fail(n.key() + ": Feld " + n.col() + "," + n.row() + " schon belegt");
            items.add(n.item());
            var it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, n.item()));
            if (it == net.minecraft.world.item.Items.AIR) h.fail(n.key() + ": Item " + n.item() + " ist nicht registriert");
        }
        for (var id : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) {
            if (!id.getNamespace().equals(NuclearSQ.MODID) || !id.getPath().startsWith("nuclear_bomb")) continue;
            if (!items.contains(id.getPath())) h.fail(id.getPath() + " fehlt im Secret-Weapons-Baum");
        }
        List<com.sqc.nuclearsq.progression.PlantOverview.Node> others = new java.util.ArrayList<>(com.sqc.nuclearsq.progression.AchievementTree.NODES);
        others.addAll(com.sqc.nuclearsq.progression.AssemblyTree.NODES);
        others.addAll(com.sqc.nuclearsq.progression.PlantOverview.NODES);
        for (var n : others)
            if (n.group() == com.sqc.nuclearsq.progression.PlantOverview.Group.WEAPON) h.fail(n.key() + ": Waffe gehoert in den Secret-Weapons-Baum");
        boolean[] linked = new boolean[nodes.size()];
        for (var e : edges) {
            if (e.from() == e.to()) h.fail("Linie von " + nodes.get(e.from()).key() + " auf sich selbst");
            linked[e.from()] = linked[e.to()] = true;
        }
        for (int k = 0; k < nodes.size(); k++) if (!linked[k]) h.fail(nodes.get(k).key() + ": ohne Linie");
        for (String lang : List.of("de_de", "en_us")) {
            com.google.gson.JsonObject l;
            try (var in = NuclearGameTests.class.getResourceAsStream("/assets/" + NuclearSQ.MODID + "/lang/" + lang + ".json")) {
                l = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception ex) {
                throw new net.minecraft.gametest.framework.GameTestAssertException(lang + " nicht lesbar: " + ex);
            }
            for (String k : List.of("compendium.weapons.chapter", "compendium.weapons_overview.title", "compendium.weapons_overview.text",
                    "compendium.weapons_arming.title", "compendium.weapons_arming.text"))
                if (!l.has(k)) h.fail(lang + ": " + k + " fehlt");
            for (String s : List.of("grp_start", "grp_reactor", "grp_chem", "grp_energy", "grp_manufacturing", "grp_robotic", "grp_computing", "protection", "achievements", "weapons"))
                if (!l.has("compendium.section." + s)) h.fail(lang + ": compendium.section." + s + " fehlt");
            for (var n : nodes) {
                for (String part : List.of("name", "short"))
                    if (!l.has("compendium.plant." + n.key() + "." + part)) h.fail(lang + ": compendium.plant." + n.key() + "." + part + " fehlt");
                if (!l.has("compendium." + n.page() + ".title") || !l.has("compendium." + n.page() + ".text")) h.fail(lang + ": Seite " + n.page() + " ohne Titel oder Text");
                if (!l.has("compendium.plant.group." + n.group().name().toLowerCase(java.util.Locale.ROOT))) h.fail(lang + ": Gruppe " + n.group() + " ohne Namen");
            }
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "vein_miner", timeoutTicks = 40)
    public static void veinMinerBreaksConnectedBlocks(GameTestHelper h) {
        List<BlockPos> vein = List.of(new BlockPos(3, 2, 3), new BlockPos(4, 2, 3), new BlockPos(5, 3, 4), new BlockPos(6, 3, 4));
        BlockPos other = new BlockPos(4, 2, 4), apart = new BlockPos(10, 2, 10);
        for (BlockPos p : vein) h.setBlock(p, Blocks.COPPER_ORE);
        h.setBlock(other, Blocks.IRON_ORE);
        h.setBlock(apart, Blocks.COPPER_ORE);
        net.minecraft.server.level.ServerPlayer sp = AuditFixTests.testPlayer(h);
        boolean was = com.sqc.nuclearsq.NuclearConfig.VEIN_MINER.get();
        com.sqc.nuclearsq.NuclearConfig.VEIN_MINER.set(true);
        try {
            com.sqc.nuclearsq.VeinMiner.setHeld(sp, true);
            sp.gameMode.destroyBlock(h.absolutePos(vein.get(0)));
        } finally {
            com.sqc.nuclearsq.VeinMiner.setHeld(sp, false);
            com.sqc.nuclearsq.NuclearConfig.VEIN_MINER.set(was);
        }
        h.succeedWhen(() -> {
            for (BlockPos p : vein) h.assertBlockNotPresent(Blocks.COPPER_ORE, p);
            h.assertBlockPresent(Blocks.IRON_ORE, other);
            h.assertBlockPresent(Blocks.COPPER_ORE, apart);
        });
    }

    @GameTest(template = FLOOR, batch = "transport_energy", timeoutTicks = 200)
    public static void cablesCarryPower(GameTestHelper h) {
        Block cable = com.sqc.nuclearsq.transport.TransportRegistry.CABLES.get(com.sqc.nuclearsq.transport.TransportTier.BASIC).get();
        BlockPos src = new BlockPos(2, 4, 7);
        h.setBlock(src, com.sqc.nuclearsq.registry.TestSourceRegistry.ENERGY_SOURCE.get());
        for (int x = 3; x <= 5; x++) h.setBlock(new BlockPos(x, 4, 7), cable);
        BigMachineBlockEntity battery = TestSinks.battery(h, new BlockPos(6, 4, 7), Direction.WEST);
        h.runAfterDelay(60, () -> {
            BlockPos mid = h.absolutePos(new BlockPos(4, 4, 7)), first = h.absolutePos(new BlockPos(3, 4, 7));
            if (com.sqc.nuclearsq.transport.ConduitBlock.sideAt(h.getLevel(), mid, Direction.EAST) != com.sqc.nuclearsq.transport.ConduitSide.ON
                    || com.sqc.nuclearsq.transport.ConduitBlock.sideAt(h.getLevel(), mid, Direction.WEST) != com.sqc.nuclearsq.transport.ConduitSide.ON) {
                throw new GameTestAssertException("Kabel verbinden sich nicht: " + h.getLevel().getBlockState(mid));
            }
            if (com.sqc.nuclearsq.transport.ConduitBlock.sideAt(h.getLevel(), first, Direction.WEST) != com.sqc.nuclearsq.transport.ConduitSide.PORT) {
                throw new GameTestAssertException("Kabel an der Stromquelle zeigt keinen Anschluss (Klemmenkasten): " + h.getLevel().getBlockState(first));
            }
            if (battery.energy().getEnergyStored() <= 0) throw new GameTestAssertException("Strom kommt nicht ueber die Kabel im Akku an");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "transport_cable_states", timeoutTicks = 20)
    public static void conduitStatesStayFew(GameTestHelper h) {
        for (var map : List.of(com.sqc.nuclearsq.transport.TransportRegistry.CABLES, com.sqc.nuclearsq.transport.TransportRegistry.PIPES,
                com.sqc.nuclearsq.transport.TransportRegistry.ITEM_PIPES)) {
            for (var entry : map.entrySet()) {
                Block b = entry.getValue().get();
                int want = b instanceof com.sqc.nuclearsq.transport.CableBlock ? 12 : 2;
                if (b.getStateDefinition().getPossibleStates().size() != want) {
                    throw new GameTestAssertException(b + ": " + b.getStateDefinition().getPossibleStates().size() + " Zustaende statt " + want);
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "transport_cable_mount", timeoutTicks = 60)
    public static void cablesMountOnWallsAndCeilings(GameTestHelper h) {
        var cable = (com.sqc.nuclearsq.transport.CableBlock) com.sqc.nuclearsq.transport.TransportRegistry.CABLES
                .get(com.sqc.nuclearsq.transport.TransportTier.BASIC).get();
        var mount = com.sqc.nuclearsq.transport.CableBlock.MOUNT;
        var on = com.sqc.nuclearsq.transport.ConduitSide.ON;
        var port = com.sqc.nuclearsq.transport.ConduitSide.PORT;

        BlockPos wall = new BlockPos(3, 4, 6);
        h.setBlock(wall, Blocks.STONE);
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        BlockPos absWall = h.absolutePos(wall);
        var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(absWall).add(0, 0, 0.5), Direction.SOUTH, absWall, false);
        var ctx = new net.minecraft.world.item.context.BlockPlaceContext(h.getLevel(), player,
                net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(cable), hit);
        BlockState placed = cable.getStateForPlacement(ctx);
        if (placed == null || placed.getValue(mount) != Direction.NORTH) throw new GameTestAssertException("Klick an die Wand montiert nicht an der Wand: " + placed);
        BlockPos roof = new BlockPos(8, 6, 3);
        h.setBlock(roof, Blocks.STONE);
        BlockPos absRoof = h.absolutePos(roof);
        var ctxUp = new net.minecraft.world.item.context.BlockPlaceContext(h.getLevel(), player,
                net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(cable),
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(absRoof).add(0, -0.5, 0), Direction.DOWN, absRoof, false));
        BlockState up = cable.getStateForPlacement(ctxUp);
        if (up == null || up.getValue(mount) != Direction.UP) throw new GameTestAssertException("Klick an die Decke montiert nicht an der Decke: " + up);

        for (int x = 3; x <= 5; x++) h.setBlock(new BlockPos(x, 4, 7), cable.defaultBlockState().setValue(mount, Direction.NORTH));
        h.setBlock(new BlockPos(6, 4, 7), cable.defaultBlockState());
        h.runAfterDelay(5, () -> {
            BlockState mid = h.getBlockState(new BlockPos(4, 4, 7));
            if (mid.getValue(mount) != Direction.NORTH) throw new GameTestAssertException("Montage geht beim Verbinden verloren: " + mid);
            BlockPos midAbs = h.absolutePos(new BlockPos(4, 4, 7)), lastAbs = h.absolutePos(new BlockPos(5, 4, 7)), floorAbs = h.absolutePos(new BlockPos(6, 4, 7));
            if (com.sqc.nuclearsq.transport.ConduitBlock.sideAt(h.getLevel(), midAbs, Direction.EAST) != on || com.sqc.nuclearsq.transport.ConduitBlock.sideAt(h.getLevel(), midAbs, Direction.WEST) != on) {
                throw new GameTestAssertException("Wandkabel verbinden sich nicht: " + mid);
            }
            if (com.sqc.nuclearsq.transport.ConduitBlock.sideAt(h.getLevel(), lastAbs, Direction.EAST) != port) throw new GameTestAssertException("Wand -> Boden ohne Klemmenkasten: " + h.getBlockState(new BlockPos(5, 4, 7)));
            if (com.sqc.nuclearsq.transport.ConduitBlock.sideAt(h.getLevel(), floorAbs, Direction.WEST) != port) throw new GameTestAssertException("Boden -> Wand ohne Klemmenkasten: " + h.getBlockState(new BlockPos(6, 4, 7)));
            AABB box = mid.getShape(h.getLevel(), h.absolutePos(new BlockPos(4, 4, 7))).bounds();
            if (box.minZ > 0.01 || box.maxZ > 0.5 || box.minY < 0.2) throw new GameTestAssertException("Trefferbox liegt nicht an der Wand: " + box);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "transport_cable_edge", timeoutTicks = 60)
    public static void cablesBendAroundWallToFloorEdge(GameTestHelper h) {
        var cable = com.sqc.nuclearsq.transport.TransportRegistry.CABLES.get(com.sqc.nuclearsq.transport.TransportTier.BASIC).get();
        var mount = com.sqc.nuclearsq.transport.CableBlock.MOUNT;
        var on = com.sqc.nuclearsq.transport.ConduitSide.ON;
        var port = com.sqc.nuclearsq.transport.ConduitSide.PORT;
        BlockPos floor = new BlockPos(4, 4, 7), wall = floor.above();
        h.setBlock(floor.north(), Blocks.STONE);
        h.setBlock(wall.north(), Blocks.STONE);
        h.setBlock(floor.south(), cable.defaultBlockState());
        h.setBlock(floor, cable.defaultBlockState());
        h.setBlock(wall, cable.defaultBlockState().setValue(mount, Direction.NORTH));
        BlockPos roof = new BlockPos(8, 5, 7), wall2 = roof.below();
        h.setBlock(roof.above(), Blocks.STONE);
        h.setBlock(roof.north(), Blocks.STONE);
        h.setBlock(wall2.north(), Blocks.STONE);
        h.setBlock(roof, cable.defaultBlockState().setValue(mount, Direction.UP));
        h.setBlock(wall2, cable.defaultBlockState().setValue(mount, Direction.NORTH));
        h.runAfterDelay(2, () -> {
            var lv = h.getLevel();
            BlockPos fa = h.absolutePos(floor), wa = h.absolutePos(wall), ra = h.absolutePos(roof), w2a = h.absolutePos(wall2);
            BlockState f = h.getBlockState(floor);
            if (com.sqc.nuclearsq.transport.ConduitBlock.sideAt(lv, fa, Direction.UP) != port || com.sqc.nuclearsq.transport.ConduitBlock.sideAt(lv, fa, Direction.NORTH) != port
                    || com.sqc.nuclearsq.transport.ConduitBlock.sideAt(lv, fa, Direction.SOUTH) != on) {
                throw new GameTestAssertException("Bodenkabel laeuft nicht um die Kante: " + f);
            }
            if (com.sqc.nuclearsq.transport.CableBlock.edgeSide(lv, fa) != Direction.NORTH) throw new GameTestAssertException("Kante nicht erkannt: " + f);
            if (com.sqc.nuclearsq.transport.ConduitBlock.sideAt(lv, wa, Direction.DOWN) != on) throw new GameTestAssertException("Wandkabel mit Klemmenkasten statt Kante: " + h.getBlockState(wall));
            if (com.sqc.nuclearsq.transport.CableBlock.edgeSide(lv, ra) != Direction.NORTH || com.sqc.nuclearsq.transport.ConduitBlock.sideAt(lv, ra, Direction.DOWN) != port) {
                throw new GameTestAssertException("Deckenkabel laeuft nicht um die Kante: " + h.getBlockState(roof));
            }
            if (com.sqc.nuclearsq.transport.ConduitBlock.sideAt(lv, w2a, Direction.UP) != on) throw new GameTestAssertException("Wandkabel unter der Decke mit Klemmenkasten: " + h.getBlockState(wall2));
            AABB box = f.getShape(h.getLevel(), h.absolutePos(floor)).bounds();
            if (box.maxY < 0.99 || box.minZ > 0.01) throw new GameTestAssertException("Trefferbox der Kante zu klein: " + box);
            h.setBlock(wall, Blocks.AIR);
            BlockState f2 = h.getBlockState(floor);
            if (com.sqc.nuclearsq.transport.CableBlock.edgeSide(lv, fa) != null
                    || com.sqc.nuclearsq.transport.ConduitBlock.sideAt(lv, fa, Direction.UP) != com.sqc.nuclearsq.transport.ConduitSide.NONE) {
                throw new GameTestAssertException("Kante bleibt nach dem Abbau: " + f2);
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "transport_cable_dye", timeoutTicks = 60)
    public static void cablesIgnoreDye(GameTestHelper h) {
        Block cable = com.sqc.nuclearsq.transport.TransportRegistry.CABLES.get(com.sqc.nuclearsq.transport.TransportTier.BASIC).get();
        BlockPos pos = new BlockPos(4, 4, 7);
        h.setBlock(pos, cable);
        BlockPos abs = h.absolutePos(pos);
        BlockState before = h.getBlockState(pos);
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack red = new ItemStack(net.minecraft.world.item.Items.RED_DYE);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, red);
        var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        var result = before.useItemOn(red, h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        if (result != net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            throw new GameTestAssertException("Farbstoff wirkt noch auf Kabel: " + result);
        }
        if (red.getCount() != 1) throw new GameTestAssertException("Farbstoff wurde verbraucht");
        if (h.getBlockState(pos) != before) throw new GameTestAssertException("Kabelzustand geaendert: " + h.getBlockState(pos));
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "transport_fluid", timeoutTicks = 240)
    public static void pipesCarryFluidAndPull(GameTestHelper h) {
        Block pipe = com.sqc.nuclearsq.transport.TransportRegistry.PIPES.get(com.sqc.nuclearsq.transport.TransportTier.BASIC).get();
        BlockPos src = new BlockPos(2, 4, 7), treat = new BlockPos(6, 4, 7), out1 = new BlockPos(2, 4, 8), out2 = new BlockPos(2, 4, 9);
        h.setBlock(src, com.sqc.nuclearsq.registry.TestSourceRegistry.FLUID_SOURCE.get());
        for (int x = 3; x <= 5; x++) h.setBlock(new BlockPos(x, 4, 7), pipe);
        h.setBlock(treat, com.sqc.nuclearsq.radiation.ProtectionRegistry.SHOWER.get());
        com.sqc.nuclearsq.radiation.DeconShowerBlockEntity t = h.getBlockEntity(treat);
        h.runAfterDelay(40, () -> {
            if (!t.tank().getFluid().is(net.minecraft.world.level.material.Fluids.WATER) || t.tank().getFluidAmount() <= 0) {
                throw new GameTestAssertException("Rohre bringen kein Wasser in die Dusche: " + t.tank().getFluidAmount());
            }
            h.setBlock(out1, pipe);
            h.setBlock(out2, pipe);
            com.sqc.nuclearsq.transport.ConduitBlockEntity p1 = h.getBlockEntity(out1);
            if (p1.mode(net.minecraft.core.Direction.NORTH) != com.sqc.nuclearsq.transport.ConduitBlockEntity.PULL) {
                p1.cycle(net.minecraft.core.Direction.NORTH, true);
            }
            ((com.sqc.nuclearsq.transport.ConduitBlock) pipe).refresh(h.getLevel(), h.absolutePos(out1));
            h.runAfterDelay(140, () -> {
                if (com.sqc.nuclearsq.transport.ConduitBlock.sideAt(h.getLevel(), h.absolutePos(out1), net.minecraft.core.Direction.NORTH)
                        != com.sqc.nuclearsq.transport.ConduitSide.PULL) {
                    throw new GameTestAssertException("Saug-Seite wird nicht angezeigt: " + h.getBlockState(out1));
                }
                com.sqc.nuclearsq.transport.ConduitBlockEntity p2 = h.getBlockEntity(out2);
                FluidStack got = p1.fluid().isEmpty() ? p2.fluid() : p1.fluid();
                if (!got.is(net.minecraft.world.level.material.Fluids.WATER)) {
                    throw new GameTestAssertException("Saug-Rohr holt kein Wasser: " + got.getFluid() + " " + got.getAmount()
                            + ", Tank der Dusche " + t.tank().getFluidAmount());
                }
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "transport_fluid_branch", timeoutTicks = 140)
    public static void pipesSkipDeadEndBranch(GameTestHelper h) {
        Block pipe = com.sqc.nuclearsq.transport.TransportRegistry.PIPES.get(com.sqc.nuclearsq.transport.TransportTier.BASIC).get();
        BlockPos src = new BlockPos(2, 4, 2), shower = new BlockPos(6, 4, 2), b1 = new BlockPos(4, 4, 3), b2 = new BlockPos(4, 4, 4);
        h.setBlock(src, com.sqc.nuclearsq.registry.TestSourceRegistry.FLUID_SOURCE.get());
        for (int x = 3; x <= 5; x++) h.setBlock(new BlockPos(x, 4, 2), pipe);
        h.setBlock(b1, pipe);
        h.setBlock(b2, pipe);
        h.setBlock(shower, com.sqc.nuclearsq.radiation.ProtectionRegistry.SHOWER.get());
        com.sqc.nuclearsq.radiation.DeconShowerBlockEntity t = h.getBlockEntity(shower);
        int[] stray = {0};
        h.onEachTick(() -> {
            com.sqc.nuclearsq.transport.ConduitBlockEntity p1 = h.getBlockEntity(b1), p2 = h.getBlockEntity(b2);
            stray[0] = Math.max(stray[0], p1.fluid().getAmount() + p2.fluid().getAmount());
        });
        h.runAfterDelay(100, () -> {
            if (t.tank().getFluidAmount() <= 0) throw new GameTestAssertException("Rohre bringen kein Wasser in die Dusche");
            if (stray[0] > 0) throw new GameTestAssertException("Wasser laeuft in den toten Abzweig: " + stray[0] + " mB");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "uranium_mill", timeoutTicks = 400)
    public static void oreMillMakesYellowcake(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.ORE_MILL, new BlockPos(5, 4, 4));
        charge(be);
        var auto = be.itemsAt("item_in");
        if (auto.insertItem(0, new ItemStack(ReactorRegistry.YELLOWCAKE.get()), true).isEmpty()) throw new GameTestAssertException("Muehle nimmt Yellowcake an");
        if (!auto.insertItem(0, new ItemStack(ReactorRegistry.URANIUM_FRAGMENT.get()), false).isEmpty()) throw new GameTestAssertException("Muehle nimmt kein Uran-Fragment");
        h.runAfterDelay(260, () -> {
            formed(be);
            int yc = 0;
            for (int i = 1; i <= 4; i++) if (be.items().getStackInSlot(i).is(ReactorRegistry.YELLOWCAKE.get())) yc += be.items().getStackInSlot(i).getCount();
            if (yc < 1 || !be.items().getStackInSlot(0).isEmpty()) throw new GameTestAssertException("Yellowcake " + yc + ", Eingang " + be.items().getStackInSlot(0));
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "repository", timeoutTicks = 200)
    public static void repositoryStoresWaste(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.WASTE_REPOSITORY, new BlockPos(5, 4, 4));
        charge(be);
        var auto = be.itemsAt("items");
        if (auto.insertItem(0, new ItemStack(Blocks.DIRT), true).isEmpty()) throw new GameTestAssertException("Endlager nimmt Erde an");
        ItemStack residue = new ItemStack(CatalogRegistry.ITEM_MAP.get("filter_residue_sqc").get(), 8);
        if (!auto.insertItem(0, residue, false).isEmpty()) throw new GameTestAssertException("Endlager nimmt keinen Filterrueckstand");
        if (!auto.extractItem(0, 1, false).isEmpty()) throw new GameTestAssertException("Aus dem Endlager laesst sich etwas herausnehmen");
        h.runAfterDelay(100, () -> {
            formed(be);
            if (be.disposed() != 8 || !be.items().getStackInSlot(0).isEmpty()) {
                throw new GameTestAssertException("Eingelagert " + be.disposed() + " statt 8, Puffer " + be.items().getStackInSlot(0));
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "uranium_mill_dust", timeoutTicks = 400)
    public static void oreMillMakesDust(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.ORE_MILL, new BlockPos(5, 4, 4));
        charge(be);
        if (!be.itemsAt("item_in").insertItem(0, new ItemStack(net.minecraft.world.item.Items.RAW_IRON), false).isEmpty()) {
            throw new GameTestAssertException("Muehle nimmt kein Roheisen");
        }
        var smelt = h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING,
                new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(ReactorRegistry.IRON_DUST.get())), h.getLevel());
        if (smelt.isEmpty() || !smelt.get().value().getResultItem(h.getLevel().registryAccess()).is(net.minecraft.world.item.Items.IRON_INGOT)) {
            throw new GameTestAssertException("Eisenstaub schmilzt nicht zu Eisenbarren");
        }
        h.runAfterDelay(260, () -> {
            formed(be);
            int dust = 0;
            for (int i = 1; i <= 4; i++) if (be.items().getStackInSlot(i).is(ReactorRegistry.IRON_DUST.get())) dust += be.items().getStackInSlot(i).getCount();
            if (dust < 2 || dust > 3 || !be.items().getStackInSlot(0).isEmpty()) throw new GameTestAssertException("Eisenstaub " + dust + ", Eingang " + be.items().getStackInSlot(0));
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "uranium_centrifuge", timeoutTicks = 300)
    public static void centrifugeEnrichesYellowcake(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.ENRICHMENT_CENTRIFUGE, new BlockPos(5, 4, 4));
        charge(be);
        var auto = be.itemsAt("item_in");
        if (auto.insertItem(0, new ItemStack(ReactorRegistry.URANIUM_FRAGMENT.get()), true).isEmpty()) throw new GameTestAssertException("Zentrifuge nimmt Erz an");
        auto.insertItem(0, new ItemStack(ReactorRegistry.YELLOWCAKE.get(), 2), false);
        h.runAfterDelay(240, () -> {
            formed(be);
            boolean ok = false;
            for (int i = 1; i <= 4; i++) ok |= be.items().getStackInSlot(i).is(ReactorRegistry.ENRICHED_URANIUM.get());
            if (!ok || !be.items().getStackInSlot(0).isEmpty()) throw new GameTestAssertException("Kein angereichertes Uran (Zustand " + be.crafter().status() + ")");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "kit_place", timeoutTicks = 60)
    public static void kitPlacesAndDismantlesMachine(GameTestHelper h) {
        MachineKits.Def def = MachineKits.def("main_coolant_pump");
        MachineKits.Placement pl = MachineKits.placement(def, h.absolutePos(new BlockPos(7, 4, 4)), net.minecraft.core.Direction.SOUTH);
        if (!MachineKits.blocked(h.getLevel(), pl, null).isEmpty()) throw new GameTestAssertException("Platz gilt als belegt");
        MachineKits.place(h.getLevel(), pl);
        h.runAfterDelay(10, () -> {
            if (!(h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("Kein Controller aufgestellt");
            formed(be);
            BlockPos part = null;
            for (MultiblockStructure.Cell c : pl.cells()) if (!c.pos().equals(pl.anchor())) { part = c.pos(); break; }
            if (MachineKits.owner(h.getLevel(), part) == null) throw new GameTestAssertException("Teil gehoert zu keiner Maschine");
            net.minecraft.server.level.ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
            player.gameMode.destroyBlock(part);
            for (BlockPos p : pl.footprint()) {
                if (!h.getLevel().getBlockState(p).isAir()) throw new GameTestAssertException("Rest bei " + p + ": " + h.getLevel().getBlockState(p));
            }
            boolean kit = !h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pl.anchor()).inflate(6),
                    e -> e.getItem().is(KitRegistry.KITS.get("main_coolant_pump").get())).isEmpty();
            if (!kit) throw new GameTestAssertException("Kit faellt nicht heraus");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "kit_blocked", timeoutTicks = 20)
    public static void kitRefusesBlockedSpace(GameTestHelper h) {
        MachineKits.Placement pl = MachineKits.placement(MachineKits.def("main_coolant_pump"), h.absolutePos(new BlockPos(7, 4, 4)), net.minecraft.core.Direction.SOUTH);
        List<BlockPos> fp = pl.footprint();
        h.getLevel().setBlockAndUpdate(fp.get(0), Blocks.WATER.defaultBlockState());
        if (!MachineKits.blocked(h.getLevel(), pl, null).isEmpty()) throw new GameTestAssertException("Wasser blockiert");
        h.getLevel().setBlockAndUpdate(fp.get(fp.size() - 1), Blocks.STONE.defaultBlockState());
        List<BlockPos> b = MachineKits.blocked(h.getLevel(), pl, null);
        if (b.size() != 1 || !b.get(0).equals(fp.get(fp.size() - 1))) throw new GameTestAssertException("Blockiert: " + b);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "transport_tab", timeoutTicks = 20)
    public static void transportInTab(GameTestHelper h) {
        var tab = com.sqc.nuclearsq.registry.ModCreativeTabs.TAB.get();
        tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(
                h.getLevel().enabledFeatures(), true, h.getLevel().registryAccess()));
        for (var d : com.sqc.nuclearsq.transport.TransportRegistry.BLOCK_ITEMS) {
            if (tab.getDisplayItems().stream().noneMatch(s -> s.is(d.get()))) throw new GameTestAssertException(d.getId() + " fehlt im Tab");
        }
        for (String id : List.of("battery_small", "battery_medium", "battery_large")) {
            if (tab.getDisplayItems().stream().noneMatch(s -> s.is(KitRegistry.KITS.get(id).get()))) throw new GameTestAssertException(id + "-Kit fehlt im Tab");
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "creative_tab", timeoutTicks = 20)
    public static void creativeSourcesInTab(GameTestHelper h) {
        var tab = com.sqc.nuclearsq.registry.ModCreativeTabs.TAB.get();
        tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(
                h.getLevel().enabledFeatures(), true, h.getLevel().registryAccess()));
        for (var it : List.of(com.sqc.nuclearsq.registry.TestSourceRegistry.ENERGY_SOURCE_ITEM.get(),
                com.sqc.nuclearsq.registry.TestSourceRegistry.FLUID_SOURCE_ITEM.get())) {
            if (tab.getDisplayItems().stream().noneMatch(s -> s.is(it))) throw new GameTestAssertException(it + " fehlt im Tab");
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "creative_tab", timeoutTicks = 20)
    public static void radiationSensorsInProtectionSection(GameTestHelper h) {
        var tab = com.sqc.nuclearsq.registry.ModCreativeTabs.TAB.get();
        tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(
                h.getLevel().enabledFeatures(), true, h.getLevel().registryAccess()));
        List<net.minecraft.world.item.ItemStack> items = List.copyOf(tab.getDisplayItems());
        java.util.function.Function<net.minecraft.world.item.Item, Integer> idx = it -> {
            for (int i = 0; i < items.size(); i++) if (items.get(i).is(it)) return i;
            throw new GameTestAssertException(it + " fehlt im Tab");
        };
        int geiger = idx.apply(com.sqc.nuclearsq.registry.CatalogRegistry.GEIGER_COUNTER.get());
        int monitor = idx.apply(com.sqc.nuclearsq.data.DataRegistry.AREA_MONITOR.get().asItem());
        int sensor = idx.apply(com.sqc.nuclearsq.data.DataRegistry.AREA_SENSOR.get().asItem());
        if (monitor != geiger + 1 || sensor != geiger + 2) {
            throw new GameTestAssertException("Sensoren nicht hinter dem Geigerzaehler: Geiger " + geiger + ", Ortsdosis " + monitor + ", Flaeche " + sensor);
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "creative_tab", timeoutTicks = 20)
    public static void buildingPartsInPartsSection(GameTestHelper h) {
        var tab = com.sqc.nuclearsq.registry.ModCreativeTabs.TAB.get();
        tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(
                h.getLevel().enabledFeatures(), true, h.getLevel().registryAccess()));
        String section = "";
        int parts = 0;
        java.util.Set<net.minecraft.world.item.Item> inParts = new java.util.HashSet<>();
        for (var st : tab.getDisplayItems()) {
            String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
            if (path.startsWith("header_")) { section = path; continue; }
            if (path.equals("spacer_sqc")) continue;
            boolean part = st.getItem() instanceof com.sqc.nuclearsq.multiblock.PartBlockItem;
            if (section.equals("header_manufacturing_sqc")) {
                inParts.add(st.getItem());
                if (part) parts++;
            } else if (part) {
                throw new GameTestAssertException(path + " ist nur Bauteil, steht aber unter " + section);
            }
        }
        if (parts < 30) throw new GameTestAssertException("Zu wenige Anlagenteile unter Fertigung: " + parts);
        for (String id : List.of("steel_sheet_sqc", "glass_seal_sqc", "uranium_core_sqc", "control_board_1_sqc", "copper_coil_sqc")) {
            if (!inParts.contains(com.sqc.nuclearsq.registry.CatalogRegistry.ITEM_MAP.get(id).get()))
                throw new GameTestAssertException(id + " fehlt unter Fertigung");
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "creative_sources", timeoutTicks = 200)
    public static void creativeSourcesFeedRodDrive(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.CONTROL_ROD_DRIVE, new BlockPos(2, 4, 2));
        BigMachine.Port e = be.machine().port("energy_in"), w = be.machine().port("hydraulic_in");
        h.setBlock(new BlockPos(2 + e.x(), 4 + e.y(), 2 + e.z() - 1), com.sqc.nuclearsq.registry.TestSourceRegistry.ENERGY_SOURCE.get());
        h.setBlock(new BlockPos(2 + w.x(), 4 + w.y(), 2 + w.z() + 1), com.sqc.nuclearsq.registry.TestSourceRegistry.FLUID_SOURCE.get());
        h.runAfterDelay(25, () -> {
            formed(be);
            be.setRodTarget(15);
            h.runAfterDelay(120, () -> {
                if (be.energy().getEnergyStored() <= 0) throw new GameTestAssertException("Stromquelle laedt den Antrieb nicht");
                if (be.tank("hydraulic_in").getFluidAmount() <= 0) throw new GameTestAssertException("Fluessigkeitsquelle fuellt die Hydraulik nicht");
                if (be.rod() < 5) throw new GameTestAssertException("Staebe fahren nicht ein: Stellung " + be.rod());
                h.succeed();
            });
        });
    }

    private static void terminalClick(GameTestHelper h, int rot) {
        BigMachine m = BigMachine.CONTROL_ROD_DRIVE;
        BlockPos ctrl = h.absolutePos(new BlockPos(7, 4, 7));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, rot)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        BigMachine.Port tp = m.port("rod_terminal");
        BlockPos term = m.structure.world(ctrl, rot, tp.x(), tp.y(), tp.z());
        int[] n = com.sqc.nuclearsq.multiblock.MultiblockStructure.rotate(0, -1, rot);
        h.runAfterDelay(25, () -> {
            if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("kein Controller");
            formed(be);
            net.minecraft.server.level.ServerPlayer sp = new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-mock-player"),
                    net.minecraft.server.level.ClientInformation.createDefault()) {
                @Override
                public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider,
                                                      java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> extra) {
                    containerMenu = provider.createMenu(1, getInventory(), this);
                    return java.util.OptionalInt.of(1);
                }
            };
            Vec3 screen = Vec3.atCenterOf(term);
            Vec3 eye = screen.add(n[0] * 2.5, 0.1, n[1] * 2.5);
            sp.setPos(eye.x, eye.y - sp.getEyeHeight(), eye.z);
            var hit = h.getLevel().clip(new net.minecraft.world.level.ClipContext(eye, screen,
                    net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, sp));
            if (!hit.getBlockPos().equals(term)) {
                throw new GameTestAssertException("Drehung " + rot + ": Blick auf den Bildschirm trifft "
                        + h.getLevel().getBlockState(hit.getBlockPos()) + " statt des Terminal-Anschlusses");
            }
            sp.gameMode.useItemOn(sp, h.getLevel(), ItemStack.EMPTY, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            if (!(sp.containerMenu instanceof com.sqc.nuclearsq.machine.DeviceMenu dm) || dm.kind != com.sqc.nuclearsq.machine.DeviceMenu.ROD_DRIVE) {
                throw new GameTestAssertException("Drehung " + rot + ": Rechtsklick oeffnet kein Terminal (" + sp.containerMenu + ")");
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_rods_click0", timeoutTicks = 100)
    public static void bigControlRodTerminalClickRot0(GameTestHelper h) { terminalClick(h, 0); }

    @GameTest(template = FLOOR, batch = "big_rods_click1", timeoutTicks = 100)
    public static void bigControlRodTerminalClickRot1(GameTestHelper h) { terminalClick(h, 1); }

    @GameTest(template = FLOOR, batch = "big_rods_click2", timeoutTicks = 100)
    public static void bigControlRodTerminalClickRot2(GameTestHelper h) { terminalClick(h, 2); }

    @GameTest(template = FLOOR, batch = "big_rods_click3", timeoutTicks = 100)
    public static void bigControlRodTerminalClickRot3(GameTestHelper h) { terminalClick(h, 3); }

    @GameTest(template = FLOOR, batch = "big_rods_terminal", timeoutTicks = 300)
    public static void bigControlRodTerminalMovesRods(GameTestHelper h) {
        BigMachineBlockEntity be = rodDrive(h, new BlockPos(2, 4, 2));
        BigMachine.Port term = be.machine().port("rod_terminal");
        if (term == null || term.kind() != com.sqc.nuclearsq.machine.big.BigPortKind.TERMINAL) {
            throw new GameTestAssertException("Steuerstabantrieb hat keinen Terminal-Port");
        }
        h.runAfterDelay(25, () -> {
            formed(be);
            if (!(h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(2 + term.x(), 4 + term.y(), 2 + term.z())))
                    instanceof com.sqc.nuclearsq.machine.big.BigPortBlockEntity pe) || pe.findController() != be) {
                throw new GameTestAssertException("Terminal-Port nicht mit dem Controller verbunden");
            }
            be.setRodTarget(6);
            h.runAfterDelay(90, () -> {
                if (!be.terminalMode() || be.rodTarget() != 6) throw new GameTestAssertException("Terminal-Betrieb/Soll nicht gesetzt");
                rodAt(be, "Terminal", 6);
                be.setTerminalMode(false);
                h.runAfterDelay(90, () -> {
                    rodAt(be, "Redstone ohne Signal", 0);
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "big_rods_inlet", timeoutTicks = 60)
    public static void bigControlRodDriveInletsCarryRods(GameTestHelper h) {
        BigMachineBlockEntity be = rodDrive(h, new BlockPos(2, 1, 2));
        h.runAfterDelay(25, () -> {
            formed(be);
            for (RodShaft s : be.shafts()) {
                BlockPos inlet = be.rodBase(s).above(4);
                BlockState st = h.getLevel().getBlockState(inlet);
                if (!st.isFaceSturdy(h.getLevel(), inlet, net.minecraft.core.Direction.UP)) {
                    throw new GameTestAssertException("Einlass " + s.cellX + "/" + s.cellZ + " ohne Auflage: " + st);
                }
                if (!h.getLevel().getBlockState(inlet.above()).isAir()) {
                    throw new GameTestAssertException("Ueber dem Einlass " + s.cellX + "/" + s.cellZ + " ist kein Platz fuer den Stab: " + h.getLevel().getBlockState(inlet.above()));
                }
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_rods_link", timeoutTicks = 500)
    public static void bigControlRodDrivesLinkByChannel(GameTestHelper h) {
        BigMachineBlockEntity a = rodDrive(h, new BlockPos(0, 4, 1));
        BigMachineBlockEntity b = rodDrive(h, new BlockPos(8, 4, 1));
        h.runAfterDelay(25, () -> {
            formed(a);
            formed(b);
            a.setChannel(3);
            b.setChannel(3);
            if (a.linked().size() != 2) throw new GameTestAssertException("Kanal 3: " + a.linked().size() + " Antriebe statt 2");
            b.setRodTarget(5);
            if (a.rodTarget() != 5 || !a.terminalMode()) throw new GameTestAssertException("Soll wird nicht an die Gruppe weitergegeben");
            h.runAfterDelay(80, () -> {
                rodAt(a, "A (Terminal von B)", 5);
                rodAt(b, "B", 5);
                a.setTerminalMode(false);
                BigMachine.Port sig = a.machine().port("control_signal");
                h.setBlock(new BlockPos(sig.x(), 4 + sig.y(), 1 + sig.z() - 1), Blocks.REDSTONE_BLOCK);
                h.runAfterDelay(130, () -> {
                    if (b.terminalMode()) throw new GameTestAssertException("Betriebsart wird nicht an die Gruppe weitergegeben");
                    rodAt(a, "A (Redstone)", 15);
                    rodAt(b, "B (Redstone an A)", 15);
                    b.setChannel(0);
                    h.runAfterDelay(170, () -> {
                        rodAt(a, "A", 15);
                        rodAt(b, "B ohne Kanal", 0);
                        h.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "big_rods", timeoutTicks = 420)
    public static void bigControlRodDriveScramOverridesSignal(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.CONTROL_ROD_DRIVE, new BlockPos(2, 4, 2));
        charge(be);
        fill(be, "hydraulic_in", Fluids.WATER, 4000);
        BigMachine.Port sig = be.machine().port("control_signal");
        h.setBlock(new BlockPos(2 + sig.x(), 4 + sig.y(), 2 + sig.z() - 1), Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(220, () -> {
            formed(be);
            rodAt(be, "Redstone 15", 15);
            if (be.tank("hydraulic_in").getFluidAmount() != 4000 - 15 * 2 * BigMachineBlockEntity.ROD_STEP_TICKS) {
                throw new GameTestAssertException("Hydraulikwasser: " + be.tank("hydraulic_in").getFluidAmount());
            }
            be.scram();
            if (!be.terminalMode() || be.rodTarget() != 0) throw new GameTestAssertException("SCRAM setzt nicht Terminal-Betrieb mit Soll 0");
            h.runAfterDelay(170, () -> {
                rodAt(be, "nach SCRAM", 0);
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "big_rods_meltdown", timeoutTicks = 500)
    public static void controlRodDriveMeltdownCountdown(GameTestHelper h) {
        BigMachineBlockEntity be = rodDrive(h, new BlockPos(2, 4, 2));
        twoRods(h, be);
        h.runAfterDelay(25, () -> {
            formed(be);
            if (be.core() == null) throw new GameTestAssertException("Antrieb hat keinen Kern");
            for (int k = 0; k < 9; k++) be.setShaftLocked(k, true);
            be.setRodTarget(15);
        });
        h.runAfterDelay(300, () -> {
            DriveCore c = be.core();
            if (c.heat() <= 0f) throw new GameTestAssertException("Trockener Behaelter heizt den Kern nicht");
            c.setHeat(DriveCore.MAX_HEAT);
            h.runAfterDelay(40, () -> {
                if (!c.overheated()) throw new GameTestAssertException("Kern auf 100 % nicht ueberhitzt");
                if (c.meltdownLeft() <= 0) throw new GameTestAssertException("Kein Meltdown-Countdown");
                if (be.reactorPower() != 0) throw new GameTestAssertException("Leistung trotz Ueberhitzung: " + be.reactorPower());
                c.setHeat(DriveCore.MAX_HEAT * 0.5f);
                h.runAfterDelay(2, () -> {
                    if (c.meltdownLeft() != 0) throw new GameTestAssertException("Countdown bricht nicht ab: " + c.meltdownLeft());
                    if (c.overheated()) throw new GameTestAssertException("Ueberhitzung endet nicht unter 60 %");
                    be.scram();
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "big_rods_shaft", timeoutTicks = 900)
    public static void bigControlRodDrivePullsInRods(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.CONTROL_ROD_DRIVE, new BlockPos(2, 4, 2));
        RodShaft[] shafts = be.shafts();
        if (shafts.length != 9) throw new GameTestAssertException("Erwartet 9 Schaechte, sind " + shafts.length);
        int[] heads = {10, 9, 11, -1, 10, 9, 11, 10, 9};
        for (int k = 0; k < 9; k++) {
            if (heads[k] < 0) continue;
            BlockPos head = be.rodBase(shafts[k]).above(heads[k]);
            for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) {
                h.getLevel().setBlockAndUpdate(head.below(i), ReactorRegistry.FUEL_ROD_BLOCK.get().defaultBlockState()
                        .setValue(ReactorProps.ROD_FORMED, true));
            }
            h.getLevel().setBlockAndUpdate(head, ReactorRegistry.FUEL_ROD_HEAD.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true));
        }
        charge(be);
        fill(be, "hydraulic_in", Fluids.WATER, 4000);
        BigMachine.Port sig = be.machine().port("control_signal");
        BlockPos sp = new BlockPos(2 + sig.x(), 4 + sig.y(), 2 + sig.z() - 1);
        h.setBlock(sp, Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(25, () -> {
            for (int k = 0; k < 9; k++) be.setShaftLocked(k, true);
            if (shafts[3].locked()) throw new GameTestAssertException("Leerer Schacht laesst sich sperren");
        });
        h.runAfterDelay(220, () -> {
            formed(be);
            for (int k = 0; k < 9; k++) {
                RodShaft s = shafts[k];
                if (s.headY() != heads[k]) throw new GameTestAssertException("Schacht " + k + ": Stab bei " + s.headY() + ", erwartet " + heads[k]);
                if (heads[k] < 0) continue;
                if (!s.isDriven(false) || !h.getLevel().getBlockState(be.rodBase(s).above(heads[k])).getValue(ReactorProps.DRIVEN)) {
                    throw new GameTestAssertException("Schacht " + k + ": Stab wird nicht eingezogen (DRIVEN fehlt)");
                }
                if (s.shown(1f) <= 0.5f) throw new GameTestAssertException("Schacht " + k + ": Stab faehrt nicht ein: " + s.shown(1f));
            }
            h.setBlock(sp, Blocks.AIR);
            h.runAfterDelay(560, () -> {
                if (be.rod() != 0) throw new GameTestAssertException("Stabstellung nicht 0: " + be.rod());
                for (int k = 0; k < 9; k++) {
                    RodShaft s = shafts[k];
                    if (heads[k] < 0) continue;
                    if (s.shown(1f) != 0f || s.isDriven(false)
                            || h.getLevel().getBlockState(be.rodBase(s).above(heads[k] - 2)).getValue(ReactorProps.DRIVEN)) {
                        throw new GameTestAssertException("Schacht " + k + ": Stab nicht wieder oben/sichtbar, Weg " + s.shown(1f));
                    }
                }
                h.succeed();
            });
        });
    }

    private static void twoRods(GameTestHelper h, BigMachineBlockEntity be) {
        for (int k = 0; k < 9; k++) {
            BlockPos head = be.rodBase(be.shafts()[k]).above(9);
            for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) {
                h.getLevel().setBlockAndUpdate(head.below(i), ReactorRegistry.FUEL_ROD_BLOCK.get().defaultBlockState()
                        .setValue(ReactorProps.ROD_FORMED, true));
            }
            h.getLevel().setBlockAndUpdate(head, ReactorRegistry.FUEL_ROD_HEAD.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true));
        }
    }

    private static int rodFuel(GameTestHelper h, BigMachineBlockEntity be, int k) {
        int sum = 0;
        BlockPos head = be.rodBase(be.shafts()[k]).above(9);
        for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) {
            if (h.getLevel().getBlockEntity(head.below(i)) instanceof com.sqc.nuclearsq.reactor.FuelRodBlockEntity f) sum += f.getRemaining();
        }
        return sum;
    }

    @GameTest(template = FLOOR, batch = "big_rods_vessel", timeoutTicks = 700)
    public static void fuelRodDriveVesselHeatsPrimary(GameTestHelper h) {
        BigMachineBlockEntity be = rodDrive(h, new BlockPos(2, 4, 2));
        twoRods(h, be);
        pumping(h, be);
        BigMachine.Port win = be.machine().port("primary_cold_in"), sout = be.machine().port("primary_hot_out");
        if (win == null || sout == null) throw new GameTestAssertException("Primaer-Ports fehlen");
        if (be.machine().port("flux_signal") == null) throw new GameTestAssertException("Flux-Signal-Port fehlt");
        if (win.x() == sout.x()) throw new GameTestAssertException("Kalt-Eingang und Heiss-Ausgang auf derselben Seite");
        BlockPos wp = h.absolutePos(new BlockPos(2 + win.x(), 4 + win.y(), 2 + win.z()));
        BlockPos sp = h.absolutePos(new BlockPos(2 + sout.x(), 4 + sout.y(), 2 + sout.z()));
        h.runAfterDelay(25, () -> {
            formed(be);
            for (int k = 0; k < 9; k++) be.setShaftLocked(k, true);
            be.setRodTarget(15);
        });
        h.runAfterDelay(400, () -> {
            if (be.shafts()[0].shown() < 3.9f) throw new GameTestAssertException("Stab nicht eingezogen: " + be.shafts()[0].shown());
            if (!be.vesselDry() || be.statusLamp() != 2) throw new GameTestAssertException("Ohne Primaerwasser keine Sicherung/rote Lampe");
            if (be.tank("primary_hot_out").getFluidAmount() != 0) throw new GameTestAssertException("Heisses Primaerwasser ohne Zulauf");
            if (rodFuel(h, be, 0) != 4 * com.sqc.nuclearsq.reactor.FuelRodBlockEntity.UNIT) throw new GameTestAssertException("Abbrand ohne Durchfluss");
            IFluidHandler in = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, wp, null);
            if (in == null) throw new GameTestAssertException("Kalt-Port hat keinen Fluessigkeits-Anschluss");
            if (in.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.SIMULATE) != 0) {
                throw new GameTestAssertException("Kalt-Port nimmt normales Wasser an");
            }
            int got = in.fill(new FluidStack(ModFluids.PRIMARY_WATER.source.get(), 32000), IFluidHandler.FluidAction.EXECUTE);
            if (got != 32000) throw new GameTestAssertException("Kalt-Port nimmt " + got + " statt 32000 mB");
            h.runAfterDelay(45, () -> {
                if (be.vesselDry()) throw new GameTestAssertException("Sicherung bleibt trotz Primaerwasser aktiv");
                if (be.tank("primary_cold_in").getFluidAmount() >= 32000) throw new GameTestAssertException("Primaerwasser wird nicht erhitzt");
                if (be.signalOut() <= 0) throw new GameTestAssertException("Kein Flux-Signal trotz Leistung");
                IFluidHandler out = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, sp, null);
                if (out == null) throw new GameTestAssertException("Heiss-Port hat keinen Fluessigkeits-Anschluss");
                FluidStack hot = out.drain(100000, IFluidHandler.FluidAction.EXECUTE);
                if (hot.isEmpty() || !ModFluids.isHotPrimary(hot)) {
                    throw new GameTestAssertException("Am Heiss-Port kommt kein heisses Primaerwasser: " + hot.getAmount() + " " + hot.getFluid());
                }
                if (hot.getAmount() < 60 * 320) throw new GameTestAssertException("Zu wenig Durchfluss: " + hot.getAmount());
                if (rodFuel(h, be, 0) >= 4 * com.sqc.nuclearsq.reactor.FuelRodBlockEntity.UNIT) throw new GameTestAssertException("Ladung sinkt nicht");
                be.scram();
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "big_rods_hydraulic", timeoutTicks = 60)
    public static void rodDriveHydraulicTakesFeedwater(GameTestHelper h) {
        BigMachineBlockEntity be = rodDrive(h, new BlockPos(2, 4, 2));
        h.runAfterDelay(5, () -> {
            var t = be.tank("hydraulic_in");
            if (t == null || !t.isFluidValid(new FluidStack(Fluids.WATER, 1)) || !t.isFluidValid(new FluidStack(ModFluids.FEEDWATER.source.get(), 1))
                    || t.isFluidValid(new FluidStack(ModFluids.STEAM.source.get(), 1))) throw new GameTestAssertException("Hydraulik nimmt die falschen Fluessigkeiten");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_rods_vessel_cold", timeoutTicks = 200)
    public static void fuelRodDriveVesselColdWithoutRods(GameTestHelper h) {
        BigMachineBlockEntity be = rodDrive(h, new BlockPos(2, 4, 2));
        fill(be, "primary_cold_in", ModFluids.PRIMARY_WATER.source.get(), 8000);
        h.runAfterDelay(120, () -> {
            formed(be);
            if (be.tank("primary_hot_out").getFluidAmount() != 0 || be.tank("primary_cold_in").getFluidAmount() != 8000) {
                throw new GameTestAssertException("Behaelter erhitzt ohne Staebe");
            }
            if (be.vesselDry()) throw new GameTestAssertException("Sicherung ohne Staebe");
            if (be.signalOut() != 0) throw new GameTestAssertException("Flux-Signal ohne Staebe: " + be.signalOut());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_rods_lock", timeoutTicks = 800)
    public static void fuelRodDriveLockAndClamp(GameTestHelper h) {
        BigMachineBlockEntity be = rodDrive(h, new BlockPos(2, 4, 2));
        RodShaft[] shafts = be.shafts();
        for (int k = 0; k < 2; k++) {
            BlockPos head = be.rodBase(shafts[k]).above(9);
            for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) {
                h.getLevel().setBlockAndUpdate(head.below(i), ReactorRegistry.FUEL_ROD_BLOCK.get().defaultBlockState()
                        .setValue(ReactorProps.ROD_FORMED, true));
            }
            h.getLevel().setBlockAndUpdate(head, ReactorRegistry.FUEL_ROD_HEAD.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true));
        }
        RodShaft a = shafts[0], b = shafts[1];
        boolean[] bad = new boolean[2];
        float[] last = {0f};
        h.onEachTick(() -> {
            if (a.shown() != last[0] && a.open() < 1f) bad[0] = true;
            last[0] = a.shown();
            if (b.shown() > 0f) bad[1] = true;
        });
        h.runAfterDelay(25, () -> {
            formed(be);
            if (a.headY() != 9 || b.headY() != 9) throw new GameTestAssertException("Staebe nicht erkannt: " + a.headY() + "/" + b.headY());
            if (a.locked() || b.locked()) throw new GameTestAssertException("Neuer Stab ist schon gesperrt");
            if (a.open() != 1f) throw new GameTestAssertException("Rahmen ohne Sperre nicht offen: " + a.open());
            be.setShaftLocked(0, true);
            be.setShaftLocked(2, true);
            if (shafts[2].locked()) throw new GameTestAssertException("Leerer Schacht laesst sich sperren");
        });
        h.runAfterDelay(45, () -> {
            if (!a.locked() || a.open() != 0f) throw new GameTestAssertException("Gesperrter Stab oben: Rahmen nicht zu: " + a.open());
            if (b.open() != 1f) throw new GameTestAssertException("Freier Stab: Rahmen nicht offen: " + b.open());
            if (be.statusLamp() != 1) throw new GameTestAssertException("Statuslampe bei freiem Stab nicht gelb: " + be.statusLamp());
            be.setRodTarget(15);
        });
        h.runAfterDelay(340, () -> {
            rodAt(be, "Antrieb", 15);
            if (bad[0]) throw new GameTestAssertException("Stab fuhr ein, bevor der Rahmen ganz offen war");
            if (bad[1]) throw new GameTestAssertException("Nicht gesperrter Stab wurde eingezogen");
            if (a.shown() < 1f || a.open() != 0f || !a.isDriven(false)) {
                throw new GameTestAssertException("Gesperrter Stab unten nicht eingezogen/festgeklemmt: Weg " + a.shown() + ", Rahmen " + a.open());
            }
            be.setShaftLocked(0, false);
        });
        h.runAfterDelay(700, () -> {
            rodAt(be, "Antrieb nach Freigabe", 15);
            if (a.locked() || a.shown() != 0f || a.open() != 1f || a.isDriven(false)) {
                throw new GameTestAssertException("Freigegebener Stab nicht oben/offen: Weg " + a.shown() + ", Rahmen " + a.open());
            }
            if (h.getLevel().getBlockState(be.rodBase(a).above(7)).getValue(ReactorProps.DRIVEN)) {
                throw new GameTestAssertException("Freigegebener Stab bleibt unsichtbar");
            }
            if (bad[1]) throw new GameTestAssertException("Nicht gesperrter Stab wurde eingezogen");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_rods_water", timeoutTicks = 500)
    public static void fuelRodDriveWorksUnderWater(GameTestHelper h) {
        BigMachine m = BigMachine.CONTROL_ROD_DRIVE;
        BlockPos min = h.absolutePos(new BlockPos(2, 1, 2));
        int top = h.absolutePos(new BlockPos(0, 12, 0)).getY();
        for (int x = 0; x < m.w; x++) {
            for (int z = 0; z < m.d; z++) {
                for (int y = min.getY(); y <= top; y++) h.getLevel().setBlock(min.offset(x, y - min.getY(), z), Blocks.WATER.defaultBlockState(), 2);
            }
        }
        BlockPos ctrl = min.subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) {
            h.getLevel().setBlock(c.pos(), com.sqc.nuclearsq.multiblock.Waterlog.at(c.block().defaultBlockState(), h.getLevel(), c.pos()), 3);
        }
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("kein Controller");
        charge(be);
        fill(be, "hydraulic_in", Fluids.WATER, 4000);
        RodShaft a = be.shafts()[0];
        BlockPos head = be.rodBase(a).above(9);
        for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) {
            BlockPos q = head.below(i);
            h.getLevel().setBlock(q, com.sqc.nuclearsq.multiblock.Waterlog.at(ReactorRegistry.FUEL_ROD_BLOCK.get().defaultBlockState()
                    .setValue(ReactorProps.ROD_FORMED, true), h.getLevel(), q), 3);
        }
        h.getLevel().setBlock(head, com.sqc.nuclearsq.multiblock.Waterlog.at(ReactorRegistry.FUEL_ROD_HEAD.get().defaultBlockState()
                .setValue(ReactorProps.ROD_FORMED, true), h.getLevel(), head), 3);
        h.runAfterDelay(25, () -> {
            formed(be);
            int dry = 0, shells = 0;
            for (int x = 0; x < m.w; x++) {
                for (int z = 0; z < m.d; z++) {
                    for (int y = min.getY(); y <= top; y++) {
                        BlockState st = h.getLevel().getBlockState(min.offset(x, y - min.getY(), z));
                        if (st.getFluidState().isEmpty()) dry++;
                        if (st.is(m.shellBlock())) shells++;
                    }
                }
            }
            if (dry > 0) throw new GameTestAssertException(dry + " Felder im gefluteten Antrieb ohne Wasser");
            if (shells == 0) throw new GameTestAssertException("Keine Kollisions-Hilfsbloecke gesetzt");
            if (a.headY() != 9) throw new GameTestAssertException("Stab unter Wasser nicht erkannt: " + a.headY());
            be.setShaftLocked(0, true);
            be.setRodTarget(15);
        });
        h.runAfterDelay(420, () -> {
            rodAt(be, "Antrieb unter Wasser", 15);
            if (a.shown() < 4f || a.open() != 0f || !a.isDriven(false)) {
                throw new GameTestAssertException("Stab unter Wasser nicht eingezogen/geklemmt: Weg " + a.shown() + ", Rahmen " + a.open());
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "rod_radiation", timeoutTicks = 200)
    public static void fuelRodRadiationFollowsCharge(GameTestHelper h) {
        BlockPos p = new BlockPos(2, 2, 2);
        BlockPos abs = h.absolutePos(p);
        int unit = com.sqc.nuclearsq.reactor.FuelRodBlockEntity.UNIT;
        h.setBlock(p, ReactorRegistry.FUEL_ROD_BLOCK.get());
        h.runAfterDelay(25, () -> {
            rodRadiation(h, abs, com.sqc.nuclearsq.radiation.RadiationValues.FRESH_ROD_SV_H, "Frischer Brennstab");
            com.sqc.nuclearsq.reactor.FuelRodBlockEntity be = h.getBlockEntity(p);
            be.setRemaining(unit / 4);
        });
        h.runAfterDelay(50, () -> {
            rodRadiation(h, abs, com.sqc.nuclearsq.reactor.FuelRodBlockEntity.svH(unit / 4, false), "Viertel Ladung");
            com.sqc.nuclearsq.reactor.FuelRodBlockEntity be = h.getBlockEntity(p);
            be.setRemaining(0);
        });
        h.runAfterDelay(80, () -> {
            rodRadiation(h, abs, com.sqc.nuclearsq.radiation.RadiationValues.SPENT_ROD_SV_H, "Leerer Brennstab");
            com.sqc.nuclearsq.reactor.FuelRodBlockEntity be = h.getBlockEntity(p);
            be.setCooled(true);
        });
        h.runAfterDelay(110, () -> {
            rodRadiation(h, abs, com.sqc.nuclearsq.radiation.RadiationValues.COOLED_ROD_SV_H, "Abgeklungener Brennstab");
            h.succeed();
        });
    }

    private static void rodRadiation(GameTestHelper h, BlockPos abs, double want, String what) {
        double q = com.sqc.nuclearsq.radiation.RadiationManager.emitted(h.getLevel(), abs);
        if (Math.abs(q - want) > want * 0.01) throw new GameTestAssertException(what + " strahlt " + q + " statt " + want);
    }

    @GameTest(template = FLOOR, batch = "big_emergency", timeoutTicks = 120)
    public static void bigEmergencyCoolingRunsWithPower(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.EMERGENCY_COOLING, new BlockPos(2, 4, 2));
        charge(be);
        h.onEachTick(() -> keepEmergencyFlowing(be));
        h.runAfterDelay(40, () -> {
            formed(be);
            int fw = be.tank("primary_out").getFluidAmount();
            if (be.manual() || fw <= 0 || !be.isRunning()) throw new GameTestAssertException("Notkuehlung laeuft mit Strom ohne Signal nicht: " + fw + " " + be.stateKey());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_terminal", timeoutTicks = 120)
    public static void bigMachineTerminalSwitchesOffAndDumps(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.STEAM_SEPARATOR, new BlockPos(2, 4, 2));
        be.setEnabled(false);
        fill(be, "wet_steam_in", ModFluids.WET_STEAM.source.get(), 400);
        h.runAfterDelay(40, () -> {
            formed(be);
            expect(be, "steam_out", ModFluids.STEAM.source.get(), 0);
            if (be.isRunning()) throw new GameTestAssertException("Abscheider laeuft trotz AUS");
            be.setEnabled(true);
        });
        h.runAfterDelay(80, () -> {
            if (be.tank("steam_out").getFluidAmount() <= 0) throw new GameTestAssertException("Abscheider laeuft nach EIN nicht");
            int port = BigMachine.STEAM_SEPARATOR.ports.indexOf(BigMachine.STEAM_SEPARATOR.port("steam_out"));
            be.dumpTank(port);
            if (!be.tank("steam_out").isEmpty()) throw new GameTestAssertException("Leeren hat den Tank nicht geleert");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_emergency_manual", timeoutTicks = 100)
    public static void bigEmergencyCoolingManualRunsWithoutSignal(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.EMERGENCY_COOLING, new BlockPos(2, 4, 2));
        charge(be);
        h.onEachTick(() -> keepEmergencyFlowing(be));
        be.setManual(true);
        h.runAfterDelay(60, () -> {
            formed(be);
            int fw = be.tank("primary_out").getFluidAmount();
            if (fw <= 0 || !be.isRunning()) throw new GameTestAssertException("Notkuehlung im Handbetrieb laeuft nicht: " + fw);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_containment", timeoutTicks = 80)
    public static void bigContainmentRunsWithPower(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.CONTAINMENT, new BlockPos(3, 4, 3));
        charge(be);
        h.runAfterDelay(30, () -> {
            formed(be);
            if (!be.isRunning()) throw new GameTestAssertException("Sicherheitshuelle laeuft trotz Strom nicht");
            if (be.itemsAt("airlock_items") == null || be.itemsAt("airlock_items").getSlots() != 27) {
                throw new GameTestAssertException("Schleuse hat nicht 27 Plaetze");
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_containment_pass", timeoutTicks = 80)
    public static void bigContainmentPassesFeedwaterAndWetSteam(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.CONTAINMENT, new BlockPos(3, 4, 3));
        h.runAfterDelay(30, () -> {
            formed(be);
            if (fillPort(be, "feedwater_in", Fluids.WATER, 100) != 0) throw new GameTestAssertException("Speisewasser-Durchfuehrung nimmt Wasser an");
            if (fillPort(be, "wet_steam_in", ModFluids.STEAM.source.get(), 100) != 0) throw new GameTestAssertException("Nassdampf-Durchfuehrung nimmt Dampf an");
            fill(be, "feedwater_in", ModFluids.PREHEATED_FEEDWATER.source.get(), 1000);
            fill(be, "wet_steam_in", ModFluids.WET_STEAM.source.get(), 1000);
        });
        h.runAfterDelay(40, () -> {
            expect(be, "feedwater_in", ModFluids.PREHEATED_FEEDWATER.source.get(), 0);
            expect(be, "wet_steam_in", ModFluids.WET_STEAM.source.get(), 0);
            if (be.tank("feedwater_out").getFluidAmount() + be.tank("wet_steam_out").getFluidAmount() == 0) {
                throw new GameTestAssertException("Durchfuehrungen geben nichts heraus");
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_heavy_assembler", timeoutTicks = 560)
    public static void bigHeavyAssemblerCraftsShapedRecipe(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.HEAVY_ASSEMBLER, new BlockPos(2, 4, 2));
        charge(be);
        h.onEachTick(() -> charge(be));
        net.neoforged.neoforge.items.ItemStackHandler grid = be.items();
        if (grid == null || grid.getSlots() != 26) throw new GameTestAssertException("Raster hat nicht 26 Plaetze");
        java.util.function.Function<String, net.minecraft.world.item.Item> mod = id -> net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
        net.minecraft.world.item.Item steel = mod.apply("steel_sheet_sqc"), lead = mod.apply("lead_shielding_sqc");
        for (int slot = 0; slot < 25; slot++) {
            int row = slot / 5, col = slot % 5;
            boolean edge = row == 0 || row == 4 || col == 0 || col == 4, corner = (row == 0 || row == 4) && (col == 0 || col == 4);
            if (edge) grid.setStackInSlot(slot, new ItemStack(corner ? steel : lead));
        }
        var auto = be.itemsAt("item_in");
        var result = be.itemsAt("item_out");
        if (auto.insertItem(12, new ItemStack(steel), true).isEmpty()) throw new GameTestAssertException("Trichter fuellt ein leeres Rasterfeld");
        if (!auto.insertItem(10, new ItemStack(lead, 2), false).isEmpty()) throw new GameTestAssertException("Nachfuellen eines Blei-Felds klappt nicht");
        if (!auto.extractItem(10, 1, true).isEmpty()) throw new GameTestAssertException("Trichter holt Zutaten aus dem Raster");
        h.runAfterDelay(470, () -> {
            formed(be);
            ItemStack out = grid.getStackInSlot(25);
            if (!out.is(mod.apply("castor_large_sqc")) || out.getCount() != 1) {
                throw new GameTestAssertException("Erwartet 1 grossen Castor, ist " + out + " (Zustand " + be.crafter().status() + ")");
            }
            if (grid.getStackInSlot(10).getCount() != 2 || !grid.getStackInSlot(0).isEmpty() || !grid.getStackInSlot(14).isEmpty()) {
                throw new GameTestAssertException("Zutaten falsch verbraucht: " + grid.getStackInSlot(10) + ", " + grid.getStackInSlot(0) + ", " + grid.getStackInSlot(14));
            }
            if (auto.extractItem(25, 64, true).getCount() != 0) throw new GameTestAssertException("Eingang gibt das Ergebnis her");
            if (result.extractItem(25, 64, false).getCount() != 1) throw new GameTestAssertException("Trichter bekommt das Ergebnis nicht");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_pump", timeoutTicks = 100)
    public static void bigMainCoolantPumpFeedsDrive(GameTestHelper h) {
        BigMachineBlockEntity drive = rodDrive(h, new BlockPos(2, 4, 2));
        BigMachineBlockEntity be = buildBig(h, BigMachine.MAIN_COOLANT_PUMP, new BlockPos(10, 4, 4));
        charge(be);
        if (fillPort(be, "primary_in", ModFluids.COOLANT.source.get(), 100) != 0) throw new GameTestAssertException("Pumpe nimmt Kuehlmittel an");
        if (fillPort(be, "primary_in", ModFluids.HOT_PRIMARY_WATER.source.get(), 100) != 0) throw new GameTestAssertException("Pumpe nimmt heisses Primaerwasser an");
        fill(be, "primary_in", ModFluids.PRIMARY_WATER.source.get(), 64000);
        int[] flow = {0};
        h.onEachTick(() -> {
            flow[0] = Math.max(flow[0], drive.pumpFlow());
            be.tank("primary_out").drain(100000, IFluidHandler.FluidAction.EXECUTE);
        });
        h.runAfterDelay(40, () -> {
            formed(be);
            formed(drive);
            int rate = be.machine().port("primary_in").rate();
            int used = 64000 - be.tank("primary_in").getFluidAmount();
            if (used < Math.min(64000, rate * 30) || used > rate * 41 || used % rate != 0) throw new GameTestAssertException("Foerdermenge " + used + " mB nach 40 Ticks");
            if (flow[0] != rate) throw new GameTestAssertException("Antrieb bekommt " + flow[0] + " statt " + rate + " mB/t gemeldet");
            if (!be.isRunning()) throw new GameTestAssertException("Pumpe gilt nicht als laufend");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_pump_full", timeoutTicks = 100)
    public static void mainCoolantPumpShowsFlowInFullLoop(GameTestHelper h) {
        BigMachineBlockEntity drive = rodDrive(h, new BlockPos(2, 4, 2));
        BigMachineBlockEntity be = buildBig(h, BigMachine.MAIN_COOLANT_PUMP, new BlockPos(10, 4, 4));
        BigMachineBlockEntity off = buildBig(h, BigMachine.MAIN_COOLANT_PUMP, new BlockPos(10, 4, 10));
        charge(be);
        for (BigMachineBlockEntity p : new BigMachineBlockEntity[] {be, off}) {
            fill(p, "primary_in", ModFluids.PRIMARY_WATER.source.get(), p.tank("primary_in").getCapacity());
            fill(p, "primary_out", ModFluids.PRIMARY_WATER.source.get(), p.tank("primary_out").getCapacity());
        }
        h.runAfterDelay(20, () -> {
            formed(be);
            formed(off);
            formed(drive);
            int rate = be.machine().port("primary_in").rate();
            if (be.lastMb() != rate) throw new GameTestAssertException("Volle Pumpe mit Strom zeigt " + be.lastMb() + " statt " + rate + " mB/t");
            if (!be.isRunning()) throw new GameTestAssertException("Volle Pumpe mit Strom gilt nicht als laufend");
            if (drive.pumpFlow() != rate) throw new GameTestAssertException("Antrieb bekommt " + drive.pumpFlow() + " statt " + rate + " mB/t");
            if (off.lastMb() != 0) throw new GameTestAssertException("Pumpe ohne Strom zeigt " + off.lastMb() + " mB/t");
            if (be.tank("primary_in").getSpace() != 0 || be.tank("primary_out").getSpace() != 0) throw new GameTestAssertException("Volle Tanks veraendert");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_pump_two", timeoutTicks = 100)
    public static void mainCoolantPumpFeedsBothDrives(GameTestHelper h) {
        BigMachineBlockEntity a = rodDrive(h, new BlockPos(2, 4, 2)), b = rodDrive(h, new BlockPos(2, 4, 10));
        BigMachineBlockEntity be = buildBig(h, BigMachine.MAIN_COOLANT_PUMP, new BlockPos(10, 4, 7));
        charge(be);
        fill(be, "primary_in", ModFluids.PRIMARY_WATER.source.get(), 64000);
        int[] flow = {0, 0};
        h.onEachTick(() -> {
            flow[0] = Math.max(flow[0], a.pumpFlow());
            flow[1] = Math.max(flow[1], b.pumpFlow());
            be.tank("primary_out").drain(100000, IFluidHandler.FluidAction.EXECUTE);
        });
        h.runAfterDelay(40, () -> {
            formed(be);
            formed(a);
            formed(b);
            if (flow[0] <= 0 || flow[1] <= 0) throw new GameTestAssertException("Durchfluss an den Antrieben " + flow[0] + " / " + flow[1]);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "xenon_stop", timeoutTicks = 20)
    public static void xenonStopsFullPowerReactor(GameTestHelper h) {
        com.sqc.nuclearsq.machine.big.CoreChemistry full = new com.sqc.nuclearsq.machine.big.CoreChemistry(), part = new com.sqc.nuclearsq.machine.big.CoreChemistry();
        net.minecraft.world.phys.Vec3 c = net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 250, 1)));
        int t = 1;
        for (; t <= 24000 && !full.poisoned(); t++) {
            full.tick(h.getLevel(), c, t, full.powerFactor(), 1f);
            part.tick(h.getLevel(), c, t, 0.8f * part.powerFactor(), 1f);
        }
        if (!full.poisoned()) throw new GameTestAssertException("Xenon bleibt bei " + full.xenonPercent() + " %, kein Stopp");
        if (t < 12000) throw new GameTestAssertException("Stopp schon nach " + t + " Ticks");
        if (part.poisoned()) throw new GameTestAssertException("80 % Leistung stoppt auch");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "big_pump_hp", timeoutTicks = 100)
    public static void bigHighPressurePumpPumpsDouble(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.HIGH_PRESSURE_PUMP, new BlockPos(4, 4, 4));
        charge(be);
        fill(be, "primary_in", ModFluids.PRIMARY_WATER.source.get(), 128000);
        h.onEachTick(() -> be.tank("primary_out").drain(100000, IFluidHandler.FluidAction.EXECUTE));
        h.runAfterDelay(40, () -> {
            formed(be);
            int rate = be.machine().port("primary_in").rate();
            int used = 128000 - be.tank("primary_in").getFluidAmount();
            if (used < Math.min(128000, rate * 30) || used > rate * 41 || used % rate != 0) throw new GameTestAssertException("Foerdermenge " + used + " mB nach 40 Ticks");
            if (rate != 2 * BigMachine.MAIN_COOLANT_PUMP.port("primary_in").rate()) throw new GameTestAssertException("Hochdruckstufe nicht doppelt so stark wie die normale Pumpe");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_rods_no_pump", timeoutTicks = 500)
    public static void fuelRodDriveNeedsMainCoolantPump(GameTestHelper h) {
        BigMachineBlockEntity be = rodDrive(h, new BlockPos(2, 4, 2));
        twoRods(h, be);
        fill(be, "primary_cold_in", ModFluids.PRIMARY_WATER.source.get(), 8000);
        h.runAfterDelay(25, () -> {
            formed(be);
            for (int k = 0; k < 9; k++) be.setShaftLocked(k, true);
            be.setRodTarget(15);
        });
        h.runAfterDelay(400, () -> {
            if (be.shafts()[0].shown() < 3.9f) throw new GameTestAssertException("Stab nicht eingezogen: " + be.shafts()[0].shown());
            if (!be.vesselDry()) throw new GameTestAssertException("Durchfluss ohne Pumpe");
            if (be.tank("primary_cold_in").getFluidAmount() != 8000) throw new GameTestAssertException("Primaerwasser fliesst ohne Pumpe");
            if (rodFuel(h, be, 0) != 4 * com.sqc.nuclearsq.reactor.FuelRodBlockEntity.UNIT) throw new GameTestAssertException("Abbrand ohne Pumpe");
            be.scram();
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "big_reprocessing", timeoutTicks = 800)
    public static void bigReprocessingPlantTurnsResidueIntoAsh(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.REPROCESSING_PLANT, new BlockPos(2, 4, 2));
        charge(be);
        var auto = be.itemsAt("item_in");
        if (auto.insertItem(0, new ItemStack(Blocks.DIRT), true).isEmpty()) throw new GameTestAssertException("Anlage nimmt fremde Items an");
        if (auto.insertItem(1, new ItemStack(CatalogRegistry.ITEM_MAP.get("filter_residue_sqc").get()), true).isEmpty()) {
            throw new GameTestAssertException("Ergebnis-Slot nimmt Items an");
        }
        if (!auto.insertItem(0, new ItemStack(CatalogRegistry.ITEM_MAP.get("filter_residue_sqc").get(), 16), false).isEmpty()) {
            throw new GameTestAssertException("Rueckstand passt nicht hinein");
        }
        h.runAfterDelay(640, () -> {
            formed(be);
            int left = be.items().getStackInSlot(0).getCount(), ash = 0;
            for (int i = 1; i <= 4; i++) {
                ItemStack o = be.items().getStackInSlot(i);
                if (o.is(CatalogRegistry.ASH_ITEM.get())) ash += o.getCount();
            }
            if (left != 8 || ash != 4) {
                throw new GameTestAssertException("Erwartet 8 Rest + 4 Asche, ist " + left + " + " + ash + " (Zustand " + be.crafter().status() + ")");
            }
            if (!auto.extractItem(0, 1, true).isEmpty()) throw new GameTestAssertException("Trichter holt Rueckstand aus dem Eingang");
            h.succeed();
        });
    }

    private static com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity buildTurbine(GameTestHelper h, BlockPos min, boolean coupling,
            boolean generator, List<BlockPos> placed) {
        MultiblockStructure t = MultiblockStructure.TURBINE;
        BlockPos ctrl = h.absolutePos(min).subtract(t.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : t.cells(ctrl, 0)) {
            h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
            placed.add(c.pos());
        }
        int cx = com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity.COUPLING_X;
        int cy = com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity.COUPLING_Y;
        int cz = com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity.COUPLING_Z;
        if (coupling) {
            BlockPos cp = t.world(ctrl, 0, cx, cy, cz);
            Direction.Axis axis = Direction.getNearest(Vec3.atLowerCornerOf(t.world(ctrl, 0, cx, cy, cz + 1).subtract(cp))).getAxis();
            h.getLevel().setBlockAndUpdate(cp, com.sqc.nuclearsq.registry.MultiblockRegistry.SHAFT_COUPLING.get().defaultBlockState()
                    .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, axis));
            placed.add(cp);
        }
        if (generator) {
            BlockPos gen = t.world(ctrl, 0, cx, cy, cz + 1);
            for (MultiblockStructure.Cell c : BigMachine.SHAFT_GENERATOR.structure.cells(gen, 0)) {
                h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
                placed.add(c.pos());
            }
        }
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity be)) {
            throw new GameTestAssertException("Turbinen-Controller hat kein BlockEntity");
        }
        int n = be.steam().fill(new FluidStack(ModFluids.STEAM.source.get(), 200000), IFluidHandler.FluidAction.EXECUTE);
        if (n != 200000) throw new GameTestAssertException("Turbine nimmt " + n + " statt 200000 mB Dampf an");
        return be;
    }

    private static void clear(GameTestHelper h, List<BlockPos> placed) {
        for (int i = placed.size() - 1; i >= 0; i--) h.getLevel().setBlock(placed.get(i), Blocks.AIR.defaultBlockState(), 2 | 16);
    }

    @GameTest(template = FLOOR, batch = "turbine_shaft_gen", timeoutTicks = 120)
    public static void turbineShaftGenerator(GameTestHelper h) {
        List<BlockPos> placed = new java.util.ArrayList<>();
        var tb = buildTurbine(h, new BlockPos(4, 4, 0), true, true, placed);
        BlockPos genPos = placed.get(placed.size() - 1);
        BigMachineBlockEntity gen = null;
        for (BlockPos q : placed) {
            if (h.getLevel().getBlockEntity(q) instanceof BigMachineBlockEntity b && b.machine() == BigMachine.SHAFT_GENERATOR) gen = b;
        }
        if (gen == null) throw new GameTestAssertException("Wellengenerator-Controller fehlt (" + genPos + ")");
        BigMachineBlockEntity g = gen;
        int[] start = new int[3];
        h.runAfterDelay(40, () -> {
            start[0] = tb.steam().getFluidAmount();
            start[1] = tb.energyStored();
            start[2] = g.energy().getEnergyStored();
        });
        h.runAfterDelay(80, () -> {
            try {
                if (!tb.isFormed()) throw new GameTestAssertException("Turbine nicht aufgebaut: " + tb.matched() + "/" + tb.total());
                formed(g);
                if (!tb.isCoupled()) throw new GameTestAssertException("Turbine erkennt die Wellenkupplung nicht");
                int used = start[0] - tb.steam().getFluidAmount();
                int fe = g.energy().getEnergyStored() - start[2];
                if (used <= 0) throw new GameTestAssertException("Turbine verbraucht keinen Dampf");
                if (tb.energyStored() != start[1]) {
                    throw new GameTestAssertException("Turbinenspeicher " + start[1] + " -> " + tb.energyStored() + " FE, sollte gleich bleiben");
                }
                if (fe != used * com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity.FE_PER_MB) {
                    throw new GameTestAssertException("Generator bekam " + fe + " FE, erwartet " + used + " mB x 8");
                }
                if (!g.isRunning() || g.lastMb() <= 0) throw new GameTestAssertException("Generator gilt nicht als laufend (" + g.lastMb() + ")");
                if (tb.lastShaft() <= 0) throw new GameTestAssertException("Turbine meldet keine Wellenleistung");
            } finally {
                clear(h, placed);
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "turbine_shaft_none", timeoutTicks = 120)
    public static void turbineShaftWithoutConsumer(GameTestHelper h) {
        List<BlockPos> placed = new java.util.ArrayList<>();
        var tb = buildTurbine(h, new BlockPos(4, 4, 0), true, false, placed);
        h.runAfterDelay(80, () -> {
            try {
                if (!tb.isFormed()) throw new GameTestAssertException("Turbine nicht aufgebaut: " + tb.matched() + "/" + tb.total());
                int used = 200000 - tb.steam().getFluidAmount();
                if (used <= 0) throw new GameTestAssertException("Turbine verbraucht keinen Dampf");
                if (tb.energyStored() != used * com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity.FE_PER_MB) {
                    throw new GameTestAssertException("Turbinenspeicher " + tb.energyStored() + " FE, erwartet " + used + " mB x 8");
                }
                if (tb.lastShaft() != 0) throw new GameTestAssertException("Wellenleistung ohne Abnehmer: " + tb.lastShaft());
            } finally {
                clear(h, placed);
            }
            h.succeed();
        });
    }

    private static ItemStack spentRod() {
        ItemStack s = new ItemStack(ReactorRegistry.FUEL_ROD_BLOCK_ITEM.get());
        s.set(com.sqc.nuclearsq.registry.ModDataComponents.FUEL_CHARGE.get(), 0);
        return s;
    }

    private static void setCooling(GameTestHelper h, BigMachineBlockEntity be, int ticks) {
        var reg = h.getLevel().registryAccess();
        net.minecraft.nbt.CompoundTag tag = be.saveWithoutMetadata(reg);
        int[] c = new int[com.sqc.nuclearsq.machine.big.BigCrafter.SHAFTS];
        java.util.Arrays.fill(c, ticks);
        tag.getCompound("crafter").putIntArray("cool", c);
        be.loadWithComponents(tag, reg);
    }

    private static BigMachineBlockEntity buildRack(GameTestHelper h, int blocks) {
        BigMachine m = BigMachine.SPENT_FUEL_RACK;
        BigMachineBlockEntity be = buildBig(h, m, new BlockPos(2, 1, 2));
        BlockPos min = h.absolutePos(new BlockPos(2, 1 + m.structure.height(), 2));
        for (int x = 0; x < m.structure.width(); x++) {
            for (int z = 0; z < m.structure.depth(); z++) {
                for (int y = 0; y < blocks; y++) h.getLevel().setBlock(min.offset(x, y, z), Blocks.WATER.defaultBlockState(), 2);
            }
        }
        return be;
    }

    private static final int RACK_HEAD = 10;
    private static final int RACK_DRIVE_WAIT = 380;

    private static void placeRackRod(GameTestHelper h, BigMachineBlockEntity be, int shaft, boolean spent) {
        BlockPos head = be.rodBase(be.shafts()[shaft]).above(RACK_HEAD);
        for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) {
            h.getLevel().setBlockAndUpdate(head.below(i), ReactorRegistry.FUEL_ROD_BLOCK.get().defaultBlockState()
                    .setValue(ReactorProps.ROD_FORMED, true).setValue(com.sqc.nuclearsq.multiblock.Waterlog.WATERLOGGED, true));
            if (spent && h.getLevel().getBlockEntity(head.below(i)) instanceof com.sqc.nuclearsq.reactor.FuelRodBlockEntity f) f.setRemaining(0);
        }
        h.getLevel().setBlockAndUpdate(head, ReactorRegistry.FUEL_ROD_HEAD.get().defaultBlockState()
                .setValue(ReactorProps.ROD_FORMED, true).setValue(com.sqc.nuclearsq.multiblock.Waterlog.WATERLOGGED, true));
    }

    @GameTest(template = FLOOR, batch = "rack_drive", timeoutTicks = 1000)
    public static void spentFuelRackDrivesInCoolsAndReleases(GameTestHelper h) {
        BigMachineBlockEntity be = buildRack(h, 5);
        fill(be, "water_in", Fluids.WATER, 2000);
        if (be.shafts().length != com.sqc.nuclearsq.machine.big.BigCrafter.SHAFTS || be.shafts().length != 9) {
            throw new GameTestAssertException("Gestell hat " + be.shafts().length + " statt 9 Schaechte");
        }
        placeRackRod(h, be, 4, true);
        h.runAfterDelay(RACK_DRIVE_WAIT, () -> {
            formed(be);
            RodShaft s = be.shafts()[4];
            if (s.headY() != RACK_HEAD) throw new GameTestAssertException("Stab nicht erkannt: " + s.headY());
            if (!s.locked() || !be.rackInPlace(4)) throw new GameTestAssertException("Stab nicht eingefahren: " + s.shown());
            if (!h.getLevel().getBlockState(be.rodBase(s).above(RACK_HEAD)).getValue(ReactorProps.DRIVEN)) {
                throw new GameTestAssertException("Stab-Bloecke nicht DRIVEN");
            }
            for (int i = 0; i < 9; i++) {
                if (i != 4 && be.rackState(i) != com.sqc.nuclearsq.machine.big.BigCrafter.RackView.EMPTY) {
                    throw new GameTestAssertException("Schacht " + i + " nicht leer");
                }
            }
            if (be.rackState(4) != com.sqc.nuclearsq.machine.big.BigCrafter.RackView.HOT) throw new GameTestAssertException("Stab nicht heiss");
            var c = be.crafter();
            if (c.status() != com.sqc.nuclearsq.machine.big.BigCrafter.OK) throw new GameTestAssertException("Zustand: " + c.status() + ", Saeule " + c.column());
            setCooling(h, be, c.coolTicks() - 3);
            h.runAfterDelay(40, () -> {
                if (be.rackState(4) != com.sqc.nuclearsq.machine.big.BigCrafter.RackView.COOLED) {
                    throw new GameTestAssertException("Stab nicht abgeklungen (Zustand " + be.crafter().status() + ")");
                }
                if (be.shafts()[4].locked() || !be.rackReleased(4)) throw new GameTestAssertException("Abgeklungener Stab nicht freigegeben");
                if (be.crafter().waterAmount() >= 2000) throw new GameTestAssertException("Kein Wasser verbraucht");
                h.runAfterDelay(RACK_DRIVE_WAIT, () -> {
                    RodShaft r = be.shafts()[4];
                    BlockPos top = be.rodBase(r).above(RACK_HEAD);
                    if (r.shown() > 0.001f || h.getLevel().getBlockState(top).getValue(ReactorProps.DRIVEN)) {
                        throw new GameTestAssertException("Stab nicht wieder hochgefahren: " + r.shown());
                    }
                    var drops = net.minecraft.world.level.block.Block.getDrops(h.getLevel().getBlockState(top.below()), h.getLevel(),
                            top.below(), h.getLevel().getBlockEntity(top.below()));
                    if (drops.size() != 1 || !drops.get(0).is(com.sqc.nuclearsq.registry.SpentFuelRegistry.SPENT_FUEL.get())) {
                        throw new GameTestAssertException("Abgeklungener Block gibt " + drops);
                    }
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "rack_one", timeoutTicks = 1200)
    public static void spentFuelRackOneRodPerShaft(GameTestHelper h) {
        BigMachineBlockEntity be = buildRack(h, 5);
        for (int i = 0; i < 9; i++) placeRackRod(h, be, i, true);
        h.runAfterDelay(RACK_DRIVE_WAIT, () -> {
            for (int i = 0; i < 9; i++) {
                if (!be.rackInPlace(i)) throw new GameTestAssertException("Schacht " + i + " nicht eingefahren");
            }
            if (be.rackState(9) != com.sqc.nuclearsq.machine.big.BigCrafter.RackView.EMPTY) throw new GameTestAssertException("10. Schacht vorhanden");
            BlockPos above = be.rodBase(be.shafts()[0]).above(RACK_HEAD + 1);
            h.getLevel().setBlockAndUpdate(above, ReactorRegistry.FUEL_ROD_HEAD.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true));
            be.setRackReleased(0, true);
            h.runAfterDelay(25, () -> {
                if (be.shafts()[0].headY() != RACK_HEAD) throw new GameTestAssertException("Schacht 0 sieht den zweiten Stab: " + be.shafts()[0].headY());
                if (be.shafts()[0].locked()) throw new GameTestAssertException("Freigegebener Stab bleibt gesperrt");
                h.runAfterDelay(RACK_DRIVE_WAIT, () -> {
                    RodShaft s = be.shafts()[0];
                    if (s.shown() > 0.001f) throw new GameTestAssertException("Stab faehrt nicht heraus: " + s.shown());
                    if (h.getLevel().getBlockState(be.rodBase(s).above(RACK_HEAD)).getValue(ReactorProps.DRIVEN)) {
                        throw new GameTestAssertException("Herausgefahrener Stab noch DRIVEN");
                    }
                    be.setRackReleased(0, false);
                    if (!s.locked()) throw new GameTestAssertException("Stab laesst sich nicht wieder einfahren");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "rack_grab", timeoutTicks = 200)
    public static void spentFuelRackGrabsRodQuickly(GameTestHelper h) {
        BigMachineBlockEntity be = buildRack(h, 5);
        h.runAfterDelay(25, () -> {
            formed(be);
            placeRackRod(h, be, 2, true);
            h.runAfterDelay(8, () -> {
                RodShaft s = be.shafts()[2];
                if (s.headY() != RACK_HEAD) throw new GameTestAssertException("Stab nach 8 Ticks nicht erkannt: " + s.headY());
                if (!s.locked()) throw new GameTestAssertException("Stab nach 8 Ticks nicht gegriffen");
                if (be.inletY() != RACK_HEAD - FuelRodHeadBlock.ROD_BLOCKS) {
                    throw new GameTestAssertException("Klemmrahmen bei " + be.inletY() + ", Stab-Unterkante " + (RACK_HEAD - FuelRodHeadBlock.ROD_BLOCKS));
                }
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "cherenkov", timeoutTicks = 100)
    public static void cherenkovLevelPerMachine(GameTestHelper h) {
        BigMachineBlockEntity be = buildRack(h, 0);
        h.runAfterDelay(25, () -> {
            formed(be);
            if (be.cherenkov() != com.sqc.nuclearsq.reactor.Cherenkov.DEFAULT) throw new GameTestAssertException("Startwert " + be.cherenkov());
            BlockPos inside = be.rodBase(be.shafts()[4]).above(2);
            if (com.sqc.nuclearsq.reactor.Cherenkov.find(h.getLevel(), inside) != be) throw new GameTestAssertException("Gestell nicht gefunden");
            if (com.sqc.nuclearsq.reactor.Cherenkov.find(h.getLevel(), inside.above(10)) != null) throw new GameTestAssertException("Block ausserhalb gefunden");
            var p = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            p.setPos(inside.getCenter());
            com.sqc.nuclearsq.reactor.Cherenkov.apply(p, be.getBlockPos(), 99);
            if (be.cherenkov() != com.sqc.nuclearsq.reactor.Cherenkov.MAX) throw new GameTestAssertException("Nicht begrenzt: " + be.cherenkov());
            com.sqc.nuclearsq.reactor.Cherenkov.apply(p, be.getBlockPos(), 3);
            var reg = h.getLevel().registryAccess();
            net.minecraft.nbt.CompoundTag tag = be.saveWithoutMetadata(reg);
            be.setCherenkov(12);
            be.loadWithComponents(tag, reg);
            if (be.cherenkov() != 3) throw new GameTestAssertException("Nach Laden " + be.cherenkov() + " statt 3");
            p.setPos(inside.getCenter().add(60, 0, 0));
            com.sqc.nuclearsq.reactor.Cherenkov.apply(p, be.getBlockPos(), 7);
            if (be.cherenkov() != 3) throw new GameTestAssertException("Aus der Ferne geaendert");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "rod_water", timeoutTicks = 60)
    public static void fuelRodKeepsNoWaterOutsideWater(GameTestHelper h) {
        BlockPos water = new BlockPos(4, 4, 3), wet = new BlockPos(3, 4, 3), lifted = new BlockPos(3, 5, 3), stream = new BlockPos(6, 4, 3);
        BlockState rod = ReactorRegistry.FUEL_ROD_BLOCK.get().defaultBlockState();
        h.setBlock(water, Blocks.WATER);
        h.setBlock(wet, rod.setValue(com.sqc.nuclearsq.multiblock.Waterlog.WATERLOGGED, true));
        h.setBlock(lifted, rod.setValue(com.sqc.nuclearsq.multiblock.Waterlog.WATERLOGGED, true));
        h.setBlock(stream, rod);
        h.runAfterDelay(20, () -> {
            if (!h.getBlockState(wet).getValue(com.sqc.nuclearsq.multiblock.Waterlog.WATERLOGGED)) throw new GameTestAssertException("Stab neben Quellwasser trocken");
            if (h.getBlockState(lifted).getValue(com.sqc.nuclearsq.multiblock.Waterlog.WATERLOGGED)) throw new GameTestAssertException("Stab ueber dem Wasser behaelt Wasser");
            if (h.getBlockState(stream).getValue(com.sqc.nuclearsq.multiblock.Waterlog.WATERLOGGED)) throw new GameTestAssertException("Fliessendes Wasser macht den Stab zur Quelle");
            h.setBlock(water, Blocks.AIR);
            h.runAfterDelay(10, () -> {
                if (h.getBlockState(wet).getValue(com.sqc.nuclearsq.multiblock.Waterlog.WATERLOGGED)) throw new GameTestAssertException("Stab ohne Wasser daneben behaelt Wasser");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "rack_feed", timeoutTicks = 600)
    public static void spentFuelRackNeedsFeed(GameTestHelper h) {
        BigMachineBlockEntity be = buildRack(h, 5);
        placeRackRod(h, be, 0, true);
        h.runAfterDelay(RACK_DRIVE_WAIT, () -> {
            var c = be.crafter();
            if (!be.rackInPlace(0)) throw new GameTestAssertException("Stab nicht eingefahren");
            if (c.status() != com.sqc.nuclearsq.machine.big.BigCrafter.NO_FEED) throw new GameTestAssertException("Zustand ohne Zulauf: " + c.status());
            if (c.column() != c.columnNeeded()) throw new GameTestAssertException("Saeule " + c.column() + " statt " + c.columnNeeded());
            double dry = com.sqc.nuclearsq.radiation.RadiationManager.emitted(h.getLevel(), be.getBlockPos());
            if (Math.abs(dry - com.sqc.nuclearsq.spent.SpentFuel.DRY_SV_H) > 1e-6) throw new GameTestAssertException("Ohne Zulauf " + dry + " Sv/h");
            setCooling(h, be, c.coolTicks() - 3);
            h.runAfterDelay(40, () -> {
                if (be.rackState(0) != com.sqc.nuclearsq.machine.big.BigCrafter.RackView.HOT) throw new GameTestAssertException("Stab ohne Zulauf abgeklungen");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "rack_column", timeoutTicks = 600)
    public static void spentFuelRackNeedsWaterColumn(GameTestHelper h) {
        BigMachineBlockEntity be = buildRack(h, 3);
        fill(be, "water_in", Fluids.WATER, 2000);
        placeRackRod(h, be, 0, true);
        BlockPos head = be.rodBase(be.shafts()[0]).above(RACK_HEAD);
        for (int y = 0; y < 2; y++) {
            BlockPos p = head.below(y);
            h.getLevel().setBlockAndUpdate(p, h.getLevel().getBlockState(p).setValue(com.sqc.nuclearsq.multiblock.Waterlog.WATERLOGGED, false));
        }
        h.runAfterDelay(RACK_DRIVE_WAIT, () -> {
            var c = be.crafter();
            if (!be.rackInPlace(0)) throw new GameTestAssertException("Stab nicht eingefahren");
            if (c.status() != com.sqc.nuclearsq.machine.big.BigCrafter.SHALLOW) throw new GameTestAssertException("Zustand bei flachem Wasser: " + c.status());
            if (c.column() != 3) throw new GameTestAssertException("Saeule " + c.column() + " statt 3");
            double dry = com.sqc.nuclearsq.radiation.RadiationManager.emitted(h.getLevel(), be.getBlockPos());
            if (Math.abs(dry - com.sqc.nuclearsq.spent.SpentFuel.DRY_SV_H) > 1e-6) throw new GameTestAssertException("Flaches Wasser " + dry + " Sv/h");
            setCooling(h, be, c.coolTicks() - 3);
            h.runAfterDelay(40, () -> {
                if (be.rackState(0) != com.sqc.nuclearsq.machine.big.BigCrafter.RackView.HOT) throw new GameTestAssertException("Stab bei flachem Wasser abgeklungen");
                if (be.crafter().waterAmount() != 2000) throw new GameTestAssertException("Wasser verbraucht ohne Abklingen");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "rack_items", timeoutTicks = 100)
    public static void spentFuelRackHasNoItemPort(GameTestHelper h) {
        BigMachineBlockEntity be = buildRack(h, 5);
        h.runAfterDelay(25, () -> {
            formed(be);
            if (be.items() != null || be.itemsAt("item_out") != null) throw new GameTestAssertException("Gestell hat einen Item-Speicher");
            if (be.crafter() == null) throw new GameTestAssertException("Gestell ohne Abkling-Logik");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "castor_large", timeoutTicks = 40)
    public static void largeCastorDropsWithContents(GameTestHelper h) {
        BlockPos master = h.absolutePos(new BlockPos(3, 4, 3));
        var block = com.sqc.nuclearsq.registry.SpentFuelRegistry.LARGE_CASTOR.get();
        for (int p = 0; p < com.sqc.nuclearsq.spent.LargeCastorBlock.PARTS; p++) {
            h.getLevel().setBlockAndUpdate(master.offset(com.sqc.nuclearsq.spent.LargeCastorBlock.dx(p), com.sqc.nuclearsq.spent.LargeCastorBlock.dy(p), com.sqc.nuclearsq.spent.LargeCastorBlock.dz(p)), block.defaultBlockState().setValue(com.sqc.nuclearsq.spent.LargeCastorBlock.PART, p));
        }
        if (!(h.getLevel().getBlockEntity(master) instanceof com.sqc.nuclearsq.spent.LargeCastorBlockEntity cask)) {
            throw new GameTestAssertException("Grosser Castor hat kein BlockEntity");
        }
        if (cask.capacity() != 36 * 16) throw new GameTestAssertException("Platz " + cask.capacity());
        cask.items().insertItem(20, new ItemStack(com.sqc.nuclearsq.registry.SpentFuelRegistry.SPENT_FUEL.get(), 3), false);
        h.runAfterDelay(5, () -> h.getLevel().destroyBlock(master.offset(1, 2, 1), true));
        h.runAfterDelay(15, () -> {
            for (int p = 0; p < com.sqc.nuclearsq.spent.LargeCastorBlock.PARTS; p++) {
                if (h.getLevel().getBlockState(master.offset(com.sqc.nuclearsq.spent.LargeCastorBlock.dx(p), com.sqc.nuclearsq.spent.LargeCastorBlock.dy(p), com.sqc.nuclearsq.spent.LargeCastorBlock.dz(p))).is(block)) throw new GameTestAssertException("Teil " + p + " steht noch");
            }
            int found = 0;
            for (var e : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(master).inflate(4))) {
                ItemStack s = e.getItem();
                if (!s.is(com.sqc.nuclearsq.registry.SpentFuelRegistry.LARGE_CASTOR_ITEM.get())) continue;
                found += s.getCount();
                int[] ic = com.sqc.nuclearsq.spent.CastorCaskBlockEntity.counts(s.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER,
                        net.minecraft.world.item.component.ItemContainerContents.EMPTY).nonEmptyItems());
                if (ic[1] != 3) throw new GameTestAssertException("Castor-Item behaelt den Inhalt nicht: " + ic[1]);
            }
            if (found != 1) throw new GameTestAssertException(found + " Castor-Items gefallen statt 1");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "castor_large", timeoutTicks = 20)
    public static void largeCastorPickUpWithShift(GameTestHelper h) {
        BlockPos master = h.absolutePos(new BlockPos(3, 4, 3));
        var block = com.sqc.nuclearsq.registry.SpentFuelRegistry.LARGE_CASTOR.get();
        for (int p = 0; p < com.sqc.nuclearsq.spent.LargeCastorBlock.PARTS; p++) {
            h.getLevel().setBlockAndUpdate(master.offset(com.sqc.nuclearsq.spent.LargeCastorBlock.dx(p), com.sqc.nuclearsq.spent.LargeCastorBlock.dy(p), com.sqc.nuclearsq.spent.LargeCastorBlock.dz(p)), block.defaultBlockState().setValue(com.sqc.nuclearsq.spent.LargeCastorBlock.PART, p));
        }
        var cask = (com.sqc.nuclearsq.spent.LargeCastorBlockEntity) h.getLevel().getBlockEntity(master);
        cask.items().insertItem(7, new ItemStack(com.sqc.nuclearsq.registry.SpentFuelRegistry.SPENT_FUEL.get(), 5), false);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.fromString("3b8e1c64-5a27-4d90-9f1e-c40d7a62e5b1"), "nsq_castor"));
        player.getInventory().clearContent();
        player.setShiftKeyDown(true);
        BlockPos top = master.offset(1, 2, 1);
        player.setPos(top.getX() + 0.5, top.getY() + 2, top.getZ() + 0.5);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(top), Direction.UP, top, false);
        h.getLevel().getBlockState(top).useWithoutItem(h.getLevel(), player, hit);
        for (int p = 0; p < com.sqc.nuclearsq.spent.LargeCastorBlock.PARTS; p++) {
            if (h.getLevel().getBlockState(master.offset(com.sqc.nuclearsq.spent.LargeCastorBlock.dx(p), com.sqc.nuclearsq.spent.LargeCastorBlock.dy(p), com.sqc.nuclearsq.spent.LargeCastorBlock.dz(p))).is(block)) throw new GameTestAssertException("Teil " + p + " steht noch");
        }
        int have = 0;
        for (ItemStack s : player.getInventory().items) {
            if (!s.is(com.sqc.nuclearsq.registry.SpentFuelRegistry.LARGE_CASTOR_ITEM.get())) continue;
            have += s.getCount();
            int[] ic = com.sqc.nuclearsq.spent.CastorCaskBlockEntity.counts(s.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER,
                    net.minecraft.world.item.component.ItemContainerContents.EMPTY).nonEmptyItems());
            if (ic[1] != 5) throw new GameTestAssertException("Aufgehobener Castor behaelt den Inhalt nicht: " + ic[1]);
        }
        if (have != 1) throw new GameTestAssertException(have + " Castor-Items im Inventar statt 1");
        if (!h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(master).inflate(4)).isEmpty()) throw new GameTestAssertException("Zusaetzlicher Drop");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "spent_carried", timeoutTicks = 20)
    public static void spentFuelRadiatesWhenCarried(GameTestHelper h) {
        var p = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        double base = com.sqc.nuclearsq.radiation.RadiationManager.carried(p);
        p.getInventory().add(spentRod());
        p.getInventory().add(new ItemStack(com.sqc.nuclearsq.registry.SpentFuelRegistry.SPENT_FUEL.get(), 10));
        double want = com.sqc.nuclearsq.spent.SpentFuel.HOT_ROD_CARRY_SV_H + 10 * com.sqc.nuclearsq.spent.SpentFuel.COOLED_CARRY_SV_H;
        double got = com.sqc.nuclearsq.radiation.RadiationManager.carried(p) - base;
        if (Math.abs(got - want) > 1e-9) throw new GameTestAssertException("Getragen " + got + " statt " + want + " Sv/h");
        p.getInventory().clearContent();
        ItemStack cask = new ItemStack(com.sqc.nuclearsq.registry.SpentFuelRegistry.LARGE_CASTOR_ITEM.get());
        cask.set(net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(spentRod())));
        p.getInventory().add(cask);
        if (com.sqc.nuclearsq.radiation.RadiationManager.carried(p) != 0) throw new GameTestAssertException("Castor-Inhalt strahlt");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "coal_generator", timeoutTicks = 100)
    public static void coalGeneratorBurnsCoal(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.COAL_GENERATOR, new BlockPos(3, 4, 3));
        var auto = be.itemsAt("item_in");
        if (!auto.insertItem(0, new ItemStack(Blocks.STONE), true).is(Blocks.STONE.asItem())) throw new GameTestAssertException("Generator nimmt Stein");
        if (!auto.insertItem(0, new ItemStack(net.minecraft.world.item.Items.COAL, 2), false).isEmpty()) throw new GameTestAssertException("Generator nimmt keine Kohle");
        BlockPos out = BigMachine.COAL_GENERATOR.structure.world(be.getBlockPos(), 0, 3, 0, 1);
        BigMachineBlockEntity sink = TestSinks.batteryAt(h, out, Direction.WEST);
        h.runAfterDelay(60, () -> {
            formed(be);
            formed(sink);
            if (be.crafter().status() != com.sqc.nuclearsq.machine.big.BigCrafter.OK) throw new GameTestAssertException("Zustand " + be.crafter().status() + ", Akku " + sink.energy().getEnergyStored());
            if (be.items().getStackInSlot(0).getCount() != 1) throw new GameTestAssertException("Kohle nicht angezuendet: " + be.items().getStackInSlot(0));
            if (!be.isRunning()) throw new GameTestAssertException("Generator gilt nicht als laufend");
            if (be.energy().getEnergyStored() > 720 || sink.energy().getEnergyStored() < 30 * 720) {
                throw new GameTestAssertException("Strom: Generator " + be.energy().getEnergyStored() + ", Akku " + sink.energy().getEnergyStored());
            }
            if (be.lastOut() != 720) throw new GameTestAssertException("Abgabe " + be.lastOut() + " statt 720 FE/t");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "coal_generator", timeoutTicks = 100)
    public static void coalGeneratorWaitsForConsumer(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.COAL_GENERATOR, new BlockPos(3, 4, 3));
        be.itemsAt("item_in").insertItem(0, new ItemStack(net.minecraft.world.item.Items.COAL, 2), false);
        h.runAfterDelay(40, () -> {
            formed(be);
            int progress = be.crafter().progress();
            if (be.crafter().status() != com.sqc.nuclearsq.machine.big.BigCrafter.OUTPUT_FULL) throw new GameTestAssertException("Zustand " + be.crafter().status() + " statt kein Abnehmer");
            if (be.energy().getEnergyStored() > 720) throw new GameTestAssertException("Generator speichert " + be.energy().getEnergyStored() + " FE");
            h.runAfterDelay(20, () -> {
                if (be.crafter().progress() != progress) throw new GameTestAssertException("Brennt ohne Abnehmer weiter: " + progress + " -> " + be.crafter().progress());
                if (be.items().getStackInSlot(0).getCount() < 1) throw new GameTestAssertException("Kohle verbraucht ohne Abnehmer");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "coal_generator", timeoutTicks = 100)
    public static void coalGeneratorCards(GameTestHelper h) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.COAL_GENERATOR, new BlockPos(3, 4, 3));
        var cards = com.sqc.nuclearsq.registry.PowerRegistry.COAL_CARDS;
        if (!be.itemsAt("item_in").insertItem(1, new ItemStack(cards.get(0).get()), true).is(cards.get(0).get())) throw new GameTestAssertException("Trichter setzt Karten ein");
        be.items().setStackInSlot(1, new ItemStack(cards.get(0).get(), 8));
        be.items().setStackInSlot(2, new ItemStack(cards.get(1).get(), 4));
        be.items().setStackInSlot(3, new ItemStack(cards.get(2).get(), 2));
        for (int i = 0; i < 3; i++) {
            if (!net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(be.itemsAt("item_in"), new ItemStack(net.minecraft.world.item.Items.COAL, 64), false).isEmpty()) {
                throw new GameTestAssertException("Brennstofffeld nimmt keinen Stapel " + (i + 1));
            }
        }
        if (net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(be.itemsAt("item_in"), new ItemStack(net.minecraft.world.item.Items.COAL, 1), false).isEmpty()) {
            throw new GameTestAssertException("Brennstofffeld nimmt mehr als 3 Stapel");
        }
        BlockPos out = BigMachine.COAL_GENERATOR.structure.world(be.getBlockPos(), 0, 3, 0, 1);
        BigMachineBlockEntity sink = TestSinks.batteryAt(h, out, Direction.WEST);
        h.runAfterDelay(60, () -> {
            formed(be);
            formed(sink);
            if (be.lastOut() != 3200) throw new GameTestAssertException("Abgabe " + be.lastOut() + " statt 3200 FE/t");
            if (be.crafter().maxProgress() != 1600 * 50 * 10000 / (100 * 35560)) throw new GameTestAssertException("Brenndauer " + be.crafter().maxProgress());
            if (be.items().getStackInSlot(0).getCount() != 64 || be.crafter().needed() != 127) throw new GameTestAssertException("Vorrat " + be.items().getStackInSlot(0) + " und " + be.crafter().needed());
            for (int i = 1; i < 4; i++) {
                if (!be.itemsAt("item_out").extractItem(i, 8, true).isEmpty()) throw new GameTestAssertException("Ausgang gibt Karte " + i + " her");
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "solar", timeoutTicks = 140)
    public static void solarFollowsTheSun(GameTestHelper h) {
        var lvl = h.getLevel();
        lvl.setWeatherParameters(12000, 0, false, false);
        lvl.setDayTime(6000);
        BigMachineBlockEntity field = buildBig(h, BigMachine.SOLAR_FIELD, new BlockPos(2, 4, 2));
        BlockPos free = h.absolutePos(new BlockPos(11, 4, 11));
        for (BlockPos c : List.of(new BlockPos(11, 4, 11), new BlockPos(11, 4, 1), new BlockPos(1, 4, 11), new BlockPos(1, 4, 1), new BlockPos(6, 4, 11))) {
            if (lvl.canSeeSky(h.absolutePos(c).above())) { free = h.absolutePos(c); break; }
        }
        BlockPos panelPos = free;
        lvl.setBlockAndUpdate(panelPos, com.sqc.nuclearsq.registry.PowerRegistry.SOLAR_PANEL.get().defaultBlockState());
        h.runAfterDelay(30, () -> {
            formed(field);
            for (MultiblockStructure.Cell c : BigMachine.SOLAR_FIELD.structure.cells(field.getBlockPos(), field.formedRot())) {
                BlockState st = lvl.getBlockState(c.pos());
                if (st.hasProperty(com.sqc.nuclearsq.reactor.ReactorCasingBlock.SKY)
                        && (!st.getValue(com.sqc.nuclearsq.reactor.ReactorCasingBlock.SKY) || st.getLightBlock(lvl, c.pos()) != 0
                        || st.isValidSpawn(lvl, c.pos(), net.minecraft.world.entity.EntityType.ZOMBIE))) {
                    throw new GameTestAssertException("Solarfeld-Gehaeuse sperrt Himmelslicht oder laesst Monster spawnen bei " + c.pos());
                }
            }
            if (BigMachine.SOLAR_FIELD.extra("fe_per_tick_peak") != 2400 || field.energy().getMaxEnergyStored() != 1000000) {
                throw new GameTestAssertException("Solarfeld-Spitze " + BigMachine.SOLAR_FIELD.extra("fe_per_tick_peak") + " (2400), Puffer " + field.energy().getMaxEnergyStored() + " (1000000)");
            }
            if (!(lvl.getBlockEntity(panelPos) instanceof com.sqc.nuclearsq.power.SolarPanelBlockEntity panel)) throw new GameTestAssertException("Solarmodul ohne BlockEntity");
            if (panel.rate() <= 0) throw new GameTestAssertException("Solarmodul mittags ohne Strom: " + panel.rate());
            if (panel.delivered() != 0) throw new GameTestAssertException("Solarmodul ohne Abnehmer gibt " + panel.delivered() + " FE/t ab");
            if (panel.capacity() != 69 * 400 || panel.stored() <= 0) throw new GameTestAssertException("Solarmodul puffert nicht: " + panel.stored() + " / " + panel.capacity());
            if (field.lastMb() <= 0 || !field.isRunning() || field.energy().getEnergyStored() <= field.lastMb()) {
                throw new GameTestAssertException("Solarfeld mittags ohne Abnehmer puffert nicht: " + field.lastMb() + " FE/t, Puffer " + field.energy().getEnergyStored());
            }
            lvl.setDayTime(10000);
            float fixed = com.sqc.nuclearsq.power.SolarMath.daylight(lvl), tracked = com.sqc.nuclearsq.power.SolarMath.trackedDaylight(lvl);
            if (fixed > 0.9f || tracked < 0.95f) throw new GameTestAssertException("Nachfuehrung am Nachmittag: fest " + fixed + ", nachgefuehrt " + tracked);
            lvl.setDayTime(18000);
            if (com.sqc.nuclearsq.power.SolarMath.trackedDaylight(lvl) != 0f) throw new GameTestAssertException("Nachfuehrung nachts nicht 0");
            int stored = field.energy().getEnergyStored();
            h.runAfterDelay(50, () -> {
                if (panel.rate() != 0) throw new GameTestAssertException("Solarmodul nachts: " + panel.rate());
                if (field.lastMb() != 0 || field.isRunning()) throw new GameTestAssertException("Solarfeld nachts: " + field.lastMb());
                if (field.energy().getEnergyStored() < stored) throw new GameTestAssertException("Solarfeld-Puffer ohne Abnehmer verliert Strom: " + field.energy().getEnergyStored() + " < " + stored);
                lvl.setDayTime(6000);
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "wind", timeoutTicks = 200)
    public static void windTurbinesDeliverPower(GameTestHelper h) {
        var lvl = h.getLevel();
        lvl.setWeatherParameters(0, 12000, true, true);
        BigMachineBlockEntity small = buildBig(h, BigMachine.WIND_TURBINE_SMALL, new BlockPos(1, 4, 1));
        BigMachineBlockEntity medium = buildBig(h, BigMachine.WIND_TURBINE_MEDIUM, new BlockPos(9, 4, 1));
        h.runAfterDelay(120, () -> {
            for (BigMachineBlockEntity be : List.of(small, medium)) {
                formed(be);
                BigMachine wm = be.machine();
                if (wm.structure.charAt(wm.extra("hub_x"), wm.extra("hub_y"), wm.extra("hub_z")) == ' ') throw new GameTestAssertException(wm.id + ": Nabenfeld ist im Blockmuster leer");
                int peak = be.machine().extra("fe_per_tick_peak");
                if (be.lastMb() <= 0 || be.lastMb() > peak || !be.isRunning()) {
                    throw new GameTestAssertException(be.machine().id + " im Sturm: " + be.lastMb() + " FE/t, Spitze " + peak + ", Zustand " + be.stateKey()
                            + ", Hoehe " + be.windHeightPct() + " %, Umgebung " + be.windSurroundPct() + " %, Wind " + be.windGustPct() + " %");
                }
                if (be.energy().getEnergyStored() > be.lastMb()) throw new GameTestAssertException(be.machine().id + " speichert Strom: " + be.energy().getEnergyStored());
                if (!be.isGenerator() || be.generatorPeak() != peak) throw new GameTestAssertException(be.machine().id + " ist kein Erzeuger mit Spitze " + peak);
            }
            if (BigMachine.WIND_TURBINE_SMALL.extra("fe_per_tick_peak") != 352 || BigMachine.WIND_TURBINE_MEDIUM.extra("fe_per_tick_peak") != 1760
                    || BigMachine.WIND_TURBINE_LARGE.extra("fe_per_tick_peak") != 7040) throw new GameTestAssertException("Spitzenleistung der Windturbinen geaendert");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "wind", timeoutTicks = 260)
    public static void windTurbineNeedsOpenSkyAboveTheHub(GameTestHelper h) {
        var lvl = h.getLevel();
        lvl.setWeatherParameters(0, 12000, true, true);
        BigMachineBlockEntity be = buildBig(h, BigMachine.WIND_TURBINE_SMALL, new BlockPos(1, 4, 1));
        BlockPos hub = be.machine().structure.world(be.getBlockPos(), 0, be.machine().extra("hub_x"), be.machine().extra("hub_y"), be.machine().extra("hub_z"));
        BlockPos roof = hub.above(be.machine().h - be.machine().extra("hub_y") + 1);
        lvl.setBlockAndUpdate(roof, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        h.startSequence()
                .thenWaitUntil(() -> {
                    formed(be);
                    if (lvl.canSeeSky(roof.below())) throw new GameTestAssertException("warte auf das Licht unter dem Dach");
                })
                .thenWaitUntil(() -> {
                    if (be.lastMb() != 0 || be.isRunning() || !be.stateKey().equals("no_wind")) {
                        throw new GameTestAssertException("Windturbine unter Dach: " + be.lastMb() + " FE/t, Zustand " + be.stateKey());
                    }
                })
                .thenExecute(() -> lvl.setBlockAndUpdate(roof, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()))
                .thenWaitUntil(() -> {
                    if (be.lastMb() <= 0 || !be.isRunning()) throw new GameTestAssertException("Windturbine ohne Dach liefert nichts: " + be.lastMb() + " FE/t, Zustand " + be.stateKey());
                })
                .thenSucceed();
    }

    @GameTest(template = FLOOR, batch = "wind", timeoutTicks = 200)
    public static void largeWindTurbineFormsAndRuns(GameTestHelper h) {
        var lvl = h.getLevel();
        lvl.setWeatherParameters(0, 12000, true, true);
        BigMachineBlockEntity be = buildBig(h, BigMachine.WIND_TURBINE_LARGE, new BlockPos(1, 4, 1));
        h.runAfterDelay(120, () -> {
            formed(be);
            BigMachine m = be.machine();
            if (m.w != 35 || m.h != 72 || m.d != 14) throw new GameTestAssertException("Raster " + m.w + "x" + m.h + "x" + m.d);
            if (m.structure.charAt(m.extra("hub_x"), m.extra("hub_y"), m.extra("hub_z")) == ' ') throw new GameTestAssertException("Nabenfeld ist im Blockmuster leer");
            if (be.lastMb() <= 0 || be.lastMb() > 6400) throw new GameTestAssertException("Windkraftanlage im Sturm: " + be.lastMb() + " FE/t, Zustand " + be.stateKey());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "wind", timeoutTicks = 20)
    public static void windMathFactors(GameTestHelper h) {
        if (Math.abs(com.sqc.nuclearsq.power.WindMath.height(40) - 0.3f) > 1e-4 || Math.abs(com.sqc.nuclearsq.power.WindMath.height(160) - 1f) > 1e-4
                || com.sqc.nuclearsq.power.WindMath.height(0) != 0.3f || com.sqc.nuclearsq.power.WindMath.height(300) != 1f
                || com.sqc.nuclearsq.power.WindMath.height(100) <= com.sqc.nuclearsq.power.WindMath.height(80)) throw new GameTestAssertException("Hoehenfaktor");
        float lo = 9, hi = -9;
        for (long t = 0; t < 40000; t += 7) {
            float g = com.sqc.nuclearsq.power.WindMath.gust(t);
            lo = Math.min(lo, g);
            hi = Math.max(hi, g);
        }
        if (lo < 0.2f || hi > 1f || lo > 0.4f || hi < 0.8f) throw new GameTestAssertException("Boeen zwischen " + lo + " und " + hi);
        if (com.sqc.nuclearsq.power.WindMath.rate(1200, 1f, 1f, 1f) != 1200 || com.sqc.nuclearsq.power.WindMath.rate(1200, 1f, 1f, 0.03f) != 0
                || com.sqc.nuclearsq.power.WindMath.rate(64, 0.5f, 0.5f, 0.5f) != 8) throw new GameTestAssertException("Leistung aus den Faktoren");
        if (com.sqc.nuclearsq.power.WindMath.surround(0f) != 0.2f || com.sqc.nuclearsq.power.WindMath.surround(1f) != 1f) throw new GameTestAssertException("Umgebungsfaktor");
        var lvl = h.getLevel();
        BlockPos center = h.absolutePos(new BlockPos(8, 9, 8));
        float open = com.sqc.nuclearsq.power.WindMath.openness(lvl, center, 7, 2);
        if (open < 0.8f) throw new GameTestAssertException("Freie Flaeche: " + open);
        for (int dy : new int[] {0, -3}) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == 4) lvl.setBlockAndUpdate(center.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                }
            }
        }
        float walled = com.sqc.nuclearsq.power.WindMath.openness(lvl, center, 7, 2);
        if (walled > 0.45f) throw new GameTestAssertException("Zugebaut: " + walled);
        h.succeed();
    }

    private static final java.util.Set<String> NO_RECIPE = java.util.Set.of(
            "oil_well_sqc",
            "turbine_port_energy_sqc", "turbine_port_steam_sqc", "turbine_port_water_sqc", "coal_generator_controller_sqc",
            "small_parts_assembler_controller_sqc", "micro_assembler_controller_sqc", "equipment_assembler_controller_sqc", "plate_mill_controller_sqc", "cnc_mill_controller_sqc", "solder_machine_controller_sqc", "hammer_mill_controller_sqc", "kiln_controller_sqc", "blast_furnace_controller_sqc", "oil_derrick_controller_sqc", "distillation_column_controller_sqc", "cracker_controller_sqc", "gas_generator_controller_sqc", "fluid_buffer_controller_sqc", "exo_station_controller_sqc",
            "server_rack_small_controller_sqc", "compute_cluster_controller_sqc", "server_rack_controller_sqc", "server_rack_large_controller_sqc", "pattern_rack_controller_sqc",
            "spent_fuel_sqc", "filter_residue_sqc", "radioactive_fallout_sqc", "bred_blanket_sqc", "bred_neptunium_sqc",
            "hot_coolant_bucket_sqc", "feedwater_bucket_sqc", "preheated_feedwater_bucket_sqc",
            "coin_sqc", "coin_roll_sqc", "coin_bar_sqc",
            "plasma_optimizer_sqc",
            "creative_energy_source_sqc", "creative_fluid_source_sqc",
            "reactor_detonator_sqc",
            "lithium_ore_sqc", "deepslate_lithium_ore_sqc", "raw_lithium_sqc", "worn_wall_tile_sqc",
            "contaminated_rubble_sqc");

    private static boolean noRecipeNeeded(String path) {
        return NO_RECIPE.contains(path) || path.startsWith("header_") || path.equals("spacer_sqc")
                || com.sqc.nuclearsq.registry.ResourceRegistry.fromWorld(path) || com.sqc.nuclearsq.registry.ResourceRegistry.replacedByMekanism(path);
    }

    @GameTest(template = FLOOR, batch = "recipe_coverage")
    public static void everyItemHasARecipe(GameTestHelper h) {
        var regs = h.getLevel().registryAccess();
        java.util.Map<net.minecraft.world.item.Item, java.util.Set<String>> src = new java.util.HashMap<>();
        for (var holder : h.getLevel().getRecipeManager().getRecipes()) {
            var r = holder.value();
            String where = r instanceof com.sqc.nuclearsq.assembler.HeavyAssemblyRecipe ? "Gross-Assembler"
                    : r instanceof com.sqc.nuclearsq.assembler.AssemblyRecipe ? "Reaktor-Assembler"
                    : r.getType() == net.minecraft.world.item.crafting.RecipeType.CRAFTING ? "Werkbank"
                    : r.getType() == net.minecraft.world.item.crafting.RecipeType.SMELTING || r.getType() == net.minecraft.world.item.crafting.RecipeType.BLASTING ? "Ofen"
                    : r.getType().toString();
            if (r instanceof ReprocessingRecipe rr) {
                for (var res : rr.results()) src.computeIfAbsent(res.stack().getItem(), k -> new java.util.TreeSet<>()).add("Anlage " + rr.kind().id);
                continue;
            }
            ItemStack out = r.getResultItem(regs);
            if (!out.isEmpty()) src.computeIfAbsent(out.getItem(), k -> new java.util.TreeSet<>()).add(where);
        }
        List<String> missing = new java.util.ArrayList<>(), twice = new java.util.ArrayList<>();
        java.util.Set<String> assemblers = java.util.Set.of("Gross-Assembler", "Reaktor-Assembler", NuclearSQ.MODID + ":small_parts_assembly", NuclearSQ.MODID + ":equipment_assembly");
        StringBuilder table = new StringBuilder("REZEPT-TABELLE");
        for (var e : net.minecraft.core.registries.BuiltInRegistries.ITEM.entrySet()) {
            var id = e.getKey().location();
            if (!id.getNamespace().equals(NuclearSQ.MODID)) continue;
            var where = src.get(e.getValue());
            table.append("\n  ").append(id.getPath()).append(" = ").append(where == null ? "-" : String.join(", ", where));
            if (where == null && !noRecipeNeeded(id.getPath())) missing.add(id.getPath());
            if (where != null && where.stream().filter(assemblers::contains).count() > 1) twice.add(id.getPath() + " " + where);
        }
        NuclearSQ.LOGGER.info(table.toString());
        if (!missing.isEmpty()) throw new GameTestAssertException("Ohne Rezept (" + missing.size() + "): " + String.join(", ", missing));
        if (!twice.isEmpty()) throw new GameTestAssertException("In zwei Assemblern (" + twice.size() + "): " + String.join(", ", twice));
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "recipe_coverage", timeoutTicks = 20)
    public static void compendiumShowsWhereItemsAreMade(GameTestHelper h) {
        var level = h.getLevel();
        var rm = level.getRecipeManager();
        var regs = level.registryAccess();
        List<String> problems = new java.util.ArrayList<>();
        for (var e : net.minecraft.core.registries.BuiltInRegistries.ITEM.entrySet()) {
            var id = e.getKey().location();
            if (!id.getNamespace().equals(NuclearSQ.MODID) || noRecipeNeeded(id.getPath())) continue;
            var src = com.sqc.nuclearsq.progression.RecipeSources.forItem(rm, regs, e.getValue());
            if (src.isEmpty()) problems.add(id.getPath() + ": keine Maschine");
            for (var s : src) {
                if (s.where() == com.sqc.nuclearsq.progression.RecipeSources.Where.OTHER
                        && (s.type().getNamespace().equals(NuclearSQ.MODID) || s.type().getNamespace().equals("minecraft")))
                    problems.add(id.getPath() + ": Rezepttyp " + s.type() + " ohne Maschine");
                if (s.needs().isEmpty() && s.where() != com.sqc.nuclearsq.progression.RecipeSources.Where.OTHER) problems.add(id.getPath() + ": " + s.where() + " ohne Zutaten");
                if (!s.result().is(e.getValue())) problems.add(id.getPath() + ": falsches Ergebnis " + s.result());
            }
        }
        java.util.Map<String, com.sqc.nuclearsq.progression.RecipeSources.Where> expect = java.util.Map.of(
                "small_parts_assembler_kit_sqc", com.sqc.nuclearsq.progression.RecipeSources.Where.WORKBENCH,
                "kiln_kit_sqc", com.sqc.nuclearsq.progression.RecipeSources.Where.WORKBENCH,
                "reactor_assembler_kit_sqc", com.sqc.nuclearsq.progression.RecipeSources.Where.SMALL_PARTS_ASSEMBLER,
                "uranium_fragment_sqc", com.sqc.nuclearsq.progression.RecipeSources.Where.BLAST_FURNACE,
                "steel_sheet_sqc", com.sqc.nuclearsq.progression.RecipeSources.Where.SMALL_PARTS_ASSEMBLER,
                "battery_small_kit_sqc", com.sqc.nuclearsq.progression.RecipeSources.Where.HEAVY_ASSEMBLER,
                "hard_drive_1_sqc", com.sqc.nuclearsq.progression.RecipeSources.Where.SMALL_PARTS_ASSEMBLER,
                "copper_dust_sqc", com.sqc.nuclearsq.progression.RecipeSources.Where.ORE_MILLING);
        for (var e : expect.entrySet()) {
            var it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, e.getKey()));
            var src = com.sqc.nuclearsq.progression.RecipeSources.forItem(rm, regs, it);
            if (src.stream().noneMatch(s -> s.where() == e.getValue())) problems.add(e.getKey() + ": erwartet " + e.getValue() + ", ist " + src.stream().map(s -> s.where().name()).toList());
        }
        for (var w : com.sqc.nuclearsq.progression.RecipeSources.Where.values())
            if (!com.sqc.nuclearsq.progression.RecipeSources.CATALOG_STAGES.contains(w)) problems.add("Station " + w + " fehlt in RecipeSources.CATALOG_STAGES");
        for (String lang : List.of("de_de", "en_us")) {
            com.google.gson.JsonObject l;
            try (var in = NuclearGameTests.class.getResourceAsStream("/assets/" + NuclearSQ.MODID + "/lang/" + lang + ".json")) {
                l = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception ex) {
                throw new GameTestAssertException(lang + " nicht lesbar: " + ex);
            }
            for (String k : List.of("compendium.made_in", "compendium.recipe.shaped", "compendium.recipe.assembler"))
                if (!l.has(k)) problems.add(lang + ": " + k + " fehlt");
            for (var w : com.sqc.nuclearsq.progression.RecipeSources.Where.values()) {
                if (w == com.sqc.nuclearsq.progression.RecipeSources.Where.OTHER) continue;
                if (!l.has(w.langKey())) problems.add(lang + ": " + w.langKey() + " fehlt");
                if (lang.equals("de_de") && w.iconStack().isEmpty()) problems.add(w + ": Bild-Item " + w.icon + " nicht registriert");
                if (lang.equals("de_de") && w.icon.getNamespace().equals(NuclearSQ.MODID))
                    for (String suffix : List.of(".png", "_small.png"))
                        if (NuclearGameTests.class.getResource("/assets/" + NuclearSQ.MODID + "/textures/gui/compendium/machines/" + w.icon.getPath() + suffix) == null)
                            problems.add(w + ": Maschinenbild " + w.icon.getPath() + suffix + " fehlt");
            }
        }
        if (!problems.isEmpty()) throw new GameTestAssertException(problems.size() + " Probleme: " + String.join("; ", problems));
        h.succeed();
    }
}
