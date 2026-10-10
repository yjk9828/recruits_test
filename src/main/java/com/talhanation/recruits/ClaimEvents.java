package com.talhanation.recruits;

import com.talhanation.recruits.compat.OPACBridge;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = Main.MOD_ID)
public class ClaimEvents {

    public static final int UPKEEP_INTERVAL_TICKS = 30 * 60 * 20; // 30분
    public static final int SIEGE_REQUIRED_SECONDS = 10;          // ★ 테스트용: 10초
    public static final int MIN_RECRUITS_FOR_SIEGE = 1;           // 최소 병력: 1명 이상이면 즉시 점거 시작

    private static int upkeepTickCounter = 0;
    private static int siegeTickCounter = 0;

    // 청크별 점거 시간 기록 (ChunkPos -> 누적 초)
    private static final Map<ChunkPos, Integer> SIEGE_PROGRESS_MAP = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        // 1. 공성전 점거 루프 (매 1초 = 20틱마다)
        siegeTickCounter++;
        if (siegeTickCounter >= 20) {
            siegeTickCounter = 0;
            processSiegeTick(overworld);
        }

        // 2. 30분 주기 영토 유지비 루프
        upkeepTickCounter++;
        if (upkeepTickCounter >= UPKEEP_INTERVAL_TICKS) {
            upkeepTickCounter = 0;
            processUpkeep(server, overworld);
        }
    }

    private static void processSiegeTick(ServerLevel level) {
        if (!OPACBridge.isOPACLoaded()) return;

        MinecraftServer server = level.getServer();
        Set<ChunkPos> activeChunksThisTick = new HashSet<>();

        // 접속 중인 모든 플레이어 위치의 청크를 스캔
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ChunkPos chunkPos = new ChunkPos(player.blockPosition());

            UUID ownerId = OPACBridge.getChunkOwnerId(level, chunkPos);
            if (ownerId == null) continue; // 미점령지(야생)는 무시

            // 해당 청크(16x16) 내의 모든 엔티티 검사
            int minX = chunkPos.getMinBlockX();
            int minZ = chunkPos.getMinBlockZ();
            AABB chunkBox = new AABB(minX, level.getMinBuildHeight(), minZ, minX + 16, level.getMaxBuildHeight(), minZ + 16);

            List<Entity> entities = level.getEntities((Entity) null, chunkBox);
            int invaderRecruitsCount = 0;
            ServerPlayer invaderPlayer = null;

            for (Entity e : entities) {
                if (!e.isAlive()) continue;

                if (e instanceof AbstractRecruitEntity recruit) {
                    UUID recruitOwnerUUID = recruit.getOwnerUUID();
                    // 주인이 있고, 그 주인이 이 청크 파티 멤버가 아니라면 침공자로 판정!
                    if (recruitOwnerUUID != null && !OPACBridge.isPlayerMemberOfChunkParty(level, chunkPos, recruitOwnerUUID)) {
                        invaderRecruitsCount++;
                        if (invaderPlayer == null) {
                            invaderPlayer = level.getServer().getPlayerList().getPlayer(recruitOwnerUUID);
                        }
                    }
                }
            }

            // 외부인 병사가 1명 이상 주둔 중인 경우 점거 진행
            if (invaderRecruitsCount >= MIN_RECRUITS_FOR_SIEGE) {
                activeChunksThisTick.add(chunkPos);

                int secondsHeld = SIEGE_PROGRESS_MAP.getOrDefault(chunkPos, 0) + 1;
                SIEGE_PROGRESS_MAP.put(chunkPos, secondsHeld);

                // 주인 플레이어가 접속 중이면 액션바에 카운트다운 출력
                if (invaderPlayer != null) {
                    invaderPlayer.sendSystemMessage(
                        Component.literal("§6⚔ [영토 점거 중] §e" + secondsHeld + " / " + SIEGE_REQUIRED_SECONDS + "초 §a(주둔 병력: " + invaderRecruitsCount + "명)"),
                        true
                    );
                }

                // 10초 도달 시 강제 Unclaim 실행
                if (secondsHeld >= SIEGE_REQUIRED_SECONDS) {
                    OPACBridge.unclaimChunk(level, chunkPos);
                    SIEGE_PROGRESS_MAP.remove(chunkPos);

                    notifyAllPlayers(server, Component.literal("§c🚩 [영토 함락] §f[" + chunkPos.x + ", " + chunkPos.z + "] 청크의 점령이 해제되어 무주지화되었습니다!").withStyle(ChatFormatting.GOLD));
                }
            }
        }

        // 병사가 청크를 벗어나면 진행도 초기화
        SIEGE_PROGRESS_MAP.keySet().removeIf(pos -> !activeChunksThisTick.contains(pos));
    }

    private static void processUpkeep(MinecraftServer server, ServerLevel overworld) {
        Random random = new Random();

        for (RecruitsTeam team : TeamEvents.recruitsTeamManager.getTeams()) {
            String teamId = team.getStringID();

            List<ChunkPos> chunks = OPACBridge.getClaimedChunksForTeam(overworld, teamId);
            if (chunks.isEmpty()) continue;

            int requiredUpkeep = chunks.size(); // 청크당 1G
            int currentBalance = team.getBalance();

            if (currentBalance >= requiredUpkeep) {
                team.withdraw(requiredUpkeep);
                notifyTeam(server, teamId, Component.literal("[영토 유지비] " + chunks.size() + "개 청크 유지비 " + requiredUpkeep + "G 지출 완료. (잔액: " + team.getBalance() + "G)").withStyle(ChatFormatting.AQUA));
            } else {
                int shortage = requiredUpkeep - currentBalance;
                team.setBalance(0);

                Collections.shuffle(chunks, random);
                List<ChunkPos> lostChunks = chunks.subList(0, Math.min(shortage, chunks.size()));

                for (ChunkPos lostChunk : lostChunks) {
                    OPACBridge.unclaimChunk(overworld, lostChunk);
                }

                notifyTeam(server, teamId, Component.literal("[경고] 유지비 체납으로 인해 " + lostChunks.size() + "개 청크가 강제 몰수(무주지화)되었습니다!").withStyle(ChatFormatting.RED));
            }
        }

        TeamEvents.recruitsTeamManager.save(overworld);
    }

    private static void notifyTeam(MinecraftServer server, String teamId, Component msg) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getTeam() != null && player.getTeam().getName().equals(teamId)) {
                player.sendSystemMessage(msg);
            }
        }
    }

    private static void notifyAllPlayers(MinecraftServer server, Component msg) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(msg);
        }
    }
}