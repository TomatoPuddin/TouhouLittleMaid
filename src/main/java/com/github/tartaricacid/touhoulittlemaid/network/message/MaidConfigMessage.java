package com.github.tartaricacid.touhoulittlemaid.network.message;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class MaidConfigMessage {
    private final int id;
    private final boolean home;
    private final boolean pick;
    private final boolean ride;
    private final MaidSchedule schedule;

    public MaidConfigMessage(int id, boolean home, boolean pick, boolean ride, MaidSchedule schedule) {
        this.id = id;
        this.home = home;
        this.pick = pick;
        this.ride = ride;
        this.schedule = schedule;
    }

    public static void encode(MaidConfigMessage message, PacketBuffer buf) {
        buf.writeInt(message.id);
        buf.writeBoolean(message.home);
        buf.writeBoolean(message.pick);
        buf.writeBoolean(message.ride);
        buf.writeEnum(message.schedule);
    }

    public static MaidConfigMessage decode(PacketBuffer buf) {
        return new MaidConfigMessage(buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readEnum(MaidSchedule.class));
    }

    public static void handle(MaidConfigMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                ServerPlayerEntity sender = context.getSender();
                if (sender == null) {
                    return;
                }
                Entity entity = sender.level.getEntity(message.id);
                if (entity instanceof EntityMaid && ((EntityMaid) entity).isOwnedBy(sender)) {
                    EntityMaid maid = (EntityMaid) entity;
                    if (maid.isHomeModeEnable() != message.home) {
                        handleHome(message, sender, maid);
                    }
                    if (maid.isPickup() != message.pick) {
                        maid.setPickup(message.pick);
                    }
                    if (maid.isRideable() != message.ride) {
                        maid.setRideable(message.ride);
                    }
                    if (maid.getVehicle() != null && !(maid.getVehicle() instanceof EntitySit)) {
                        maid.stopRiding();
                    }
                    if (maid.getSchedule() != message.schedule) {
                        maid.setSchedule(message.schedule);
                    }
                }
            });
        }
        context.setPacketHandled(true);
    }

    private static void handleHome(MaidConfigMessage message, ServerPlayer sender, EntityMaid maid) {
        if (message.home) {
            ResourceLocation dimension = maid.getSchedulePos().getDimension();
            if (!dimension.equals(maid.level.dimension().location())) {
                CheckSchedulePosMessage tips = new CheckSchedulePosMessage(new TranslationTextComponent("message.touhou_little_maid.kappa_compass.maid_dimension_check"));
                NetworkHandler.sendToClientPlayer(tips, sender);
                return;
            }
            BlockPos nearestPos = maid.getSchedulePos().getNearestPos(maid);
            if (nearestPos != null && nearestPos.distSqr(maid.blockPosition()) > 32 * 32) {
                CheckSchedulePosMessage tips = new CheckSchedulePosMessage(new TranslationTextComponent("message.touhou_little_maid.check_schedule_pos.too_far"));
                NetworkHandler.sendToClientPlayer(tips, sender);
                return;
            }
            maid.getSchedulePos().setHomeModeEnable(maid, maid.blockPosition());
        } else {
            maid.restrictTo(BlockPos.ZERO, MaidConfig.MAID_NON_HOME_RANGE.get());
        }
        maid.setHomeModeEnable(message.home);
    }
}
