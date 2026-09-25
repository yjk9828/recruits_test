package com.talhanation.recruits.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.talhanation.recruits.world.PillagerPatrolSpawn;
import com.talhanation.recruits.world.RecruitsPatrolSpawn;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.monster.Pillager;

public class PatrolSpawnCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 1. 루트는 누구나 통과할 수 있도록 requires 제거[cite: 38]
        LiteralArgumentBuilder<CommandSourceStack> literalBuilder = Commands.literal("recruits");

        // 2. 관리자 전용인 "spawn"에 OP 2레벨 권한 부여[cite: 38]
        literalBuilder.then(Commands.literal("spawn").requires((source) -> source.hasPermission(2))
                        .then(Commands.literal("pillagerPatrol")
                                .then(Commands.literal("tiny").executes( (commandSource) -> {
                                    PillagerPatrolSpawn.spawnPillagerPatrol(commandSource.getSource().getEntity().getOnPos().above(), commandSource.getSource().getEntity().getOnPos(), commandSource.getSource().getLevel());
                                    return 0;
                                }))
                                .then(Commands.literal("small").executes( (commandSource) -> {
                                    PillagerPatrolSpawn.spawnSmallPillagerPatrol(commandSource.getSource().getEntity().getOnPos().above(), commandSource.getSource().getEntity().getOnPos(), commandSource.getSource().getLevel());
                                    return 0;
                                }))
                                .then(Commands.literal("medium").executes( (commandSource) -> {
                                    PillagerPatrolSpawn.spawnMediumPillagerPatrol(commandSource.getSource().getEntity().getOnPos().above(), commandSource.getSource().getEntity().getOnPos(), commandSource.getSource().getLevel());
                                    return 0;
                                }))
                                .then(Commands.literal("large").executes( (commandSource) -> {
                                    PillagerPatrolSpawn.spawnLargePillagerPatrol(commandSource.getSource().getEntity().getOnPos().above(), commandSource.getSource().getEntity().getOnPos(), commandSource.getSource().getLevel());
                                    return 0;
                                }))
                        )
                        .then(Commands.literal("recruitPatrol")
                            .then(Commands.literal("tiny").executes( (commandSource) -> {
                                RecruitsPatrolSpawn.spawnTinyPatrol(commandSource.getSource().getEntity().getOnPos().above(), commandSource.getSource().getLevel());
                                return 0;
                            }))
                            .then(Commands.literal("small").executes( (commandSource) -> {
                                RecruitsPatrolSpawn.spawnSmallPatrol(commandSource.getSource().getEntity().getOnPos().above(), commandSource.getSource().getLevel());
                                return 0;
                            }))
                            .then(Commands.literal("medium").executes( (commandSource) -> {
                                RecruitsPatrolSpawn.spawnMediumPatrol(commandSource.getSource().getEntity().getOnPos().above(), commandSource.getSource().getLevel());
                                return 0;
                            }))
                            .then(Commands.literal("large").executes( (commandSource) -> {
                                RecruitsPatrolSpawn.spawnLargePatrol(commandSource.getSource().getEntity().getOnPos().above(), commandSource.getSource().getLevel());
                                return 0;
                            }))
                            .then(Commands.literal("huge").executes( (commandSource) -> {
                                RecruitsPatrolSpawn.spawnHugePatrol(commandSource.getSource().getEntity().getOnPos().above(), commandSource.getSource().getLevel());
                                return 0;
                            }))
                            .then(Commands.literal("caravan").executes( (commandSource) -> {
                                RecruitsPatrolSpawn.spawnCaravan(commandSource.getSource().getEntity().getOnPos().above(), commandSource.getSource().getLevel());
                                return 0;
                            }))
                        )
        );

        dispatcher.register(literalBuilder);
    }
}