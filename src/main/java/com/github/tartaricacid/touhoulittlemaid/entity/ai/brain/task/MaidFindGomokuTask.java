package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.block.BlockJoy;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGomoku;
import net.minecraft.block.BlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

public class MaidFindGomokuTask extends MaidMoveToBlockTask {
    public MaidFindGomokuTask(float movementSpeed, int searchLength) {
        super(movementSpeed, searchLength);
    }

    @Override
    protected boolean shouldMoveTo(ServerWorld worldIn, EntityMaid entityIn, BlockPos pos) {
        BlockState blockstate = worldIn.getBlockState(pos);
        return blockstate.getBlock() instanceof BlockJoy && !this.isOccupied(worldIn, pos);
    }

    @Override
    protected void start(ServerWorld worldIn, EntityMaid maid, long gameTimeIn) {
        if (maid.getVehicle() == null && !maid.isInSittingPose()) {
            this.searchForDestination(worldIn, maid);
        }
    }

    private boolean isOccupied(ServerWorld worldIn, BlockPos pos) {
        TileEntity te = worldIn.getBlockEntity(pos);
        if (te instanceof TileEntityGomoku) {
            TileEntityGomoku gomoku = (TileEntityGomoku) te;
            return worldIn.getEntity(gomoku.getSitId()) != null;
        }
        return true;
    }
}
