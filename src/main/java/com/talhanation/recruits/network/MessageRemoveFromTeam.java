package com.talhanation.recruits.network;

import com.talhanation.recruits.TeamEvents;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;

public class MessageRemoveFromTeam implements Message<MessageRemoveFromTeam> {

    private String player;

    public MessageRemoveFromTeam() {
    }

    public MessageRemoveFromTeam(String player) {
        this.player = player;
    }

    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    public void executeServerSide(NetworkEvent.Context context) {
        ServerPlayer sender = Objects.requireNonNull(context.getSender());
        ServerLevel level = sender.serverLevel();

        // --------- 수정된 부분 시작 ---------
        ServerPlayer target = level.getServer().getPlayerList().getPlayerByName(player);
        if (target != null && target.getTeam() != null) {
            TeamEvents.tryToRemoveFromTeam(
                target.getTeam(),   // 추방대상 팀
                sender,             // 추방 명령자(리더 등)
                target,             // 추방 대상
                level,
                player,             // 추방대상 이름
                true
            );
        }
        // --------- 수정된 부분 끝 ---------
    }

    public MessageRemoveFromTeam fromBytes(FriendlyByteBuf buf) {
        this.player = buf.readUtf();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(player);
    }
}
