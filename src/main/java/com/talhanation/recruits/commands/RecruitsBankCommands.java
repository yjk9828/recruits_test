package com.talhanation.recruits.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.talhanation.recruits.TeamEvents;
import com.talhanation.recruits.world.RecruitsTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.TeamArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;

public class RecruitsBankCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // .requires() 검사를 넣지 않아야 OP 없는 일반 플레이어도 명령어 사용 가능!
        LiteralArgumentBuilder<CommandSourceStack> bankCommand = Commands.literal("recruits")
            .then(Commands.literal("bank")
                // 1. 잔액 확인 (/recruits bank balance)
                .then(Commands.literal("balance")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayer();
                        if (player == null) return 0;
                        if (player.getTeam() == null) {
                            context.getSource().sendFailure(Component.literal("소속된 파벌이 없습니다.").withStyle(ChatFormatting.RED));
                            return 0;
                        }
                        RecruitsTeam team = TeamEvents.recruitsTeamManager.getTeamByStringID(player.getTeam().getName());
                        if (team == null) return 0;

                        context.getSource().sendSuccess(() ->
                                Component.literal("[" + team.getTeamDisplayName() + "] 파벌 금고 잔액: " + team.getBalance() + "G")
                                        .withStyle(ChatFormatting.GOLD), false);
                        return 1;
                    })
                )
                // 2. 내 파벌 입금 (/recruits bank deposit <수량>)
                .then(Commands.literal("deposit")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayer();
                            if (player == null) return 0;
                            if (player.getTeam() == null) {
                                context.getSource().sendFailure(Component.literal("소속된 파벌이 없습니다.").withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            int amount = IntegerArgumentType.getInteger(context, "amount");
                            return executeDeposit(context.getSource(), player, player.getTeam().getName(), amount);
                        })
                    )
                    // 3. 타 파벌 송금/입금 (/recruits bank deposit <파벌> <수량>)
                    .then(Commands.argument("targetFaction", TeamArgument.team())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(context -> {
                                ServerPlayer player = context.getSource().getPlayer();
                                if (player == null) return 0;
                                PlayerTeam targetTeam = TeamArgument.getTeam(context, "targetFaction");
                                int amount = IntegerArgumentType.getInteger(context, "amount");
                                return executeDeposit(context.getSource(), player, targetTeam.getName(), amount);
                            })
                        )
                    )
                )
                // 4. 출금 (/recruits bank withdraw <수량> - 리더 전용)
                .then(Commands.literal("withdraw")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayer();
                            if (player == null) return 0;
                            if (player.getTeam() == null) {
                                context.getSource().sendFailure(Component.literal("소속된 파벌이 없습니다.").withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            int amount = IntegerArgumentType.getInteger(context, "amount");
                            RecruitsTeam team = TeamEvents.recruitsTeamManager.getTeamByStringID(player.getTeam().getName());
                            if (team == null) return 0;

                            // 리더 권한 검사 (자체 로직으로 차단)
                            if (!team.getTeamLeaderUUID().equals(player.getUUID())) {
                                context.getSource().sendFailure(Component.literal("출금 권한이 없습니다. (파벌 리더만 가능)").withStyle(ChatFormatting.RED));
                                return 0;
                            }

                            if (team.withdraw(amount)) {
                                ItemStack payout = TeamEvents.getCurrency().copy();
                                payout.setCount(amount);
                                if (!player.getInventory().add(payout)) {
                                    player.drop(payout, false);
                                }
                                TeamEvents.recruitsTeamManager.save(player.serverLevel());
                                context.getSource().sendSuccess(() ->
                                        Component.literal("파벌 금고에서 " + amount + "G를 출금했습니다. (남은 잔액: " + team.getBalance() + "G)")
                                                .withStyle(ChatFormatting.GOLD), false);
                                return 1;
                            } else {
                                context.getSource().sendFailure(Component.literal("파벌 금고 잔액이 부족합니다. (현재: " + team.getBalance() + "G)").withStyle(ChatFormatting.RED));
                                return 0;
                            }
                        })
                    )
                )
            );

        dispatcher.register(bankCommand);
    }

    private static int executeDeposit(CommandSourceStack source, ServerPlayer player, String teamName, int amount) {
        RecruitsTeam team = TeamEvents.recruitsTeamManager.getTeamByStringID(teamName);
        if (team == null) {
            source.sendFailure(Component.literal("해당 파벌을 찾을 수 없습니다.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (TeamEvents.playerHasEnoughEmeralds(player, amount)) {
            TeamEvents.doPayment(player, amount);
            team.deposit(amount);
            TeamEvents.recruitsTeamManager.save(player.serverLevel());
            source.sendSuccess(() ->
                    Component.literal("[" + team.getTeamDisplayName() + "] 금고에 " + amount + "G를 입금했습니다. (총 잔액: " + team.getBalance() + "G)")
                            .withStyle(ChatFormatting.GREEN), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("인벤토리에 충분한 화폐가 없습니다.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }
}