package com.talhanation.recruits.compat;

import com.talhanation.recruits.TeamEvents;
import com.talhanation.recruits.world.RecruitsTeam;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import xaero.pac.common.claims.action.api.ClaimingAction;
import xaero.pac.common.server.claims.action.listener.api.IClaimActionListenerAPI;
import xaero.pac.common.server.claims.action.listener.override.api.ClaimActionPermissionOverride;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;

import java.util.UUID;

public class OPACClaimCostListener implements IClaimActionListenerAPI {

    public static final int CLAIM_COST_PER_CHUNK = 5; // 청크당 5G(에메랄드 5개)

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
        // 기본 권한 검사는 그대로 통과시킵니다.
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
        // 청크 점령(CLAIM) 성공 시에만 비용 검사
        if (action == ClaimingAction.CLAIM) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, dimensionId
            ));

            if (level == null) return;
            ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

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
                // 잔액 부족 시 청크 강제 회수
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