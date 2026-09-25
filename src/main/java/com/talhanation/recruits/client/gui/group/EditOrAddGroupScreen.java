package com.talhanation.recruits.client.gui.group;

import com.mojang.blaze3d.systems.RenderSystem;
import com.talhanation.recruits.Main;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.widget.ExtendedButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class EditOrAddGroupScreen extends Screen {

    private static final int fontColor = 4210752;
    private static final int errorColor = 0xFF5555; // 에러 메시지용 빨간색
    
    private EditBox groupNameField;
    private EditBox parentSearchField;
    
    private final RecruitsGroupListScreen parent;
    private final RecruitsGroup groupToEdit;
    private int leftPos;
    private int topPos;
    private int imageWidth;
    private int imageHeight;
    
    private List<RecruitsGroup> suggestionList = new ArrayList<>();
    private boolean showSuggestions = false;
    private int selectedParentId = -1;

    private static final ResourceLocation RESOURCE_LOCATION = new ResourceLocation(Main.MOD_ID,"textures/gui/gui_small.png");
    private static final MutableComponent TEXT_CANCEL = Component.translatable("gui.recruits.groups.cancel");
    private static final MutableComponent TEXT_SAVE = Component.translatable("gui.recruits.groups.save");
    private static final MutableComponent TEXT_ADD = Component.translatable("gui.recruits.groups.add");
    private static final MutableComponent TEXT_EDIT_TITLE = Component.translatable("gui.recruits.groups.edit_title");
    private static final MutableComponent TEXT_ADD_TITLE = Component.translatable("gui.recruits.groups.add_title");
    
    // [NEW] 중복 경고 메시지
    private static final Component TEXT_DUPLICATE_WARNING = Component.literal("이미 존재하는 이름입니다!");

    public EditOrAddGroupScreen(RecruitsGroupListScreen parent) {
        this(parent, null);
    }

    public EditOrAddGroupScreen(RecruitsGroupListScreen parent, RecruitsGroup groupToEdit) {
        super(Component.literal(""));
        this.parent = parent;
        this.groupToEdit = groupToEdit;
        this.imageWidth = 250;
        this.imageHeight = 110;

        if (groupToEdit != null) {
            this.selectedParentId = groupToEdit.getParentId();
        }
    }

    @Override
    protected void init() {
        super.init();

        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        // 1. 그룹 이름 입력창
        groupNameField = new EditBox(this.font, leftPos + 10, topPos + 20, 220, 20, Component.literal("Name"));
        if (groupToEdit != null) {
            groupNameField.setValue(groupToEdit.getName());
        }
        this.addRenderableWidget(groupNameField);

        // 2. 상위 부대 검색창
        parentSearchField = new EditBox(this.font, leftPos + 10, topPos + 50, 220, 20, Component.literal("Search Parent"));
        parentSearchField.setResponder(this::onSearchInput);
        
        if (this.selectedParentId != -1) {
            RecruitsGroup parentGroup = getGroupById(this.selectedParentId);
            if (parentGroup != null) {
                parentSearchField.setValue(parentGroup.getName());
            }
        } else {
            parentSearchField.setHint(Component.literal("상위 부대 검색 (비워두면 최상위)"));
        }
        this.addRenderableWidget(parentSearchField);

        // 3. 버튼들
        this.addRenderableWidget(new ExtendedButton(leftPos + 10, topPos + 80, 60, 20, groupToEdit == null ? TEXT_ADD : TEXT_SAVE, button -> {
            // [MODIFIED] 중복 검사 통과 시에만 실행
            if (isNameDuplicate(groupNameField.getValue())) return;

            if (groupToEdit == null) {
                addGroup();
            } else {
                editGroup();
            }
        }));

        this.addRenderableWidget(new ExtendedButton(leftPos + 170, topPos + 80, 60, 20, TEXT_CANCEL, button -> {
            this.minecraft.setScreen(this.parent);
        }));
    }

    // [NEW] 이름 중복 검사 메서드
    private boolean isNameDuplicate(String name) {
        if (name == null || name.trim().isEmpty()) return false; // 빈 값은 다른 로직에서 처리(혹은 허용)한다고 가정

        for (RecruitsGroup group : RecruitsGroupList.groups) {
            // 수정 중일 때는 자기 자신의 이름과는 같아도 됨 (이름 변경 없이 다른거 바꿀 때)
            if (groupToEdit != null && group.getId() == groupToEdit.getId()) {
                continue;
            }
            
            // 대소문자 구분 없이 비교 (같은 이름으로 간주)
            if (group.getName().trim().equalsIgnoreCase(name.trim())) {
                return true;
            }
        }
        return false;
    }

    private void onSearchInput(String text) {
        if (text.isEmpty()) {
            this.selectedParentId = -1; 
            this.showSuggestions = false;
            return;
        }

        String query = text.toLowerCase(Locale.ROOT);
        this.suggestionList = RecruitsGroupList.groups.stream()
                .filter(g -> groupToEdit == null || g.getId() != groupToEdit.getId()) 
                .filter(g -> g.getName().toLowerCase(Locale.ROOT).contains(query))
                .limit(5) 
                .collect(Collectors.toList());

        this.showSuggestions = !this.suggestionList.isEmpty();
    }

    private RecruitsGroup getGroupById(int id) {
        return RecruitsGroupList.groups.stream().filter(g -> g.getId() == id).findFirst().orElse(null);
    }

    private void addGroup() {
        String groupName = groupNameField.getValue();
        if (!groupName.isEmpty()) {
            int newId = getNewID(RecruitsGroupList.groups);
            RecruitsGroup newGroup = new RecruitsGroup(newId, groupName, false);
            newGroup.setParentId(selectedParentId);
            
            RecruitsGroupList.groups.add(newGroup);
            RecruitsGroupList.saveGroups(false);
            this.minecraft.setScreen(this.parent);
        }
    }

    private int getNewID(List<RecruitsGroup> groups) {
        int newId = 0;
        for (RecruitsGroup group: groups){
            if(group.getId() > newId){
                newId = group.getId();
            }
        }
        return newId + 1;
    }

    private void editGroup() {
        String newName = groupNameField.getValue();
        if (!newName.isEmpty() && groupToEdit != null) {
            groupToEdit.setName(newName);
            
            if (selectedParentId != groupToEdit.getId()) {
                groupToEdit.setParentId(selectedParentId);
            }

            RecruitsGroupList.saveGroups(false);
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void tick() {
        super.tick();
        groupNameField.tick();
        parentSearchField.tick();
    }

    private void renderForeground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        guiGraphics.drawString(font, groupToEdit == null? TEXT_ADD_TITLE : TEXT_EDIT_TITLE, leftPos + 10  , topPos + 5, fontColor, false);
        
        // [NEW] 중복일 경우 경고 메시지 출력
        if (isNameDuplicate(groupNameField.getValue())) {
            // 입력창 바로 위에 빨간색 경고 표시
            guiGraphics.drawString(font, TEXT_DUPLICATE_WARNING, leftPos + 10, topPos + 10, errorColor, false);
        }
    }
    
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        guiGraphics.blit(RESOURCE_LOCATION, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(guiGraphics);
        this.renderBackground(guiGraphics, mouseX, mouseY, delta);
        super.render(guiGraphics, mouseX, mouseY, delta);
        this.renderForeground(guiGraphics, mouseX, mouseY, delta);

        if (showSuggestions && parentSearchField.isFocused()) {
            renderSuggestions(guiGraphics, mouseX, mouseY);
        }
    }

    private void renderSuggestions(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startX = parentSearchField.getX();
        int startY = parentSearchField.getY() + parentSearchField.getHeight();
        int width = parentSearchField.getWidth();
        int itemHeight = 12;
        int totalHeight = suggestionList.size() * itemHeight;

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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (showSuggestions && parentSearchField.isFocused()) {
            int startX = parentSearchField.getX();
            int startY = parentSearchField.getY() + parentSearchField.getHeight();
            int width = parentSearchField.getWidth();
            int itemHeight = 12;
            int totalHeight = suggestionList.size() * itemHeight;

            if (mouseX >= startX && mouseX < startX + width && mouseY >= startY && mouseY < startY + totalHeight) {
                int index = (int) ((mouseY - startY) / itemHeight);
                if (index >= 0 && index < suggestionList.size()) {
                    RecruitsGroup selected = suggestionList.get(index);
                    this.selectedParentId = selected.getId();
                    this.parentSearchField.setValue(selected.getName());
                    this.showSuggestions = false;
                    this.setFocused(null);
                    return true;
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}