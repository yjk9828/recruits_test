package com.talhanation.recruits;

import com.talhanation.recruits.client.gui.group.RecruitsGroup;
import com.talhanation.recruits.config.RecruitsServerConfig;
import com.talhanation.recruits.entities.*;
import com.talhanation.recruits.inventory.CommandMenu;
import com.talhanation.recruits.network.*;
import com.talhanation.recruits.util.FormationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors; // [필수] 지난번 빌드 오류 해결용

public class CommandEvents {
    public static final MutableComponent TEXT_EVERYONE = Component.translatable("chat.recruits.text.everyone");
    public static final MutableComponent TEXT_GROUP = Component.translatable("chat.recruits.text.group");

    // ... (onMovementCommand 등 위쪽 메서드들은 기존 유지) ...
    
	public static void onMovementCommand(ServerPlayer player, int movementState, int[] groupIds, int formation) {
        
        // [FIX] 과거의 잔재 청산: 현재 명령받는 그룹만 'Active' 상태로 저장
        saveActiveGroups(player, groupIds);

        // 1. 플레이어 주변의 모든 Recruit 로드
        List<AbstractRecruitEntity> allRecruits = Objects.requireNonNull(player.getCommandSenderWorld())
                .getEntitiesOfClass(AbstractRecruitEntity.class, player.getBoundingBox().inflate(120));

        // 2. 명령 대상 필터링
        List<AbstractRecruitEntity> targetRecruits = new ArrayList<>();
        for (AbstractRecruitEntity recruit : allRecruits) {
            if (recruit.getOwnerUUID() != null && recruit.getOwnerUUID().equals(player.getUUID())) {
                for (int id : groupIds) {
                    if (recruit.isEffectedByCommand(player.getUUID(), id)) {
                        targetRecruits.add(recruit);
                        break;
                    }
                }
            }
        }

        if (targetRecruits.isEmpty()) return;

        // 3. 대형 및 이동 명령 적용
        if(formation != 0 && (movementState == 2|| movementState == 4 || movementState == 6 || movementState == 7 || movementState == 8)) {
            Vec3 targetPos = null;

            switch (movementState){
               case 2 -> targetPos = FormationUtils.getGeometricMedian(targetRecruits, (ServerLevel) player.getCommandSenderWorld());
               case 4 -> targetPos = player.position();
               case 6 -> {
                   HitResult hitResult = player.pick(200, 1F, true);
                   targetPos = hitResult.getLocation();
               }
               case 7 -> {
                   Vec3 center = FormationUtils.getGeometricMedian(targetRecruits, (ServerLevel) player.getCommandSenderWorld());
                   Vec3 forward = player.getForward();
                   Vec3 pos = center.add(forward.scale(getForwardScale(targetRecruits)));
                   BlockPos blockPos = FormationUtils.getPositionOrSurface(player.getCommandSenderWorld(), new BlockPos((int) pos.x, (int) pos.y, (int) pos.z));
                   targetPos = new Vec3(pos.x, blockPos.getY(), pos.z);
               }
               case 8 -> {
                   Vec3 center = FormationUtils.getGeometricMedian(targetRecruits, (ServerLevel) player.getCommandSenderWorld());
                   Vec3 forward = player.getForward();
                   Vec3 pos = center.add(forward.scale(-getForwardScale(targetRecruits)));
                   BlockPos blockPos = FormationUtils.getPositionOrSurface(player.getCommandSenderWorld(), new BlockPos((int) pos.x, (int) pos.y, (int) pos.z));
                   targetPos = new Vec3(pos.x, blockPos.getY(), pos.z);
               }
            }
            applyFormation(formation, targetRecruits, player, targetPos);
        }
        else{
            for(AbstractRecruitEntity recruit : targetRecruits){
                int state = recruit.getFollowState();
                switch (movementState) {
                    case 0 -> { if (state != 0) recruit.setFollowState(0); }
                    case 1 -> { if (state != 1) recruit.setFollowState(1); }
                    case 2 -> { if (state != 2) recruit.setFollowState(2); }
                    case 3 -> { if (state != 3) recruit.setFollowState(3); }
                    case 4 -> { if (state != 4) recruit.setFollowState(4); }
                    case 5 -> { if (state != 5) recruit.setFollowState(5); }
                    case 6 -> {
                        HitResult hitResult = player.pick(100, 1F, true);
                        if (hitResult.getType() == HitResult.Type.BLOCK) {
                            BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                            recruit.setMovePos(blockHitResult.getBlockPos());
                            recruit.setFollowState(0);
                            recruit.setShouldMovePos(true);
                        }
                    }
                    case 7 -> {
                        Vec3 forward = player.getForward();
                        Vec3 pos = recruit.position().add(forward.scale(getForwardScale(recruit)));
                        BlockPos blockPos = FormationUtils.getPositionOrSurface(player.getCommandSenderWorld(), new BlockPos((int) pos.x, (int) pos.y, (int) pos.z));
                        recruit.setHoldPos(new Vec3(pos.x, blockPos.getY(), pos.z));
                        recruit.ownerRot = player.getYRot();
                        recruit.setFollowState(3);
                    }
                    case 8 -> {
                        Vec3 forward = player.getForward();
                        Vec3 pos = recruit.position().add(forward.scale(-getForwardScale(recruit)));
                        BlockPos blockPos = FormationUtils.getPositionOrSurface(player.getCommandSenderWorld(), new BlockPos((int) pos.x, (int) pos.y, (int) pos.z));
                        recruit.setHoldPos(new Vec3(pos.x, blockPos.getY(), pos.z));
                        recruit.ownerRot = player.getYRot();
                        recruit.setFollowState(3);
                    }
                }
                recruit.isInFormation = false;
            }
        }
        
         for(AbstractRecruitEntity recruit : targetRecruits) {
             recruit.setUpkeepTimer(recruit.getUpkeepCooldown());
             if (recruit.getShouldMount()) recruit.setShouldMount(false);
             checkPatrolLeaderState(recruit);
             recruit.forcedUpkeep = false;
         }
    }

