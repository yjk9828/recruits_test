package com.talhanation.recruits.client.gui;

import com.talhanation.recruits.Main;
import com.talhanation.recruits.client.events.ClientEvent;
import com.talhanation.recruits.client.events.CommandCategoryManager;
import com.talhanation.recruits.client.gui.commandscreen.ICommandCategory;
import com.talhanation.recruits.client.gui.group.*;
import com.talhanation.recruits.client.gui.widgets.CascadingGroupWidget;
import com.talhanation.recruits.config.RecruitsClientConfig;
import com.talhanation.recruits.inventory.CommandMenu;
import com.talhanation.recruits.network.*;
import com.talhanation.recruits.util.GroupHierarchyUtil;
import de.maxhenkel.corelib.inventory.ScreenBase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.widget.ExtendedButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Set; // [FIX] 추가됨
import java.util.HashSet; // [FIX] 추가됨


@OnlyIn(Dist.CLIENT)
public class CommandScreen extends ScreenBase<CommandMenu> {

    private static final ResourceLocation RESOURCE_LOCATION = new ResourceLocation(Main.MOD_ID, "textures/gui/command_gui.png");
    private static final MutableComponent TEXT_EVERYONE = Component.translatable("gui.recruits.command.text.everyone");
    private static final int fontColor = 16250871;
    public final Player player;
    public BlockPos rayBlockPos;
    public Entity rayEntity;
    private ICommandCategory currentCategory;
    public static List<RecruitsGroup> groups;
    public static Formation formation;

    //private CascadingGroupWidget groupSelector;
    //private RecruitsGroup selectedGroup; // [중요] 1버튼 방식 변수 유지
		// [추가] 2개의 그룹 변수와 위젯 변수 선언
	private RecruitsGroup selectedGroupMain; // 주 그룹
	private RecruitsGroup selectedGroupSub;  // 보조 그룹

	private CascadingGroupWidget groupSelectorMain;
	private CascadingGroupWidget groupSelectorSub;

    public CommandScreen(CommandMenu commandContainer, Inventory playerInventory, Component title) {
        super(RESOURCE_LOCATION, commandContainer, playerInventory, Component.literal(""));
        player = playerInventory.player;
    }
    @Override
    public boolean keyReleased(int x, int y, int z) {
        super.keyReleased(x, y, z);
        if(!RecruitsClientConfig.CommandScreenToggle.get()) this.onClose();
        return true;
    }

    public <T extends GuiEventListener & Renderable & NarratableEntry> @NotNull T addRenderableWidget(@NotNull T widget) {
        this.renderables.add(widget);
        return this.addWidget(widget);
    }

    @Override
    public void onClose() {
        super.onClose();
        this.saveGroups();
        this.saveCategoryOnClient();
        groups = new ArrayList<>();
    }

    @Override
    protected void init() {
        super.init();
        this.rayBlockPos = getBlockPos();
        this.rayEntity = ClientEvent.getEntityByLooking();
        this.currentCategory = getSelectionFromClient();
        formation = getSavedFormationFromClient();
        
        this.loadSelectedGroupFromClient();
    }

    private boolean buttonsSet = false;

    @Override
    protected void containerTick() {
        super.containerTick();
        if(!buttonsSet){
            this.setButtons();
            this.saveGroups();
            this.buttonsSet = true;
        }
    }

    private void saveGroups() {
        if(groups != null && !groups.isEmpty()){
            Main.SIMPLE_CHANNEL.sendToServer(new MessageServerSavePlayerGroups(groups, true));
        }
    }

