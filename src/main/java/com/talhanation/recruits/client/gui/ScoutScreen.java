package com.talhanation.recruits.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.talhanation.recruits.Main;
import com.talhanation.recruits.client.gui.component.ActivateableButton;
import com.talhanation.recruits.entities.ScoutEntity;
import com.talhanation.recruits.network.MessageScoutTask;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public class ScoutScreen extends RecruitsScreenBase {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Main.MOD_ID, "textures/gui/gui_big.png");
    private static final Component TITLE = Component.translatable("gui.recruits.more_screen.title");
    
    private final Player player;
    private final ScoutEntity scout;
    
    // [수정] State 대신 ScoutMode 사용
    private ScoutEntity.ScoutMode task;

    private static final MutableComponent SCOUTING = Component.translatable("gui.recruits.inv.text.scoutScoutTask");
    private static final MutableComponent TOOLTIP_SCOUTING = Component.translatable("gui.recruits.inv.tooltip.scoutScoutTask");

    private static final Component ADVANCED_SCOUTING = Component.literal("Long Range Scout");
    private static final Component TOOLTIP_ADVANCED = Component.literal("Scouts area 128-200 blocks away.");
    
    private static final Component COMMANDING = Component.literal("Strategic Command");
    private static final Component TOOLTIP_COMMAND = Component.literal("Detects enemies & Orders artillery fire.");

    private ActivateableButton buttonScouting;
    private ActivateableButton buttonAdvanced;
    private ActivateableButton buttonCommand;

    public ScoutScreen(ScoutEntity scout, Player player) {
        super(TITLE, 195,160);
        this.player = player;
        this.scout = scout;
    }

    @Override
    protected void init() {
        super.init();
        // [수정] ScoutMode로 변환하여 가져옴
        this.task = ScoutEntity.ScoutMode.fromIndex(scout.getTaskState());
        setButtons();
    }

	private void setButtons(){
        clearWidgets();
        
        int startY = guiTop + 40;
        int buttonHeight = 20;
        int spacing = 24;
        
        // 현재 레벨 실시간 확인
        int currentLevel = this.scout.getXpLevel();

        // 1. Scouting (기본 정찰) - 레벨 6 제한
        boolean canScout = currentLevel >= 6;
        
        buttonScouting = new ActivateableButton(guiLeft + 32, startY, 130, buttonHeight, SCOUTING,
            btn -> {
                // [안전장치] 클릭했을 때 레벨을 한 번 더 체크!
                if (this.scout.getXpLevel() >= 6) {
                    toggleTask(ScoutEntity.ScoutMode.SCOUTING);
                }
            }
        );
        buttonScouting.active = canScout; // 시각적 비활성화
        
        if (canScout) {
            buttonScouting.setTooltip(Tooltip.create(TOOLTIP_SCOUTING));
        } else {
            buttonScouting.setTooltip(Tooltip.create(Component.literal("Requires Level 6").withStyle(ChatFormatting.RED)));
        }
        addRenderableWidget(buttonScouting);

        // 2. Advanced Scouting (장거리 정찰) - 레벨 12 제한
        boolean canAdvanced = currentLevel >= 12;

        buttonAdvanced = new ActivateableButton(guiLeft + 32, startY + spacing, 130, buttonHeight, ADVANCED_SCOUTING,
            btn -> {
                // [안전장치] 레벨 12 미만이면 클릭 무시
                if (this.scout.getXpLevel() >= 12) {
                    toggleTask(ScoutEntity.ScoutMode.ADVANCED_SCOUTING);
                }
            }
        );
        buttonAdvanced.active = canAdvanced;
        
        if (canAdvanced) {
            buttonAdvanced.setTooltip(Tooltip.create(TOOLTIP_ADVANCED));
        } else {
            buttonAdvanced.setTooltip(Tooltip.create(Component.literal("Requires Level 12").withStyle(ChatFormatting.RED)));
        }
        addRenderableWidget(buttonAdvanced);

        // 3. Commanding (전략 지휘) - 레벨 18 제한
        boolean canCommand = currentLevel >= 18;

        buttonCommand = new ActivateableButton(guiLeft + 32, startY + spacing * 2, 130, buttonHeight, COMMANDING,
            btn -> {
                // [안전장치] 레벨 18 미만이면 클릭 무시
                if (this.scout.getXpLevel() >= 18) {
                    toggleTask(ScoutEntity.ScoutMode.COMMANDING);
                }
            }
        );
        buttonCommand.active = canCommand;

        if (canCommand) {
            buttonCommand.setTooltip(Tooltip.create(TOOLTIP_COMMAND));
        } else {
            buttonCommand.setTooltip(Tooltip.create(Component.literal("Requires Level 18").withStyle(ChatFormatting.RED)));
        }
        addRenderableWidget(buttonCommand);
    }

    private void toggleTask(ScoutEntity.ScoutMode targetState) {
        if(this.scout != null) {
            if(task == targetState){
                task = ScoutEntity.ScoutMode.IDLE;
                // 0번 인덱스 전송
                Main.SIMPLE_CHANNEL.sendToServer(new MessageScoutTask(scout.getUUID(), 0));
            }
            else{
                task = targetState;
                // 해당 모드의 인덱스 전송
                Main.SIMPLE_CHANNEL.sendToServer(new MessageScoutTask(scout.getUUID(), targetState.getIndex()));
            }
            // 상태 변경 후 버튼 갱신 (선택 상태 등을 반영하려면 여기서 추가 로직 필요할 수 있음)
            setButtons(); 
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.setShaderTexture(0, TEXTURE);
        guiGraphics.blit(TEXTURE, guiLeft, guiTop, 0, 0, xSize, ySize);
    }

    @Override
    public void renderForeground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        if(task != null){
            String text = "";
            ChatFormatting color = ChatFormatting.WHITE;

            if(task == ScoutEntity.ScoutMode.IDLE) {
                text = "Status: Idle";
                color = ChatFormatting.GRAY;
            }
            else if(task == ScoutEntity.ScoutMode.SCOUTING) {
                text = "Status: Scouting";
                color = ChatFormatting.GREEN;
            }
            else if(task == ScoutEntity.ScoutMode.ADVANCED_SCOUTING) {
                text = "Status: Long Range";
                color = ChatFormatting.AQUA;
            }
            else if(task == ScoutEntity.ScoutMode.COMMANDING) {
                text = "Status: Commanding";
                color = ChatFormatting.RED;
            }
            else {
                text = "Active Task: " + task.name();
            }
            
            // 텍스트 중앙 정렬 및 색상 적용
            MutableComponent statusText = Component.literal(text).withStyle(color);
            guiGraphics.drawString(font, statusText, guiLeft + xSize / 2 - font.width(text) / 2, guiTop + 17, FONT_COLOR, false);
        }
    }
}