    // ... (getForwardScale, applyFormation, onMovementCommandGUI 등 중간 메서드 생략 - 기존 유지) ...
    private static double getForwardScale(List<AbstractRecruitEntity> recruits) {
        for (AbstractRecruitEntity recruit : recruits){
            if(recruit instanceof CaptainEntity) return getForwardScale(recruit);
        }
        return 10;
    }
    private static double getForwardScale(AbstractRecruitEntity recruit) {
        return (recruit instanceof CaptainEntity captain && captain.smallShipsController.ship != null && captain.smallShipsController.ship.isCaptainDriver()) ? 25 : 10;
    }
    public static void applyFormation(int formation, List<AbstractRecruitEntity> recruits, ServerPlayer player, Vec3 targetPos) {
        switch (formation){
            case 1 -> FormationUtils.lineUpFormation(player, recruits, targetPos);
            case 2 -> FormationUtils.squareFormation(player, recruits, targetPos);
            case 3 -> FormationUtils.triangleFormation(player, recruits, targetPos);
            case 4 -> FormationUtils.hollowCircleFormation(player, recruits, targetPos);
            case 5 -> FormationUtils.hollowSquareFormation(player, recruits, targetPos);
            case 6 -> FormationUtils.vFormation(player, recruits, targetPos);
            case 7 -> FormationUtils.circleFormation(player, recruits, targetPos);
            case 8 -> FormationUtils.movementFormation(player, recruits, targetPos);
        }
    }
    public static void onMovementCommandGUI(AbstractRecruitEntity recruit, int movementState) {
        int state = recruit.getFollowState();
        switch (movementState) {
            case 0 -> { if (state != 0) recruit.setFollowState(0); }
            case 1 -> { if (state != 1) recruit.setFollowState(1); }
            case 2 -> { if (state != 2) recruit.setFollowState(2); }
            case 3 -> { if (state != 3) recruit.setFollowState(3); }
            case 4 -> { if (state != 4) recruit.setFollowState(4); }
            case 5 -> { if (state != 5) recruit.setFollowState(5); }
        }
        recruit.setUpkeepTimer(recruit.getUpkeepCooldown());
        if (recruit.getShouldMount()) recruit.setShouldMount(false);
        checkPatrolLeaderState(recruit);
        recruit.forcedUpkeep = false;
    }
    public static void checkPatrolLeaderState(AbstractRecruitEntity recruit) {
        if(recruit instanceof AbstractLeaderEntity leader) {
            AbstractLeaderEntity.State patrolState = AbstractLeaderEntity.State.fromIndex(leader.getPatrollingState());
            if(patrolState == AbstractLeaderEntity.State.PATROLLING || patrolState == AbstractLeaderEntity.State.WAITING) {
                leader.setPatrolState(AbstractLeaderEntity.State.PAUSED);
            }
            else if(patrolState == AbstractLeaderEntity.State.RETREATING || patrolState == AbstractLeaderEntity.State.UPKEEP){
                leader.resetPatrolling();
                leader.setPatrolState(AbstractLeaderEntity.State.IDLE);
            }
        }
    }
    public static void onAggroCommand(UUID player_uuid, AbstractRecruitEntity recruit, int x_state, int group, boolean fromGui) {
        if (recruit.isEffectedByCommand(player_uuid, group)){
            int state = recruit.getState();
            if (state != x_state) recruit.setState(x_state);
        }
    }
    public static void onStrategicFireCommand(Player player, UUID player_uuid, AbstractRecruitEntity recruit, int group, boolean should) {
        if (recruit.isEffectedByCommand(player_uuid, group)){
            if (recruit instanceof IStrategicFire bowman){
                HitResult hitResult = player.pick(200, 1F, false);
                bowman.setShouldStrategicFire(should);
                if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                    bowman.setStrategicFirePos(((BlockHitResult) hitResult).getBlockPos());
                }
            }
        }
    }

    public static void openCommandScreen(Player player) {
        if (player instanceof ServerPlayer) {
            updateCommandScreen((ServerPlayer)player);
            NetworkHooks.openScreen((ServerPlayer) player, new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() { return Component.literal("command_screen"); }
                @Override
                public @NotNull AbstractContainerMenu createMenu(int i, @NotNull Inventory playerInventory, @NotNull Player playerEntity) {
                    return new CommandMenu(i, playerEntity);
                }
            }, packetBuffer -> {packetBuffer.writeUUID(player.getUUID());});
        } else {
            Main.SIMPLE_CHANNEL.sendToServer(new MessageCommandScreen(player));
        }
    }

    // ... (onServerPlayerTick, onPlayerLoggedIn, saveFormation 등은 기존 유지) ...
    @SubscribeEvent
    public void onServerPlayerTick(TickEvent.PlayerTickEvent event){
        if(event.player instanceof ServerPlayer serverPlayer && serverPlayer.tickCount % 20 == 0){
            int formation = getSavedFormation(serverPlayer);
            if(formation > 0){
                int[] savedPos = getSavedFormationPos(serverPlayer);
                if(savedPos.length == 0) {
                    savedPos = new int[]{(int) serverPlayer.getX(), (int) serverPlayer.getZ()};
                    saveFormationPos(serverPlayer, savedPos);
                }
                Vec3 oldPos = new Vec3(savedPos[0], serverPlayer.getY(), savedPos[1]);
                Vec3 targetPosition = serverPlayer.position();

                if(targetPosition.distanceToSqr(oldPos) > 50){
                    List<AbstractRecruitEntity> list = Objects.requireNonNull(serverPlayer).getCommandSenderWorld().getEntitiesOfClass(
                                    AbstractRecruitEntity.class, serverPlayer.getBoundingBox().inflate(120));
                    int[] array = getActiveGroups(serverPlayer);
                    list.removeIf(recruit -> Arrays.stream(array).noneMatch(x -> recruit.isEffectedByCommand(serverPlayer.getUUID(), x)));
                    applyFormation(formation, list, serverPlayer, targetPosition);
                    saveFormationPos(serverPlayer, new int[]{(int) targetPosition.x, (int) targetPosition.z});
                }
            }
        }
    }
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        CompoundTag playerData = event.getEntity().getPersistentData();
        CompoundTag data = playerData.getCompound(Player.PERSISTED_NBT_TAG);
            if (!data.contains("MaxRecruits")) data.putInt("MaxRecruits", RecruitsServerConfig.MaxRecruitsForPlayer.get());
            if (!data.contains("CommandingGroup")) data.putInt("CommandingGroup", 0);
            if (!data.contains("TotalRecruits")) data.putInt("TotalRecruits", 0);
            if (!data.contains("ActiveGroups")) data.putIntArray("ActiveGroups", new int[0]);
            if (!data.contains("Formation")) data.putInt("Formation", 0);
            if (!data.contains("FormationPos")) data.putIntArray("FormationPos", new int[]{(int) event.getEntity().getX(), (int) event.getEntity().getZ()});
        playerData.put(Player.PERSISTED_NBT_TAG, data);
    }
    public static int getSavedFormation(Player player) {
        CompoundTag playerNBT = player.getPersistentData();
        CompoundTag nbt = playerNBT.getCompound(Player.PERSISTED_NBT_TAG);
        return nbt.getInt("Formation");
    }
    public static void saveFormation(Player player, int formation) {
        CompoundTag playerNBT = player.getPersistentData();
        CompoundTag nbt = playerNBT.getCompound(Player.PERSISTED_NBT_TAG);
        nbt.putInt( "Formation", formation);
        playerNBT.put(Player.PERSISTED_NBT_TAG, nbt);
    }
    public static int[] getSavedFormationPos(Player player) {
        CompoundTag playerNBT = player.getPersistentData();
        CompoundTag nbt = playerNBT.getCompound(Player.PERSISTED_NBT_TAG);
        return nbt.getIntArray("FormationPos");
    }
    public static void saveFormationPos(Player player, int[] pos) {
        CompoundTag playerNBT = player.getPersistentData();
        CompoundTag nbt = playerNBT.getCompound(Player.PERSISTED_NBT_TAG);
        nbt.putIntArray( "FormationPos", pos);
        playerNBT.put(Player.PERSISTED_NBT_TAG, nbt);
    }
    public static int[] getActiveGroups(Player player) {
        CompoundTag playerNBT = player.getPersistentData();
        CompoundTag nbt = playerNBT.getCompound(Player.PERSISTED_NBT_TAG);
        return nbt.getIntArray("ActiveGroups");
    }
    public static void saveActiveGroups(Player player, int[] count) {
        CompoundTag playerNBT = player.getPersistentData();
        CompoundTag nbt = playerNBT.getCompound(Player.PERSISTED_NBT_TAG);
        nbt.putIntArray( "ActiveGroups", count);
        playerNBT.put(Player.PERSISTED_NBT_TAG, nbt);
    }
    public static void handleRecruiting(Player player, AbstractRecruitEntity recruit){
        // ... (handleRecruiting 로직 기존 유지) ...
        // 내용이 길어서 생략하지만 파일에 원래 있던 그대로 두세요. 변경 없음.
        String name = recruit.getName().getString() + ": ";
        int sollPrice = recruit.getCost();
        Inventory playerInv = player.getInventory();
        int playerEmeralds = 0;
        String str = RecruitsServerConfig.RecruitCurrency.get();
        Optional<Holder<Item>> holder = ForgeRegistries.ITEMS.getHolder(ResourceLocation.tryParse(str));
        ItemStack currencyItemStack = holder.map(itemHolder -> itemHolder.value().getDefaultInstance()).orElseGet(Items.EMERALD::getDefaultInstance);
        Item currency = currencyItemStack.getItem();
        for (int i = 0; i < playerInv.getContainerSize(); i++){
            ItemStack itemStackInSlot = playerInv.getItem(i);
            Item itemInSlot = itemStackInSlot.getItem();
            if (itemInSlot.equals(currency)) playerEmeralds += itemStackInSlot.getCount();
        }
        boolean playerCanPay = playerEmeralds >= sollPrice;
        if (playerCanPay || player.isCreative()){
            if(recruit.hire(player)) {
                playerEmeralds -= sollPrice;
                for (int i = 0; i < playerInv.getContainerSize(); i++) {
                    ItemStack itemStackInSlot = playerInv.getItem(i);
                    if (itemStackInSlot.getItem().equals(currency)) playerInv.removeItemNoUpdate(i);
                }
                ItemStack emeraldsLeft = currencyItemStack.copy();
                emeraldsLeft.setCount(playerEmeralds);
                playerInv.add(emeraldsLeft);
                if(player.getTeam() != null){
                    if(player.getCommandSenderWorld().isClientSide){
                        Main.SIMPLE_CHANNEL.sendToServer(new MessageAddRecruitToTeam(player.getTeam().getName(), 1));
                    }
                    else {
                        ServerPlayer serverPlayer = (ServerPlayer) player;
                        TeamEvents.addNPCToData(serverPlayer.serverLevel(), player.getTeam().getName(), 1);
                    }
                }
            }
        }
        else player.sendSystemMessage(TEXT_HIRE_COSTS(name, sollPrice, currency));
    }

    // ... (onMountButton 등 기타 이벤트 핸들러 유지) ...
    public static void onMountButton(UUID player_uuid, AbstractRecruitEntity recruit, UUID mount_uuid, int group) {
        if (recruit.isEffectedByCommand(player_uuid, group)){
            if(mount_uuid != null) recruit.shouldMount(true, mount_uuid);
            else if(recruit.getMountUUID() != null) recruit.shouldMount(true, recruit.getMountUUID());
            recruit.dismount = 0;
        }
    }
    public static void onDismountButton(UUID player_uuid, AbstractRecruitEntity recruit, int group) {
        if (recruit.isEffectedByCommand(player_uuid, group)){
            recruit.shouldMount(false, null);
            if(recruit.isPassenger()){
                recruit.stopRiding();
                recruit.dismount = 180;
            }
        }
    }
    public static void onProtectButton(UUID player_uuid, AbstractRecruitEntity recruit, UUID protect_uuid, int group) {
        if (recruit.isEffectedByCommand(player_uuid, group)){
            recruit.shouldProtect(true, protect_uuid);
        }
    }
    public static void onClearTargetButton(UUID player_uuid, AbstractRecruitEntity recruit, int group) {
        if (recruit.isEffectedByCommand(player_uuid, group)){
            recruit.setTarget(null);
            recruit.setLastHurtByPlayer(null);
            recruit.setLastHurtMob(null);
            recruit.setLastHurtByMob(null);
        }
    }
    public static void onClearUpkeepButton(UUID player_uuid, AbstractRecruitEntity recruit, int group) {
        if (recruit.isEffectedByCommand(player_uuid, group)){
            recruit.clearUpkeepEntity();
            recruit.clearUpkeepPos();
        }
    }
    public static void onUpkeepCommand(UUID player_uuid, AbstractRecruitEntity recruit, int group, boolean isEntity, UUID entity_uuid, BlockPos blockPos) {
        if (recruit.isEffectedByCommand(player_uuid, group)){
            if (isEntity) {
                recruit.setUpkeepUUID(Optional.of(entity_uuid));
                recruit.clearUpkeepPos();
            }
            else {
                recruit.setUpkeepPos(blockPos);
                recruit.clearUpkeepEntity();
            }
            recruit.forcedUpkeep = true;
            recruit.setUpkeepTimer(0);
            onClearTargetButton(player_uuid, recruit, group);
        }
    }
    public static void onShieldsCommand(ServerPlayer serverPlayer, UUID player_uuid, AbstractRecruitEntity recruit, int group, boolean shields) {
        if (recruit.isEffectedByCommand(player_uuid, group)){
            recruit.setShouldBlock(shields);
        }
    }
    public static void onRangedFireCommand(ServerPlayer serverPlayer, UUID player_uuid, AbstractRecruitEntity recruit, int group, boolean should) {
        if (recruit.isEffectedByCommand(player_uuid, group)){
            recruit.setShouldRanged(should);
        }
    }
    public static void onRestCommand(ServerPlayer serverPlayer, UUID player_uuid, AbstractRecruitEntity recruit, int group, boolean should) {
        if (recruit.isEffectedByCommand(player_uuid, group)){
            recruit.setShouldRest(should);
        }
    }
    private static MutableComponent TEXT_HIRE_COSTS(String name, int sollPrice, Item item) {
        return Component.translatable("chat.recruits.text.hire_costs", name, String.valueOf(sollPrice), item.getDescription().getString());
    }

    private static final List<RecruitsGroup> GROUP_DEFAULT_SETTING = new ArrayList<>(
            Arrays.asList(
                    new RecruitsGroup(0, "No Group", false),
                    new RecruitsGroup(1, "Infantry", false),
                    new RecruitsGroup(2, "Ranged", false),
                    new RecruitsGroup(3, "Cavalry", false)
            )
    );

    public static void updateCommandScreen(ServerPlayer player) {
        Main.SIMPLE_CHANNEL.send(PacketDistributor.PLAYER.with(()-> player), new MessageToClientUpdateCommandScreen(getCompoundTagFromRecruitsGroupList(getAvailableGroups(player))));
    }

    public static void updateRecruitInventoryScreen(ServerPlayer player) {
        Main.SIMPLE_CHANNEL.send(PacketDistributor.PLAYER.with(()-> player), new MessageToClientUpdateRecruitInventoryScreen(getCompoundTagFromRecruitsGroupList(loadPlayersGroupsFromNBT(player))));
    }

    // [CRITICAL FIX] : 인원이 0이어도 리스트에 포함시켜서 클라이언트가 계층 구조를 알 수 있게 함
    public static List<RecruitsGroup> getAvailableGroups(ServerPlayer player) {
        List<AbstractRecruitEntity> list = Objects.requireNonNull(player.getCommandSenderWorld().getEntitiesOfClass(AbstractRecruitEntity.class, player.getBoundingBox().inflate(120)));
        list.removeIf(recruit -> !recruit.isEffectedByCommand(player.getUUID(), 0));

        List<RecruitsGroup> allGroups = loadPlayersGroupsFromNBT(player);

        Map<Integer, Integer> groupCounts = new HashMap<>();

        for (AbstractRecruitEntity recruit : list) {
            int groupId = recruit.getGroup();
            groupCounts.put(groupId, groupCounts.getOrDefault(groupId, 0) + 1);
        }

        // [수정됨] 필터링 없이 모든 그룹을 추가하고, 인원수만 업데이트함
        List<RecruitsGroup> availableGroups = new ArrayList<>();
        for (RecruitsGroup group : allGroups) {
            // 인원이 있으면 설정, 없으면 0 (기본값)
            group.setCount(groupCounts.getOrDefault(group.getId(), 0));
            
            // 무조건 추가하여 클라이언트에 모든 그룹 데이터 전송
            availableGroups.add(group);
        }

        return availableGroups;
    }

