package dev.lopyluna.death_keeper;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class GravestoneCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("data")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("get").then(Commands.literal("gravestone").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(c -> {
                            var level = c.getSource().getLevel();
                            var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                            var snaps = DeathSnapshotData.getSnapshots(level, pos);
                            if (snaps.isEmpty()) {
                                c.getSource().sendFailure(Component.literal("No Snapshot Exist"));
                                return -1;
                            }
                            int i = 1;
                            HolderLookup.Provider provider = level.registryAccess();
                            for (var snap : snaps) c.getSource().sendSuccess(() -> Component.literal("Snapshot " + i + " - ").append(NbtUtils.toPrettyComponent(DeathSnapshot.CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), snap).result().orElse(new CompoundTag()))), false);
                            return 0;
                        })
                )
        )));

        dispatcher.register(Commands.literal("gravestone")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("place").then(Commands.argument("target", EntityArgument.entity()).then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(c -> {
                            var level = c.getSource().getLevel();
                            var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                            var target = EntityArgument.getEntity(c, "target");

                            var snap = new DeathSnapshot();
                            snap.dim = level.dimension().location();
                            snap.uuid = target.getUUID();
                            snap.entityType = target.getType();
                            snap.dir = target.getDirection();
                            snap.inv = new ArrayList<>();
                            var pair = AllEvents.placeGrave(snap.uuid, snap.entityType, level, pos, snap.dir);
                            snap.pos = pair.getSecond();
                            var toMerge = pair.getFirst();
                            if (toMerge != null) AllEvents.mergeSnapshot(toMerge, snap);
                            else {
                                var text = AllEvents.spawnTextDisplay(level, snap.pos, snap.uuid, snap.entityType);
                                snap.uuidText = text != null ? text.getUUID() : null;
                                DeathSnapshotData.add(level, snap);
                            }
                            return 0;
                        })
                )))
                .then(Commands.literal("insert").then(Commands.argument("item", ItemArgument.item(context)).then(Commands.argument("count", IntegerArgumentType.integer(1)).then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(c -> {
                            var level = c.getSource().getLevel();
                            var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                            var snaps = DeathSnapshotData.getSnapshots(level, pos);
                            if (snaps.isEmpty()) {
                                c.getSource().sendFailure(Component.literal("No Snapshot Exist"));
                                return -1;
                            }
                            var item = ItemArgument.getItem(c, "item");
                            var count = IntegerArgumentType.getInteger(c, "count");
                            var snap = snaps.getFirst();
                            snap.inv.add(item.createItemStack(count, false));
                            snap.inv = AllEvents.mergeStacks(snap.inv);
                            return 0;
                        })
                ))))
                .then(Commands.literal("remove").then(Commands.argument("item", ItemArgument.item(context)).then(Commands.argument("count", IntegerArgumentType.integer(1)).then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(c -> {
                            var level = c.getSource().getLevel();
                            var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                            var snaps = DeathSnapshotData.getSnapshots(level, pos);
                            if (snaps.isEmpty()) {
                                c.getSource().sendFailure(Component.literal("No Snapshot Exist"));
                                return -1;
                            }
                            var filter = ItemArgument.getItem(c, "item");
                            var count = IntegerArgumentType.getInteger(c, "count");
                            var snap = snaps.getFirst();
                            var item = filter.getItem();
                            var flag = true;
                            for (var i = 0; snap.inv.size() > i; i++) {
                                var stack = snap.inv.get(i);
                                if (!stack.is(item)) continue;
                                stack.shrink(count);
                                flag = false;
                            }
                            if (flag) return -1;
                            snap.inv = AllEvents.mergeStacks(snap.inv);
                            return 0;
                        })
                ))))
                .then(Commands.literal("data").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(c -> {
                            var level = c.getSource().getLevel();
                            var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                            var snaps = DeathSnapshotData.getSnapshots(level, pos);
                            if (snaps.isEmpty()) {
                                c.getSource().sendFailure(Component.literal("No Snapshot Exist"));
                                return -1;
                            }
                            int i = 1;
                            HolderLookup.Provider provider = level.registryAccess();
                            for (var snap : snaps) c.getSource().sendSuccess(() -> Component.literal("Snapshot " + i + " - ").append(NbtUtils.toPrettyComponent(DeathSnapshot.CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), snap).result().orElse(new CompoundTag()))), false);
                            return 0;
                        })
                ))
                .then(Commands.literal("inventory").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(c -> {
                            var level = c.getSource().getLevel();
                            var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                            var snaps = DeathSnapshotData.getSnapshots(level, pos);
                            if (snaps.isEmpty()) {
                                c.getSource().sendFailure(Component.literal("No Gravestone Exist"));
                                return -1;
                            }
                            int i = 0;
                            var group = new ArrayList<ItemStack>();
                            for (var snap : snaps) for (var stack : snap.inv) {
                                i++;
                                var merge = false;
                                for (var exist: group) if (ItemStack.isSameItemSameComponents(exist, stack)) {
                                    exist.grow(stack.getCount());
                                    merge = true;
                                    break;
                                }
                                if (!merge) group.add(stack.copy());

                            }
                            if (group.isEmpty()) {
                                c.getSource().sendFailure(Component.literal("Gravestone is Empty"));
                                return -1;
                            }

                            var msg = Component.literal("Total Stacks - " + i + " | Inventory - ");
                            var flag = true;
                            for (var stack : group) {
                                msg = msg.append(flag ? " " : ", ").append(stack.getHoverName());
                                if (stack.getCount() > 1) msg = msg.append(" x" + stack.getCount());
                                flag = false;
                            }

                            var finalMsg = msg;
                            c.getSource().sendSuccess(() -> finalMsg, false);
                            group.clear();
                            return 0;
                        })
                ))
                .then(Commands.literal("destroy").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(c -> {
                            var level = c.getSource().getLevel();
                            var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                            var snaps = DeathSnapshotData.getSnapshots(level, pos);
                            if (snaps.isEmpty()) {
                                c.getSource().sendFailure(Component.literal("No Gravestone Exist"));
                                return -1;
                            }
                            var removal = new ArrayList<DeathSnapshot>();
                            for (var snap : snaps) {
                                if (snap.uuidText != null && level.getEntity(snap.uuidText) instanceof Display.TextDisplay text) text.discard();
                                removal.add(snap);
                            }
                            for (var snap : removal) DeathSnapshotData.remove(level, snap);
                            level.destroyBlock(pos, true);
                            return 0;
                        })
                ))
                .then(Commands.literal("claim").then(Commands.argument("target", EntityArgument.player()).then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(c -> {
                            try {
                                var level = c.getSource().getLevel();
                                var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                                var snaps = DeathSnapshotData.getSnapshots(level, pos);
                                if (snaps.isEmpty()) {
                                    c.getSource().sendFailure(Component.literal("No Snapshot Exist"));
                                    return -1;
                                }
                                var target = EntityArgument.getPlayer(c, "target");
                                var targetSize = snaps.size();
                                var size = 0;
                                var inv = target.getInventory();
                                int slot = inv.getFreeSlot();

                                var totalItems = 0;
                                var items = 0;
                                var removal = new ArrayList<DeathSnapshot>();
                                for (var snap : snaps) {
                                    if (slot == -1) break;
                                    if (!snap.entityType.equals(EntityType.PLAYER) || snap.uuid.equals(target.getUUID())) {
                                        totalItems += snap.inv.size();
                                        var remove = new ArrayList<ItemStack>();
                                        for (var stack : snap.inv) {
                                            slot = inv.getFreeSlot();
                                            if (slot == -1) break;
                                            ItemHandlerHelper.giveItemToPlayer(target, stack);
                                            remove.add(stack);
                                            items++;
                                        }
                                        snap.inv = new ArrayList<>(snap.inv);
                                        snap.inv.removeAll(remove);
                                        if (slot == -1) break;
                                        if (snap.uuidText != null && level.getEntity(snap.uuidText) instanceof Display.TextDisplay text)
                                            text.discard();
                                        removal.add(snap);
                                        size++;
                                    }
                                }
                                for (var snap : removal) DeathSnapshotData.remove(level, snap);
                                if (size >= targetSize) level.destroyBlock(pos, true);
                                if (items != 0) {
                                    int finalItems = items;
                                    int finalTotalItems = totalItems;
                                    c.getSource().sendSuccess(() -> Component.literal("Successfully Received " + finalItems + " out of " + finalTotalItems + " Items"), false);
                                } else c.getSource().sendFailure(Component.literal("Unable to Receive Items"));
                                return 0;
                            } catch (Exception e) {
                                e.printStackTrace(System.err);
                                return -1;
                            }
                        })
                )))
                .then(Commands.literal("list").then(Commands.argument("target", EntityArgument.entity())
                        .executes(c -> {
                            var levels = c.getSource().getServer().getAllLevels();
                            var snaps = new ArrayList<DeathSnapshot>();
                            for (var level : levels) snaps.addAll(DeathSnapshotData.getSnapshots(level));
                            if (snaps.isEmpty()) {
                                c.getSource().sendFailure(Component.literal("No Snapshot Exist"));
                                return -1;
                            }
                            var target = EntityArgument.getEntity(c, "target");
                            var uuid = target.getUUID();
                            var type = target.getType();
                            var player = type.equals(EntityType.PLAYER);
                            int size = 0;
                            var msg = Component.empty();
                            for (var snap : snaps) {
                                if (player ? !snap.uuid.equals(uuid) : !snap.entityType.equals(type)) continue;
                                msg = msg.append(size == 0 ? "" : " | ");
                                size++;
                                msg = msg.append("Snapshot " + size + ", " + snap.pos.toShortString() + ", " + snap.dim.toString() + ", " + snap.inv.size() + " Items");
                            }
                            msg = Component.literal("Total Snapshots for ").append(target.getName()).append(" - " + size + " | ").append(msg);
                            var finalMsg = msg;
                            c.getSource().sendSuccess(() -> finalMsg, false);
                            return 0;
                        })
                ))
                .then(Commands.literal("clear")
                        .executes(c -> {
                            var level = c.getSource().getLevel();
                            var snaps = DeathSnapshotData.getSnapshots(level);
                            if (snaps.isEmpty()) {
                                c.getSource().sendFailure(Component.literal("No Gravestone Exist"));
                                return -1;
                            }
                            var removal = new ArrayList<DeathSnapshot>();
                            for (var snap : snaps) {
                                if (snap.uuidText != null && level.getEntity(snap.uuidText) instanceof Display.TextDisplay text) text.discard();
                                removal.add(snap);
                                level.destroyBlock(snap.pos, true);
                            }
                            c.getSource().sendSuccess(() -> Component.literal("Successfully Removed " + removal.size() + " Gravestones"), false);
                            for (var remove : removal) DeathSnapshotData.remove(level, remove);
                            removal.clear();
                            return 0;
                        })
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(c -> {
                                    var level = c.getSource().getLevel();
                                    var snaps = DeathSnapshotData.getSnapshots(level);
                                    if (snaps.isEmpty()) {
                                        c.getSource().sendFailure(Component.literal("No Gravestone Exist"));
                                        return -1;
                                    }
                                    var targets = EntityArgument.getEntities(c, "targets");
                                    var types = targets.stream().map(Entity::getType).filter(type -> !type.equals(EntityType.PLAYER)).collect(Collectors.toSet());
                                    var uuids = targets.stream().filter(e -> e.getType().equals(EntityType.PLAYER)).map(Entity::getUUID).collect(Collectors.toSet());
                                    var removal = new ArrayList<DeathSnapshot>();
                                    for (var snap : snaps) {
                                        if (snap.entityType.equals(EntityType.PLAYER) ? !uuids.contains(snap.uuid): !types.contains(snap.entityType)) continue;
                                        if (snap.uuidText != null && level.getEntity(snap.uuidText) instanceof Display.TextDisplay text) text.discard();
                                        removal.add(snap);
                                        level.destroyBlock(snap.pos, true);
                                    }
                                    c.getSource().sendSuccess(() -> Component.literal("Successfully Removed " + removal.size() + " Gravestones"), false);
                                    for (var remove : removal) DeathSnapshotData.remove(level, remove);
                                    removal.clear();
                                    return 0;
                                })
                ))
        );

    }
}
