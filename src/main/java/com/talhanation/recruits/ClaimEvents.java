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
import net.minecraftforge.event.server.ServerStartingEvent;
import java.util.*;

@Mod.EventBusSubscriber(modid = Main.MOD_ID)
public class ClaimEvents {

    public static final int UPKEEP_INTERVAL_TICKS = 30 * 60 * 20; // 30분 유지비
    public static final int SIEGE_REQUIRED_SECONDS = 60;          // ★ 1분 점거 필요
    public static final int MIN_RECRUITS_FOR_SIEGE = 2;           // ★ 병사 2명 이상 필요

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
	@SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // 서버 기동 시 OPAC 영토 점령 비용(5G) 검사 리스너 자동 등록
        OPACBridge.registerClaimCostListener(event.getServer());
    }

    private static void processSiegeTick(ServerLevel level) {
        if (!OPACBridge.isOPACLoaded()) return;

        MinecraftServer server = level.getServer();
        Set<ChunkPos> activeChunksThisTick = new HashSet<>();

        // 접속 중인 모든 플레이어를 대상으로 서 있는 청크 스캔
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ChunkPos chunkPos = new ChunkPos(player.blockPosition());

            UUID ownerId = OPACBridge.getChunkOwnerId(level, chunkPos);
            if (ownerId == null) continue; // 미점령지(야생)는 무시

            // 플레이어가 이미 그 청크의 파티 멤버라면 침략군이 아니므로 무시
            if (OPACBridge.isPlayerMemberOfChunkParty(level, chunkPos, player.getUUID())) {
                continue;
            }

            // 해당 청크(16x16) 범위 내 모든 엔티티 스캔
            int minX = chunkPos.getMinBlockX();
            int minZ = chunkPos.getMinBlockZ();
            AABB chunkBox = new AABB(minX, level.getMinBuildHeight(), minZ, minX + 16, level.getMaxBuildHeight(), minZ + 16);

            List<Entity> entities = level.getEntities((Entity) null, chunkBox);
            int myRecruitsCount = 0;
            boolean otherRecruitsPresent = false;

            for (Entity e : entities) {
                if (!e.isAlive()) continue;

                if (e instanceof AbstractRecruitEntity recruit) {
                    UUID recruitOwnerUUID = recruit.getOwnerUUID();

                    // 1) 현재 플레이어가 주인인 병사 카운트
                    if (recruitOwnerUUID != null && recruitOwnerUUID.equals(player.getUUID())) {
                        myRecruitsCount++;
                    } 
                    // 2) 침략군 병사 외에 다른 모든 병사(방어군 또는 타 세력) 감지
                    else {
                        otherRecruitsPresent = true;
                    }
                }
            }

            // 조건 검사:
            // 1. 청크 안에 다른 병사(수비군/타군)가 1명이라도 있으면 교전 중으로 판단하여 점거 차단
            if (otherRecruitsPresent) {
                player.sendSystemMessage(Component.literal("§c[점거 저지됨] 청크 내에 잔존 방어 병력이 있습니다! 방어군을 먼저 무력화하세요."), true);
                continue;
            }

            // 2. 플레이어와 함께 있는 아군 병사가 최소 2명 이상이어야 함
            if (myRecruitsCount < MIN_RECRUITS_FOR_SIEGE) {
                player.sendSystemMessage(Component.literal("§e[점거 대기] 아군 병사가 부족합니다 (" + myRecruitsCount + "/" + MIN_RECRUITS_FOR_SIEGE + "명 주둔 필요)"), true);
                continue;
            }

            // 모든 조건 만족 (플레이어 있음 + 아군 병사 2명 이상 + 타 병사 전멸)
            activeChunksThisTick.add(chunkPos);

            int secondsHeld = SIEGE_PROGRESS_MAP.getOrDefault(chunkPos, 0) + 1;
            SIEGE_PROGRESS_MAP.put(chunkPos, secondsHeld);

            // 매 초 액션바에 점거 게이지 출력
            player.sendSystemMessage(
                Component.literal("§6⚔ [영토 점거 중] §e" + secondsHeld + " / " + SIEGE_REQUIRED_SECONDS + "초 §a(주둔 병력: " + myRecruitsCount + "명)"),
                true
            );

            // 60초 도달 시 강제 Unclaim 실행
            if (secondsHeld >= SIEGE_REQUIRED_SECONDS) {
                OPACBridge.unclaimChunk(level, chunkPos);
                SIEGE_PROGRESS_MAP.remove(chunkPos);

                notifyAllPlayers(server, Component.literal("§c🚩 [영토 함락] §f[" + player.getName().getString() + "] 세력이 [" + chunkPos.x + ", " + chunkPos.z + "] 청크의 수비대를 무력화하고 영토를 탈환(무주지화)했습니다!").withStyle(ChatFormatting.GOLD));
            }
        }

        // 플레이어나 병사가 이탈해 점거가 끊기면 타이머 초기화
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