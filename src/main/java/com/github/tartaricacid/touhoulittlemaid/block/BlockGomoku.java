package com.github.tartaricacid.touhoulittlemaid.block;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Statue;
import com.github.tartaricacid.touhoulittlemaid.block.properties.GomokuPart;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.network.message.ChessDataToClientMessage;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGomoku;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class BlockGomoku extends BaseEntityBlock {
    public static final EnumProperty<GomokuPart> PART = EnumProperty.create("part", GomokuPart.class);
    public static final VoxelShape LEFT_UP = Block.box(8, 0, 8, 16, 2, 16);
    public static final VoxelShape UP = Block.box(0, 0, 8, 16, 2, 16);
    public static final VoxelShape RIGHT_UP = Block.box(0, 0, 8, 8, 2, 16);
    public static final VoxelShape LEFT_CENTER = Block.box(8, 0, 0, 16, 2, 16);
    public static final VoxelShape CENTER = Block.box(0, 0, 0, 16, 2, 16);
    public static final VoxelShape RIGHT_CENTER = Block.box(0, 0, 0, 8, 2, 16);
    public static final VoxelShape LEFT_DOWN = Block.box(8, 0, 0, 16, 2, 8);
    public static final VoxelShape DOWN = Block.box(0, 0, 0, 16, 2, 8);
    public static final VoxelShape RIGHT_DOWN = Block.box(0, 0, 0, 8, 2, 8);

    public BlockGomoku() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(2.0F, 3.0F).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(PART, GomokuPart.CENTER));
    }

    private static void handleGomokuRemove(Level world, BlockPos pos, BlockState state) {
        if (!world.isClientSide) {
            GomokuPart part = state.getValue(PART);
            BlockPos centerPos = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));
            BlockEntity te = world.getBlockEntity(centerPos);
            if (te instanceof TileEntityGomoku) {
                for (int i = -1; i < 2; i++) {
                    for (int j = -1; j < 2; j++) {
                        world.setBlockAndUpdate(centerPos.offset(i, 0, j), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        handleGomokuRemove(world, pos, state);
        super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    public void onBlockExploded(BlockState state, Level world, BlockPos pos, Explosion explosion) {
        handleGomokuRemove(world, pos, state);
        super.onBlockExploded(state, world, pos, explosion);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos centerPos = context.getClickedPos();
        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                BlockPos searchPos = centerPos.offset(i, 0, j);
                if (!context.getLevel().getBlockState(searchPos).canBeReplaced(context)) {
                    return null;
                }
            }
        }
        return this.defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(worldIn, pos, state, placer, stack);
        if (worldIn.isClientSide) {
            return;
        }
        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                BlockPos searchPos = pos.offset(i, 0, j);
                GomokuPart part = GomokuPart.getPartByPos(i, j);
                if (part != null && !part.isCenter()) {
                    worldIn.setBlock(searchPos, state.setValue(PART, part), Block.UPDATE_ALL);
                }
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()) {
            GomokuPart part = state.getValue(PART);
            Vec3 location = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            int[] chessPos = getChessPos(location.x, location.z, part);
            if (chessPos != null) {
                BlockPos centerPos = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));
                BlockEntity te = level.getBlockEntity(centerPos);
                if (te instanceof TileEntityGomoku gomoku && gomoku.isPlayerTurn()) {
                    int[][] chessData = gomoku.getChessData();
                    Point playerPoint = new Point(chessPos[0], chessPos[1], Point.BLACK);
                    if (gomoku.isInProgress() && chessData[playerPoint.x][playerPoint.y] == Point.EMPTY) {
                        gomoku.setChessData(playerPoint.x, playerPoint.y, playerPoint.type);
                        gomoku.setInProgress(TouhouLittleMaid.SERVICE.getStatue(chessData, playerPoint) == Statue.IN_PROGRESS);
                        level.playSound(null, pos, InitSounds.GOMOKU.get(), SoundSource.BLOCKS, 1.0f, 0.8F + level.random.nextFloat() * 0.4F);
                        if (gomoku.isInProgress()) {
                            gomoku.setPlayerTurn(false);
                            NetworkHandler.sendToClientPlayer(new ChessDataToClientMessage(centerPos, chessData, playerPoint), player);
                        }
                        gomoku.refresh();
                        return InteractionResult.SUCCESS;
                    }
                }
            }
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    @Nullable
    private static int[] getChessPos(double x, double y, GomokuPart part) {
        switch (part) {
            case LEFT_UP -> {
                return getData(x, y, 0.505, 0.505, 0.54, 0.54, 0, 0);
            }
            case UP -> {
                return getData(x, y, 0.037, 0.505, 0.08, 0.54, 4, 0);
            }
            case RIGHT_UP -> {
                return getData(x, y, -0.037, 0.505, -0.01, 0.54, 11, 0);
            }
            case LEFT_CENTER -> {
                return getData(x, y, 0.505, 0.037, 0.54, 0.07, 0, 4);
            }
            case CENTER -> {
                return getData(x, y, 0.037, 0.037, 0.08, 0.07, 4, 4);
            }
            case RIGHT_CENTER -> {
                return getData(x, y, -0.037, 0.037, -0.01, 0.07, 11, 4);
            }
            case LEFT_DOWN -> {
                return getData(x, y, 0.505, 0, 0.54, 0, 0, 11);
            }
            case DOWN -> {
                return getData(x, y, 0.037, 0, 0.08, 0, 4, 11);
            }
            case RIGHT_DOWN -> {
                return getData(x, y, -0.037, 0, -0.01, 0, 11, 11);
            }
            default -> {
                return null;
            }
        }
    }

    @Nullable
    private static int[] getData(double x, double y, double xOffset, double yOffset, double xStartOffset,
                                 double yStartOffset, int xIndexOffset, int yIndexOffset) {
        int xIndex = (int) ((x - xOffset) / 0.1316);
        int yIndex = (int) ((y - yOffset) / 0.1316);
        double xStart = xStartOffset + xIndex * 0.1316;
        double xEnd = xStart + 0.07;
        double yStart = yStartOffset + yIndex * 0.1316;
        double yEnd = yStart + 0.07;
        xIndex += xIndexOffset;
        yIndex += yIndexOffset;
        boolean checkIndex = 0 <= xIndex && xIndex <= 14 && 0 <= yIndex && yIndex <= 14;
        boolean checkClick = xStart < x && x < xEnd && yStart < y && y < yEnd;
        if (checkIndex && checkClick) {
            return new int[]{xIndex, yIndex};
        }
        return null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PART).isCenter()) {
            return new TileEntityGomoku(pos, state);
        }
        return null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        switch (state.getValue(PART)) {
            case LEFT_UP -> {
                return LEFT_UP;
            }
            case UP -> {
                return UP;
            }
            case RIGHT_UP -> {
                return RIGHT_UP;
            }
            case LEFT_CENTER -> {
                return LEFT_CENTER;
            }
            case RIGHT_CENTER -> {
                return RIGHT_CENTER;
            }
            case LEFT_DOWN -> {
                return LEFT_DOWN;
            }
            case DOWN -> {
                return DOWN;
            }
            case RIGHT_DOWN -> {
                return RIGHT_DOWN;
            }
            default -> {
                return CENTER;
            }
        }
    }
}
