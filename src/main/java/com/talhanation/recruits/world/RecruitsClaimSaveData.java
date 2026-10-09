package com.talhanation.recruits.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public class RecruitsClaimSaveData extends SavedData {
    private static final String FILE_NAME = "recruits_claims";

    // ChunkPos -> Team String ID
    private final Map<ChunkPos, String> claims = new HashMap<>();

    public RecruitsClaimSaveData() {
    }

    public static RecruitsClaimSaveData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                RecruitsClaimSaveData::load,
                RecruitsClaimSaveData::new,
                FILE_NAME
        );
    }

    public static RecruitsClaimSaveData load(CompoundTag nbt) {
        RecruitsClaimSaveData data = new RecruitsClaimSaveData();
        ListTag list = nbt.getList("Claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int x = entry.getInt("X");
            int z = entry.getInt("Z");
            String teamId = entry.getString("Team");
            data.claims.put(new ChunkPos(x, z), teamId);
        }
        return data;
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag nbt) {
        ListTag list = new ListTag();
        for (Map.Entry<ChunkPos, String> entry : this.claims.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("X", entry.getKey().x);
            tag.putInt("Z", entry.getKey().z);
            tag.putString("Team", entry.getValue());
            list.add(tag);
        }
        nbt.put("Claims", list);
        return nbt;
    }

    public Map<ChunkPos, String> getClaims() {
        return this.claims;
    }
}