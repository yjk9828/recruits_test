package com.talhanation.recruits.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.*;

public class RecruitsClaimManager {
    // 런타임 캐시 (ChunkPos -> Team String ID)
    private final Map<ChunkPos, String> chunkClaims = new HashMap<>();

    public void init(ServerLevel level) {
        RecruitsClaimSaveData data = RecruitsClaimSaveData.get(level);
        this.chunkClaims.clear();
        this.chunkClaims.putAll(data.getClaims());
    }

    public void save(ServerLevel level) {
        RecruitsClaimSaveData data = RecruitsClaimSaveData.get(level);
        data.getClaims().clear();
        data.getClaims().putAll(this.chunkClaims);
        data.setDirty();
    }

    @Nullable
    public String getTeamAt(ChunkPos chunkPos) {
        return this.chunkClaims.get(chunkPos);
    }

    public boolean isChunkClaimed(ChunkPos chunkPos) {
        return this.chunkClaims.containsKey(chunkPos);
    }

    public boolean isOwnedByTeam(ChunkPos chunkPos, String teamId) {
        return Objects.equals(this.chunkClaims.get(chunkPos), teamId);
    }

    /**
     * 청크 점령 등록
     */
    public boolean claimChunk(ServerLevel level, ChunkPos chunkPos, String teamId) {
        if (isChunkClaimed(chunkPos)) {
            return false; // 이미 다른 팀 또는 아군이 점령 중
        }
        this.chunkClaims.put(chunkPos, teamId);
        save(level);
        return true;
    }

    /**
     * 청크 점령 해제
     */
    public boolean unclaimChunk(ServerLevel level, ChunkPos chunkPos, String teamId) {
        if (isOwnedByTeam(chunkPos, teamId)) {
            this.chunkClaims.remove(chunkPos);
            save(level);
            return true;
        }
        return false;
    }

    /**
     * 특정 팀의 모든 점령지 일괄 해제 (팀 해산 시 호출)
     */
    public void unclaimAllForTeam(ServerLevel level, String teamId) {
        boolean changed = this.chunkClaims.entrySet().removeIf(entry -> entry.getValue().equals(teamId));
        if (changed) {
            save(level);
        }
    }

    public List<ChunkPos> getChunksClaimedBy(String teamId) {
        List<ChunkPos> result = new ArrayList<>();
        for (Map.Entry<ChunkPos, String> entry : this.chunkClaims.entrySet()) {
            if (entry.getValue().equals(teamId)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public Map<ChunkPos, String> getAllClaims() {
        return Collections.unmodifiableMap(this.chunkClaims);
    }
}