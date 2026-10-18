package com.talhanation.recruits;

import com.talhanation.recruits.compat.OPACBridge;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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

    public static final int UPKEEP_INTERVAL_TICKS = 60 * 60 * 20; // 60분 유지비
    public static final int SIEGE_REQUIRED_SECONDS = 60;          // 1분 점거 필요
    public static final int MIN_RECRUITS_FOR_SIEGE = 2;           // 병사 2명 이상 필요
    public static final int REQUIRED_CLEAR_SKY_HEIGHT = 100;      // ★ 머리 위 확보 필요 높이(100칸)

    private static int upkeepTickCounter = 0;
    private static int siegeTickCounter = 0;

    private static final Map<ChunkPos, Integer> SIEGE_PROGRESS_MAP = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        siegeTickCounter++;
        if (siegeTickCounter >= 20) {
            siegeTickCounter = 0;
            processSiegeTick(overworld);
        }

        upkeepTickCounter++;
        if (upkeepTickCounter >= UPKEEP_INTERVAL_TICKS) {
            upkeepTickCounter = 0;
            processUpkeep(server, overworld);
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        OPACBridge.registerClaimCostListener(event.getServer());
    }

    private static void processSiegeTick(ServerLevel level) {
        if (!OPACBridge.isOPACLoaded()) return;

        MinecraftServer server = level.getServer();
        Set<ChunkPos> activeChunksThisTick = new HashSet<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ChunkPos chunkPos = new ChunkPos(player.blockPosition());

            UUID ownerId = OPACBridge.getChunkOwnerId(level, chunkPos);
            if (ownerId == null) continue;

            if (OPACBridge.isPlayerMemberOfChunkParty(level, chunkPos, player.getUUID())) {
                continue;
            }

            // =========================================================
            // 조건 검사 1: 지상 노출 검사 (머리 위 100칸 공기 블록 확인)
            // =========================================================
            if (isSkyObstructed(level, player)) {
                player.sendSystemMessage(Component.literal("§c[점거 저지됨] 머리 위가 막혀 있습니다! 지상으로 나와 하늘을 확보하세요 (100칸 필요)."), true);
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

                    if (recruitOwnerUUID != null && recruitOwnerUUID.equals(player.getUUID())) {
                        myRecruitsCount++;
                    } else {
                        otherRecruitsPresent = true;
                    }
                }
            }

            // 조건 검사 2: 방어 병력 무력화 여부
            if (otherRecruitsPresent) {
                player.sendSystemMessage(Component.literal("§c[점거 저지됨] 청크 내에 잔존 방어 병력이 있습니다! 방어군을 먼저 무력화하세요."), true);
                continue;
            }

            // 조건 검사 3: 아군 병사 2명 이상 주둔 여부
            if (myRecruitsCount < MIN_RECRUITS_FOR_SIEGE) {
                player.sendSystemMessage(Component.literal("§e[점거 대기] 아군 병사가 부족합니다 (" + myRecruitsCount + "/" + MIN_RECRUITS_FOR_SIEGE + "명 주둔 필요)"), true);
                continue;
            }

            // 모든 조건 통과 (하늘 개방 + 병사 2명 이상 + 적 병사 전멸)
            activeChunksThisTick.add(chunkPos);

            int secondsHeld = SIEGE_PROGRESS_MAP.getOrDefault(chunkPos, 0) + 1;
            SIEGE_PROGRESS_MAP.put(chunkPos, secondsHeld);

            player.sendSystemMessage(
                Component.literal("§6⚔ [영토 점거 중] §e" + secondsHeld + " / " + SIEGE_REQUIRED_SECONDS + "초 §a(주둔 병력: " + myRecruitsCount + "명)"),
                true
            );

            if (secondsHeld >= SIEGE_REQUIRED_SECONDS) {
                OPACBridge.unclaimChunk(level, chunkPos);
                SIEGE_PROGRESS_MAP.remove(chunkPos);

                notifyAllPlayers(server, Component.literal("§c🚩 [영토 함락] §f[" + player.getName().getString() + "] 세력이 [" + chunkPos.x + ", " + chunkPos.z + "] 청크의 수비대를 무력화하고 영토를 탈환(무주지화)했습니다!").withStyle(ChatFormatting.GOLD));
            }
        }

        SIEGE_PROGRESS_MAP.keySet().removeIf(pos -> !activeChunksThisTick.contains(pos));
    }

    /**
     * 플레이어 머리 위 100칸 이내에 장애물(공기가 아닌 블록)이 있는지 검사합니다.
     */
    private static boolean isSkyObstructed(ServerLevel level, ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();
        int startY = playerPos.getY() + 2; // 플레이어 머리 바로 위
        int endY = Math.min(playerPos.getY() + 1 + REQUIRED_CLEAR_SKY_HEIGHT, level.getMaxBuildHeight() - 1);

        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();
        for (int y = startY; y <= endY; y++) {
            checkPos.set(playerPos.getX(), y, playerPos.getZ());
            // 공기 블록이 아니라면(돌, 흙, 나뭇잎, 인공 건축물 등) 장애물로 판정
            if (!level.getBlockState(checkPos).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static void processUpkeep(MinecraftServer server, ServerLevel overworld) {
        Random random = new Random();

        for (RecruitsTeam team : TeamEvents.recruitsTeamManager.getTeams()) {
            String teamId = team.getStringID();

            List<ChunkPos> chunks = OPACBridge.getClaimedChunksForTeam(overworld, teamId);
            if (chunks.isEmpty()) continue;

            int requiredUpkeep = chunks.size();
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