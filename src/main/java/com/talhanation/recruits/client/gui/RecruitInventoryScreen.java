package com.talhanation.recruits.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.talhanation.recruits.Main;
import com.talhanation.recruits.RecruitEvents;
import com.talhanation.recruits.client.gui.group.RecruitsGroup;
import com.talhanation.recruits.compat.SmallShips;
import com.talhanation.recruits.compat.workers.IVillagerWorker;
import com.talhanation.recruits.entities.*;
import com.talhanation.recruits.inventory.RecruitInventoryMenu;
import com.talhanation.recruits.network.*;
import de.maxhenkel.corelib.inventory.ScreenBase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.widget.ExtendedButton;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@OnlyIn(Dist.CLIENT)
public class RecruitInventoryScreen extends ScreenBase<RecruitInventoryMenu> {
    private static final ResourceLocation RESOURCE_LOCATION = new ResourceLocation(Main.MOD_ID,"textures/gui/recruit_gui.png" );

    private static final MutableComponent TEXT_HEALTH = Component.translatable("gui.recruits.inv.health");
    private static final MutableComponent TEXT_LEVEL = Component.translatable("gui.recruits.inv.level");
    private static final MutableComponent TEXT_KILLS = Component.translatable("gui.recruits.inv.kills");
    private static final MutableComponent TEXT_DISBAND = Component.translatable("gui.recruits.inv.text.disband");
    private static final MutableComponent TEXT_INFO_FOLLOW = Component.translatable("gui.recruits.inv.info.text.follow");
    private static final MutableComponent TEXT_INFO_WANDER = Component.translatable("gui.recruits.inv.info.text.wander");
    private static final MutableComponent TEXT_INFO_HOLD_POS = Component.translatable("gui.recruits.inv.info.text.hold_pos");
    private static final MutableComponent TEXT_INFO_PASSIVE = Component.translatable("gui.recruits.inv.info.text.passive");
    private static final MutableComponent TEXT_INFO_NEUTRAL = Component.translatable("gui.recruits.inv.info.text.neutral");
    private static final MutableComponent TEXT_INFO_AGGRESSIVE = Component.translatable("gui.recruits.inv.info.text.aggressive");
    private static final MutableComponent TEXT_INFO_LISTEN = Component.translatable("gui.recruits.inv.info.text.listen");
    private static final MutableComponent TEXT_INFO_IGNORE = Component.translatable("gui.recruits.inv.info.text.ignore");
    private static final MutableComponent TEXT_INFO_RAID = Component.translatable("gui.recruits.inv.info.text.raid");
    private static final MutableComponent TEXT_INFO_PROTECT = Component.translatable("gui.recruits.inv.info.text.protect");
    private static final MutableComponent TEXT_INFO_WORKING = Component.translatable("gui.recruits.inv.info.text.working");
    private static final MutableComponent TEXT_DISMOUNT = Component.translatable("gui.recruits.inv.text.dismount");
    private static final MutableComponent TEXT_BACK_TO_MOUNT = Component.translatable("gui.recruits.inv.text.backToMount");
    private static final MutableComponent TOOLTIP_DISMOUNT = Component.translatable("gui.recruits.inv.tooltip.dismount");
    private static final MutableComponent TOOLTIP_FOLLOW = Component.translatable("gui.recruits.inv.tooltip.follow");
    private static final MutableComponent TOOLTIP_WANDER = Component.translatable("gui.recruits.inv.tooltip.wander");
    private static final MutableComponent TOOLTIP_HOLD_MY_POS = Component.translatable("gui.recruits.inv.tooltip.holdMyPos");
    private static final MutableComponent TOOLTIP_HOLD_POS = Component.translatable("gui.recruits.inv.tooltip.holdPos");
    private static final MutableComponent TOOLTIP_BACK_TO_POS = Component.translatable("gui.recruits.inv.tooltip.backToPos");
    private static final MutableComponent TOOLTIP_CLEAR_TARGET = Component.translatable("gui.recruits.inv.tooltip.clearTargets");
    private static final MutableComponent TOOLTIP_MOUNT = Component.translatable("gui.recruits.inv.tooltip.mount");
    private static final MutableComponent TOOLTIP_PASSIVE = Component.translatable("gui.recruits.inv.tooltip.passive");
    private static final MutableComponent TOOLTIP_NEUTRAL = Component.translatable("gui.recruits.inv.tooltip.neutral");
    private static final MutableComponent TOOLTIP_AGGRESSIVE = Component.translatable("gui.recruits.inv.tooltip.aggressive");
    private static final MutableComponent TOOLTIP_RAID = Component.translatable("gui.recruits.inv.tooltip.raid");
    private static final MutableComponent TOOLTIP_BACK_TO_MOUNT = Component.translatable("gui.recruits.inv.tooltip.backToMount");
    private static final MutableComponent TOOLTIP_CLEAR_UPKEEP = Component.translatable("gui.recruits.inv.tooltip.clearUpkeep");
    private static final MutableComponent TEXT_FOLLOW = Component.translatable("gui.recruits.inv.text.follow");
    private static final MutableComponent TEXT_WANDER = Component.translatable("gui.recruits.inv.text.wander");
    private static final MutableComponent TEXT_HOLD_MY_POS = Component.translatable("gui.recruits.inv.text.holdMyPos");
    private static final MutableComponent TEXT_HOLD_POS = Component.translatable("gui.recruits.inv.text.holdPos");
    private static final MutableComponent TEXT_BACK_TO_POS = Component.translatable("gui.recruits.inv.text.backToPos");
    private static final MutableComponent TEXT_PASSIVE = Component.translatable("gui.recruits.inv.text.passive");
    private static final MutableComponent TEXT_NEUTRAL = Component.translatable("gui.recruits.inv.text.neutral");
    private static final MutableComponent TEXT_AGGRESSIVE = Component.translatable("gui.recruits.inv.text.aggressive");
    private static final MutableComponent TEXT_RAID = Component.translatable("gui.recruits.inv.text.raid");
    private static final MutableComponent TEXT_CLEAR_TARGET = Component.translatable("gui.recruits.inv.text.clearTargets");
    private static final MutableComponent TEXT_MOUNT = Component.translatable("gui.recruits.command.text.mount");
    private static final MutableComponent TEXT_CLEAR_UPKEEP = Component.translatable("gui.recruits.inv.text.clearUpkeep");

