package com.talhanation.recruits.network;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist; // [변경] Dist 임포트
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

public class MessageTogglePvP implements Message<MessageTogglePvP> {
    private UUID entityUUID;
    private boolean enable;

    public MessageTogglePvP() {
    }

    public MessageTogglePvP(UUID uuid, boolean enable) {
        this.entityUUID = uuid;
        this.enable = enable;
    }

    // [변경] 반환 타입을 LogicalSide -> Dist로 변경
    // 서버에서 실행되어야 하므로 DEDICATED_SERVER를 반환합니다.
    @Override
    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.entityUUID);
        buf.writeBoolean(this.enable);
    }

    @Override
    public MessageTogglePvP fromBytes(FriendlyByteBuf buf) {
        this.entityUUID = buf.readUUID();
        this.enable = buf.readBoolean();
        return this;
    }

    @Override
    public void executeServerSide(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                // serverLevel() 사용
                Entity entity = player.serverLevel().getEntity(this.entityUUID);
                if (entity instanceof AbstractRecruitEntity recruit) {
                    if (recruit.isOwnedBy(player)) {
                        recruit.setOnlyPvP(this.enable);
                    }
                }
            }
        });
    }

    @Override
    public void executeClientSide(NetworkEvent.Context context) {
        // 서버 전용 패킷이므로 클라이언트 로직은 비워둡니다.
    }
}