	private void setButtons(){
        int x = this.width / 2;
        int y = this.height / 2;

        // 1. 위젯 초기화
        clearWidgets();
        
        // [안전장치] 혹시 모를 리스트 참조 오류 방지를 위해 초기화 (필드에 groupButtons가 있다면 필수)
        // groupButtons = new ArrayList<>(); 

        // ==========================================================
        // [LAYER 1: 하단 레이어] 배경에 깔릴 버튼들 먼저 생성
        // ==========================================================

        // 1-1. 그룹 관리(...) 버튼
        createManageGroupsButton(0, x - 210, y - 80);

        // 1-2. 하단 카테고리 탭 생성 (칼, 부츠, 상자 아이콘)
        createCategoryButtons(x, y);

        // 1-3. 중앙 명령 버튼들 생성 (Move, Forward 등)
        // 이 버튼들이 먼저 만들어져야 콤보박스가 펼쳐졌을 때 그 아래에 깔립니다.
        if (currentCategory != null) {
            currentCategory.createButtons(this, x, y, groups, player);
        }

        // ==========================================================
        // [LAYER 2: 상단 레이어] 그룹 선택기 (콤보박스)
        // 나중에 add할수록 화면의 맨 위에 그려집니다.
        // ==========================================================
        if(groups != null){
            GroupHierarchyUtil.relinkGroups(groups);

            // [2-1] 서브 그룹 선택기 (중간 우선순위)
            // 메인 콤보박스보다는 아래여야 하므로 먼저 추가합니다.
            this.groupSelectorSub = new CascadingGroupWidget(
                    x - 210, y - 105, 110, 20, groups,
                    (group) -> {
                        this.selectedGroupSub = group;
                        this.updateSelectorText();
                    }
            );
            addRenderableWidget(this.groupSelectorSub);

            // 서브 그룹 X 버튼
            addRenderableWidget(new ExtendedButton(x - 95, y - 105, 20, 20, Component.literal("X"), btn -> {
                this.selectedGroupSub = null;
                this.updateSelectorText();
            }));

            // [2-2] 메인 그룹 선택기 (최상위 우선순위)
            // 가장 마지막에 추가되므로 화면의 '가장 맨 위'에 그려집니다.
            // 펼치면 서브 그룹 선택기나 명령 버튼들을 모두 덮습니다.
            this.groupSelectorMain = new CascadingGroupWidget(
                    x - 210, y - 130, 110, 20, groups,
                    (group) -> {
                        this.selectedGroupMain = group;
                        this.updateSelectorText();
                    }
            );
            addRenderableWidget(this.groupSelectorMain);

            // 메인 그룹 X 버튼
            addRenderableWidget(new ExtendedButton(x - 95, y - 130, 20, 20, Component.literal("X"), btn -> {
                this.selectedGroupMain = null;
                this.updateSelectorText();
            }));

            // 텍스트 초기화
            this.updateSelectorText();
        }
    }

    private void createManageGroupsButton(int index, int x, int y){
        ExtendedButton groupButton = new ExtendedButton(x, y, 20, 20, Component.literal("..."),
                button -> {
                    minecraft.setScreen(new RecruitsGroupListScreen(player));

                });
        addRenderableWidget(groupButton);
    }

    private void setCurrentCategory(ICommandCategory currentCategory){
        this.currentCategory = currentCategory;
        this.saveCategoryOnClient();
        this.setButtons();
    }

    public void setFormation(Formation f){
        formation = f;
        this.saveFormationSelection();
        this.setButtons();
    }

    private ICommandCategory getSelectionFromClient() {
        CompoundTag playerNBT = player.getPersistentData();
        CompoundTag nbt = playerNBT.getCompound(Player.PERSISTED_NBT_TAG);

        byte x = nbt.getByte("RecruitsCategory");
        return CommandCategoryManager.getByIndex(x);
    }
    
	private void saveCategoryOnClient() {
		CompoundTag playerNBT = player.getPersistentData();
		CompoundTag nbt = playerNBT.getCompound(Player.PERSISTED_NBT_TAG);

		nbt.putInt("RecruitsCategory", CommandCategoryManager.getCategories().indexOf(currentCategory));

		// [수정] 메인과 서브 그룹 ID를 각각 저장 (-1은 선택 안 함)
		int mainId = (this.selectedGroupMain != null) ? this.selectedGroupMain.getId() : -1;
		int subId = (this.selectedGroupSub != null) ? this.selectedGroupSub.getId() : -1;

		nbt.putInt("LastSelectedGroupID_Main", mainId);
		nbt.putInt("LastSelectedGroupID_Sub", subId);

		playerNBT.put(Player.PERSISTED_NBT_TAG, nbt);
	}
    