    private static final MutableComponent TEXT_PROMOTE = Component.translatable("gui.recruits.inv.text.promote");
    private static final MutableComponent TEXT_SPECIAL = Component.translatable("gui.recruits.inv.text.special");
    private static final MutableComponent TOOLTIP_PROMOTE = Component.translatable("gui.recruits.inv.tooltip.promote");
    private static final MutableComponent TOOLTIP_DISABLED_PROMOTE = Component.translatable("gui.recruits.inv.tooltip.promote_disabled");
    private static final MutableComponent TOOLTIP_SPECIAL = Component.translatable("gui.recruits.inv.tooltip.special");
    private static final int fontColor = 4210752;
    private final AbstractRecruitEntity recruit;
    private final Inventory playerInventory;
    public static List<RecruitsGroup> groups;
    private RecruitsGroup currentGroup;
    private int follow;
    private int aggro;
    private Button clearUpkeep;
    private boolean canPromote;
    private boolean buttonsSet;

    // Search related fields
    private EditBox groupSearchField;
    private List<RecruitsGroup> suggestionList = new ArrayList<>();
    private boolean showSuggestions = false;

    public RecruitInventoryScreen(RecruitInventoryMenu recruitContainer, Inventory playerInventory, Component title) {
        super(RESOURCE_LOCATION, recruitContainer, playerInventory, Component.literal(""));
        this.recruit = recruitContainer.getRecruit();
        this.playerInventory = playerInventory;
        imageWidth = 176;
        imageHeight = 223;
    }

