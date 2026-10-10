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
        this.teamTag = team.toNBT();
    }

    @Override
    public Dist getExecutingSide() {
        return Dist.CLIENT;
    }

    @Override
    public void executeClientSide(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof TeamInspectionScreen screen) {
                RecruitsTeam updatedTeam = RecruitsTeam.fromNBT(this.teamTag);
                screen.updateTeamData(updatedTeam);
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
        buf.writeNbt(this.teamTag);
    }
}