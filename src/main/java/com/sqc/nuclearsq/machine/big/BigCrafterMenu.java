package com.sqc.nuclearsq.machine.big;

import com.sqc.nuclearsq.registry.HeavyAssemblyRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class BigCrafterMenu extends AbstractContainerMenu {
    public static final int WATER = 12, RACK_COOL = 14, RACK_COLUMN = RACK_COOL + BigCrafter.SHAFTS,
            PART_CHANCE = RACK_COLUMN + 3, PART_CLASSES = 5, PART_LAST = PART_CHANCE + PART_CLASSES, PART_BONUS = PART_LAST + 1,
            FLUIDS = PART_BONUS + 1, FLUID_TANKS = 3, LOCK = FLUIDS + 2 * FLUID_TANKS, DATA = LOCK + 1;
    public static final int PART_OX = 160, PART_OY = 40, PART_UY = 62, PART_CARD_X = 93;
    public static final int GX = 14, GY = 30, ASM_OX = 196, ASM_OY = 66, ASM_INV_Y = 134, PART_INV_Y = 150;
    public static final int SMALL_GX = GX + 18, SMALL_GY = GY + 18;
    public static final int IN_X = 50, IN_Y = 58, OUT_X = 160, OUT_Y = 49, PLANT_INV_Y = 110, GEN_CARD_X = 166;
    public static final int AUX_X = 22, AUX_Y = 58, FLUID_Y = 100, FLUID_INV_Y = 132;
    public static final int SHAFT_Y = 72, SHAFT_W = 72, SHAFT_H = 26, SHAFT_DY = 30, RACK_INV_Y = SHAFT_Y + 3 * SHAFT_DY + 10;
    public static final int PLACE_STATE_SHIFT = 7, PLACE_LOCKED = 1 << 9, PLACE_IN = 1 << 10, PLACE_RELEASED = 1 << 11, PLACE_MOVING = 1 << 12;
    public static final int W = 240, INV_X = (W - 9 * 18) / 2 + 1;

    public final BlockPos pos;
    public final BigMachine machine;
    private final ContainerData data;
    private final int own;

    public BigCrafterMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), BigMachine.values()[Mth.clamp(buf.readVarInt(), 0, BigMachine.values().length - 1)], null,
                new SimpleContainerData(DATA));
    }

    public BigCrafterMenu(int id, Inventory inv, BlockPos pos, BigMachine machine, IItemHandler items, ContainerData data) {
        super(HeavyAssemblyRegistry.CRAFTER_MENU.get(), id);
        checkContainerDataCount(data, DATA);
        this.pos = pos;
        this.machine = machine;
        this.data = data;
        boolean asm = machine == BigMachine.HEAVY_ASSEMBLER, small = machine.smallGrid();
        if (items == null) items = new ItemStackHandler(asm ? BigCrafter.GRID + 1 : small ? BigCrafter.SMALL_GRID + 1
                : machine == BigMachine.PARTICLE_ASSEMBLER ? com.sqc.nuclearsq.particle.ParticleClasses.SLOTS
                : machine == BigMachine.COAL_GENERATOR ? 4 : machine.hasAuxSlot() ? 2 + BigCrafter.PLANT_OUTPUTS : 1 + BigCrafter.PLANT_OUTPUTS);
        if (machine == BigMachine.SPENT_FUEL_RACK) {
        } else if (machine == BigMachine.COAL_GENERATOR) {
            addSlot(new SlotItemHandler(items, 0, IN_X, IN_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return BigCrafter.isFuel(stack);
                }
            });
            for (int k = 1; k < 4; k++) {
                var card = com.sqc.nuclearsq.registry.PowerRegistry.COAL_CARDS.get(k - 1);
                addSlot(new SlotItemHandler(items, k, GEN_CARD_X + (k - 1) * 20, IN_Y) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.is(card.get());
                    }
                });
            }
        } else if (machine == BigMachine.PARTICLE_ASSEMBLER) {
            for (int i = 0; i < com.sqc.nuclearsq.particle.ParticleClasses.OUTPUTS; i++) {
                addSlot(takeOnly(items, i, PART_OX + (i % 3) * 18, PART_OY + (i / 3) * 18));
            }
            for (var u : com.sqc.nuclearsq.particle.ParticleClasses.Upgrade.values()) {
                addSlot(new SlotItemHandler(items, u.slot(), 9 + u.ordinal() * 20, PART_UY) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return com.sqc.nuclearsq.particle.ParticleUpgradeItem.kindOf(stack) == u;
                    }
                });
            }
            addSlot(new SlotItemHandler(items, com.sqc.nuclearsq.particle.ParticleClasses.CARD, PART_CARD_X, PART_UY) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return com.sqc.nuclearsq.particle.ParticleClassCardItem.tierOf(stack) > 0;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        } else if (small) {
            for (int i = 0; i < BigCrafter.SMALL_GRID; i++) addSlot(new SlotItemHandler(items, i, SMALL_GX + (i % 3) * 18, SMALL_GY + (i / 3) * 18));
            addSlot(takeOnly(items, BigCrafter.SMALL_OUT, ASM_OX, ASM_OY));
        } else if (asm) {
            for (int i = 0; i < BigCrafter.GRID; i++) addSlot(new SlotItemHandler(items, i, GX + (i % 5) * 18, GY + (i / 5) * 18));
            addSlot(takeOnly(items, BigCrafter.ASM_OUT, ASM_OX, ASM_OY));
        } else {
            Player player = inv.player;
            addSlot(new SlotItemHandler(items, 0, IN_X, IN_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return BigCrafter.findReprocessing(player.level(), machine, stack) != null;
                }
            });
            for (int i = 0; i < BigCrafter.PLANT_OUTPUTS; i++) {
                addSlot(BigCrafter.givesXp(machine) ? xpSlot(items, 1 + i, OUT_X + (i % 2) * 18, OUT_Y + (i / 2) * 18, pos)
                        : takeOnly(items, 1 + i, OUT_X + (i % 2) * 18, OUT_Y + (i / 2) * 18));
            }
            if (machine.hasAuxSlot()) {
                addSlot(new SlotItemHandler(items, com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.AUX_SLOT, AUX_X, AUX_Y) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return BigCrafter.findAux(player.level(), machine, stack);
                    }
                });
            }
        }
        own = slots.size();
        int iy = inventoryY();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, iy + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, INV_X + col * 18, iy + 58));
        addDataSlots(data);
    }

    private static Slot takeOnly(IItemHandler h, int slot, int x, int y) {
        return new SlotItemHandler(h, slot, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        };
    }

    private static Slot xpSlot(IItemHandler h, int slot, int x, int y, BlockPos pos) {
        return new SlotItemHandler(h, slot, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                if (player.level() instanceof net.minecraft.server.level.ServerLevel sl && sl.getBlockEntity(pos) instanceof BigMachineBlockEntity be && be.crafter() != null) {
                    be.crafter().dropXp(sl, player);
                    be.setChanged();
                }
                super.onTake(player, stack);
            }
        };
    }

    public int inventoryY() {
        return isAssembler(machine) ? ASM_INV_Y : machine == BigMachine.PARTICLE_ASSEMBLER ? PART_INV_Y : machine == BigMachine.SPENT_FUEL_RACK ? RACK_INV_Y
                : fluidRows() ? FLUID_INV_Y : PLANT_INV_Y;
    }

    public boolean fluidRows() {
        return machine.hasFluidRows() && machine.ports.stream().anyMatch(machine::showsTank);
    }

    public java.util.List<BigMachine.Port> fluidPorts() {
        return machine.ports.stream().filter(machine::showsTank).limit(FLUID_TANKS).toList();
    }

    public int fluid(int i) { return data.get(FLUIDS + 2 * i); }
    public int fluidCapacity(int i) { return data.get(FLUIDS + 2 * i + 1); }

    public static boolean isAssembler(BigMachine m) {
        return m == BigMachine.HEAVY_ASSEMBLER || m.smallGrid();
    }

    public int get(int i) { return data.get(i); }

    public float progress() {
        int max = data.get(1);
        return max <= 0 ? 0f : Math.min(1f, (float) data.get(0) / max);
    }

    public int energy() { return (data.get(3) << 16) | (data.get(2) & 0xFFFF); }
    public int capacity() { return (data.get(8) << 16) | (data.get(7) & 0xFFFF); }
    public int status() { return data.get(4); }
    public int needed() { return data.get(5); }
    public boolean enabled() { return data.get(6) == 1; }
    public int energyPerTick() { return data.get(9); }
    public int rackPlace(int shaft) { return data.get(RACK_COOL + shaft); }
    public int column() { return data.get(RACK_COLUMN); }
    public int columnNeeded() { return data.get(RACK_COLUMN + 1); }
    public int flow() { return data.get(RACK_COLUMN + 2); }
    public int classChance(int k) { return data.get(PART_CHANCE + k); }
    public int lastClass() { return data.get(PART_LAST) - 1; }
    public int bonus() { return data.get(PART_BONUS); }
    public int water() { return data.get(WATER); }
    public int waterCapacity() { return data.get(WATER + 1); }
    public int lock() { return data.get(LOCK); }

    public boolean lockable() {
        return machine != BigMachine.PARTICLE_ASSEMBLER && com.sqc.nuclearsq.progression.MachineRecipes.has(machine);
    }

    static ContainerData data(BigMachineBlockEntity be) {
        return new ContainerData() {
            @Override
            public int get(int i) {
                BigCrafter c = be.crafter();
                int fe = be.energy() == null ? 0 : be.energy().getEnergyStored();
                int cap = be.energy() == null ? 0 : be.energy().getMaxEnergyStored();
                return switch (i) {
                    case 0 -> c.progress();
                    case 1 -> c.maxProgress();
                    case 2 -> fe & 0xFFFF;
                    case 3 -> fe >>> 16;
                    case 4 -> c.status();
                    case 5 -> c.needed();
                    case 6 -> be.enabled() ? 1 : 0;
                    case 7 -> cap & 0xFFFF;
                    case 8 -> cap >>> 16;
                    case 9 -> c.energyPerTick();
                    case 10 -> be.matched();
                    case 11 -> be.total();
                    case WATER -> c.waterAmount();
                    case WATER + 1 -> c.waterCapacity();
                    case RACK_COLUMN -> c.column();
                    case RACK_COLUMN + 1 -> c.columnNeeded();
                    case RACK_COLUMN + 2 -> c.flow();
                    case PART_LAST -> c.lastClass() + 1;
                    case LOCK -> c.lockIndex();
                    case FLUIDS, FLUIDS + 2, FLUIDS + 4 -> c.fluidAmount((i - FLUIDS) / 2);
                    case FLUIDS + 1, FLUIDS + 3, FLUIDS + 5 -> c.fluidCapacity((i - FLUIDS) / 2);
                    case PART_BONUS -> be.machine() == BigMachine.PARTICLE_ASSEMBLER
                            ? com.sqc.nuclearsq.particle.ParticleClasses.get().bonus(c.upgradeLevels()) : 0;
                    default -> i >= RACK_COOL && i < RACK_COLUMN && be.machine() == BigMachine.SPENT_FUEL_RACK ? place(be, c, i - RACK_COOL)
                            : i >= PART_CHANCE && i < PART_LAST && be.machine() == BigMachine.PARTICLE_ASSEMBLER ? chance(c, i - PART_CHANCE) : 0;
                };
            }

            @Override
            public void set(int i, int v) { }

            @Override
            public int getCount() { return DATA; }
        };
    }

    private static int chance(BigCrafter c, int k) {
        int[] ch = com.sqc.nuclearsq.particle.ParticleClasses.get().chances(c.upgradeLevels(), c.cardTier());
        return k < ch.length ? ch[k] : 0;
    }

    private static int place(BigMachineBlockEntity be, BigCrafter c, int shaft) {
        RodShaft s = be.shafts()[shaft];
        int state = be.rackState(shaft);
        boolean moving = s.headY() >= 0 && (s.busy(BigMachineBlockEntity.ROD_MAX) || !s.locked() && s.shown() > 0.001f);
        return c.coolPercent(shaft) | state << PLACE_STATE_SHIFT | (s.locked() ? PLACE_LOCKED : 0) | (be.rackInPlace(shaft) ? PLACE_IN : 0)
                | (be.rackReleased(shaft) ? PLACE_RELEASED : 0) | (moving ? PLACE_MOVING : 0);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < own) {
            if (!moveItemStackTo(stack, own, own + 36, true)) return ItemStack.EMPTY;
        } else {
            int end = machine == BigMachine.HEAVY_ASSEMBLER ? BigCrafter.GRID : machine.smallGrid() ? BigCrafter.SMALL_GRID
                    : machine == BigMachine.SPENT_FUEL_RACK ? 0 : machine == BigMachine.COAL_GENERATOR ? 4 : 1;
            int start = machine == BigMachine.PARTICLE_ASSEMBLER ? com.sqc.nuclearsq.particle.ParticleClasses.OUTPUTS : 0;
            if (machine == BigMachine.PARTICLE_ASSEMBLER) end = com.sqc.nuclearsq.particle.ParticleClasses.SLOTS;
            if (machine.hasAuxSlot() && (machine == BigMachine.KILN ? BigCrafter.auxFor(player.level(), machine, slots.get(0).getItem(), stack)
                    : BigCrafter.findAux(player.level(), machine, stack) && BigCrafter.findReprocessing(player.level(), machine, stack) == null)) {
                start = com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.AUX_SLOT;
                end = start + 1;
            }
            if (!moveItemStackTo(stack, start, end, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof BigMachineBlockEntity be && be.machine() == machine
                && com.sqc.nuclearsq.data.RemoteAccess.inReach(player, pos);
    }
}
