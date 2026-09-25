package com.talhanation.recruits.client.gui.group;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.*;

public class RecruitsGroup {
    private UUID uuid;                   // 그룹 고유 UUID
    private String name;                 // 부대명
    private UUID playerUUID;             // 소유자 플레이어 UUID
    private int count;                   // 소속 병사 수
    private boolean disabled;
    private int image;                   // 아이콘 인덱스

    // [계층 구조 필드 - 단 한 번만 선언]
    @Nullable
    private UUID parentUUID;             // 상위 부대 UUID
    private int legacyParentId = -1;     // int 기반 UI 호환용 부모 ID
    private final List<UUID> childrenUUIDs = new ArrayList<>();
    private final List<Integer> legacyChildrenIds = new ArrayList<>();
    public List<UUID> members = new ArrayList<>();

    // [지휘 및 분대장 상태]
    public UUID leaderUUID;              // 분대장 엔티티 UUID
    public BlockPos upkeep;              // 보급 상자 위치
    public int aggroState;
    public int followState;

    // 생성자: 신규 생성 시
    public RecruitsGroup(String name, UUID playerUUID, int image) {
        this.uuid = UUID.randomUUID();
        this.name = name;
        this.playerUUID = playerUUID;
        this.image = image;
        this.disabled = false;
    }

    // 기본 생성자 (UUID 기반)
    public RecruitsGroup(UUID uuid, String name, boolean disabled) {
        this.uuid = uuid;
        this.name = name;
        this.disabled = disabled;
    }

    // 기존 생성자 호환: new RecruitsGroup(int, String, boolean)
    public RecruitsGroup(int legacyId, String name, boolean disabled) {
        this.uuid = new UUID(0L, (long) legacyId);
        this.name = name;
        this.disabled = disabled;
    }