    @Override
    protected void init() {
        super.init();

        int zeroLeftPos = leftPos + 180;
        int zeroTopPos = topPos + 10;
        int topPosGab = 5;
        this.canPromote = this.recruit.getXpLevel() >= 3;

        this.clearWidgets();
        //PASSIVE
        ExtendedButton buttonPassive = new ExtendedButton(zeroLeftPos - 270, zeroTopPos + (20 + topPosGab) * 0, 80, 20, TEXT_PASSIVE,
            button -> {
                this.aggro = recruit.getState();
                if (this.aggro != 3) {
                    this.aggro = 3;
                    Main.SIMPLE_CHANNEL.sendToServer(new MessageAggroGui(aggro, recruit.getUUID()));
                }
            });
        buttonPassive.setTooltip(Tooltip.create(TOOLTIP_PASSIVE));
        addRenderableWidget(buttonPassive);

        // NEUTRAL
        ExtendedButton buttonNeutral = new ExtendedButton(zeroLeftPos - 270, zeroTopPos + (20 + topPosGab) * 1, 80, 20, TEXT_NEUTRAL,
                button -> {
                    this.aggro = recruit.getState();
                    if (this.aggro != 0) {
                        this.aggro = 0;
                        Main.SIMPLE_CHANNEL.sendToServer(new MessageAggroGui(aggro, recruit.getUUID()));
                    }
                });
        buttonNeutral.setTooltip(Tooltip.create(TOOLTIP_NEUTRAL));
        addRenderableWidget(buttonNeutral);

        //AGGRESSIVE
        ExtendedButton buttonAggressive = new ExtendedButton(zeroLeftPos - 270, zeroTopPos + (20 + topPosGab) * 2, 80, 20, TEXT_AGGRESSIVE,
                button -> {
                    this.aggro = recruit.getState();
                    if (this.aggro != 1) {
                        this.aggro = 1;
                        Main.SIMPLE_CHANNEL.sendToServer(new MessageAggroGui(aggro, recruit.getUUID()));
                    }
                });
        buttonAggressive.setTooltip(Tooltip.create(TOOLTIP_AGGRESSIVE));
        addRenderableWidget(buttonAggressive);

        //RAID
        ExtendedButton buttonRaid = new ExtendedButton(zeroLeftPos - 270, zeroTopPos + (20 + topPosGab) * 3, 80, 20, TEXT_RAID,
                button -> {
                    this.aggro = recruit.getState();
                    if (this.aggro != 2) {
                        this.aggro = 2;
                        Main.SIMPLE_CHANNEL.sendToServer(new MessageAggroGui(aggro, recruit.getUUID()));
                    }
                });
        buttonRaid.setTooltip(Tooltip.create(TOOLTIP_RAID));
        addRenderableWidget(buttonRaid);

        //CLEAR TARGET
        ExtendedButton buttonClearTarget = new ExtendedButton(zeroLeftPos - 270, zeroTopPos + (20 + topPosGab) * 4, 80, 20, TEXT_CLEAR_TARGET,
                button -> {
                    Main.SIMPLE_CHANNEL.sendToServer(new MessageClearTargetGui(playerInventory.player.getUUID(), recruit.getUUID()));
                });
        buttonClearTarget.setTooltip(Tooltip.create(TOOLTIP_CLEAR_TARGET));
        addRenderableWidget(buttonClearTarget);


        //MOUNT
        ExtendedButton buttonMount =  new ExtendedButton(zeroLeftPos - 270, zeroTopPos + (20 + topPosGab) * 5, 80, 20, TEXT_MOUNT,
            button -> {
                Main.SIMPLE_CHANNEL.sendToServer(new MessageMountEntityGui(recruit.getUUID(), false));
            }
            );
        buttonMount.setTooltip(Tooltip.create(TOOLTIP_MOUNT));
        addRenderableWidget(buttonMount);


        //WANDER
        ExtendedButton buttonWander = new ExtendedButton(zeroLeftPos, zeroTopPos + (20 + topPosGab) * 0, 80, 20, TEXT_WANDER,
                button -> {
                    this.follow = recruit.getFollowState();
                    if (this.follow != 0) {
                        this.follow = 0;
                        Main.SIMPLE_CHANNEL.sendToServer(new MessageFollowGui(follow, recruit.getUUID()));
                    }
                });
        buttonWander.setTooltip(Tooltip.create(TOOLTIP_WANDER));
        addRenderableWidget(buttonWander);


        //FOLLOW
        ExtendedButton buttonFollow = new ExtendedButton(zeroLeftPos, zeroTopPos + (20 + topPosGab) * 1, 80, 20, TEXT_FOLLOW,
                button -> {
                    this.follow = recruit.getFollowState();
                    if (this.follow != 1) {
                        this.follow = 1;
                        Main.SIMPLE_CHANNEL.sendToServer(new MessageFollowGui(follow, recruit.getUUID()));
                    }
                });
        buttonFollow.setTooltip(Tooltip.create(TOOLTIP_FOLLOW));
        addRenderableWidget(buttonFollow);


        //HOLD POS
        ExtendedButton buttonHoldPos = new ExtendedButton(zeroLeftPos, zeroTopPos + (20 + topPosGab) * 2, 80, 20, TEXT_HOLD_POS,
                button -> {
                    this.follow = recruit.getFollowState();
                    if (this.follow != 2) {
                        this.follow = 2;
                        Main.SIMPLE_CHANNEL.sendToServer(new MessageFollowGui(follow, recruit.getUUID()));
                    }
                });
        buttonHoldPos.setTooltip(Tooltip.create(TOOLTIP_HOLD_POS));
        addRenderableWidget(buttonHoldPos);


        //BACK TO POS
        ExtendedButton buttonBackToPos = new ExtendedButton(zeroLeftPos, zeroTopPos + (20 + topPosGab) * 3, 80, 20, TEXT_BACK_TO_POS,
                button -> {
                    this.follow = recruit.getFollowState();
                    if (this.follow != 3) {
                        this.follow = 3;
                        Main.SIMPLE_CHANNEL.sendToServer(new MessageFollowGui(follow, recruit.getUUID()));
                    }
                });
        buttonBackToPos.setTooltip(Tooltip.create(TOOLTIP_BACK_TO_POS));
        addRenderableWidget(buttonBackToPos);


        //HOLD MY POS
        ExtendedButton buttonHoldMyPos = new ExtendedButton(zeroLeftPos, zeroTopPos + (20 + topPosGab) * 4, 80, 20, TEXT_HOLD_MY_POS,
                button -> {
                    this.follow = recruit.getFollowState();
                    if (this.follow != 4) {
                        this.follow = 4;
                        Main.SIMPLE_CHANNEL.sendToServer(new MessageFollowGui(follow, recruit.getUUID()));
                    }
                });
        buttonHoldMyPos.setTooltip(Tooltip.create(TOOLTIP_HOLD_MY_POS));
        addRenderableWidget(buttonHoldMyPos);

        //Dismount
        ExtendedButton buttonDismount = new ExtendedButton(zeroLeftPos, zeroTopPos + (20 + topPosGab) * 5, 80, 20, TEXT_DISMOUNT,
                button -> {
                    this.follow = recruit.getFollowState();
                    if (this.follow != 4) {
                        this.follow = 4;
                        Main.SIMPLE_CHANNEL.sendToServer(new MessageDismountGui(playerInventory.player.getUUID(), recruit.getUUID()));
                    }
                });
        buttonDismount.setTooltip(Tooltip.create(TOOLTIP_DISMOUNT));
        addRenderableWidget(buttonDismount);

        //BACK TO MOUNT
        ExtendedButton backToMount = addRenderableWidget(new ExtendedButton(zeroLeftPos, zeroTopPos + (20 + topPosGab) * 6, 80, 20, TEXT_BACK_TO_MOUNT,
                button -> {
                    Main.SIMPLE_CHANNEL.sendToServer(new MessageMountEntityGui(recruit.getUUID(), true));
                }
        ));
        backToMount.setTooltip(Tooltip.create(TOOLTIP_BACK_TO_MOUNT));
        addRenderableWidget(backToMount);

		String btnText = recruit.isOnlyPvP() ? "ONLY P: ON" : "ONLY P: OFF";
			int textColor = recruit.isOnlyPvP() ? 0x00FF00 : 0xFF0000; // ON이면 초록, OFF면 빨강 느낌 (텍스트 색상은 버튼 내부 구현에 따라 다를 수 있음)

			ExtendedButton buttonOnlyPvP = new ExtendedButton(zeroLeftPos - 270, zeroTopPos + (20 + topPosGab) * 6, 80, 20, Component.literal(btnText),
				button -> {
					// 1. 현재 상태 반전
					boolean newState = !recruit.isOnlyPvP();
					
					// 2. 클라이언트 측 즉시 반영 (반응성을 위해)
					recruit.setOnlyPvP(newState);
					
					// 3. 버튼 텍스트 업데이트
					button.setMessage(Component.literal(newState ? "ONLY P: ON" : "ONLY P: OFF"));
					
					// 4. 서버로 패킷 전송
					Main.SIMPLE_CHANNEL.sendToServer(new MessageTogglePvP(recruit.getUUID(), newState));
				}
			);
			
			// 툴팁 설정 (설명 추가)
			buttonOnlyPvP.setTooltip(Tooltip.create(Component.literal("ON: Attack Players/Recruits Only\nOFF: Attack All Hostiles")));
			
			addRenderableWidget(buttonOnlyPvP);

        //LISTEN
        addRenderableWidget(new ExtendedButton(leftPos + 77, topPos + 100, 12, 12, Component.literal("<"), button -> {
            Main.SIMPLE_CHANNEL.sendToServer(new MessageListen(!recruit.getListen(), recruit.getUUID()));
        }));

        addRenderableWidget(new ExtendedButton(leftPos + 77 + 81, topPos + 100, 12, 12, Component.literal(">"), button -> {
            Main.SIMPLE_CHANNEL.sendToServer(new MessageListen(!recruit.getListen(), recruit.getUUID()));
        }));

        //more (Disband)
        addRenderableWidget(new ExtendedButton(leftPos + 77 + 55, topPos + 4, 40, 12, Component.literal("..."),
                button -> {
                    minecraft.setScreen(new DisbandScreen(this, this.recruit, this.playerInventory.player));
                }
        ));

        // Promote / Rank / Special 버튼 로직
        if(recruit instanceof ICompanion || recruit instanceof IVillagerWorker){
            // 1. Special 버튼 (기존 유지, 8번 슬롯)
            Button specialButton = addRenderableWidget(new ExtendedButton(zeroLeftPos, zeroTopPos + (20 + topPosGab) * 8, 80, 20, TEXT_SPECIAL,
                    button -> {
                        if(recruit instanceof ScoutEntity scout){
                            this.minecraft.setScreen(new ScoutScreen(scout, getMinecraft().player));
                            return;
                        }
                        else if(recruit instanceof MessengerEntity messenger){
                            this.minecraft.setScreen(new MessengerScreen(messenger, getMinecraft().player));
                            return;
                        }
                        else if(recruit instanceof IVillagerWorker worker && worker.hasOnlyScreen()){
                            this.minecraft.setScreen(worker.getSpecialScreen(recruit, getMinecraft().player));
                            return;
                        }
                        Main.SIMPLE_CHANNEL.sendToServer(new MessageOpenSpecialScreen(this.playerInventory.player, recruit.getUUID()));
                        this.onClose();
                    }
            ));

            specialButton.setTooltip(Tooltip.create(TOOLTIP_SPECIAL));
            specialButton.active = canPromote;

            // 2. [추가] "Rank" 버튼 (7번 슬롯) - 진급 화면 열기
            addRenderableWidget(new ExtendedButton(zeroLeftPos, zeroTopPos + (20 + topPosGab) * 7, 80, 20, Component.literal("Rank"),
                    button -> {
                        RecruitEvents.openPromoteScreen(this.playerInventory.player, this.recruit);
                        this.onClose();
                    }
            ));

        }
        else {
            // 기본 병과일 경우 Promote 버튼만 표시 (8번 슬롯)
            Button promoteButton = addRenderableWidget(new ExtendedButton(zeroLeftPos, zeroTopPos + (20 + topPosGab) * 8, 80, 20, TEXT_PROMOTE,
                    button -> {
                        RecruitEvents.openPromoteScreen(this.playerInventory.player, this.recruit);
                        this.onClose();
                    }
            ));
            promoteButton.setTooltip(Tooltip.create(canPromote ? TOOLTIP_PROMOTE : TOOLTIP_DISABLED_PROMOTE));
            promoteButton.active = canPromote;
        }

        // Group Search Field Initialization
        this.groupSearchField = new EditBox(this.font, leftPos + 77, topPos + 114, 93, 12, Component.literal("Group"));
        this.groupSearchField.setResponder(this::onSearchInput);
        
        // 소유자가 아니면 수정 불가
        boolean isOwner = Minecraft.getInstance().player.getUUID().equals(recruit.getOwnerUUID());
        this.groupSearchField.setEditable(isOwner);
        this.groupSearchField.setVisible(isOwner);
        
        this.addRenderableWidget(this.groupSearchField);
    }

