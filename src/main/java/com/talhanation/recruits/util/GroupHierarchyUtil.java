package com.talhanation.recruits.util;

import com.talhanation.recruits.client.gui.group.RecruitsGroup;
import java.util.*;
import java.util.stream.Collectors;

public class GroupHierarchyUtil {
    
    // [NEW] 리스트 내의 모든 그룹의 부모-자식 관계를 다시 연결합니다.
    public static void relinkGroups(List<RecruitsGroup> groups) {
        if (groups == null) return;

        // 1. 모든 그룹의 자식 목록 초기화
        for (RecruitsGroup group : groups) {
            group.getChildrenIds().clear();
        }

        // 2. ID 기반 맵 생성
        Map<Integer, RecruitsGroup> groupMap = groups.stream()
                .collect(Collectors.toMap(RecruitsGroup::getId, g -> g));

        // 3. 다시 연결
        for (RecruitsGroup child : groups) {
            int parentId = child.getParentId();
            if (parentId != -1 && groupMap.containsKey(parentId)) {
                groupMap.get(parentId).addChild(child.getId());
            }
        }
    }

    // 기존 메서드 유지
    public static List<Integer> getTargetGroupIds(RecruitsGroup rootGroup, List<RecruitsGroup> allGroups) {
        if (rootGroup == null) return Collections.emptyList();
        
        Set<Integer> targetIds = new HashSet<>();
        collectRecursive(rootGroup, allGroups, targetIds);
        return new ArrayList<>(targetIds);
    }

    private static void collectRecursive(RecruitsGroup current, List<RecruitsGroup> allGroups, Set<Integer> targetIds) {
        targetIds.add(current.getId());

        if (current.hasChildren()) {
            for (Integer childId : current.getChildrenIds()) {
                allGroups.stream()
                        .filter(g -> g.getId() == childId)
                        .findFirst()
                        .ifPresent(childGroup -> collectRecursive(childGroup, allGroups, targetIds));
            }
        }
    }
}