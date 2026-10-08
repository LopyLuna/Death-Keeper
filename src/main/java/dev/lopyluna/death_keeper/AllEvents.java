package dev.lopyluna.death_keeper;

import com.mojang.datafixers.util.Pair;
import dev.lopyluna.death_keeper.gravestone.GravestoneBlock;
import dev.lopyluna.death_keeper.kuramastone.GravestoneRemovedEvent;
import dev.lopyluna.death_keeper.mixins.DisplayAccessor;
import dev.lopyluna.death_keeper.mixins.TextDisplayAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.*;

import static dev.lopyluna.death_keeper.DeathKeeper.GRAVESTONE;
import static dev.lopyluna.death_keeper.DeathKeeper.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class AllEvents {

    public static Map<UUID, List<ItemStack>> returnItemsQueue = new LinkedHashMap<>();

    public static List<ItemStack> getStacksQueue(UUID uuid) {
        return returnItemsQueue.computeIfAbsent(uuid, u -> new ArrayList<>());
    }

    public static void addStacksQueue(List<ItemStack> stacks, UUID uuid) {
        getStacksQueue(uuid).addAll(stacks);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        GravestoneCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    @SubscribeEvent
    public static void onLivingExpDrops(LivingExperienceDropEvent event) {
        if (!Config.gravestoneOnDeath) return;
        int drops = event.getDroppedExperience();
        if (!Config.emptyGraves && drops == 0) return;
        var entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (entity.isAlive()) return;
        if (!Config.entities.contains(entity.getType())) return;

        var pos = entity.blockPosition();
        var type = entity.getType();
        var pairA = getOrCreateSnapshot(level, pos, type);
        var snap = pairA.getFirst();
        snap.dim = level.dimension().location();
        snap.uuid = entity.getUUID();
        snap.entityType = type;
        snap.dir = entity.getDirection();
        snap.exp = drops;
        if (snap.inv == null) snap.inv = new ArrayList<>();

        if (pairA.getSecond()) {
            var pairB = placeGrave(snap.uuid, snap.entityType, level, pos, snap.dir);
            snap.pos = pairB.getSecond();
            var toMerge = pairB.getFirst();

            if (toMerge != null) mergeSnapshot(toMerge, snap);
            else {
                var text = AllEvents.spawnTextDisplay(level, snap.pos, snap.uuid, snap.entityType);
                snap.uuidText = text != null ? text.getUUID() : null;
                DeathSnapshotData.add(level, snap);
            }
        }

        event.setCanceled(true);
    }

    public static Pair<DeathSnapshot, Boolean> getOrCreateSnapshot(ServerLevel level, BlockPos pos, EntityType<?> type) {
        var snaps = DeathSnapshotData.getSnapshots(level, pos);
        if (snaps.isEmpty()) return new Pair<>(new DeathSnapshot(), true);
        for (var snap : snaps) if (snap.entityType.equals(type)) return new Pair<>(snap, false);
        return new Pair<>(snaps.getFirst(), false);
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!Config.gravestoneOnDeath) return;
        var drops = event.getDrops();
        if (!Config.emptyGraves && drops.isEmpty()) return;
        var entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (entity.isAlive()) return;
        if (!Config.entities.contains(entity.getType())) return;

        List<ItemStack> stacks = new ArrayList<>();
        for (var item : drops) {
            var stack = item.getItem();
            if (stack.isEmpty()) continue;
            stacks.add(stack.copy());
        }
        var pos = entity.blockPosition();
        var type = entity.getType();
        var pairA = getOrCreateSnapshot(level, pos, type);
        var snap = pairA.getFirst();
        snap.dim = level.dimension().location();
        snap.uuid = entity.getUUID();
        snap.entityType = type;
        snap.dir = entity.getDirection();
        snap.inv = stacks;

        var flag = pairA.getSecond();
        Pair<DeathSnapshot, BlockPos> pairB = null;
        if (flag) {
            pairB = placeGrave(snap.uuid, snap.entityType, level, pos, snap.dir);
            snap.pos = pairB.getSecond();
        }

        var split = splitStacks(level.random, snap.inv);
        addStacksQueue(split.getFirst(), snap.uuid);
        snap.inv = split.getSecond();

        if (flag) {
            var toMerge = pairB.getFirst();

            if (toMerge != null) mergeSnapshot(toMerge, snap);
            else {
                var text = AllEvents.spawnTextDisplay(level, snap.pos, snap.uuid, snap.entityType);
                snap.uuidText = text != null ? text.getUUID() : null;
                DeathSnapshotData.add(level, snap);
            }
        }
        event.setCanceled(true);
    }

    public static Pair<List<ItemStack>, List<ItemStack>> splitStacks(RandomSource random, List<ItemStack> stacks) {
        var amount = 0;
        for (var stack : stacks) amount += stack.getCount();
        var amountLost = Math.round((float) amount * (float) (Config.itemLostPercentage / 100.0));

        var kept = new ArrayList<ItemStack>();
        var lost = new ArrayList<ItemStack>();

        var work = new ArrayList<ItemStack>();
        for (var stack : stacks) work.add(stack.copy());
        Collections.shuffle(work);

        int remaining = amountLost;

        while (remaining > 0 && !work.isEmpty()) {
            int index = random.nextInt(work.size());
            var stack = work.get(index);

            int count = stack.getCount();
            if (0 >= count) {
                work.remove(index);
                continue;
            }

            int remove = 1 + random.nextInt(count);
            remove = Math.min(remove, remaining);


            var lostStack = stack.copy();
            lostStack.setCount(remove);
            lost.add(lostStack);

            stack.setCount(count - remove);
            remaining -= remove;

            if (0 >= stack.getCount()) work.remove(index);
        }

        for (var stack : work) if (stack.getCount() > 0) kept.add(stack);
        return new Pair<>(kept, lost);
    }

    public static void mergeSnapshot(DeathSnapshot snap, DeathSnapshot other) {
        snap.exp += other.exp;
        snap.inv.addAll(other.inv);
        snap.inv = mergeStacks(snap.inv);
        snap.lifespan = Config.maxGraveLifespan;
    }


    @SuppressWarnings("deprecation")
    public static Pair<DeathSnapshot, BlockPos> placeGrave(UUID uuid, EntityType<?> type, ServerLevel level, BlockPos origin, Direction dir) {
        var maxH = level.getMaxBuildHeight();
        var minH = level.getMinBuildHeight();
        int max = 10;
        BlockPos bestPos = null;
        var bestDist = (double)max*(double)max+(double)max*(double)max+(double)max*(double)max;
        for (int dx=-max; max>=dx; dx++) for (int y=minH; maxH>=y; y++) for (int dz=-max; max>=dz; dz++) {
            var pos = origin.offset(dx, 0, dz).atY(y);
            var below = pos.below();

            var state = level.getBlockState(pos);
            var stateBelow = level.getBlockState(below);

            if (blockCheck(state) && (stateBelow.isSolid() || !stateBelow.getFluidState().isEmpty()) && !stateBelow.isAir()) {
                var dist = origin.distSqr(pos);
                if (bestDist > dist) {
                    bestPos = pos;
                    bestDist = dist;
                }
            }
        }
        if (bestPos != null) origin = bestPos;

        var below = origin.below();
        var state = level.getBlockState(origin);
        var stateBelow = level.getBlockState(below);
        while (origin.getY() > minH && !(blockCheck(state) && (stateBelow.isSolid() || !stateBelow.getFluidState().isEmpty()) && !stateBelow.isAir())) {
            origin = origin.below();
            below = origin.below();
            state = level.getBlockState(origin);
            stateBelow = level.getBlockState(below);
        }
        while (minH > origin.getY()) origin = origin.above();

        DeathSnapshot potential = null;
        if (Config.graveMerging){
            potential = getNearestGrave(uuid, type, level, origin, Config.graveMaxDistance);
            origin = potential == null ? origin : potential.pos;
        }
        if (!level.getBlockState(origin).is(GRAVESTONE)) level.setBlock(origin, GRAVESTONE.get().defaultBlockState().setValue(GravestoneBlock.FLOATING, !level.getFluidState(below).isEmpty()).setValue(GravestoneBlock.BREAKABLE, false).setValue(GravestoneBlock.FACING, dir), 3);
        if (Config.coarseDirtBelowGraves) {
            var belowPos = origin.below();
            var belowState = level.getBlockState(belowPos);
            var belowDir = origin.below().relative(dir);
            var belowDirState = level.getBlockState(belowDir);
            if (belowState.is(BlockTags.DIRT) && !belowState.is(Blocks.COARSE_DIRT)) level.setBlock(belowPos, Blocks.COARSE_DIRT.defaultBlockState(), 3);
            if (belowDirState.is(BlockTags.DIRT) && !belowDirState.is(Blocks.COARSE_DIRT)) level.setBlock(belowDir, Blocks.COARSE_DIRT.defaultBlockState(), 3);
        }
        return new Pair<>(potential, origin);
    }

    public static boolean blockCheck(BlockState state) {
        return (state.isAir() || state.is(DeathKeeper.GRAVESTONE_REPLACEABLE) || (Config.graveMerging && state.is(GRAVESTONE))) && state.getFluidState().isEmpty();
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!Config.gravestoneOnDeath) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % 3 != 0) return;

        for (var player : level.getPlayers(s -> true)) {
            var uuid = player.getUUID();
            if (!returnItemsQueue.containsKey(uuid)) continue;
            if (!player.isAlive() || player.isSpectator()) continue;

            var inv = player.getInventory();
            int slot = inv.getFreeSlot();
            if (slot == -1) continue;

            var stacks = getStacksQueue(uuid);
            var it = stacks.iterator();

            while (it.hasNext()) {
                if (inv.getFreeSlot() == -1) break;
                ItemHandlerHelper.giveItemToPlayer(player, it.next());
                it.remove();
            }
            if (stacks.isEmpty()) returnItemsQueue.remove(uuid);
        }

        List<DeathSnapshot> removals = null;
        var snaps = DeathSnapshotData.getSnapshots(level);
        if (snaps.isEmpty()) return;
        for (var snap : snaps) {
            snap.lifespan -= 3;
            if (0 >= snap.lifespan || (snap.inv.isEmpty() && snap.exp == 0 && !Config.emptyGraves)) {
                if (snap.uuidText != null && level.getEntity(snap.uuidText) instanceof Display.TextDisplay text) text.discard();
                level.destroyBlock(snap.pos, true);
                if (removals == null) removals = new ArrayList<>();
                removals.add(snap);
                continue;
            }
            if (level.getBlockState(snap.pos).is(GRAVESTONE.get())) continue;
            dropAll(level, snap.inv, snap.exp, snap.pos.getCenter());
            snap.exp = 0;
            snap.inv = new ArrayList<>();
            if (snap.uuidText != null && level.getEntity(snap.uuidText) instanceof Display.TextDisplay text) text.discard();
            if (removals == null) removals = new ArrayList<>();
            removals.add(snap);
        }
        if (removals != null) {
            for (var remove : removals) {
                NeoForge.EVENT_BUS.post(new GravestoneRemovedEvent(remove));
                DeathSnapshotData.remove(level, remove);
            }
            removals.clear();
        }
    }

    public static void dropAll(ServerLevel level, List<ItemStack> inventory, int exp, Vec3 targetPos) {
        ExperienceOrb.award(level, targetPos, exp);
        for (var stack : inventory) {
            var itemEntity = new ItemEntity(level, targetPos.x, targetPos.y, targetPos.z, stack);
            itemEntity.setPickUpDelay(0);
            level.addFreshEntity(itemEntity);
        }
    }

    public static DeathSnapshot getNearestGrave(UUID uuid, EntityType<?> type, ServerLevel level, BlockPos pos, double max) {
        var dim = level.dimension().location();

        DeathSnapshot nearest = null;
        var nearestDist = max * max;

        for (var snap : DeathSnapshotData.getSnapshots(level)) {
            if (!snap.dim.equals(dim)) continue;
            if (type.equals(EntityType.PLAYER) ? !snap.uuid.equals(uuid) : !snap.entityType.equals(type)) continue;
            double dist = snap.pos.distSqr(pos);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = snap;
            }
        }
        return nearest;
    }

    @Nullable
    public static Display.TextDisplay spawnTextDisplay(ServerLevel level, BlockPos pos, UUID uuid, EntityType<?> type) {
        if (!Config.nonPlayerGraveTitles && !type.equals(EntityType.PLAYER)) return null;
        var text = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        var center = pos.above().getBottomCenter();
        text.setPos(center);
        var name = type.equals(EntityType.PLAYER) ? level.getPlayerByUUID(uuid) instanceof Player player ? player.getName() : type.getDescription() : type.getDescription();
        text.getEntityData().set(TextDisplayAccessor.getDataText(), name);
        text.getEntityData().set(DisplayAccessor.getDataBillboard(), (byte) 3);
        text.getEntityData().set(TextDisplayAccessor.getDataStyle(), (byte) 2);
        level.addFreshEntity(text);
        return text;
    }

    public static List<ItemStack> mergeStacks(List<ItemStack> stacks) {
        if (stacks.isEmpty()) return stacks;
        var result = new ArrayList<ItemStack>();
        for (var stack : stacks) {
            if (stack.isEmpty()) continue;

            for (var existing : result) if (ItemStack.isSameItemSameComponents(existing, stack)) {
                int maxSize = existing.getMaxStackSize();
                int space = maxSize - existing.getCount();

                if (space > 0) {
                    int move = Math.min(space, stack.getCount());
                    existing.grow(move);
                    stack.shrink(move);

                    if (stack.isEmpty()) break;
                }
            }
            if (!stack.isEmpty()) result.add(stack.copy());
        }
        return result;
    }
}
