package com.talhanation.recruits.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.fml.ModList;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.api.IServerDimensionClaimsManagerAPI;
import xaero.pac.common.server.claims.api.IServerRegionClaimsAPI;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import javax.annotation.Nullable;
import java.util.*;

public class OPACBridge {

    public static boolean isOPACLoaded() {
        return ModList.get().isLoaded("openpartiesandclaims");
    }

    /**
     * 특정 청크의 OPAC 점유자 ID(파티 ID 또는 플레이어 UUID)를 반환합니다.
     */
    @Nullable
    public static UUID getChunkOwnerId(ServerLevel level, ChunkPos chunkPos) {
        if (!isOPACLoaded()) return null;

        OpenPACServerAPI pacApi = OpenPACServerAPI.get(level.getServer());
        if (pacApi == null) return null;

        IServerClaimsManagerAPI claimsManager = pacApi.getServerClaimsManager();
        if (claimsManager == null) return null;

        ResourceLocation dimId = level.dimension().location();
        IServerDimensionClaimsManagerAPI dimClaims = claimsManager.getDimension(dimId);
        if (dimClaims == null) return null;

        int regX = chunkPos.x >> 5;
        int regZ = chunkPos.z >> 5;
        IServerRegionClaimsAPI regClaims = dimClaims.getRegion(regX, regZ);
        if (regClaims == null) return null;

        int localX = chunkPos.x & 31;
        int localZ = chunkPos.z & 31;
        IPlayerChunkClaimAPI claim = regClaims.get(localX, localZ);
        if (claim == null) return null;

        return claim.getPlayerId();
    }

    /**
     * ownerId를 통해 IServerPartyAPI 객체를 조회합니다.
     */
    @Nullable
    public static IServerPartyAPI resolveParty(IPartyManagerAPI partyManager, UUID ownerId) {
        if (partyManager == null || ownerId == null) return null;
        return partyManager.getPartyByMember(ownerId);
    }

    /**
     * 특정 플레이어가 해당 청크를 소유한 파티(또는 개인 점령자)에 소속되어 있는지 판정합니다.
     */
    public static boolean isPlayerMemberOfChunkParty(ServerLevel level, ChunkPos chunkPos, UUID playerUUID) {
        if (!isOPACLoaded() || playerUUID == null) return false;

        UUID ownerId = getChunkOwnerId(level, chunkPos);
        if (ownerId == null) return false;

        // 개인 점령 청크인 경우: 소유자 UUID와 동일한지 확인
        if (ownerId.equals(playerUUID)) return true;

        OpenPACServerAPI pacApi = OpenPACServerAPI.get(level.getServer());
        if (pacApi == null || pacApi.getPartyManager() == null) return false;

        IPartyManagerAPI partyManager = pacApi.getPartyManager();
        IServerPartyAPI party = resolveParty(partyManager, ownerId);

        if (party != null) {
            return party.getMemberInfo(playerUUID) != null;
        }

        return false;
    }

    /**
     * 특정 팀 멤버들이 소유한 OPAC 점령 청크 목록 수집 (유지비 계산용)
     */
    public static List<ChunkPos> getClaimedChunksForTeam(ServerLevel level, String teamName) {
        List<ChunkPos> result = new ArrayList<>();
        if (!isOPACLoaded()) return result;

        OpenPACServerAPI pacApi = OpenPACServerAPI.get(level.getServer());
        if (pacApi == null || pacApi.getServerClaimsManager() == null) return result;

        IServerClaimsManagerAPI claimsManager = pacApi.getServerClaimsManager();
        ResourceLocation dimId = level.dimension().location();
        IServerDimensionClaimsManagerAPI dimClaims = claimsManager.getDimension(dimId);
        if (dimClaims == null) return result;

        IPartyManagerAPI partyManager = pacApi.getPartyManager();
        if (partyManager == null) return result;

        Set<UUID> targetOwnerIds = new HashSet<>();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.getTeam() != null && player.getTeam().getName().equals(teamName)) {
                targetOwnerIds.add(player.getUUID());
                IServerPartyAPI party = partyManager.getPartyByMember(player.getUUID());
                if (party != null) {
                    targetOwnerIds.add(party.getId());
                }
            }
        }

        if (targetOwnerIds.isEmpty()) return result;

        for (int rx = -64; rx <= 64; rx++) {
            for (int rz = -64; rz <= 64; rz++) {
                IServerRegionClaimsAPI reg = dimClaims.getRegion(rx, rz);
                if (reg == null) continue;

                for (int cx = 0; cx < 32; cx++) {
                    for (int cz = 0; cz < 32; cz++) {
                        IPlayerChunkClaimAPI claim = reg.get(cx, cz);
                        if (claim != null && targetOwnerIds.contains(claim.getPlayerId())) {
                            result.add(new ChunkPos((rx << 5) + cx, (rz << 5) + cz));
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * OPAC API를 호출하여 청크를 강제 unclaim 처리합니다.
     */
    public static boolean unclaimChunk(ServerLevel level, ChunkPos chunkPos) {
        if (!isOPACLoaded()) return false;

        OpenPACServerAPI pacApi = OpenPACServerAPI.get(level.getServer());
        if (pacApi == null || pacApi.getServerClaimsManager() == null) return false;

        try {
            ResourceLocation dimId = level.dimension().location();
            pacApi.getServerClaimsManager().unclaim(dimId, chunkPos.x, chunkPos.z);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}