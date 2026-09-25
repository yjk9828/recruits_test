package com.talhanation.recruits.client.gui.group;

import java.util.ArrayList;
import java.util.List;

public class RecruitsGroup {
    private int id;
    private String name;
    private int count;
    private boolean disabled;

    // [NEW] 계층 구조를 위한 필드
    private int parentId = -1; // -1이면 부모 없음 (최상위)
    private final List<Integer> childrenIds = new ArrayList<>();

    public RecruitsGroup(int id, String name, boolean disabled) {
        this.id = id;
        this.name = name;
        this.disabled = disabled;
    }

    // --- 기존 Getter / Setter ---

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    // --- [NEW] 계층 구조 관리 메서드 ---

    public int getParentId() {
        return parentId;
    }

    public void setParentId(int parentId) {
        this.parentId = parentId;
    }

    public List<Integer> getChildrenIds() {
        return childrenIds;
    }

    public void addChild(int childId) {
        if (!childrenIds.contains(childId)) {
            childrenIds.add(childId);
        }
    }

    public void removeChild(int childId) {
        childrenIds.remove((Integer) childId);
    }

    public boolean hasChildren() {
        return !childrenIds.isEmpty();
    }
}