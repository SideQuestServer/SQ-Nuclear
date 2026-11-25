package com.sqc.nuclearsq.machine;

import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.door.DoorBlockEntity;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class AccessCommands {
    private AccessCommands() {}

    private static final String[] KINDS = {"player", "team"};

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> access = Commands.literal("nsqaccess")
                .then(Commands.literal("list").executes(c -> list(c.getSource())));
        for (String kind : KINDS) {
            access.then(Commands.literal("add").then(Commands.literal(kind).then(Commands.argument("name", StringArgumentType.word())
                    .then(level(kind, "use", MachineAccess.USE))
                    .then(level(kind, "admin", MachineAccess.ADMIN)))));
            access.then(Commands.literal("remove").then(Commands.literal(kind).then(Commands.argument("name", StringArgumentType.word())
                    .executes(c -> remove(c, kind)))));
        }
        event.getDispatcher().register(access);
        event.getDispatcher().register(Commands.literal("nsqteam")
                .then(Commands.literal("create").then(Commands.argument("team", StringArgumentType.word()).executes(AccessCommands::create)))
                .then(Commands.literal("delete").then(Commands.argument("team", StringArgumentType.word()).executes(AccessCommands::delete)))
                .then(Commands.literal("add").then(Commands.argument("team", StringArgumentType.word())
                        .then(Commands.argument("player", EntityArgument.player()).executes(c -> member(c, true)))))
                .then(Commands.literal("remove").then(Commands.argument("team", StringArgumentType.word())
                        .then(Commands.argument("player", EntityArgument.player()).executes(c -> member(c, false)))))
                .then(Commands.literal("list").executes(c -> teams(c.getSource()))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> level(String kind, String word, int level) {
        return Commands.literal(word).executes(c -> add(c, kind, level));
    }

    private static Component msg(String key, Object... args) {
        return Component.translatable("commands.nuclearsq.access." + key, args);
    }

    @Nullable
    private static MachineAccess.Holder target(ServerPlayer sp) {
        HitResult hit = sp.pick(6.0, 0f, false);
        if (!(hit instanceof BlockHitResult b)) return null;
        var pl = MachineKits.owner(sp.level(), b.getBlockPos());
        var be = sp.level().getBlockEntity(pl != null ? pl.anchor() : b.getBlockPos());
        if (be == null) be = DoorBlockEntity.controllerOf(sp.level(), b.getBlockPos());
        return be instanceof MachineAccess.Holder m ? m : null;
    }

    @Nullable
    private static MachineAccess.Holder admin(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        MachineAccess.Holder be = target(sp);
        if (be == null) {
            src.sendFailure(msg("none"));
            return null;
        }
        if (!be.access().can(sp, be.owner(), MachineAccess.ADMIN)) {
            src.sendFailure(msg("denied"));
            return null;
        }
        return be;
    }

    private static String key(String kind, String name) {
        return (kind.equals("team") ? "g:" : "p:") + name.toLowerCase(Locale.ROOT);
    }

    private static Component levelName(int level) {
        return msg("level." + level);
    }

    private static int add(CommandContext<CommandSourceStack> c, String kind, int level) throws CommandSyntaxException {
        MachineAccess.Holder be = admin(c.getSource());
        if (be == null) return 0;
        String name = StringArgumentType.getString(c, "name");
        if (kind.equals("team") && TeamData.get(c.getSource().getServer()).team(name.toLowerCase(Locale.ROOT)) == null) {
            c.getSource().sendFailure(team("unknown", name));
            return 0;
        }
        be.access().set(key(kind, name), level);
        be.setChanged();
        c.getSource().sendSuccess(() -> msg("set", name, levelName(level)), false);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> c, String kind) throws CommandSyntaxException {
        MachineAccess.Holder be = admin(c.getSource());
        if (be == null) return 0;
        String name = StringArgumentType.getString(c, "name");
        boolean had = be.access().remove(key(kind, name));
        be.setChanged();
        c.getSource().sendSuccess(() -> msg(had ? "removed" : "unknown", name), false);
        return had ? 1 : 0;
    }

    private static int list(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        MachineAccess.Holder be = target(sp);
        if (be == null) {
            src.sendFailure(msg("none"));
            return 0;
        }
        String owner = be.owner() == null ? "-" : sp.server.getProfileCache().get(be.owner()).map(p -> p.getName()).orElse(be.owner().toString());
        src.sendSuccess(() -> msg("owner", owner), false);
        for (Map.Entry<String, Integer> e : be.access().grants().entrySet()) {
            Component who = Component.literal((e.getKey().startsWith("g:") ? "[Team] " : "") + e.getKey().substring(2));
            src.sendSuccess(() -> msg("entry", who, levelName(e.getValue())), false);
        }
        return 1;
    }

    private static Component team(String key, Object... args) {
        return Component.translatable("commands.nuclearsq.team." + key, args);
    }

    private static int create(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "team").toLowerCase(Locale.ROOT);
        boolean ok = TeamData.get(sp.server).create(name, sp.getUUID());
        if (!ok) c.getSource().sendFailure(team("exists", name));
        else c.getSource().sendSuccess(() -> team("created", name), false);
        return ok ? 1 : 0;
    }

    @Nullable
    private static TeamData.Team own(CommandContext<CommandSourceStack> c, ServerPlayer sp, String name) {
        TeamData.Team t = TeamData.get(sp.server).team(name);
        if (t == null) c.getSource().sendFailure(team("unknown", name));
        else if (!t.owner.equals(sp.getUUID()) && !sp.hasPermissions(2)) {
            c.getSource().sendFailure(team("notyours", name));
            return null;
        }
        return t;
    }

    private static int delete(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "team").toLowerCase(Locale.ROOT);
        if (own(c, sp, name) == null) return 0;
        TeamData.get(sp.server).delete(name);
        c.getSource().sendSuccess(() -> team("deleted", name), false);
        return 1;
    }

    private static int member(CommandContext<CommandSourceStack> c, boolean add) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException(), who = EntityArgument.getPlayer(c, "player");
        String name = StringArgumentType.getString(c, "team").toLowerCase(Locale.ROOT);
        TeamData.Team t = own(c, sp, name);
        if (t == null) return 0;
        if (add) t.members.add(who.getUUID());
        else t.members.remove(who.getUUID());
        TeamData.get(sp.server).setDirty();
        c.getSource().sendSuccess(() -> team(add ? "added" : "removed", who.getGameProfile().getName(), name), false);
        return 1;
    }

    private static int teams(CommandSourceStack src) {
        TeamData d = TeamData.get(src.getServer());
        if (d.all().isEmpty()) src.sendSuccess(() -> team("none"), false);
        d.all().forEach((name, t) -> src.sendSuccess(() -> team("entry", name, t.members.size() + 1), false));
        return 1;
    }
}
