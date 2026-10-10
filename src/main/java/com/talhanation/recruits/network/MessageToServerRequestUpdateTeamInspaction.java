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
        return Dist.DEDICATED_SERVER;
    }

    @Override
    public void executeServerSide(NetworkEvent.Context context) {
        ServerPlayer player = Objects.requireNonNull(context.getSender());
        context.enqueueWork(() -> {
            if (this.teamId == null || this.teamId.isEmpty()) return;

            RecruitsTeam team = TeamEvents.recruitsTeamManager.getTeamByStringID(this.teamId);
            if (team == null) return;

            Main.SIMPLE_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new MessageToClientUpdateTeamInspection(team)
            );
        });
    }

    @Override
    public MessageToServerRequestUpdateTeamInspaction fromBytes(FriendlyByteBuf buf) {
        this.teamId = buf.readUtf();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        // ★ null 방어: null이면 빈 문자열 기록
        buf.writeUtf(this.teamId != null ? this.teamId : "");
    }
}