    // Search Input Handler
    private void onSearchInput(String text) {
        if (groups == null || text.isEmpty()) {
            this.showSuggestions = false;
            return;
        }

        String query = text.toLowerCase(Locale.ROOT);
        this.suggestionList = groups.stream()
                .filter(g -> g.getName().toLowerCase(Locale.ROOT).contains(query))
                .limit(5)
                .collect(Collectors.toList());

        this.showSuggestions = !suggestionList.isEmpty() && this.groupSearchField.isFocused();
    }
	

    @Override
    protected void containerTick() {
        super.containerTick();
        // Wait for groups to be loaded from server
        if(groups != null && !groups.isEmpty() && !buttonsSet){
			// 이름순으로 정렬
			groups.sort(Comparator.comparing(RecruitsGroup::getName));
			this.currentGroup = getCurrentGroup(recruit.getGroupUUID());
            
            // Update text box with current group name
            if (this.currentGroup != null) {
                this.groupSearchField.setValue(this.currentGroup.getName());
            }
            
            this.buttonsSet = true;
        }
        
        if (this.groupSearchField != null) {
            this.groupSearchField.tick();
        }
    }

	private RecruitsGroup getCurrentGroup(UUID targetUUID) {
		if (groups == null || targetUUID == null) return null;
		for (RecruitsGroup recruitsGroup : groups) {
			if (targetUUID.equals(recruitsGroup.getUUID())) {
				return recruitsGroup;
			}
		}
		return null;
	}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle suggestion clicks
        if (showSuggestions && groupSearchField.isFocused()) {
            int startX = groupSearchField.getX();
            int startY = groupSearchField.getY() + groupSearchField.getHeight();
            int width = groupSearchField.getWidth();
            int itemHeight = 12;
            int totalHeight = suggestionList.size() * itemHeight;

            if (mouseX >= startX && mouseX < startX + width && mouseY >= startY && mouseY < startY + totalHeight) {
                int index = (int) ((mouseY - startY) / itemHeight);
                if (index >= 0 && index < suggestionList.size()) {
                    RecruitsGroup selected = suggestionList.get(index);
                    this.currentGroup = selected;
                    this.groupSearchField.setValue(selected.getName());
                    this.showSuggestions = false;
                    this.setFocused(null); // Remove focus
                    
                    // Send packet
                    Main.SIMPLE_CHANNEL.sendToServer(new MessageGroup(currentGroup.getUUID(), recruit.getUUID()));
                    return true;
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        // Render Suggestions
        if (showSuggestions && groupSearchField.isFocused()) {
            renderSuggestions(guiGraphics, mouseX, mouseY);
        }
    }

    // Suggestion Rendering Logic
    private void renderSuggestions(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startX = groupSearchField.getX();
        int startY = groupSearchField.getY() + groupSearchField.getHeight();
        int width = groupSearchField.getWidth();
        int itemHeight = 12;
        int totalHeight = suggestionList.size() * itemHeight;

        // Background
        guiGraphics.fill(startX, startY, startX + width, startY + totalHeight, 0xFF000000);
        guiGraphics.renderOutline(startX, startY, width, totalHeight, 0xFFAAAAAA);

        for (int i = 0; i < suggestionList.size(); i++) {
            RecruitsGroup group = suggestionList.get(i);
            int itemY = startY + (i * itemHeight);
            
            boolean isHovered = (mouseX >= startX && mouseX < startX + width && mouseY >= itemY && mouseY < itemY + itemHeight);
            
            if (isHovered) {
                guiGraphics.fill(startX + 1, itemY, startX + width - 1, itemY + itemHeight, 0xFF333333);
            }

            guiGraphics.drawString(this.font, group.getName(), startX + 4, itemY + 2, 0xFFFFFFFF, false);
        }
    }

    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);
        int health = Mth.ceil(recruit.getHealth());
        int hunger = Mth.ceil(recruit.getHunger());
        int moral = Mth.ceil(recruit.getMorale());
        this.follow = recruit.getFollowState();
        this.aggro = recruit.getState();

        int k = 79;//rechst links
        int l = 19;//höhe

        //Titles

        guiGraphics.drawString(font, recruit.getDisplayName().getVisualOrderText(), 8, 5, fontColor, false);
        guiGraphics.drawString(font, playerInventory.getDisplayName().getVisualOrderText(), 8, this.imageHeight - 96 + 2, fontColor, false);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(0.7F, 0.7F, 1F);


        k = 112;//rechst links
        l = 32;//höhe
        int gap = 42;
        //Info


        guiGraphics.drawString(font, "Health:", k, l, fontColor, false);
        guiGraphics.drawString(font, "" + health, k + gap, l, fontColor, false);
        guiGraphics.drawString(font, "Lvl.:", k, l + 10, fontColor, false);
        guiGraphics.drawString(font, "" + recruit.getXpLevel(), k + gap, l + 10, fontColor, false);
        guiGraphics.drawString(font, "Exp.:", k, l + 20, fontColor, false);
        guiGraphics.drawString(font, "" + recruit.getXp(), k + gap, l + 20, fontColor, false);
        guiGraphics.drawString(font, "Kills:", k, l + 30, fontColor, false);
        guiGraphics.drawString(font, "" + recruit.getKills(), k + gap, l + 30, fontColor, false);
        guiGraphics.drawString(font, "Morale:", k, l + 40, fontColor, false);
        guiGraphics.drawString(font, "" + moral, k + gap, l + 40, fontColor, false);
        guiGraphics.drawString(font, "Hunger:", k, l + 50, fontColor, false);
        guiGraphics.drawString(font, "" + hunger, k + gap, l + 50, fontColor, false);
        guiGraphics.pose().popPose();

        k = 79;//rechst links
        l = 19;//höhe
        // command
        String follow = switch (this.follow) {
            case 0 -> TEXT_INFO_WANDER.getString();
            case 1 -> TEXT_INFO_FOLLOW.getString();
            case 2, 3, 4 -> TEXT_INFO_HOLD_POS.getString();
            case 5 -> TEXT_INFO_PROTECT.getString();
            case 6 -> TEXT_INFO_WORKING.getString();
            default -> throw new IllegalStateException("Unexpected value: " + this.follow);
        };
        guiGraphics.drawString(font, follow, k + 15, l + 58 + 0, fontColor, false);


        String aggro = switch (this.aggro) {
            case 0 -> TEXT_INFO_NEUTRAL.getString();
            case 1 -> TEXT_INFO_AGGRESSIVE.getString();
            case 2 -> TEXT_INFO_RAID.getString();
            case 3 -> TEXT_INFO_PASSIVE.getString();
            default -> throw new IllegalStateException("Unexpected value: " + this.aggro);
        };

        int fnt = this.aggro == 3 ? 16733525 : fontColor;
        guiGraphics.drawString(font, aggro, k + 15, l + 56 + 15, fnt, false);

        String listen;
        if (recruit.getListen()) listen = TEXT_INFO_LISTEN.getString();
        else listen = TEXT_INFO_IGNORE.getString();

        int fnt2 = recruit.getListen() ? fontColor : 16733525;
        guiGraphics.drawString(font, listen, k + 15, l + 56 + 28, fnt2, false);

        ItemStack profItem1 = null;
        ItemStack profItem2 = null;
        if(this.recruit instanceof HorsemanEntity){
            profItem1 = Items.IRON_SWORD.getDefaultInstance();
            profItem2 = Items.SADDLE.getDefaultInstance();
        }
        else if(this.recruit instanceof NomadEntity){
            profItem1 = Items.BOW.getDefaultInstance();
            profItem2 = Items.SADDLE.getDefaultInstance();
        }
        else if(this.recruit instanceof RecruitShieldmanEntity){
            profItem1 = Items.IRON_SWORD.getDefaultInstance();
            profItem2 = Items.SHIELD.getDefaultInstance();
        }
        else if(this.recruit instanceof RecruitEntity){
            profItem1 = Items.IRON_SWORD.getDefaultInstance();
        }
        else if(this.recruit instanceof BowmanEntity){
            profItem1 = Items.BOW.getDefaultInstance();
        }
        else if(this.recruit instanceof CrossBowmanEntity){
            profItem1 = Items.CROSSBOW.getDefaultInstance();
        }
        else if(this.recruit instanceof MessengerEntity){
            profItem1 = Items.FEATHER.getDefaultInstance();
            profItem2 = Items.PAPER.getDefaultInstance();
        }
        else if(this.recruit instanceof PatrolLeaderEntity){
            profItem1 = Items.IRON_SWORD.getDefaultInstance();
            profItem2 = Items.GOAT_HORN.getDefaultInstance();
        }
        else if(this.recruit instanceof CaptainEntity){
            profItem1 = SmallShips.getSmallShipsItem();
        }
        else if (this instanceof IVillagerWorker worker){
            profItem1 = worker.getCustomProfessionItem();
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(0.8F, 0.8F, 1F);

        if(profItem2 != null){
            guiGraphics.renderFakeItem(profItem2, 90, 4);
        }

        if(profItem1 != null){
            guiGraphics.renderFakeItem(profItem1, 80, 4);
        }
        guiGraphics.pose().popPose();
    }

    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        super.renderBg(guiGraphics, partialTicks, mouseX, mouseY);

        RenderSystem.clearColor(1.0F, 1.0F, 1.0F, 1.0F);
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;

        InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics, i + 50, j + 82, 30, (float)(i + 50) - mouseX, (float)(j + 75 - 50) - mouseY, this.recruit);
    }
}