    // --- Getter & Setter ---
    public UUID getUUID() { return uuid; }
    public void setUUID(UUID uuid) { this.uuid = uuid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public UUID getPlayerUUID() { return playerUUID; }
    public void setPlayerUUID(UUID playerUUID) { this.playerUUID = playerUUID; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public boolean isDisabled() { return disabled; }
    public void setDisabled(boolean disabled) { this.disabled = disabled; }

    public int getImage() { return image; }
    public void setImage(int image) { this.image = image; }

    // --- [호환 브릿지 및 계층화 버그 해결 로직] ---

    public int getId() {
        if (this.uuid == null) return 0;
        if (this.uuid.getMostSignificantBits() == 0L) {
            return (int) this.uuid.getLeastSignificantBits();
        }
        return Math.abs(this.uuid.hashCode());
    }

    public int getParentId() {
        if (this.parentUUID != null) {
            if (this.parentUUID.getMostSignificantBits() == 0L) {
                return (int) this.parentUUID.getLeastSignificantBits();
            }
            return Math.abs(this.parentUUID.hashCode());
        }
        return this.legacyParentId;
    }

    // ★ UI에서 상위 부대 설정 시 값이 정상 저장되도록 보장
    public void setParentId(int parentId) {
        this.legacyParentId = parentId;
        if (parentId == -1) {
            this.parentUUID = null;
        } else {
            this.parentUUID = new UUID(0L, (long) parentId);
        }
    }

    @Nullable
    public UUID getParentUUID() { return parentUUID; }
    public void setParentUUID(@Nullable UUID parentUUID) { 
        this.parentUUID = parentUUID;
        if (parentUUID != null && parentUUID.getMostSignificantBits() == 0L) {
            this.legacyParentId = (int) parentUUID.getLeastSignificantBits();
        } else if (parentUUID != null) {
            this.legacyParentId = Math.abs(parentUUID.hashCode());
        } else {
            this.legacyParentId = -1;
        }
    }

    public List<Integer> getChildrenIds() {
        return this.legacyChildrenIds;
    }

    public List<UUID> getChildrenUUIDs() { 
        return childrenUUIDs; 
    }
    
    public void addChild(UUID childUUID) {
        if (!childrenUUIDs.contains(childUUID)) {
            childrenUUIDs.add(childUUID);
        }
        int legacyId = (childUUID.getMostSignificantBits() == 0L) ? (int) childUUID.getLeastSignificantBits() : Math.abs(childUUID.hashCode());
        if (!legacyChildrenIds.contains(legacyId)) {
            legacyChildrenIds.add(legacyId);
        }
    }

    public void addChild(int childId) {
        if (!this.legacyChildrenIds.contains(childId)) {
            this.legacyChildrenIds.add(childId);
        }
        UUID childU = new UUID(0L, (long) childId);
        if (!this.childrenUUIDs.contains(childU)) {
            this.childrenUUIDs.add(childU);
        }
    }

    public void removeChild(UUID childUUID) {
        childrenUUIDs.remove(childUUID);
        int legacyId = (childUUID.getMostSignificantBits() == 0L) ? (int) childUUID.getLeastSignificantBits() : Math.abs(childUUID.hashCode());
        legacyChildrenIds.remove((Integer) legacyId);
    }

    public void removeChild(int childId) {
        this.legacyChildrenIds.remove((Integer) childId);
        this.childrenUUIDs.remove(new UUID(0L, (long) childId));
    }

    public boolean hasChildren() {
        return !childrenUUIDs.isEmpty() || !legacyChildrenIds.isEmpty();
    }

    /**
     * 상위 부대 명령 시 하위 부대까지 일괄 수집하는 재귀 트리거
     */
    public static Set<UUID> getAllGroupUUIDsInHierarchy(UUID rootGroupId, List<RecruitsGroup> allGroups) {
        Set<UUID> result = new HashSet<>();
        result.add(rootGroupId);
        collectChildren(rootGroupId, allGroups, result);
        return result;
    }

    private static void collectChildren(UUID parentId, List<RecruitsGroup> allGroups, Set<UUID> accumulator) {
        for (RecruitsGroup group : allGroups) {
            if (parentId.equals(group.getParentUUID()) && !accumulator.contains(group.getUUID())) {
                accumulator.add(group.getUUID());
                collectChildren(group.getUUID(), allGroups, accumulator);
            }
        }
    }

    // --- NBT 직렬화 ---
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("uuid", this.uuid);
        tag.putString("name", this.name);
        tag.putBoolean("disabled", this.disabled);
        tag.putInt("count", this.count);
        tag.putInt("image", this.image);

        // NBT 저장 시 int와 UUID를 모두 보존하여 직렬화 유실 방지
        tag.putInt("ParentID", this.getParentId());

        if (this.playerUUID != null) tag.putUUID("playerUUID", this.playerUUID);
        if (this.parentUUID != null) tag.putUUID("parentUUID", this.parentUUID);
        if (this.leaderUUID != null) tag.putUUID("leaderUUID", this.leaderUUID);

        ListTag memberList = new ListTag();
        for (UUID id : members) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            memberList.add(entry);
        }
        tag.put("members", memberList);

        return tag;
    }

    public static RecruitsGroup fromNBT(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return null;

        UUID uuid = tag.getUUID("uuid");
        String name = tag.getString("name");
        boolean disabled = tag.getBoolean("disabled");

        RecruitsGroup group = new RecruitsGroup(uuid, name, disabled);
        group.setCount(tag.getInt("count"));
        group.setImage(tag.getInt("image"));

        if (tag.contains("ParentID")) {
            group.setParentId(tag.getInt("ParentID"));
        }
        if (tag.contains("parentUUID")) {
            group.setParentUUID(tag.getUUID("parentUUID"));
        }

        if (tag.contains("playerUUID")) group.setPlayerUUID(tag.getUUID("playerUUID"));
        if (tag.contains("leaderUUID")) group.leaderUUID = tag.getUUID("leaderUUID");

        if (tag.contains("members", Tag.TAG_LIST)) {
            ListTag memberList = tag.getList("members", Tag.TAG_COMPOUND);
            for (Tag entry : memberList) {
                group.members.add(((CompoundTag) entry).getUUID("id"));
            }
        }

        return group;
    }
}