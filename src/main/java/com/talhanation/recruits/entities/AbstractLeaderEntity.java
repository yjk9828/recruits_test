package com.talhanation.recruits.entities;

import com.talhanation.recruits.Main;
import com.talhanation.recruits.TeamEvents; // 추가됨 (getRelation 사용 위해)
import com.talhanation.recruits.compat.SmallShips;
import com.talhanation.recruits.entities.ai.controller.IAttackController;
import com.talhanation.recruits.inventory.PatrolLeaderContainer;
import com.talhanation.recruits.network.MessageOpenSpecialScreen;
import com.talhanation.recruits.network.MessageToClientUpdateLeaderScreen;
import com.talhanation.recruits.util.FormationUtils;
import com.talhanation.recruits.util.NPCArmy;
import com.talhanation.recruits.util.RecruitCommanderUtil;
import com.talhanation.recruits.world.RecruitsDiplomacyManager; // 추가됨
import com.talhanation.recruits.world.RecruitsTeam; // 추가됨
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public abstract class AbstractLeaderEntity extends AbstractChunkLoaderEntity implements ICompanion {
    private static final EntityDataAccessor<Integer> WAYPOINT_INDEX = SynchedEntityData.defineId(AbstractLeaderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WAIT_TIME_IN_MIN = SynchedEntityData.defineId(AbstractLeaderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CYCLE = SynchedEntityData.defineId(AbstractLeaderEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FAST_PATROLLING = SynchedEntityData.defineId(AbstractLeaderEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> PATROLLING_STATE = SynchedEntityData.defineId(AbstractLeaderEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> INFO_MODE = SynchedEntityData.defineId(AbstractLeaderEntity.class, EntityDataSerializers.BYTE);
    // 시야 범위 설정용 변수 (128/200 토글용 - 사용 안 하더라도 GUI 호환성을 위해 유지)
    private static final EntityDataAccessor<Boolean> LONG_RANGE_MODE = SynchedEntityData.defineId(AbstractLeaderEntity.class, EntityDataSerializers.BOOLEAN);

public boolean returning;
    public boolean retreating;
    public int commandCooldown = 0;
    public BlockPos currentWaypoint;
    protected int waitingTime = 0;
    protected int waitForRecruitsUpkeepTime = 0;
    public int infoCooldown = 0;
    protected State state = State.IDLE;
    public State prevState = State.IDLE;
    protected String ownerName = "";
    public NPCArmy army;
    public NPCArmy enemyArmy;
    public IAttackController attackController;
    public boolean hasReportedEncounter = false;

    public AbstractLeaderEntity(EntityType<? extends AbstractLeaderEntity> entityType, Level world) {
        super(entityType, world);
        if(army == null && !this.level().isClientSide()){
            this.army = new NPCArmy((ServerLevel) this.level(), null, null);
        }
    }

    public Stack<BlockPos> WAYPOINTS = new Stack<>();
    public Stack<ItemStack> WAYPOINT_ITEMS = new Stack<>();

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(WAYPOINT_INDEX, 0);
        this.entityData.define(WAIT_TIME_IN_MIN, 0);
        this.entityData.define(CYCLE, false);
        this.entityData.define(FAST_PATROLLING, false);
        this.entityData.define(PATROLLING_STATE, (byte) 3);
        this.entityData.define(INFO_MODE, (byte) 0);
    }

    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("waypoint_index", this.getWaypointIndex());
        nbt.putInt("wait_time_in_min", this.getWaitTimeInMin());
        nbt.putInt("waiting_time", this.waitingTime);
        nbt.putBoolean("cycle", this.getCycle());
        nbt.putByte("patrolState", (byte) this.state.getIndex());
        nbt.putByte("prevPatrolState", (byte) this.prevState.getIndex());
        nbt.putBoolean("returning", this.returning);
        nbt.putBoolean("retreating", this.retreating);
        nbt.putByte("infoMode", this.getInfoMode());
        nbt.putString("OwnerName", this.ownerName);
        nbt.putBoolean("fastPatrolling", this.getFastPatrolling());
        nbt.putInt("waitForRecruitsUpkeepTime", this.waitForRecruitsUpkeepTime);
        nbt.putInt("infoCooldown", this.infoCooldown);

        ListTag waypointItems = new ListTag();
        for (int i = 0; i < WAYPOINT_ITEMS.size(); ++i) {
            ItemStack itemstack = WAYPOINT_ITEMS.get(i);
            if (!itemstack.isEmpty()) {
                CompoundTag compoundnbt = new CompoundTag();
                compoundnbt.putByte("WaypointItem", (byte) i);
                itemstack.save(compoundnbt);
                waypointItems.add(compoundnbt);
            }
        }
        nbt.put("WaypointItems", waypointItems);

        ListTag waypoints = new ListTag();
        for(int i = 0; i < WAYPOINTS.size(); i++){
            CompoundTag compoundnbt = new CompoundTag();
            compoundnbt.putByte("Waypoint", (byte) i);
            BlockPos pos = WAYPOINTS.get(i);
            compoundnbt.putDouble("PosX", pos.getX());
            compoundnbt.putDouble("PosY", pos.getY());
            compoundnbt.putDouble("PosZ", pos.getZ());

            waypoints.add(compoundnbt);
        }
        nbt.put("Waypoints", waypoints);

        CompoundTag armyTag = new CompoundTag();
        if(army != null) army.save(armyTag);
        nbt.put("ArmyData", armyTag);
    }

    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);

        this.setWaypointIndex(nbt.getInt("waypoint_index"));
        this.setWaitTimeInMin(nbt.getInt("wait_time_in_min"));
        this.setCycle(nbt.getBoolean("cycle"));
        this.setPatrolState(State.fromIndex(nbt.getByte("patrolState")));
        this.prevState = State.fromIndex(nbt.getByte("prevPatrolState"));
        this.returning = nbt.getBoolean("returning");
        this.retreating = nbt.getBoolean("retreating");
        this.waitingTime = nbt.getInt("waiting_time");
        this.waitForRecruitsUpkeepTime = nbt.getInt("waitForRecruitsUpkeepTime");
        this.infoCooldown = nbt.getInt("infoCooldown");
        this.setInfoMode(nbt.getByte("infoMode"));
        this.ownerName = nbt.getString("ownerName");
        this.setFastPatrolling(nbt.getBoolean("fastPatrolling"));

        ListTag waypointItems = nbt.getList("WaypointItems", 10);
        for (int i = 0; i < waypointItems.size(); ++i) {
            CompoundTag compoundnbt = waypointItems.getCompound(i);

            ItemStack itemStack = ItemStack.of(compoundnbt);
            this.WAYPOINT_ITEMS.push(itemStack);
        }

        ListTag waypoints = nbt.getList("Waypoints", 10);
        for (int i = 0; i < waypoints.size(); ++i) {
            CompoundTag compoundnbt = waypoints.getCompound(i);
            BlockPos pos = new BlockPos(
                    (int)compoundnbt.getDouble("PosX"),
                    (int)compoundnbt.getDouble("PosY"),
                    (int)compoundnbt.getDouble("PosZ"));
            this.WAYPOINTS.push(pos);
        }

        if (nbt.contains("ArmyData") && !this.getCommandSenderWorld().isClientSide()) {
            army = NPCArmy.load((ServerLevel) this.getCommandSenderWorld(), nbt.getCompound("ArmyData"));
            army.initRecruits(true);
        }
    }

    private boolean retreatingMessage = false;
    private int checkEnemyTimer;
    private boolean checkForArmy = false;
    
    public void tick(){
        super.tick();
        if(this.level().isClientSide()) return;

        if(!checkForArmy && this.army != null ){
            checkForArmy = true;
            army.initRecruits(true);
        }

        if(checkEnemyTimer > 0) checkEnemyTimer--;
        if(infoCooldown > 0) infoCooldown--;
        if(commandCooldown > 0) commandCooldown--;
        if(waitForRecruitsUpkeepTime > 0) waitForRecruitsUpkeepTime--;

        if(checkEnemyTimer == 0){
            checkEnemyTimer = 200; // 10초마다 체크
            checkForPotentialEnemies();
        }

        double distance = 0D;
        if(currentWaypoint != null) distance = this.distanceToSqr(currentWaypoint.getX(), currentWaypoint.getY(), currentWaypoint.getZ());
        
        switch (state){
            case IDLE, PAUSED, STOPPED -> {}
            case PATROLLING -> {
                if(currentWaypoint != null){
                    if(distance <= this.getDistanceToReachWaypoint()){
                        boolean isFirstWaypoint = getWaypointIndex() == 0;
                        BlockPos pos = this.getUpkeepPos();
                        if(pos != null && pos.distSqr(this.getOnPos()) < 5000 && isFirstWaypoint && (waitForRecruitsUpkeepTime == 0 || getOtherUpkeepInterruption())){
                            this.handleResupply();
                            this.waitForRecruitsUpkeepTime = this.getResupplyTime();
                            this.setPatrolState(State.UPKEEP);
                            this.retreating = false;
                            this.retreatingMessage = false;
                        }
                        else {
                            this.updateWaypointIndex();
                            this.waitingTime = 120;
                            this.setPatrolState(State.WAITING);
                        }
                    }
                    else{
                        moveToCurrentWaypoint();
                    }
                }
                else if(!WAYPOINTS.isEmpty() && hasIndex()) {
                    this.currentWaypoint = WAYPOINTS.get(getWaypointIndex());
                }
                else
                    this.setPatrolState(State.IDLE);

                if(this.enemyArmy != null && !retreating){
                    if(enemyArmySpotted()){
                        this.setPatrolState(State.ATTACKING);
                    }
                    else{
                        this.setTarget(null);
                    }
                }
            }
            case WAITING -> {
                if(timerElapsed() && hasIndex()){
                    this.currentWaypoint = WAYPOINTS.get(getWaypointIndex());
                    this.setPatrolState(State.PATROLLING);
                }
                if(distance > 25D && this.enemyArmy == null){
                    moveToCurrentWaypoint();
                }
                if(this.enemyArmy != null && this.enemyArmy.size() > 0){
                    if(enemyArmySpotted()){
                        attackController.setInitPos(enemyArmy.getPosition());
                        this.setPatrolState(State.ATTACKING);
                    }
                    else {
                        this.setTarget(null);
                    }
                }
            }
            case ATTACKING -> {
                if(this.retreating && WAYPOINTS != null && WAYPOINTS.size() > 0){
                    this.setPatrolState(State.RETREATING);
                    return;
                }
				if(army == null || enemyArmy == null){
					// 현재 상태가 위치 고수(2), 제자리 복귀(3), 내 위치 고수(4)가 아닐 때만 Wander(0)로 변경
					int currentFollowState = this.getFollowState();
					if (currentFollowState != 1 && currentFollowState != 2 && currentFollowState != 3 && currentFollowState != 4) {
						this.setFollowState(0);
					}
					
					this.setPatrolState(prevState);
					return;
				}
                attackController.tick();
            }
            case RETREATING -> {
                if(this.getOwner() != null && !retreatingMessage) {
                    this.getOwner().sendSystemMessage(RETREATING());
                    retreatingMessage = true;
                }
                this.retreating = true;
                if(army != null){
                    RecruitCommanderUtil.setRecruitsClearTargets(army.getAllRecruitUnits());
                    RecruitCommanderUtil.setRecruitsFollow(army.getAllRecruitUnits(), this.uuid);
                    RecruitCommanderUtil.setRecruitsShields(army.getAllRecruitUnits(),false);
                }
                this.setPatrolState(State.PATROLLING);
            }
            case UPKEEP -> {
                this.handleUpkeepState();
            }
        }
    }

    public boolean getOtherUpkeepInterruption() {
        if(army != null && army.size() != 0 && army.getAverageHealth() < 25){
            return true;
        }
        if(army != null && army.size() != 0 && army.getAverageMorale() < 40){
            return true;
        }
        return false;
    }

    // [최종 수정] 200칸 고정 탐색 (버튼 로직 제거)
    private void checkForPotentialEnemies() {
        if(!level().isClientSide()){
            // 거리: 200칸 고정
            double scanRange = 150.0D; 
            double scanRangeSqr = scanRange * scanRange;

            List<LivingEntity> targets = this.getCommandSenderWorld().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(scanRange)).stream()
                    .filter((target) -> {
                        // 거리 체크
                        if (this.distanceToSqr(target) > scanRangeSqr) return false;
                        
                        // 공격 대상 확인 (피아식별)
                        if (!shouldAttack(target)) return false;
                        
                        // 시야 체크: 거리가 멀면(50칸 이상) 무조건 RayTrace 사용 (128칸 벽 뚫기)
                        double distSqr = this.distanceToSqr(target);
                        if (distSqr > 2500) { 
                            if (!canSeeTargetLongRange(target)) return false;
                        } else {
                            if (!this.hasLineOfSight(target)) return false;
                        }

                        return !target.isUnderWater();
                    })
                    .toList();

            if(targets.isEmpty()) return;

            this.enemyArmy = new NPCArmy((ServerLevel) level(), targets, null);
            if(state != State.ATTACKING && canAttackWhilePatrolling()) this.setPatrolState(State.ATTACKING);
        }
    }

    // 128칸 제한 없는 장거리 시야 체크 (RayTrace)
    protected boolean canSeeTargetLongRange(Entity target) {
        Vec3 startVec = this.getEyePosition();
        Vec3 endVec = target.getEyePosition();
        return this.getCommandSenderWorld().clip(new net.minecraft.world.level.ClipContext(
                startVec, endVec,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                this
        )).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    public boolean canAttackWhilePatrolling() {
        return true;
    }

    @Override
    public void setState(int state) {
        super.setState(state);
        if(army != null){
            RecruitCommanderUtil.setRecruitsAggroState(army.getAllRecruitUnits(), state);
        }
    }

    protected void handleUpkeepState() {
        if (waitForRecruitsUpkeepTime == 0) {
            boolean allRecruitsResupplied = this.army.getAllRecruitUnits().stream().allMatch(recruit -> recruit.getUpkeepTimer() >= 0);

            if (allRecruitsResupplied) {
                waitForRecruitsUpkeepTime = this.getAgainResupplyTime();
                this.setPatrolState(State.PATROLLING);
            }
        }
    }

    public boolean enemyArmySpotted() {
        if(enemyArmy == null || army == null) return false;

        double distanceToTarget = this.army.getPosition().distanceToSqr(enemyArmy.getPosition());
        
        // [수정] 200칸 거리 (40000) 고정
        double limit = 40000; 

        if(enemyArmy != null && !enemyArmy.getAllRecruitUnits().isEmpty() && (distanceToTarget < limit || Double.isNaN(distanceToTarget)) ){
            attackController.setInitPos(enemyArmy.getPosition());
            return true;
        }
        return false;
    }

    public void handleResupply() {
        if(army == null) return;

        RecruitCommanderUtil.setRecruitsWanderFreely(army.getAllRecruitUnits());
        RecruitCommanderUtil.setRecruitsUpkeep(army.getAllRecruitUnits(), this.getUpkeepPos(), this.getUpkeepUUID());
        this.forcedUpkeep = true;
    }

    public int getResupplyTime() {
        return 1000;
    }

    public int getAgainResupplyTime() {
        return 60*20*10;
    }

    public void resetPatrolling(){
        waitForRecruitsUpkeepTime = 0;
        this.retreating = false;
        this.waitingTime = 0;
        RecruitCommanderUtil.setRecruitsClearTargets(army.getAllRecruitUnits());

        Vec3 forward = this.position().normalize().vectorTo(this.currentWaypoint.getCenter().normalize());
        FormationUtils.squareFormation(forward, army.getAllRecruitUnits(), this.position().normalize(), 1.25);

        RecruitCommanderUtil.setRecruitsShields(army.getAllRecruitUnits(),false);
    }
    public double getDistanceToReachWaypoint() {
        return 5D;
    }

    protected void moveToCurrentWaypoint() {
        if(this.tickCount % 20 == 0){
            this.getNavigation().moveTo(currentWaypoint.getX(), currentWaypoint.getY(), currentWaypoint.getZ(), this.getFastPatrolling() ? 1F : 0.6F);
            Vec3 forward = this.position().vectorTo(this.currentWaypoint.getCenter());

            if(army != null){
                FormationUtils.lineFormation(forward.normalize(), army.getAllRecruitUnits(), this.position(), 4, 1.75);
                RecruitCommanderUtil.setRecruitsPatrolMoveSpeed(army.getAllRecruitUnits(),0.7F, 60);
            }

            //this.setRecruitsToMove(this.currentWaypoint);
        }

        if (horizontalCollision || minorHorizontalCollision) {
            this.getJumpControl().jump();
        }
    }

    public void setPatrolState(State state){
        if (this.state == State.ATTACKING && state != State.ATTACKING) {
            this.hasReportedEncounter = false;
        }

        this.entityData.set(PATROLLING_STATE, (byte)  state.getIndex());

        if(this.state != this.prevState){
            this.prevState = this.state;
        }
        this.state = state;
    }

    private void sendInfoAboutTarget(LivingEntity target) {
        InfoMode infoMode = InfoMode.fromIndex(getInfoMode());
        if(this.getOwner() != null && infoCooldown == 0 && infoMode != InfoMode.NONE){
            if((infoMode == InfoMode.ALL || infoMode == InfoMode.HOSTILE) && (target.getType().getCategory() == MobCategory.MONSTER || target instanceof Pillager)){
                this.getOwner().sendSystemMessage(HOSTILE_CONTACT(this.getOnPos()));
            }
            else if((infoMode == InfoMode.ALL || infoMode == InfoMode.ENEMY) && (target instanceof Player || target instanceof AbstractRecruitEntity recruitTarget && (recruitTarget.isOwned() || recruitTarget.getTeam() != null))){
                this.getOwner().sendSystemMessage(ENEMY_CONTACT(target.getType().toString(), this.getOnPos()));
            }

            infoCooldown = 20 * 60;
        }
    }

    private boolean hasIndex(){
        return !WAYPOINTS.isEmpty() && WAYPOINTS.size() > getWaypointIndex();
    }

    private boolean timerElapsed() {
        return ++waitingTime > getWaitTimeInMin() * 60 * 20;
    }

    public void decreaseIndex() {
        int currentIndex = this.getWaypointIndex();
        int nextIndex = currentIndex - 1;
        if (nextIndex >= 0) this.setWaypointIndex(nextIndex);
    }

    public void increaseIndex(){
        int currentIndex = this.getWaypointIndex();
        int nextIndex = currentIndex + 1;

        if(nextIndex < WAYPOINTS.size())
            this.setWaypointIndex(nextIndex);
    }

    public void updateWaypointIndex(){
        int currentIndex = this.getWaypointIndex();
        boolean isCycling = this.getCycle();
        boolean isLastWaypoint = currentIndex == WAYPOINTS.size() - 1;
        boolean isFirstWaypoint = currentIndex == 0;
        if(isCycling && !retreating){
            if(isLastWaypoint){
                this.setWaypointIndex(0);
            }
            else {
                increaseIndex();
            }
        }
        else{
            if(returning || retreating){
                if(isFirstWaypoint){ //is current last waypoint
                    this.returning = false;
                    this.retreating = false;
                }
                else {
                    decreaseIndex();
                }
            }
            else{
                if(isLastWaypoint){
                    this.returning = true;
                }
                else{
                    increaseIndex();
                }
            }
        }
    }

    public String getOwnerName() {
        return ownerName;
    }
    public void setOwnerName(String name) {
        ownerName = name;
    }
    public byte getInfoMode() {
        return this.entityData.get(INFO_MODE);
    }
    public void setInfoMode(byte x) {
        this.entityData.set(INFO_MODE, x);
    }

    public void setCycle(boolean cycle) {
        this.entityData.set(CYCLE, cycle);
    }

    public void setWaitTimeInMin(int wait_time_in_min) {
        this.entityData.set(WAIT_TIME_IN_MIN, wait_time_in_min);
    }

    public void setWaypointIndex(int current_waypoint_index) {
        this.entityData.set(WAYPOINT_INDEX, current_waypoint_index);
    }

    public byte getPatrollingState() {
        return this.entityData.get(PATROLLING_STATE);
    }

    public boolean getCycle() {
        return this.entityData.get(CYCLE);
    }

    public int getWaitTimeInMin() {
        return this.entityData.get(WAIT_TIME_IN_MIN);
    }

    public int getWaypointIndex() {
        return this.entityData.get(WAYPOINT_INDEX);
    }

    public void setFastPatrolling(boolean fastPatrolling) {
        this.entityData.set(FAST_PATROLLING, fastPatrolling);
    }

    public boolean getFastPatrolling() {
        return false;
    }

    public MutableComponent ENEMY_CONTACT(String name, BlockPos pos){
        return Component.translatable("chat.recruits.text.patrol_leader_enemy_contact", this.getName().getString(), name, pos.getX(), pos.getY(), pos.getZ());
    }

    public MutableComponent HOSTILE_CONTACT(BlockPos pos){
        return Component.translatable("chat.recruits.text.patrol_leader_hostile_contact", this.getName().getString(), pos.getX(), pos.getY(), pos.getZ());
    }

    public MutableComponent RETREATING(){
        return Component.translatable("chat.recruits.text.patrol_leader_retreating", this.getName().getString());
    }

    public ItemStack getItemStackToRender(BlockPos pos){
        BlockState state = this.getCommandSenderWorld().getBlockState(pos);
        ItemStack itemStack;
        if (state.is(Blocks.WATER) || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)){
            if(this instanceof CaptainEntity){
                itemStack = SmallShips.getSmallShipsItem();
                if(itemStack == null) {
                    itemStack = Items.OAK_BOAT.getDefaultInstance();
                }
            }
            else
                itemStack = new BlockItem(Blocks.WATER, new Item.Properties()).getDefaultInstance();

        }
        else if (state.is(Blocks.AIR) || state.is(Blocks.CAVE_AIR)) itemStack = new ItemStack(Items.GRASS_BLOCK);
        else itemStack = new ItemStack(state.getBlock().asItem());

        return itemStack;
    }

    public void addWaypoint(BlockPos pos) {
        ItemStack itemStack = this.getItemStackToRender(pos);

        WAYPOINT_ITEMS.push(itemStack);
        WAYPOINTS.push(pos);
    }

    public int getArmySize() {
        if(army == null) return 0;
        else return army.size();
    }

    public enum State{
        IDLE((byte) 0),
        PATROLLING((byte) 1),
        PAUSED((byte) 2),
        STOPPED((byte) 3),
        WAITING((byte) 4),
        ATTACKING((byte) 5),
        RETREATING((byte) 6),
        UPKEEP((byte) 7);
        private final byte index;
        State(byte index){
            this.index = index;
        }

        public byte getIndex(){
            return this.index;
        }

        public static State fromIndex(byte index) {
            for (State state : State.values()) {
                if (state.getIndex() == index) {
                    return state;
                }
            }
            throw new IllegalArgumentException("Invalid State index: " + index);
        }
    }

    public enum InfoMode{
        ALL((byte) 0),
        NONE((byte) 1),
        ENEMY((byte) 2),
        HOSTILE((byte) 3);

        private final byte index;
        InfoMode(byte index){
            this.index = index;
        }

        public byte getIndex(){
            return this.index;
        }

        public InfoMode getNext(){
            int length = values().length;
            byte newIndex = (byte) (this.index + 1);
            if(newIndex >= length){
                return ALL;
            }
            else
                return fromIndex(newIndex);
        }

        public static InfoMode fromIndex(byte index) {
            for (InfoMode state : InfoMode.values()) {
                if (state.getIndex() == index) {
                    return state;
                }
            }
            throw new IllegalArgumentException("Invalid InfoMode index: " + index);
        }
    }

    @Override
    public void die(DamageSource dmg) {
        super.die(dmg);
        if(army != null && !army.getAllRecruitUnits().isEmpty()){
            RecruitCommanderUtil.setRecruitsWanderFreely(army.getAllRecruitUnits());
            RecruitCommanderUtil.setRecruitsListen(army.getAllRecruitUnits(), true);
        }
    }

    @Override
    public boolean hurt(@NotNull DamageSource dmg, float amt) {
        if (this.getMaxHealth() * 0.25 > this.getHealth() && state != State.RETREATING) {
            this.state = State.RETREATING;
        }
        return super.hurt(dmg, amt);
    }

    public void openSpecialGUI(Player player) {
        if (player instanceof ServerPlayer) {
            NetworkHooks.openScreen((ServerPlayer) player, new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() {
                    return AbstractLeaderEntity.this.getName();
                }

                @Override
                public AbstractContainerMenu createMenu(int i, @NotNull Inventory playerInventory, @NotNull Player playerEntity) {
                    return new PatrolLeaderContainer(i, playerEntity,  AbstractLeaderEntity.this);
                }
            }, packetBuffer -> {packetBuffer.writeUUID(this.getUUID());});
        } else {
            Main.SIMPLE_CHANNEL.sendToServer(new MessageOpenSpecialScreen(player, this.getUUID()));
        }

        if (player instanceof ServerPlayer) {
            Main.SIMPLE_CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player), new MessageToClientUpdateLeaderScreen(this.WAYPOINTS, this.WAYPOINT_ITEMS, this.army.getTotalUnits()));
        }
    }
}