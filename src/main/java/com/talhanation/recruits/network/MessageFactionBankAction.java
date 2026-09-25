package com.talhanation.recruits.network;

import com.talhanation.recruits.TeamEvents;
import com.talhanation.recruits.world.RecruitsTeam;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;

public class MessageFactionBankAction implements Message<MessageFactionBankAction> {

    private String teamName;
    private boolean isDeposit; // true: 입금, false: 출금
    private int amount;

    public MessageFactionBankAction() {}

    public MessageFactionBankAction(String teamName, boolean isDeposit, int amount) {
        this.teamName = teamName;
        this.isDeposit = isDeposit;
        this.amount = amount;
    }

    @Override
    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    @Override
    public void executeServerSide(NetworkEvent.Context context) {
        ServerPlayer player = Objects.requireNonNull(context.getSender());
        context.enqueueWork(() -> {
            RecruitsTeam team = TeamEvents.recruitsTeamManager.getTeamByStringID(this.teamName);
            if (team == null) return;

            ItemStack currency = TeamEvents.getCurrency();

            if (this.isDeposit) {
                // 1. 입금 로직: 지갑 검사 후 차감 및 금고 적립
                if (TeamEvents.playerHasEnoughEmeralds(player, this.amount)) {
                    TeamEvents.doPayment(player, this.amount);
                    team.deposit(this.amount);
                    TeamEvents.recruitsTeamManager.save(player.serverLevel());
                    TeamEvents.openTeamEditScreen(player); // GUI 동기화 갱신
                    player.sendSystemMessage(Component.literal("파벌 금고에 " + this.amount + "개를 입금했습니다.").withStyle(ChatFormatting.GREEN));
                } else {
                    player.sendSystemMessage(Component.literal("인벤토리에 충분한 화폐가 없습니다.").withStyle(ChatFormatting.RED));
                }
            } else {
                // 2. 출금 로직: 리더 권한 검사 및 잔고 차감 후 아이템 지급
                if (!team.getTeamLeaderUUID().equals(player.getUUID())) {
                    player.sendSystemMessage(Component.literal("출금 권한이 없습니다. (파벌 리더 전용)").withStyle(ChatFormatting.RED));
                    return;
                }

                if (team.withdraw(this.amount)) {
                    ItemStack payout = currency.copy();
                    payout.setCount(this.amount);
                    if (!player.getInventory().add(payout)) {
                        player.drop(payout, false);
                    }
                    TeamEvents.recruitsTeamManager.save(player.serverLevel());
                    TeamEvents.openTeamEditScreen(player); // GUI 동기화 갱신
                    player.sendSystemMessage(Component.literal("파벌 금고에서 " + this.amount + "개를 출금했습니다.").withStyle(ChatFormatting.GOLD));
                } else {
                    player.sendSystemMessage(Component.literal("파벌 금고 잔고가 부족합니다.").withStyle(ChatFormatting.RED));
                }
            }
        });
    }

    @Override
    public MessageFactionBankAction fromBytes(FriendlyByteBuf buf) {
        this.teamName = buf.readUtf();
        this.isDeposit = buf.readBoolean();
        this.amount = buf.readInt();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.teamName);
        buf.writeBoolean(this.isDeposit);
        buf.writeInt(this.amount);
    }
}