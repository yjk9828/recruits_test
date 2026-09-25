package com.talhanation.recruits.client.gui.widgets;

import com.mojang.blaze3d.systems.RenderSystem;
import com.talhanation.recruits.client.gui.group.RecruitsGroup;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CascadingGroupWidget extends AbstractWidget {
    private final List<RecruitsGroup> allGroups;
    private final Consumer<RecruitsGroup> onSelect;
    private boolean isOpen = false;
    
    private final Map<Integer, Integer> nearbyGroupCounts = new HashMap<>();
    private boolean showAll = false; 

    // [MODIFIED] 깊이 제한을 없애기 위해 리스트로 경로를 추적합니다.
    // 예: [대대, 중대, 소대] 순서로 저장됨
    private final List<RecruitsGroup> hoveredPath = new ArrayList<>();

    public CascadingGroupWidget(int x, int y, int width, int height, List<RecruitsGroup> allGroups, Consumer<RecruitsGroup> onSelect) {
        super(x, y, width, height, Component.literal("Select Unit"));
        this.allGroups = allGroups;
        this.onSelect = onSelect;
        scanNearbyGroups();
    }

    public void setShowAll(boolean showAll) {
        this.showAll = showAll;
        scanNearbyGroups();
    }

    public boolean hasActiveGroups() {
        return !nearbyGroupCounts.isEmpty();
    }

    public void scanNearbyGroups() {
        nearbyGroupCounts.clear();
        if (showAll) {
            for (RecruitsGroup g : allGroups) nearbyGroupCounts.put(g.getId(), 0);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        double range = 120.0;
        AABB searchBox = mc.player.getBoundingBox().inflate(range);
        List<AbstractRecruitEntity> recruits = mc.level.getEntitiesOfClass(AbstractRecruitEntity.class, searchBox);

        Map<Integer, Integer> directCounts = new HashMap<>();
        for (AbstractRecruitEntity recruit : recruits) {
            if (recruit.getOwnerUUID() != null && recruit.getOwnerUUID().equals(mc.player.getUUID())) {
                if (recruit.getListen()) { 
                    int gid = recruit.getGroup();
                    directCounts.put(gid, directCounts.getOrDefault(gid, 0) + 1);
                }
            }
        }

        for (RecruitsGroup group : allGroups) {
            int totalCount = getAggregatedCount(group, directCounts);
            if (totalCount > 0) {
                nearbyGroupCounts.put(group.getId(), totalCount);
            }
        }
    }

    private int getAggregatedCount(RecruitsGroup group, Map<Integer, Integer> directCounts) {
        int count = directCounts.getOrDefault(group.getId(), 0);
        if (group.hasChildren()) {
            for (Integer childId : group.getChildrenIds()) {
                RecruitsGroup childGroup = allGroups.stream()
                        .filter(g -> g.getId() == childId)
                        .findFirst()
                        .orElse(null);
                if (childGroup != null) {
                    count += getAggregatedCount(childGroup, directCounts);
                }
            }
        }
        return count;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int bgColor = isHovered ? 0xFF444444 : 0xFF222222;
        guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
        guiGraphics.renderOutline(getX(), getY(), width, height, 0xFF888888);
        
        int textColor = isOpen ? 0xFFFFFF00 : 0xFFFFFFFF;
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);

        if (isOpen) {
            // 최상위 루트 그룹 찾기
            List<RecruitsGroup> rootGroups = allGroups.stream()
                    .filter(g -> g.getParentId() == -1)
                    .filter(g -> nearbyGroupCounts.containsKey(g.getId())) 
                    .collect(Collectors.toList());

            // 루트 레벨 렌더링 시작 (depth 0)
            renderMenuLevel(guiGraphics, mouseX, mouseY, rootGroups, getX(), getY() + height, 0);
        }
    }

    // [MODIFIED] 재귀적으로 메뉴를 그리는 메서드 (무제한 깊이 지원)
    private void renderMenuLevel(GuiGraphics guiGraphics, int mouseX, int mouseY, List<RecruitsGroup> groups, int x, int y, int depth) {
        if (groups.isEmpty()) return;

        int itemHeight = 20;
        int menuWidth = 110;
        int totalHeight = groups.size() * itemHeight;

        guiGraphics.fill(x, y, x + menuWidth, y + totalHeight, 0xFF111111);
        guiGraphics.renderOutline(x, y, menuWidth, totalHeight, 0xFF555555);

        for (int i = 0; i < groups.size(); i++) {
            RecruitsGroup group = groups.get(i);
            int itemY = y + (i * itemHeight);
            
            // 현재 아이템에 마우스가 올라갔는지 확인
            boolean isItemHovered = (mouseX >= x && mouseX < x + menuWidth && mouseY >= itemY && mouseY < itemY + itemHeight);

            if (isItemHovered) {
                guiGraphics.fill(x + 1, itemY + 1, x + menuWidth - 1, itemY + itemHeight - 1, 0xFF444444);
                
                // [MODIFIED] 호버 상태 업데이트: 현재 깊이(depth)에 이 그룹을 저장하고, 그 이후 깊이의 기록은 지움
                updateHoverPath(depth, group);
            }

            // 텍스트 렌더링
            String label = group.getName();
            if (label.length() > 8) label = label.substring(0, 7) + "..";
            int count = nearbyGroupCounts.getOrDefault(group.getId(), 0);
            label += " [" + count + "]";
            int labelColor = (count > 0 || showAll) ? 0xFFFFFFFF : 0xFFAAAAAA;
            guiGraphics.drawString(Minecraft.getInstance().font, label, x + 5, itemY + 6, labelColor, false);

            // 하위 그룹이 있으면 화살표 표시 및 자식 메뉴 렌더링
            if (group.hasChildren()) {
                guiGraphics.drawString(Minecraft.getInstance().font, "▶", x + menuWidth - 12, itemY + 6, 0xFFAAAAAA, false);
                
                // [MODIFIED] 내가 현재 선택된 경로(hoveredPath)에 포함되어 있다면, 내 자식들을 오른쪽에 그린다.
                if (isHoveredInPath(depth, group)) {
                    List<RecruitsGroup> children = allGroups.stream()
                            .filter(g -> group.getChildrenIds().contains(g.getId()))
                            .filter(g -> nearbyGroupCounts.containsKey(g.getId()))
                            .collect(Collectors.toList());
                            
                    if (!children.isEmpty()) {
                        renderMenuLevel(guiGraphics, mouseX, mouseY, children, x + menuWidth, itemY, depth + 1);
                    }
                }
            }
        }
    }

    // [NEW] 호버 경로 업데이트 헬퍼
    private void updateHoverPath(int depth, RecruitsGroup group) {
        // 리스트 크기 확보
        while (hoveredPath.size() <= depth) {
            hoveredPath.add(null);
        }
        hoveredPath.set(depth, group);
        
        // 현재 깊이보다 더 깊은 곳의 정보는 무효화 (다른 가지로 이동했으므로)
        if (hoveredPath.size() > depth + 1) {
            hoveredPath.subList(depth + 1, hoveredPath.size()).clear();
        }
    }

    // [NEW] 특정 깊이에서 해당 그룹이 호버링 경로에 있는지 확인
    private boolean isHoveredInPath(int depth, RecruitsGroup group) {
        return depth < hoveredPath.size() && hoveredPath.get(depth) == group;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isValidClickButton(button)) return false;

        // 위젯 메인 버튼 클릭 (열기/닫기)
        if (clicked(mouseX, mouseY)) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            isOpen = !isOpen;
            if(isOpen) {
                scanNearbyGroups();
                hoveredPath.clear(); // 열 때 경로 초기화
            }
            return true;
        }

        if (isOpen) {
            // [MODIFIED] 클릭된 그룹 찾기 (재귀적으로 수행됨)
            RecruitsGroup clicked = findGroupAtRecursive((int)mouseX, (int)mouseY);
            
            if (clicked != null) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                this.setMessage(Component.literal(clicked.getName()));
                onSelect.accept(clicked);
                isOpen = false;
                return true;
            }
            // 메뉴 밖을 클릭하면 닫기
            isOpen = false;
            return true;
        }
        return false;
    }

    // [MODIFIED] 클릭 위치 탐색을 재귀적으로 변경
    private RecruitsGroup findGroupAtRecursive(int mouseX, int mouseY) {
        // 1. 루트 레벨부터 시작
        List<RecruitsGroup> currentLevelGroups = allGroups.stream()
                .filter(g -> g.getParentId() == -1)
                .filter(g -> nearbyGroupCounts.containsKey(g.getId()))
                .collect(Collectors.toList());

        int currentX = getX();
        int currentY = getY() + height; // 첫 메뉴는 위젯 바로 아래
        
        // 현재 호버링된 경로를 따라가며 클릭 위치 확인
        // (hoveredPath에 없는 메뉴는 화면에 안 그려지므로 클릭도 안됨)
        
        // 루트 레벨 먼저 체크
        RecruitsGroup hit = checkHit(currentLevelGroups, currentX, currentY, mouseX, mouseY);
        if (hit != null) return hit;

        // 경로를 따라가며 자식 메뉴들 체크
        for (int i = 0; i < hoveredPath.size(); i++) {
            RecruitsGroup parent = hoveredPath.get(i);
            
            // 경로상의 부모가 자식을 가지고 있다면
            if (parent != null && parent.hasChildren()) {
                List<RecruitsGroup> children = allGroups.stream()
                        .filter(g -> parent.getChildrenIds().contains(g.getId()))
                        .filter(g -> nearbyGroupCounts.containsKey(g.getId()))
                        .collect(Collectors.toList());
                
                if (children.isEmpty()) continue;

                // 자식 메뉴의 위치 계산
                // X: 부모 메뉴의 오른쪽 (이전 X + 110)
                // Y: 부모 아이템의 Y 위치 (목록에서의 인덱스 * 20)
                
                // 주의: 여기서 parent가 currentLevelGroups의 몇 번째인지 찾아야 Y좌표 계산 가능
                int parentIndex = currentLevelGroups.indexOf(parent);
                if (parentIndex == -1) break; // 뭔가 꼬임

                int childMenuY = currentY + (parentIndex * 20);
                int childMenuX = currentX + 110;
                
                // 자식 메뉴에서 클릭 체크
                hit = checkHit(children, childMenuX, childMenuY, mouseX, mouseY);
                if (hit != null) return hit;

                // 다음 루프를 위해 기준점 업데이트
                currentLevelGroups = children;
                currentX = childMenuX;
                currentY = childMenuY;
            }
        }

        return null;
    }

    private RecruitsGroup checkHit(List<RecruitsGroup> groups, int x, int y, int mouseX, int mouseY) {
        int itemHeight = 20;
        int menuWidth = 110; 
        
        // 메뉴 영역 안에 마우스가 있는지 확인
        if (mouseX >= x && mouseX < x + menuWidth && mouseY >= y && mouseY < y + (groups.size() * itemHeight)) {
            int index = (mouseY - y) / itemHeight;
            if (index >= 0 && index < groups.size()) {
                return groups.get(index);
            }
        }
        return null;
    }
    
    public int getGroupCount(int groupId) {
        return nearbyGroupCounts.getOrDefault(groupId, 0);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}
}