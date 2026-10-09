package com.talhanation.recruits.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.TeamEvents;
import com.talhanation.recruits.world.RecruitsTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

public class RecruitsClaimCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 일반 유저도 사용 가능하도록 루트 requires 제거
        LiteralArgumentBuilder<CommandSourceStack> claimCommand = Commands.literal("recruits")
            .then(Commands.literal("claim")
                // 1. 점령 청크 수 및 예상 유지비 확인 (/recruits claim list)
                .then(Commands.literal("list")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayer();
                        if (player == null) return 0;
                        if (player.getTeam() == null) {
                            context.getSource().sendFailure(Component.literal("소속된 파벌이 없습니다.").withStyle(ChatFormatting.RED));
                            return 0;
                        }

                        String teamName = player.getTeam().getName();
                        RecruitsTeam team = TeamEvents.recruitsTeamManager.getTeamByStringID(teamName);
                        if (team == null) return 0;

                        List<ChunkPos> claimedChunks = TeamEvents.recruitsClaimManager.getChunksClaimedBy(teamName);
                        int count = claimedChunks.size();
                        int upkeepCost = count; // 청크당 1G

                        context.getSource().sendSuccess(() ->
                                Component.literal("=== [" + team.getTeamDisplayName() + "] 영토 점령 현황 ===")
                                        .withStyle(ChatFormatting.GOLD), false);

                        context.getSource().sendSuccess(() ->
                                Component.literal("- 점령 중인 청크 수: " + count + "개")
                                        .withStyle(ChatFormatting.AQUA), false);

                        context.getSource().sendSuccess(() ->
                                Component.literal("- 30분당 소모 유지비: " + upkeepCost + "G (금고 잔액: " + team.getBalance() + "G)")
                                        .withStyle(ChatFormatting.YELLOW), false);

                        return count;
                    })
                )
                // 2. 단축 명령어 (/recruits claim count)
                .then(Commands.literal("count")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayer();
                        if (player == null) return 0;
                        if (player.getTeam() == null) {
                            context.getSource().sendFailure(Component.literal("소속된 파벌이 없습니다.").withStyle(ChatFormatting.RED));
                            return 0;
                        }

                        String teamName = player.getTeam().getName();
                        RecruitsTeam team = TeamEvents.recruitsTeamManager.getTeamByStringID(teamName);
                        if (team == null) return 0;

                        int count = TeamEvents.recruitsClaimManager.getChunksClaimedBy(teamName).size();
                        context.getSource().sendSuccess(() ->
                                Component.literal("[" + team.getTeamDisplayName() + "] 현재 점령 청크: " + count + "개")
                                        .withStyle(ChatFormatting.GREEN), false);
                        return count;
                    })
                )
            );

        dispatcher.register(claimCommand);
    }
}