	private void loadSelectedGroupFromClient() {
		if (groups == null) return;

		CompoundTag playerNBT = player.getPersistentData();
		CompoundTag nbt = playerNBT.getCompound(Player.PERSISTED_NBT_TAG);

		// [수정] 메인 그룹 로드
		if (nbt.contains("LastSelectedGroupID_Main")) {
			int savedId = nbt.getInt("LastSelectedGroupID_Main");
			if (savedId != -1) {
				this.selectedGroupMain = groups.stream().filter(g -> g.getId() == savedId).findFirst().orElse(null);
			}
		}
		
		// [추가] 서브 그룹 로드
		if (nbt.contains("LastSelectedGroupID_Sub")) {
			int savedId = nbt.getInt("LastSelectedGroupID_Sub");
			if (savedId != -1) {
				this.selectedGroupSub = groups.stream().filter(g -> g.getId() == savedId).findFirst().orElse(null);
			}
		}
	}

    private void createCategoryButtons(int centerX, int centerY) {
        List<ICommandCategory> allCategories = CommandCategoryManager.getCategories();
        int spacing = 21;
        int count = allCategories.size();

        int totalWidth = (count - 1) * spacing;
        int startX = centerX - totalWidth / 2;

        int buttonY = centerY + 85;

        for (int i = 0; i < count; i++) {
            ICommandCategory category = allCategories.get(i);
            int x = startX + i * spacing;

            RecruitsCategoryButton button = new RecruitsCategoryButton(
                    category.getIcon(), x, buttonY, Component.literal(""),
                    press -> this.setCurrentCategory(category)
            );

            button.setTooltip(Tooltip.create(category.getToolTipName()));
            button.active = category == this.currentCategory;
            addRenderableWidget(button);
        }
    }

	public List<Integer> getSelectedGroupIds() {
		Set<Integer> targetIds = new HashSet<>(); // Set이라서 중복 ID는 자동 제거됨

		boolean mainSelected = (this.selectedGroupMain != null);
		boolean subSelected = (this.selectedGroupSub != null);

		// 1. 메인 그룹이 선택되어 있으면 ID 수집
		if (mainSelected) {
			targetIds.addAll(GroupHierarchyUtil.getTargetGroupIds(this.selectedGroupMain, groups));
		}

		// 2. 서브 그룹이 선택되어 있으면 ID 수집 (메인과 별도로 추가)
		if (subSelected) {
			targetIds.addAll(GroupHierarchyUtil.getTargetGroupIds(this.selectedGroupSub, groups));
		}

		// 3. 둘 다 선택 안 되어 있으면(null, null) -> 전체(Everyone) 선택
		if (!mainSelected && !subSelected) {
			if (groups != null) {
				targetIds.addAll(groups.stream().map(RecruitsGroup::getId).collect(Collectors.toList()));
			}
		}

		return new ArrayList<>(targetIds);
	}

    public void sendMovementCommandToServer(int state) {
        if(state != 1){
            Main.SIMPLE_CHANNEL.sendToServer(new MessageSaveFormationFollowMovement(player.getUUID(), new int[]{}, -1));
        }

        // [MODIFIED] 공통 메서드 사용
        List<Integer> targetIds = getSelectedGroupIds();

        if(!groups.isEmpty() && !targetIds.isEmpty()){
            // [MODIFIED] 배열로 변환하여 전송
            int[] idsArray = targetIds.stream().mapToInt(i -> i).toArray();
            Main.SIMPLE_CHANNEL.sendToServer(new MessageMovement(player.getUUID(), state, idsArray, formation.getIndex()));
        }
    }

    public Formation getSavedFormationFromClient() {
        CompoundTag playerNBT = player.getPersistentData();
        CompoundTag nbt = playerNBT.getCompound(Player.PERSISTED_NBT_TAG);

        return Formation.fromIndex((byte) nbt.getInt("FormationSelection"));
    }

