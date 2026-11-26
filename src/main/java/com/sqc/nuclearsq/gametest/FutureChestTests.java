package com.sqc.nuclearsq.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.futurechest.FutureChestBlock;
import com.sqc.nuclearsq.futurechest.FutureChestBlockEntity;
import com.sqc.nuclearsq.futurechest.FutureChestMenu;
import com.sqc.nuclearsq.futurechest.FutureChestRegistry;
import com.sqc.nuclearsq.futurechest.QuantumUpgradeItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class FutureChestTests {
    private FutureChestTests() {}

    private static final String FLOOR = "floor";

    private static ServerPlayer player(GameTestHelper h, String name) {
        ServerPlayer sp = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("nuclearsq:future_chest_" + name).getBytes()), "qc_" + name));
        Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 3, 1)));
        sp.setPos(at.x, at.y, at.z);
        sp.setShiftKeyDown(false);
        sp.getInventory().clearContent();
        return sp;
    }

    private static ItemStack filled() {
        ItemStack st = new ItemStack(FutureChestRegistry.ITEM.get());
        st.set(FutureChestRegistry.CONTENTS.get(), ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 5),
                new ItemStack(Items.IRON_INGOT, 64))));
        return st;
    }

    private static int countOf(FutureChestBlockEntity be, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < be.getContainerSize(); i++) if (be.getItem(i).is(item)) n += be.getItem(i).getCount();
        return n;
    }

    @GameTest(template = FLOOR, batch = "future_chest", timeoutTicks = 120)
    public static void placeUnfoldAndBreakKeepsContents(GameTestHelper h) {
        BlockPos ground = new BlockPos(3, 1, 3), at = ground.above();
        h.setBlock(ground, Blocks.STONE);
        h.setBlock(at, Blocks.AIR);
        ServerPlayer sp = player(h, "break");
        sp.setGameMode(GameType.SURVIVAL);
        ItemStack held = filled();
        sp.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos abs = h.absolutePos(ground);
        sp.gameMode.useItemOn(sp, h.getLevel(), held, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        BlockState st = h.getBlockState(at);
        if (!st.is(FutureChestRegistry.BLOCK.get())) throw new GameTestAssertException("Kiste nicht gesetzt: " + st);
        if (!st.getValue(FutureChestBlock.UNFOLDED)) throw new GameTestAssertException("Kiste muss beim Setzen sofort aufgeklappt sein");
        if (!(h.getLevel().getBlockEntity(h.absolutePos(at)) instanceof FutureChestBlockEntity be)) throw new GameTestAssertException("Kein BlockEntity");
        if (be.animStart() != FutureChestBlockEntity.NONE) throw new GameTestAssertException("Animation darf nicht starten");
        if (countOf(be, Items.DIAMOND) != 5 || countOf(be, Items.IRON_INGOT) != 64) throw new GameTestAssertException("Inhalt nicht uebernommen");
        h.runAfterDelay(FutureChestBlockEntity.ANIM_TICKS + 5, () -> {
            if (!h.getBlockState(at).getValue(FutureChestBlock.UNFOLDED)) throw new GameTestAssertException("Kiste klappt nicht auf");
            if (be.animStart() != FutureChestBlockEntity.NONE) throw new GameTestAssertException("Animation nicht beendet");
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            sp.gameMode.destroyBlock(h.absolutePos(at));
            h.runAfterDelay(2, () -> {
                if (!h.getBlockState(at).isAir()) throw new GameTestAssertException("Kiste nicht abgebaut");
                AABB box = new AABB(h.absolutePos(ground)).inflate(3);
                long loose = h.getLevel().getEntitiesOfClass(ItemEntity.class, box).stream().filter(e -> e.getItem().is(FutureChestRegistry.ITEM.get())).count();
                ItemStack got = ItemStack.EMPTY;
                int n = 0;
                StringBuilder seen = new StringBuilder();
                for (ItemStack inv : sp.getInventory().items) {
                    if (!inv.is(FutureChestRegistry.ITEM.get())) continue;
                    got = inv;
                    n++;
                    seen.append(" [").append(inv.getOrDefault(FutureChestRegistry.CONTENTS.get(), ItemContainerContents.EMPTY).nonEmptyStream().count()).append(" Staepel]");
                }
                sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                if (n != 1 || loose != 0) throw new GameTestAssertException("Kiste muss genau einmal im Inventar landen (Inventar " + n + ", am Boden " + loose + ")" + seen);
                ItemContainerContents c = got.getOrDefault(FutureChestRegistry.CONTENTS.get(), ItemContainerContents.EMPTY);
                int diamonds = 0, iron = 0;
                for (ItemStack s : c.nonEmptyItems()) {
                    if (s.is(Items.DIAMOND)) diamonds += s.getCount();
                    if (s.is(Items.IRON_INGOT)) iron += s.getCount();
                }
                if (diamonds != 5 || iron != 64) throw new GameTestAssertException("Inhalt im Item verloren: " + diamonds + " Diamanten, " + iron + " Eisen");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "future_chest", timeoutTicks = 40)
    public static void shiftRightClickPicksUpChest(GameTestHelper h) {
        BlockPos at = new BlockPos(3, 2, 3);
        h.setBlock(at, FutureChestRegistry.BLOCK.get().defaultBlockState());
        FutureChestBlockEntity be = (FutureChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(at));
        be.setItem(3, new ItemStack(Items.DIAMOND, 5));
        ServerPlayer sp = player(h, "shift");
        sp.setGameMode(GameType.SURVIVAL);
        sp.setShiftKeyDown(true);
        BlockPos abs = h.absolutePos(at);
        h.getLevel().getBlockState(abs).useWithoutItem(h.getLevel(), sp, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        sp.setShiftKeyDown(false);
        if (!h.getBlockState(at).isAir()) throw new GameTestAssertException("Kiste steht noch");
        int n = 0;
        for (ItemStack inv : sp.getInventory().items) {
            if (!inv.is(FutureChestRegistry.ITEM.get())) continue;
            n++;
            int diamonds = 0;
            for (ItemStack s : inv.getOrDefault(FutureChestRegistry.CONTENTS.get(), ItemContainerContents.EMPTY).nonEmptyItems()) if (s.is(Items.DIAMOND)) diamonds += s.getCount();
            if (diamonds != 5) throw new GameTestAssertException("Inhalt im Item verloren: " + diamonds + " Diamanten");
        }
        long loose = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(3)).stream().filter(e -> e.getItem().is(FutureChestRegistry.ITEM.get())).count();
        if (n != 1 || loose != 0) throw new GameTestAssertException("Kiste muss genau einmal im Inventar landen (Inventar " + n + ", am Boden " + loose + ")");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "future_chest", timeoutTicks = 40)
    public static void commandPlacedChestIsReady(GameTestHelper h) {
        BlockPos at = new BlockPos(3, 2, 3);
        h.setBlock(at, FutureChestRegistry.BLOCK.get().defaultBlockState());
        BlockState st = h.getBlockState(at);
        if (!st.getValue(FutureChestBlock.UNFOLDED)) throw new GameTestAssertException("Standardzustand muss aufgeklappt sein");
        if (!(h.getLevel().getBlockEntity(h.absolutePos(at)) instanceof FutureChestBlockEntity be)) throw new GameTestAssertException("Kein BlockEntity");
        if (!be.isEmpty()) throw new GameTestAssertException("Neue Kiste muss leer sein");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "future_chest", timeoutTicks = 40)
    public static void chestDoesNotFitInChest(GameTestHelper h) {
        BlockPos at = new BlockPos(3, 2, 3);
        h.setBlock(at, FutureChestRegistry.BLOCK.get().defaultBlockState());
        FutureChestBlockEntity be = (FutureChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(at));
        ItemStack chest = new ItemStack(FutureChestRegistry.ITEM.get());
        if (be.canPlaceItem(0, chest)) throw new GameTestAssertException("Kiste darf nicht in die Kiste");
        if (!be.canPlaceItem(0, new ItemStack(Items.STONE))) throw new GameTestAssertException("Stein muss hinein duerfen");
        if (chest.getItem().canFitInsideContainerItems()) throw new GameTestAssertException("Kiste darf auch in keine Shulker-Kiste");
        ServerPlayer sp = player(h, "nest");
        FutureChestMenu menu = new FutureChestMenu(1, sp.getInventory(), be, be.upgrades(), be.capacity());
        int firstInv = be.capacity() + FutureChestBlockEntity.UPGRADE_SLOTS;
        menu.getSlot(firstInv).set(chest.copy());
        menu.getSlot(firstInv + 1).set(new ItemStack(Items.COBBLESTONE, 10));
        menu.quickMoveStack(sp, firstInv);
        menu.quickMoveStack(sp, firstInv + 1);
        if (countOf(be, FutureChestRegistry.ITEM.get()) != 0) throw new GameTestAssertException("Kiste ist per Shift-Klick in die Kiste gelangt");
        if (countOf(be, Items.COBBLESTONE) != 10) throw new GameTestAssertException("Shift-Klick verschiebt den Stapel nicht");
        h.succeed();
    }

    private static FutureChestBlockEntity chest(GameTestHelper h) {
        BlockPos at = new BlockPos(3, 2, 3);
        h.setBlock(at, FutureChestRegistry.BLOCK.get().defaultBlockState());
        return (FutureChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(at));
    }

    private static ItemStack upgrade(QuantumUpgradeItem.Kind k, int n) {
        return new ItemStack(FutureChestRegistry.UPGRADE_ITEMS.get(k).get(), n);
    }

    @GameTest(template = FLOOR, batch = "future_chest", timeoutTicks = 40)
    public static void capacityUpgradesAddSlots(GameTestHelper h) {
        FutureChestBlockEntity be = chest(h);
        if (be.capacity() != 32 || be.getContainerSize() != 32) throw new GameTestAssertException("Grundgroesse 32 erwartet, ist " + be.capacity());
        if (be.canPlaceItem(40, new ItemStack(Items.STONE))) throw new GameTestAssertException("Platz 40 darf ohne Upgrade nichts nehmen");
        be.upgrades().setItem(FutureChestBlockEntity.SLOT_CAPACITY, upgrade(QuantumUpgradeItem.Kind.CAPACITY, 1));
        if (be.capacity() != 64) throw new GameTestAssertException("64 erwartet, ist " + be.capacity());
        be.upgrades().setItem(FutureChestBlockEntity.SLOT_CAPACITY, upgrade(QuantumUpgradeItem.Kind.CAPACITY, 2));
        if (be.capacity() != 128) throw new GameTestAssertException("128 erwartet, ist " + be.capacity());
        be.setItem(100, new ItemStack(Items.STONE, 3));
        if (be.isEmptyFrom(FutureChestBlockEntity.BASE_SIZE)) throw new GameTestAssertException("Belegter Platz 100 nicht erkannt");
        FutureChestMenu menu = new FutureChestMenu(1, player(h, "capacity").getInventory(), be, be.upgrades(), be.capacity());
        if (menu.getSlot(FutureChestBlockEntity.SLOT_CAPACITY + 128).mayPickup(player(h, "capacity")))
            throw new GameTestAssertException("Platz-Upgrade darf nicht raus, solange oben etwas liegt");
        if (menu.getSlot(128 + 1).mayPlace(upgrade(QuantumUpgradeItem.Kind.SORTER, 1))) throw new GameTestAssertException("Magnet-Platz nimmt Sortierer");
        if (!menu.getSlot(128 + 1).mayPlace(upgrade(QuantumUpgradeItem.Kind.MAGNET, 1))) throw new GameTestAssertException("Magnet-Platz lehnt Magnet ab");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "future_chest", timeoutTicks = 40)
    public static void sorterMergesAndOrders(GameTestHelper h) {
        FutureChestBlockEntity be = chest(h);
        be.setItem(5, new ItemStack(Items.STONE, 20));
        be.setItem(9, new ItemStack(Items.DIAMOND, 2));
        be.setItem(20, new ItemStack(Items.STONE, 30));
        be.sort();
        if (!be.getItem(0).is(Items.DIAMOND) || be.getItem(0).getCount() != 2) throw new GameTestAssertException("Diamant muss zuerst kommen: " + be.getItem(0));
        if (!be.getItem(1).is(Items.STONE) || be.getItem(1).getCount() != 50) throw new GameTestAssertException("Stein muss zu 50 zusammenfallen: " + be.getItem(1));
        for (int i = 2; i < be.getContainerSize(); i++) if (!be.getItem(i).isEmpty()) throw new GameTestAssertException("Luecke erwartet bei " + i);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "future_chest", timeoutTicks = 80)
    public static void magnetPullsItems(GameTestHelper h) {
        FutureChestBlockEntity be = chest(h);
        BlockPos near = h.absolutePos(new BlockPos(5, 2, 3));
        ItemEntity e = new ItemEntity(h.getLevel(), near.getX() + 0.5, near.getY(), near.getZ() + 0.5, new ItemStack(Items.EMERALD, 7));
        e.setNoPickUpDelay();
        h.getLevel().addFreshEntity(e);
        h.runAfterDelay(20, () -> {
            if (e.isRemoved()) throw new GameTestAssertException("Ohne Magnet darf nichts eingezogen werden");
            be.upgrades().setItem(FutureChestBlockEntity.SLOT_MAGNET, upgrade(QuantumUpgradeItem.Kind.MAGNET, 1));
            h.runAfterDelay(25, () -> {
                if (countOf(be, Items.EMERALD) != 7 || !e.isRemoved()) throw new GameTestAssertException("Magnet hat das Item nicht eingezogen (" + countOf(be, Items.EMERALD) + ")");
                h.succeed();
            });
        });
    }
}
