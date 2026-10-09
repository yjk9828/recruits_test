package com.talhanation.recruits;

import com.talhanation.recruits.world.RecruitsTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = Main.MOD_ID)
public class ClaimEvents {

    // 청크 점령 초기 선포 비용 (에메랄드 20개)
    public static final int CLAIM_COST = 20;

    // 유지비 징수 주기: 30분 = 36,000 틱
    public static final int UPKEEP_INTERVAL_TICKS = 30 * 60 * 20;
    private static int upkeepTickCounter = 0;

    /**
     * 1-1. 배너 설치를 통한 영토 선포 + 외부인 설치 차단
     */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        ChunkPos chunkPos = new ChunkPos(pos);
        BlockEntity be = serverLevel.getBlockEntity(pos);

        // 1) 배너 설치일 때: 공식 파벌 깃발인 경우에만 영토 점령 처리
        if (be instanceof BannerBlockEntity bannerBE) {
            // 플레이어가 팀에 속해 있는지 확인 (팀이 없으면 그냥 일반 배너 설치 허용)
            if (player.getTeam() != null) {
                String teamName = player.getTeam().getName();
                RecruitsTeam team = TeamEvents.recruitsTeamManager.getTeamByStringID(teamName);

                // 모루로 지정된 깃발 이름이 파벌명과 일치하는 공식 깃발일 때만 점령 절차 돌입
                if (team != null && isTeamBannerByName(bannerBE, team, teamName)) {
                    
                    // 청크 점령 가능 여부 확인
                    if (TeamEvents.recruitsClaimManager.isChunkClaimed(chunkPos)) {
                        String ownerTeam = TeamEvents.recruitsClaimManager.getTeamAt(chunkPos);
                        if (Objects.equals(ownerTeam, teamName)) {
                            player.sendSystemMessage(Component.literal("이미 아군 파벌이 점령한 청크입니다.").withStyle(ChatFormatting.YELLOW));
                        } else {
                            player.sendSystemMessage(Component.literal("이미 다른 파벌이 점령한 청크입니다!").withStyle(ChatFormatting.RED));
                            event.setCanceled(true); // 타 영토 내 공식 깃발 알박기 방지
                        }
                        return;
                    }

                    // 파벌 금고 잔액 확인 및 차감
                    if (!team.withdraw(CLAIM_COST)) {
                        player.sendSystemMessage(Component.literal("파벌 금고 잔액이 부족합니다! (필요: " + CLAIM_COST + "G, 현재: " + team.getBalance() + "G)").withStyle(ChatFormatting.RED));
                        event.setCanceled(true);
                        return;
                    }

                    // 점령 등록 및 배너에 파벌 각인
                    TeamEvents.recruitsClaimManager.claimChunk(serverLevel, chunkPos, teamName);
                    TeamEvents.recruitsTeamManager.save(serverLevel);

                    CompoundTag customTag = bannerBE.getPersistentData();
                    customTag.putString("RecruitsClaimTeam", teamName);
                    bannerBE.setChanged();

                    player.sendSystemMessage(Component.literal("[" + team.getTeamDisplayName() + "] 새로운 영토를 선포했습니다! (비용: " + CLAIM_COST + "G)").withStyle(ChatFormatting.GREEN));
                    return;
                }
            }
            // ★ 팀이 없거나 이름이 일치하지 않는 일반 현수막은 경고 없이 통과되어 아래 일반 블록 처리로 넘어감
        }

        // 2) 일반 블록(및 일반 배너) 설치일 때: 아군이 아닌 플레이어의 점령지 내 블록 설치 차단
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
     * 1-2. 블록 파괴 보호 (점령 배너 외에는 외부인 파괴 차단)
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        ChunkPos chunkPos = new ChunkPos(pos);

        if (!TeamEvents.recruitsClaimManager.isChunkClaimed(chunkPos)) {
            return; // 미점령 청크는 자유롭게 파괴
        }

        String ownerTeam = TeamEvents.recruitsClaimManager.getTeamAt(chunkPos);
        boolean isOwner = player.getTeam() != null && player.getTeam().getName().equals(ownerTeam);

        BlockEntity be = serverLevel.getBlockEntity(pos);
        boolean isClaimBanner = false;

        if (be instanceof BannerBlockEntity bannerBE) {
            CompoundTag tag = bannerBE.getPersistentData();
            if (tag.contains("RecruitsClaimTeam")) {
                isClaimBanner = true;
            }
        }

        // 점령 거점 깃발을 부순 경우: 아군이든 적군이든 영토 해제 진행
        if (isClaimBanner) {
            TeamEvents.recruitsClaimManager.unclaimChunk(serverLevel, chunkPos, ownerTeam);
            if (isOwner) {
                player.sendSystemMessage(Component.literal("영토 점령 배너를 회수하여 점령을 해제했습니다.").withStyle(ChatFormatting.GOLD));
            } else {
                player.sendSystemMessage(Component.literal("적의 영토 배너를 파괴하여 점령을 무력화했습니다!").withStyle(ChatFormatting.RED));
            }
            return;
        }

        // 점령 배너가 아닌 일반 블록: 아군이 아니면 파괴 불가
        if (!isOwner && !player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("타 파벌의 영토에서는 블록을 파괴할 수 없습니다! (거점 깃발만 파괴 가능)").withStyle(ChatFormatting.RED));
            event.setCanceled(true);
        }
    }

    /**
     * 2. 30분 주기 영토 유지비 자동 징수 & 체납 시 무작위 영토 강제 해제
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

                int requiredUpkeep = chunks.size();
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
                        removeClaimBannerInChunk(overworld, lostChunk, teamId);
                        TeamEvents.recruitsClaimManager.unclaimChunk(overworld, lostChunk, teamId);
                    }

                    notifyTeam(event.getServer(), teamId, Component.literal("[경고] 금고 잔액 부족으로 영토 유지비 체납! " + lostChunks.size() + "개의 영토가 무작위로 강제 몰수되었습니다.").withStyle(ChatFormatting.RED));
                }
            }

            TeamEvents.recruitsTeamManager.save(overworld);
        }
    }

    private static void removeClaimBannerInChunk(ServerLevel level, ChunkPos chunkPos, String teamId) {
        if (!level.hasChunk(chunkPos.x, chunkPos.z)) return;
        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);

        List<BlockPos> bannerPositions = new ArrayList<>();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof BannerBlockEntity bannerBE) {
                CompoundTag tag = bannerBE.getPersistentData();
                if (tag.contains("RecruitsClaimTeam") && tag.getString("RecruitsClaimTeam").equals(teamId)) {
                    bannerPositions.add(be.getBlockPos());
                }
            }
        }

        for (BlockPos bPos : bannerPositions) {
            level.setBlock(bPos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void notifyTeam(net.minecraft.server.MinecraftServer server, String teamId, Component msg) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getTeam() != null && player.getTeam().getName().equals(teamId)) {
                player.sendSystemMessage(msg);
            }
        }
    }

    private static boolean isTeamBannerByName(BannerBlockEntity bannerBE, RecruitsTeam team, String teamName) {
        if (!bannerBE.hasCustomName()) return false;
        String bannerCustomName = bannerBE.getCustomName().getString().trim();
        if (bannerCustomName.equalsIgnoreCase(teamName.trim())) return true;
        String displayName = team.getTeamDisplayName();
        return displayName != null && bannerCustomName.equalsIgnoreCase(displayName.trim());
    }
}