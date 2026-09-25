package com.talhanation.recruits.network;

import com.talhanation.recruits.CommandEvents;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

public class MessageMovement implements Message<MessageMovement> {

    private UUID player_uuid;
    private int state;
    private int[] groupIds; // [MODIFIED] int -> int[] (단일 ID에서 배열로 변경)
    private int formation;

    public MessageMovement(){
    }

    // [MODIFIED] 생성자: 배열을 받도록 수정
    public MessageMovement(UUID player_uuid, int state, int[] groupIds, int formation) {
        this.player_uuid = player_uuid;
        this.state  = state;
        this.groupIds = groupIds;
        this.formation = formation;
    }
    
    // [NEW] 단일 ID 호환용 생성자 (기존 코드 호환성 유지)
    public MessageMovement(UUID player_uuid, int state, int singleGroupId, int formation) {
        this(player_uuid, state, new int[]{singleGroupId}, formation);
    }

    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    public void executeServerSide(NetworkEvent.Context context){
        // [MODIFIED] 여기서 직접 리스트를 만들지 않고, CommandEvents로 ID 배열을 넘겨서 처리 위임
        // 이렇게 해야 CommandEvents에서 "모든 그룹을 합쳐서 하나의 대형으로 만드는" 로직을 수행할 수 있음
        if (context.getSender() != null) {
            CommandEvents.onMovementCommand(context.getSender(), this.state, this.groupIds, this.formation);
        }
    }

    public MessageMovement fromBytes(FriendlyByteBuf buf) {
        this.player_uuid = buf.readUUID();
        this.state = buf.readInt();
        this.groupIds = buf.readVarIntArray(); // [MODIFIED] 배열 읽기
        this.formation = buf.readInt();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.player_uuid);
        buf.writeInt(this.state);
        buf.writeVarIntArray(this.groupIds); // [MODIFIED] 배열 쓰기
        buf.writeInt(this.formation);
    }

}