// CommandEvents.java - loadPlayersGroupsFromNBT 메서드 (363번째 줄 근처)

    public static List<RecruitsGroup> loadPlayersGroupsFromNBT(Player player) {
        CompoundTag playerNBT = player.getPersistentData();
        CompoundTag nbt = playerNBT.getCompound(Player.PERSISTED_NBT_TAG);

        List<RecruitsGroup> groups = getRecruitsGroupListFormNBT(nbt);

        if(groups.isEmpty()) {
            // [FIX] 원본 리스트를 보호하기 위해 반드시 '새로운 리스트'로 복사해서 줘야 합니다.
            // 이렇게 하지 않으면 Black Ops 그룹이 모든 플레이어에게 전염되거나 추가되지 않습니다.
            groups = new ArrayList<>(GROUP_DEFAULT_SETTING);
        }

        return groups;
    }

    // ... (savePlayersGroupsToNBT, getRecruitsGroupListFormNBT, updateCompoundTag 등은 NBT 관련이므로 앞선 수정을 유지) ...
    // 이전에 드린 코드와 동일한 NBT 저장 로직 (ParentID 포함)이 있어야 합니다.
    
    public static void savePlayersGroupsToNBT(ServerPlayer player, List<RecruitsGroup> groups, boolean update) {
        CompoundTag playerNBT = player.getPersistentData();
        CompoundTag nbt = playerNBT.getCompound(Player.PERSISTED_NBT_TAG);
        if(update) updateCompoundTag(groups, nbt, player);
        else overrideCompoundTag(groups, nbt, player);
        playerNBT.put(Player.PERSISTED_NBT_TAG, nbt);
    }

    // [지난번 수정 사항 유지: ParentID 로드/저장]
    public static List<RecruitsGroup> getRecruitsGroupListFormNBT(CompoundTag nbt){
        List<RecruitsGroup> groups = new ArrayList<>();
        if(nbt.contains("recruits-groups")){
            ListTag groupList = nbt.getList("recruits-groups", 10);
            for (int i = 0; i < groupList.size(); ++i) {
                CompoundTag compoundnbt = groupList.getCompound(i);
                int id = compoundnbt.getInt("id");
                int count = compoundnbt.getInt("count");
                String name = compoundnbt.getString("name");
                boolean disabled = compoundnbt.getBoolean("disabled");
                int parentId = compoundnbt.contains("ParentID") ? compoundnbt.getInt("ParentID") : -1; // [NEW]

                RecruitsGroup recruitsGroup = new RecruitsGroup(id, name, disabled);
                recruitsGroup.setCount(count);
                recruitsGroup.setParentId(parentId); // [NEW]

                groups.add(recruitsGroup);
            }
        }
        // 부모-자식 연결은 클라이언트 유틸에서 처리하므로 여기선 객체 생성만 충실히 하면 됨
        // 하지만 서버 로직상 필요하다면 여기서도 매핑 가능 (현재는 단순 데이터 홀더 역할이 강함)
        return groups;
    }

    public static CompoundTag updateCompoundTag(List<RecruitsGroup> groups, CompoundTag nbt, ServerPlayer player) {
        List<RecruitsGroup> currentList = loadPlayersGroupsFromNBT(player);
        Map<Integer, RecruitsGroup> groupMap = new HashMap<>();
        for (RecruitsGroup group : currentList) groupMap.put(group.getId(), group);
        for (RecruitsGroup group : groups) if (group != null) groupMap.put(group.getId(), group);

        ListTag groupList = new ListTag();
        for (RecruitsGroup group : groupMap.values()) {
            CompoundTag compoundnbt = new CompoundTag();
            compoundnbt.putInt("id", group.getId());
            compoundnbt.putInt("count", group.getCount());
            compoundnbt.putString("name", group.getName());
            compoundnbt.putBoolean("disabled", group.isDisabled());
            compoundnbt.putInt("ParentID", group.getParentId()); // [NEW]
            groupList.add(compoundnbt);
        }
        nbt.put("recruits-groups", groupList);
        return nbt;
    }

    public static CompoundTag overrideCompoundTag(List<RecruitsGroup> groups, CompoundTag nbt, ServerPlayer player) {
        ListTag groupList = new ListTag();
        for (RecruitsGroup group : groups) {
            CompoundTag compoundnbt = new CompoundTag();
            compoundnbt.putInt("id", group.getId());
            compoundnbt.putInt("count", group.getCount());
            compoundnbt.putString("name", group.getName());
            compoundnbt.putBoolean("disabled", group.isDisabled());
            compoundnbt.putInt("ParentID", group.getParentId()); // [NEW]
            groupList.add(compoundnbt);
        }
        nbt.put("recruits-groups", groupList);
        return nbt;
    }

    public static CompoundTag getCompoundTagFromRecruitsGroupList(List<RecruitsGroup> groups){
        CompoundTag nbt = new CompoundTag();
        ListTag groupList = new ListTag();
        for (RecruitsGroup group : groups) {
            CompoundTag compoundnbt = new CompoundTag();
            compoundnbt.putInt("id", group.getId());
            compoundnbt.putInt("count", group.getCount());
            compoundnbt.putString("name", group.getName());
            compoundnbt.putBoolean("disabled", group.isDisabled());
            compoundnbt.putInt("ParentID", group.getParentId()); // [NEW]
            groupList.add(compoundnbt);
        }
        nbt.put("recruits-groups", groupList);
        return nbt;
    }
// [NEW] Black Ops 그룹 자동 생성 유틸리티
    public static void ensureBlackOpsGroupExists(ServerPlayer player) {
        int blackOpsId = 999999;
        String blackOpsName = "Black Ops"; // GUI에 표시될 이름

        // 1. 현재 플레이어의 그룹 목록을 불러옵니다.
        List<RecruitsGroup> currentGroups = loadPlayersGroupsFromNBT(player);

        // 2. 이미 99999번 그룹이 있는지 확인합니다.
        boolean exists = currentGroups.stream().anyMatch(g -> g.getId() == blackOpsId);

        // 3. 없으면 새로 만들어서 추가합니다.
        if (!exists) {
            RecruitsGroup blackOpsGroup = new RecruitsGroup(blackOpsId, blackOpsName, false);
            currentGroups.add(blackOpsGroup);

            // 4. 변경된 목록을 저장(Override)합니다.
            savePlayersGroupsToNBT(player, currentGroups, false);
            
            // 5. 클라이언트(GUI)에도 즉시 반영되도록 패킷을 보냅니다.
            updateCommandScreen(player);
            
            // 로그 출력 (확인용)
            // System.out.println("Created Black Ops Group for " + player.getName().getString());
        }
    }
}