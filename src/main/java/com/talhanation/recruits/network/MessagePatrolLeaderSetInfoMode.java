package com.talhanation.recruits.network;

import com.talhanation.recruits.entities.AbstractLeaderEntity;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;

public class MessagePatrolLeaderSetInfoMode implements Message<MessagePatrolLeaderSetInfoMode> {
    private UUID recruit;
    private byte state;

    public MessagePatrolLeaderSetInfoMode() {
    }

    public MessagePatrolLeaderSetInfoMode(UUID recruit, byte state) {
        this.recruit = recruit;
        this.state = state;
    }

    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    public void executeServerSide(NetworkEvent.Context context) {
        ServerPlayer player = Objects.requireNonNull(context.getSender());
        MinecraftServer server = player.getServer();

        if (server != null) {
            // 플레이어가 있는 월드뿐만 아니라, 서버의 모든 월드를 뒤져서 엔티티를 찾습니다.
            net.minecraft.world.entity.Entity foundEntity = null;

            for (ServerLevel level : server.getAllLevels()) {
                foundEntity = level.getEntity(this.recruit);
                if (foundEntity != null) break; // 찾았으면 루프 종료
            }

            if (foundEntity instanceof AbstractLeaderEntity leader && leader.isAlive()) {
                leader.setInfoMode(state);
                // [수정] 로그 출력 코드 삭제됨 (콘솔 깔끔하게 유지)
            }
        }
    }

    public MessagePatrolLeaderSetInfoMode fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        this.state = buf.readByte();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.recruit);
        buf.writeByte(this.state);
    }
}