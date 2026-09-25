package com.talhanation.recruits.network;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

public class MessageGroup implements Message<MessageGroup> {

    @Nullable
    private UUID groupUUID;
    private UUID recruitUUID;

    public MessageGroup() {
    }

    public MessageGroup(@Nullable UUID groupUUID, UUID recruitUUID) {
        this.groupUUID = groupUUID;
        this.recruitUUID = recruitUUID;
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
                player.getBoundingBox().inflate(100),
                (recruit) -> recruit.getUUID().equals(this.recruitUUID)
        ).forEach((recruit) -> recruit.setGroup(this.groupUUID));
    }

    @Override
    public MessageGroup fromBytes(FriendlyByteBuf buf) {
        // null 가능한 UUID 읽기
        this.groupUUID = buf.readNullable(FriendlyByteBuf::readUUID);
        this.recruitUUID = buf.readUUID();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        // null 가능한 UUID 쓰기
        buf.writeNullable(this.groupUUID, FriendlyByteBuf::writeUUID);
        buf.writeUUID(this.recruitUUID);
    }
}