    public void saveFormationSelection() {
        CompoundTag playerNBT = player.getPersistentData();
        CompoundTag nbt = playerNBT.getCompound(Player.PERSISTED_NBT_TAG);

        nbt.putByte("FormationSelection", formation.getIndex());
        playerNBT.put(Player.PERSISTED_NBT_TAG, nbt);
    }

	public void sendCommandInChat(int state){
        StringBuilder groupNameBuilder = new StringBuilder();
        boolean mainSelected = (this.selectedGroupMain != null);
        boolean subSelected = (this.selectedGroupSub != null);

        // 메인 그룹 이름 추가
        if (mainSelected) {
            groupNameBuilder.append(this.selectedGroupMain.getName());
            if (this.selectedGroupMain.hasChildren()) {
                groupNameBuilder.append("+");
            }
        }

        // 서브 그룹 이름 추가 (메인이 있으면 콤마로 구분)
        if (subSelected) {
            if (groupNameBuilder.length() > 0) {
                groupNameBuilder.append(", ");
            }
            groupNameBuilder.append(this.selectedGroupSub.getName());
            if (this.selectedGroupSub.hasChildren()) {
                groupNameBuilder.append("+");
            }
        }

        // 둘 다 없으면 "Everyone"
        if (groupNameBuilder.length() == 0) {
            groupNameBuilder.append(TEXT_EVERYONE.getString());
        }
        
        String displayString = groupNameBuilder.toString(); 

        switch (state) {
            case 0 -> this.player.sendSystemMessage(TEXT_WANDER(displayString));
            case 1 -> this.player.sendSystemMessage(TEXT_FOLLOW(displayString));
            case 2 -> this.player.sendSystemMessage(TEXT_HOLD_POS(displayString));
            case 3 -> this.player.sendSystemMessage(TEXT_BACK_TO_POS(displayString));
            case 4 -> this.player.sendSystemMessage(TEXT_HOLD_MY_POS(displayString));
            case 5 -> this.player.sendSystemMessage(TEXT_PROTECT(displayString));
            case 6 -> this.player.sendSystemMessage(TEXT_MOVE(displayString));
            case 7 -> this.player.sendSystemMessage(TEXT_FORWARD(displayString));
            case 8 -> this.player.sendSystemMessage(TEXT_BACKWARD(displayString));
            case 9 -> this.player.sendSystemMessage(TEXT_CLEAR_TARGETS(displayString));

            case 10 -> this.player.sendSystemMessage(TEXT_NEUTRAL(displayString));
            case 11 -> this.player.sendSystemMessage(TEXT_AGGRESSIVE(displayString));
            case 12 -> this.player.sendSystemMessage(TEXT_RAID(displayString));
            case 13 -> this.player.sendSystemMessage(TEXT_PASSIVE(displayString));

            case 70 -> this.player.sendSystemMessage(TEXT_FIRE_AT_WILL(displayString));
            case 71 -> this.player.sendSystemMessage(TEXT_HOLD_FIRE(displayString));
            case 72 -> this.player.sendSystemMessage(TEXT_STRATEGIC_FIRE(displayString));
            case 73 -> this.player.sendSystemMessage(TEXT_STRATEGIC_FIRE_OFF(displayString));
            case 74 -> this.player.sendSystemMessage(TEXT_SHIELDS(displayString));
            case 75 -> this.player.sendSystemMessage(TEXT_SHIELDS_OFF(displayString));

            case 88 -> this.player.sendSystemMessage(TEXT_REST(displayString));
            case 91 -> this.player.sendSystemMessage(TEXT_BACK_TO_MOUNT(displayString));
            case 92 -> this.player.sendSystemMessage(TEXT_UPKEEP(displayString));
            case 93 -> this.player.sendSystemMessage(TEXT_CLEAR_UPKEEP(displayString));

            case 98 -> this.player.sendSystemMessage(TEXT_DISMOUNT(displayString));
            case 99 -> this.player.sendSystemMessage(TEXT_MOUNT(displayString));
        }
    }
    
    private static MutableComponent TEXT_WANDER(String group_string) {
        return Component.translatable("chat.recruits.command.wander", group_string);
    }

    private static MutableComponent TEXT_FOLLOW(String group_string) {
        return Component.translatable("chat.recruits.command.follow", group_string);
    }

