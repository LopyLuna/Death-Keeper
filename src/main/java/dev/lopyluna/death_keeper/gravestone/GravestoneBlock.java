package dev.lopyluna.death_keeper.gravestone;

import dev.lopyluna.death_keeper.Config;
import dev.lopyluna.death_keeper.DeathSnapshotData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;

@SuppressWarnings({"deprecation", "NullableProblems"})
public class GravestoneBlock extends Block implements SimpleWaterloggedBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty FLOATING = BooleanProperty.create("float");
    public static final BooleanProperty BREAKABLE = BooleanProperty.create("breakable");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public GravestoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BREAKABLE, false).setValue(FLOATING, false).setValue(WATERLOGGED, false));
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!state.getValue(BREAKABLE)) return 0;
        float f = state.getDestroySpeed(level, pos);

        int i = net.neoforged.neoforge.event.EventHooks.doPlayerHarvestCheck(player, state, level, pos) ? 30 : 100;
        return player.getDestroySpeed(state, pos) / f / (float)i;
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        if (!state.getValue(BREAKABLE)) return 3600000;
        return super.getExplosionResistance(state, level, pos, explosion);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel server) {
            var snaps = DeathSnapshotData.getSnapshots(server, pos);
            if (snaps.isEmpty()) {
                if (!state.getValue(BREAKABLE)) level.destroyBlock(pos, true);
                return InteractionResult.PASS;
            }
            var targetSize = snaps.size();
            var size = 0;
            var inv = player.getInventory();
            int slot = inv.getFreeSlot();
            for (var snap : snaps) {
                if (slot == -1) break;
                if (!snap.entityType.equals(EntityType.PLAYER) || snap.uuid.equals(player.getUUID())) {
                    var remove = new ArrayList<ItemStack>();

                    player.giveExperiencePoints(snap.exp);
                    snap.exp = 0;
                    for (var stack : snap.inv) {
                        slot = inv.getFreeSlot();
                        if (slot == -1) break;
                        ItemHandlerHelper.giveItemToPlayer(player, stack);
                        remove.add(stack);
                    }
                    snap.inv.removeAll(remove);
                    if (slot == -1) break;
                    if (snap.uuidText != null && server.getEntity(snap.uuidText) instanceof Display.TextDisplay text) text.discard();
                    DeathSnapshotData.remove(server, snap);
                    snap.lifespan = Config.maxGraveLifespan;
                    size++;
                }
            }
            if (size >= targetSize) level.destroyBlock(pos, true);
            if (size > 0) return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (level instanceof LevelAccessor accessor && state.getValue(WATERLOGGED)) accessor.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random).setValue(GravestoneBlock.FLOATING, !level.getFluidState(pos.below()).isEmpty());
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState()
                .setValue(BREAKABLE, true)
                .setValue(WATERLOGGED, ctx.getLevel().getFluidState(ctx.getClickedPos()).getType() == Fluids.WATER)
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(GravestoneBlock.FLOATING, !ctx.getLevel().getFluidState(ctx.getClickedPos().below()).isEmpty());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        var z = state.getValue(FACING).getAxis() == Direction.Axis.Z;
        var f = state.getValue(FLOATING);
        return z ? Block.box(2, f?-2:0, 6, 14, f?12:14, 10) : Block.box(6, f?-2:0, 2, 10, f?12:14, 14);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED, FLOATING, BREAKABLE);
    }
}
