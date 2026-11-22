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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

        ItemStack currencyStack = TeamEvents.getCurrency();
        Item currencyItem = currencyStack.getItem();
        boolean isEmeraldCurrency = (currencyItem == Items.EMERALD);

        // 1. 인벤토리 내 화폐 및 에메랄드 블록 보유량 계산
        Inventory inv = player.getInventory();
        int looseCount = 0;
        int blockCount = 0;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() == currencyItem) {
                looseCount += stack.getCount();
            } else if (isEmeraldCurrency && stack.getItem() == Items.EMERALD_BLOCK) {
                blockCount += stack.getCount();
            }
        }

        int totalValue = looseCount + (blockCount * 9);
        if (totalValue < amount) {
            source.sendFailure(Component.literal("인벤토리에 충분한 화폐가 없습니다. (보유: " + totalValue + "G / 필요: " + amount + "G)")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // 2. 낱개 화폐 우선 차감 후 부족분은 에메랄드 블록(1개당 9G)으로 차감
        int looseToConsume = Math.min(looseCount, amount);
        int remaining = amount - looseToConsume;
        int blocksToConsume = (remaining > 0) ? (int) Math.ceil(remaining / 9.0) : 0;
        int changeToReturn = (blocksToConsume * 9) - remaining;

        consumeItem(inv, currencyItem, looseToConsume);
        if (blocksToConsume > 0) {
            consumeItem(inv, Items.EMERALD_BLOCK, blocksToConsume);
        }

        // 3. 블록 단위 차감 후 남은 거스름돈 지급
        if (changeToReturn > 0) {
            ItemStack changeStack = new ItemStack(currencyItem, changeToReturn);
            if (!inv.add(changeStack)) {
                player.drop(changeStack, false);
            }
        }

        team.deposit(amount);
        TeamEvents.recruitsTeamManager.save(player.serverLevel());

        source.sendSuccess(() ->
                Component.literal("[" + team.getTeamDisplayName() + "] 금고에 " + amount + "G를 입금했습니다. (총 잔액: " + team.getBalance() + "G)")
                        .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static void consumeItem(Inventory inv, Item targetItem, int countToRemove) {
        int left = countToRemove;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (left <= 0) break;
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == targetItem) {
                int take = Math.min(stack.getCount(), left);
                stack.shrink(take);
                left -= take;
            }
        }
    }
}