    private static MutableComponent TEXT_HOLD_POS(String group_string) {
        return Component.translatable("chat.recruits.command.holdPos", group_string);
    }

    private static MutableComponent TEXT_BACK_TO_POS(String group_string) {
        return Component.translatable("chat.recruits.command.backToPos", group_string);
    }

    private static MutableComponent TEXT_BACK_TO_MOUNT(String group_string) {
        return Component.translatable("chat.recruits.command.backToMount", group_string);
    }

    private static MutableComponent TEXT_REST(String group_string) {
        return Component.translatable("chat.recruits.command.rest", group_string);
    }
    private static MutableComponent TEXT_HOLD_MY_POS(String group_string) {
        return Component.translatable("chat.recruits.command.holdMyPos", group_string);
    }

    private static MutableComponent TEXT_PROTECT(String group_string) {
        return Component.translatable("chat.recruits.command.protect", group_string);
    }

    private static MutableComponent TEXT_UPKEEP(String group_string) {
        return Component.translatable("chat.recruits.command.upkeep", group_string);
    }
    private static MutableComponent TEXT_CLEAR_UPKEEP(String group_string) {
        return Component.translatable("chat.recruits.command.clear_upkeep", group_string);
    }
    private static MutableComponent TEXT_SHIELDS_OFF(String group_string) {
        return Component.translatable("chat.recruits.command.shields_off", group_string);
    }

    private static MutableComponent TEXT_STRATEGIC_FIRE_OFF(String group_string) {
        return Component.translatable("chat.recruits.command.strategic_fire_off", group_string);
    }

    private static MutableComponent TEXT_SHIELDS(String group_string) {
        return Component.translatable("chat.recruits.command.shields", group_string);
    }

    private static MutableComponent TEXT_STRATEGIC_FIRE(String group_string) {
        return Component.translatable("chat.recruits.command.strategic_fire", group_string);
    }

    private static MutableComponent TEXT_MOVE(String group_string) {
        return Component.translatable("chat.recruits.command.move", group_string);
    }

    private static MutableComponent TEXT_FORWARD(String group_string) {
        return Component.translatable("chat.recruits.command.forward", group_string);
    }
    private static MutableComponent TEXT_BACKWARD(String group_string) {
        return Component.translatable("chat.recruits.command.backward", group_string);
    }

    private static MutableComponent TEXT_CLEAR_TARGETS(String group_string) {
        return Component.translatable("chat.recruits.command.clearTargets", group_string);
    }
    private static MutableComponent TEXT_DISMOUNT(String group_string) {
        return Component.translatable("chat.recruits.command.dismount", group_string);
    }

    private static MutableComponent TEXT_MOUNT(String group_string) {
        return Component.translatable("chat.recruits.command.mount", group_string);
    }

    private static MutableComponent TEXT_PASSIVE(String group_string) {
        return Component.translatable("chat.recruits.command.passive", group_string);
    }

    private static MutableComponent TEXT_RAID(String group_string) {
        return Component.translatable("chat.recruits.command.raid", group_string);
    }

    private static MutableComponent TEXT_AGGRESSIVE(String group_string) {
        return Component.translatable("chat.recruits.command.aggressive", group_string);
    }

    private static MutableComponent TEXT_NEUTRAL(String group_string) {
        return Component.translatable("chat.recruits.command.neutral", group_string);
    }

    private static MutableComponent TEXT_SHIELDS_UP(String group_string) {
        return Component.translatable("chat.recruits.command.shields", group_string);
    }

    private static MutableComponent TEXT_SHIELDS_DOWN(String group_string) {
        return Component.translatable("chat.recruits.command.shields_off", group_string);
    }

    private static MutableComponent TEXT_FIRE_AT_WILL(String group_string) {
        return Component.translatable("chat.recruits.command.fire_at_will", group_string);
    }

    private static MutableComponent TEXT_HOLD_FIRE(String group_string) {
        return Component.translatable("chat.recruits.command.hold_fire", group_string);
    }

