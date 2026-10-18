package com.talhanation.recruits.compat;

import com.talhanation.recruits.TeamEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsTeam;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import xaero.pac.common.claims.action.api.ClaimingAction;
import xaero.pac.common.server.claims.action.listener.api.IClaimActionListenerAPI;
import xaero.pac.common.server.claims.action.listener.override.api.ClaimActionPermissionOverride;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;

import java.util.List;
import java.util.UUID;

public class OPACClaimCostListener implements IClaimActionListenerAPI {

    public static final int CLAIM_COST_PER_CHUNK = 10; // 청크당 5G

    @Override
    public String getName() {
        return "recruits_claim_cost_listener";
    }

    @Override
    public ClaimActionPermissionOverride overrideClaimingActionPermission(
            UUID playerId,
            ResourceLocation dimensionId,
            int chunkX,
            int chunkZ,
            ClaimingAction action,
            IServerClaimsManagerAPI claimsManager,
            ClaimActionPermissionOverride currentOverride,
            MinecraftServer server
    ) {
        return currentOverride;
    }

    @Override
    public void handleSuccessfulClaimingAction(
            UUID playerId,
            ResourceLocation dimensionId,
            int chunkX,
            int chunkZ,
            ClaimingAction action,
            IServerClaimsManagerAPI claimsManager,
            MinecraftServer server
    ) {
        if (action == ClaimingAction.CLAIM) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, dimensionId
            ));

            if (level == null) return;
            ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

            // =========================================================================
            // ★ 신규 추가: 청크 내 타인 소유 Recruits 주둔 여부 검사
            // =========================================================================
            int minX = chunkPos.getMinBlockX();
            int minZ = chunkPos.getMinBlockZ();
            AABB chunkBox = new AABB(minX, level.getMinBuildHeight(), minZ, minX + 16, level.getMaxBuildHeight(), minZ + 16);
            List<Entity> entities = level.getEntities((Entity) null, chunkBox);

            boolean otherRecruitsPresent = false;
            for (Entity e : entities) {
                if (!e.isAlive()) continue;

                if (e instanceof AbstractRecruitEntity recruit) {
                    UUID recruitOwnerUUID = recruit.getOwnerUUID();
                    // 내 병사가 아닌 다른 플레이어의 병사가 있는 경우
                    if (recruitOwnerUUID == null || !recruitOwnerUUID.equals(playerId)) {
                        otherRecruitsPresent = true;
                        break;
                    }
                }
            }

            if (otherRecruitsPresent) {
                if (player != null) {
                    player.sendSystemMessage(Component.literal("§c[영토 점령 실패] 해당 청크에 다른 세력의 병사가 주둔하고 있어 점령할 수 없습니다!"));
                }
                OPACBridge.unclaimChunk(level, chunkPos);
                return;
            }

            // 1. 소속 파벌 확인
            if (player == null || player.getTeam() == null) {
                if (player != null) {
                    player.sendSystemMessage(Component.literal("§c[영토 점령 취소] 소속된 파벌(팀)이 없어 점령이 취소되었습니다."));
                }
                OPACBridge.unclaimChunk(level, chunkPos);
                return;
            }

            String teamName = player.getTeam().getName();
            RecruitsTeam team = TeamEvents.recruitsTeamManager.getTeamByStringID(teamName);
            if (team == null) {
                player.sendSystemMessage(Component.literal("§c[영토 점령 취소] 파벌 정보를 찾을 수 없어 점령이 취소되었습니다."));
                OPACBridge.unclaimChunk(level, chunkPos);
                return;
            }

            // 2. 파벌 금고 잔액 검사
            int balance = team.getBalance();
            if (balance < CLAIM_COST_PER_CHUNK) {
                player.sendSystemMessage(Component.literal("§c[영토 점령 실패] 파벌 금고 잔액이 부족하여 점령이 취소되었습니다! (필요: " + CLAIM_COST_PER_CHUNK + "G / 현재: " + balance + "G)"));
                OPACBridge.unclaimChunk(level, chunkPos);
                return;
            }

            // 3. 정상 비용 차감 및 저장
            team.withdraw(CLAIM_COST_PER_CHUNK);
            TeamEvents.recruitsTeamManager.save(level);

            player.sendSystemMessage(Component.literal("§a[영토 점령 성공] 파벌 금고에서 §e" + CLAIM_COST_PER_CHUNK + "G§a가 지출되었습니다. (남은 잔액: " + team.getBalance() + "G)"));
        }
    }
}