package com.talhanation.recruits.network;

import com.talhanation.recruits.CommandEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;

public class MessageTogglePvPGroup implements Message<MessageTogglePvPGroup> {

    private UUID player;
    private int group;
    private boolean enable;

    public MessageTogglePvPGroup() {
    }

    public MessageTogglePvPGroup(UUID player, int group, boolean enable) {
        this.player = player;
        this.group = group;
        this.enable = enable;
    }

    @Override
    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    @Override
    public void executeServerSide(NetworkEvent.Context context) {
        ServerPlayer player = Objects.requireNonNull(context.getSender());
        player.getCommandSenderWorld().getEntitiesOfClass(
                AbstractRecruitEntity.class,
                player.getBoundingBox().inflate(120),
                (recruit) -> recruit.isEffectedByCommand(this.player, this.group)
        ).forEach((recruit) -> 
                CommandEvents.onTogglePvPCommand(this.player, recruit, this.group, this.enable)
        );
    }

    @Override
    public MessageTogglePvPGroup fromBytes(FriendlyByteBuf buf) {
        this.player = buf.readUUID();
        this.group = buf.readInt();
        this.enable = buf.readBoolean();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.player);
        buf.writeInt(this.group);
        buf.writeBoolean(this.enable);
    }
}