    private static MutableComponent TEXT_SELECT_ALL_GROUPS() {
        return Component.translatable("gui.recruits.command.tip.de_select_groups");
    }
    private static MutableComponent TEXT_SCROLL_CATEGORIES() {
        return Component.translatable("gui.recruits.command.tip.scrollCategories");
    }

    int xTipPos = 140;
    int yTipPos = 157;
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);

        String tipAllGroups = TEXT_SELECT_ALL_GROUPS().getString();
        String tipScroll = TEXT_SCROLL_CATEGORIES().getString();
        guiGraphics.drawString(font, tipAllGroups, xTipPos, yTipPos, FONT_COLOR, false);
        guiGraphics.drawString(font, tipScroll, xTipPos, yTipPos + 15, FONT_COLOR, false);

    }

    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        super.renderBg(guiGraphics, partialTicks, mouseX, mouseY);
    }

    @Nullable
    private BlockPos getBlockPos(){
        HitResult rayTraceResult = player.pick(175, 1F, true);
        if (rayTraceResult != null) {
            if (rayTraceResult.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockraytraceresult = (BlockHitResult) rayTraceResult;

                return blockraytraceresult.getBlockPos();
            }
        }
        return null;
    }

	@Override
    public boolean mouseClicked(double x, double y, int id) {
        // [우선순위 1] 메인 그룹 선택기 먼저 체크
        if (this.groupSelectorMain != null && this.groupSelectorMain.mouseClicked(x, y, id)) {
            return true; // 메인 콤보박스가 클릭을 처리했으면 여기서 끝냄 (뒤에꺼 클릭 안됨)
        }

        // [우선순위 2] 서브 그룹 선택기 체크
        if (this.groupSelectorSub != null && this.groupSelectorSub.mouseClicked(x, y, id)) {
            return true; // 서브 콤보박스가 처리했으면 끝냄
        }

        // [우선순위 3] 나머지 버튼들 (X 버튼, 명령 버튼 등)
        return super.mouseClicked(x, y, id);
    }

    @Override
    public boolean mouseScrolled(double p_94686_, double p_94687_, double p_94688_) {
        if(p_94688_ > 0){
            this.setCurrentCategory(CommandCategoryManager.getPrevious(currentCategory));
        }
        else{
            this.setCurrentCategory(CommandCategoryManager.getNext(currentCategory));
        }

        return super.mouseScrolled(p_94686_, p_94687_, p_94688_);
    }

    @OnlyIn(Dist.CLIENT)
    public enum Formation {
        NONE((byte) 0),
        LINE((byte) 1),
        SQUARE((byte) 2),
        TRIANGLE((byte) 3),
        HCIRCLE((byte) 4),
        HSQUARE((byte) 5),
        VFORM((byte) 6),
        CIRCLE((byte) 7),
        MOVEMENT((byte) 8);

        private final byte index;

        Formation(byte index) {
            this.index = index;
        }

        public byte getIndex() {
            return this.index;
        }


        public static Formation fromIndex(byte index) {
            for (Formation state : Formation.values()) {
                if (state.getIndex() == index) {
                    return state;
                }
            }
            throw new IllegalArgumentException("Invalid Selection index: " + index);
        }
    }
	private void updateSelectorText() {
		// 메인 선택기 텍스트 업데이트
		if (this.groupSelectorMain != null) {
			if (this.selectedGroupMain != null) {
				int count = this.groupSelectorMain.getGroupCount(this.selectedGroupMain.getId());
				this.groupSelectorMain.setMessage(Component.literal(this.selectedGroupMain.getName() + " [" + count + "]"));
			} else {
				this.groupSelectorMain.setMessage(Component.literal("None")); // 선택 안됨 표시
			}
		}

		// 서브 선택기 텍스트 업데이트
		if (this.groupSelectorSub != null) {
			if (this.selectedGroupSub != null) {
				int count = this.groupSelectorSub.getGroupCount(this.selectedGroupSub.getId());
				this.groupSelectorSub.setMessage(Component.literal(this.selectedGroupSub.getName() + " [" + count + "]"));
			} else {
				this.groupSelectorSub.setMessage(Component.literal("None")); // 선택 안됨 표시
			}
		}
	}
}