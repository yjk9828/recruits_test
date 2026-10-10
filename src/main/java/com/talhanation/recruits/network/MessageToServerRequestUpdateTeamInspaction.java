package com.talhanation.recruits.network;

import com.talhanation.recruits.Main;
import com.talhanation.recruits.TeamEvents;
import com.talhanation.recruits.world.RecruitsTeam;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Objects;

public class MessageToServerRequestUpdateTeamInspaction implements Message<MessageToServerRequestUpdateTeamInspaction> {

    private String teamId;

    public MessageToServerRequestUpdateTeamInspaction() {}

    public MessageToServerRequestUpdateTeamInspaction(String teamId) {
        this.teamId = teamId != null ? teamId : "";
    }

    @Override
    public Dist getExecutingSide() {
        // CoreLib 규격: 서버 수신 패킷은 DEDICATED_SERVER 반환 (null 금지)
        return Dist.DEDICATED_SERVER;
    }

    @Override
    public void executeServerSide(NetworkEvent.Context context) {
        ServerPlayer player = Objects.requireNonNull(context.getSender());
        context.enqueueWork(() -> {
            String targetId = this.teamId;

            // teamId가 비어 있으면 플레이어의 바닐라 스코어보드 팀 이름 사용
            if ((targetId == null || targetId.isEmpty()) && player.getTeam() != null) {
                targetId = player.getTeam().getName();
            }

            if (targetId == null || targetId.isEmpty()) return;

            if (TeamEvents.recruitsTeamManager != null) {
                RecruitsTeam team = TeamEvents.recruitsTeamManager.getTeamByStringID(targetId);
                if (team != null) {
                    Main.SIMPLE_CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new MessageToClientUpdateTeamInspection(team)
                    );
                }
            }
        });
    }

    @Override
    public MessageToServerRequestUpdateTeamInspaction fromBytes(FriendlyByteBuf buf) {
        this.teamId = buf.readUtf();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.teamId != null ? this.teamId : "");
    }
}