package com.talhanation.recruits.network;

import com.talhanation.recruits.client.gui.team.TeamInspectionScreen;
import com.talhanation.recruits.world.RecruitsTeam;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

public class MessageToClientUpdateTeamInspection implements Message<MessageToClientUpdateTeamInspection> {

    private CompoundTag teamTag;

    public MessageToClientUpdateTeamInspection() {}

    public MessageToClientUpdateTeamInspection(RecruitsTeam team) {
        this.teamTag = team != null ? team.toNBT() : new CompoundTag();
    }

    @Override
    public Dist getExecutingSide() {
        return Dist.CLIENT;
    }

    @Override
    public void executeClientSide(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            if (this.teamTag != null && !this.teamTag.isEmpty()) {
                RecruitsTeam updatedTeam = RecruitsTeam.fromNBT(this.teamTag);
                
                // 1. 클라이언트 정적 팀 캐시 즉시 갱신 (화면이 재열릴 때도 유지)
                TeamInspectionScreen.recruitsTeam = updatedTeam;

                // 2. 현재 열려있는 화면이 TeamInspectionScreen이면 UI 즉시 리로드
                if (Minecraft.getInstance().screen instanceof TeamInspectionScreen screen) {
                    screen.updateTeamData(updatedTeam);
                }
            }
        });
    }

    @Override
    public MessageToClientUpdateTeamInspection fromBytes(FriendlyByteBuf buf) {
        this.teamTag = buf.readNbt();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(this.teamTag != null ? this.teamTag : new CompoundTag());
    }
}