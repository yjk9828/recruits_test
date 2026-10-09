package com.talhanation.recruits;

import com.talhanation.recruits.world.RecruitsTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = Main.MOD_ID)
public class ClaimEvents {

    // 유지비 징수 주기: 30분 = 36,000 틱
    public static final int UPKEEP_INTERVAL_TICKS = 30 * 60 * 20;
    private static int upkeepTickCounter = 0;

    /**
     * 1. 블록 설치 보호
     * 점령지 내 외부인 설치 차단
     */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        ChunkPos chunkPos = new ChunkPos(pos);

        if (TeamEvents.recruitsClaimManager.isChunkClaimed(chunkPos)) {
            String ownerTeam = TeamEvents.recruitsClaimManager.getTeamAt(chunkPos);
            boolean isOwner = player.getTeam() != null && player.getTeam().getName().equals(ownerTeam);

            if (!isOwner && !player.hasPermissions(2)) { // OP 제외
                player.sendSystemMessage(Component.literal("타 파벌의 영토에서는 블록을 설치할 수 없습니다!").withStyle(ChatFormatting.RED));
                event.setCanceled(true);
            }
        }
    }

    /**
     * 2. 블록 파괴 보호
     * 점령지 내 외부인 파괴 차단 (TNT 등 엔티티 폭발은 통과)
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        ChunkPos chunkPos = new ChunkPos(pos);

        if (!TeamEvents.recruitsClaimManager.isChunkClaimed(chunkPos)) {
            return;
        }

        String ownerTeam = TeamEvents.recruitsClaimManager.getTeamAt(chunkPos);
        boolean isOwner = player.getTeam() != null && player.getTeam().getName().equals(ownerTeam);

        if (!isOwner && !player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("타 파벌의 영토에서는 블록을 파괴할 수 없습니다!").withStyle(ChatFormatting.RED));
            event.setCanceled(true);
        }
    }

    /**
     * 3. 30분 주기 영토 유지비 자동 징수 & 체납 시 무작위 영토 강제 몰수
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        upkeepTickCounter++;
        if (upkeepTickCounter >= UPKEEP_INTERVAL_TICKS) {
            upkeepTickCounter = 0;

            ServerLevel overworld = event.getServer().overworld();
            if (overworld == null) return;

            Map<ChunkPos, String> allClaims = TeamEvents.recruitsClaimManager.getAllClaims();
            if (allClaims.isEmpty()) return;

            Map<String, List<ChunkPos>> teamChunksMap = new HashMap<>();
            for (Map.Entry<ChunkPos, String> entry : allClaims.entrySet()) {
                teamChunksMap.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
            }

            Random random = new Random();

            for (Map.Entry<String, List<ChunkPos>> entry : teamChunksMap.entrySet()) {
                String teamId = entry.getKey();
                List<ChunkPos> chunks = entry.getValue();
                RecruitsTeam team = TeamEvents.recruitsTeamManager.getTeamByStringID(teamId);
                if (team == null) continue;

                int requiredUpkeep = chunks.size(); // 청크당 1G
                int currentBalance = team.getBalance();

                if (currentBalance >= requiredUpkeep) {
                    team.withdraw(requiredUpkeep);
                    notifyTeam(event.getServer(), teamId, Component.literal("[영토 유지비] " + chunks.size() + "개 청크의 유지비 " + requiredUpkeep + "G가 금고에서 정상 지출되었습니다. (남은 잔액: " + team.getBalance() + "G)").withStyle(ChatFormatting.AQUA));
                } else {
                    int shortage = requiredUpkeep - currentBalance;
                    team.setBalance(0);

                    Collections.shuffle(chunks, random);
                    List<ChunkPos> lostChunks = chunks.subList(0, Math.min(shortage, chunks.size()));

                    for (ChunkPos lostChunk : lostChunks) {
                        TeamEvents.recruitsClaimManager.unclaimChunk(overworld, lostChunk, teamId);
                    }

                    notifyTeam(event.getServer(), teamId, Component.literal("[경고] 금고 잔액 부족으로 영토 유지비 체납! " + lostChunks.size() + "개의 영토가 무작위로 강제 몰수되었습니다.").withStyle(ChatFormatting.RED));
                }
            }

            TeamEvents.recruitsTeamManager.save(overworld);
        }
    }

    private static void notifyTeam(net.minecraft.server.MinecraftServer server, String teamId, Component msg) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getTeam() != null && player.getTeam().getName().equals(teamId)) {
                player.sendSystemMessage(msg);
            }
        }
    }
}