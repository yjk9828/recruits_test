package com.talhanation.recruits.entities;

import com.talhanation.recruits.TeamEvents;
import com.talhanation.recruits.entities.ai.UseShield;
import com.talhanation.recruits.util.FormationUtils;
import com.talhanation.recruits.world.RecruitsDiplomacyManager;
import com.talhanation.recruits.world.RecruitsPlayerInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team; 
import net.minecraftforge.common.ForgeMod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ScoutEntity extends AbstractRecruitEntity implements ICompanion {

    private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(ScoutEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> SCOUT_MODE_ID = SynchedEntityData.defineId(ScoutEntity.class, EntityDataSerializers.BYTE);
	private long lastCombatMsgTick = -600; // 처음에 바로 보고할 수 있도록 초기화

    private final Predicate<ItemEntity> ALLOWED_ITEMS = (item) ->
            (!item.hasPickUpDelay() && item.isAlive() && getInventory().canAddItem(item.getItem()) && this.wantsToPickUp(item.getItem()));

    public enum ScoutMode {
        IDLE(0),
        SCOUTING(1),
        ADVANCED_SCOUTING(2),
        COMMANDING(3),
        SEARCHING_STRUCTURE(4),
        SEARCHING_LOST_RECRUITS(5);

        private final int index;
        ScoutMode(int index){ this.index = index; }
        public int getIndex(){ return this.index; }
        public static ScoutMode fromIndex(int index) {
            for (ScoutMode m : ScoutMode.values()) {
                if (m.getIndex() == index) return m;
            }
            return IDLE;
        }
    }

    private ScoutMode scoutMode = ScoutMode.IDLE;

    public ScoutEntity(EntityType<? extends AbstractRecruitEntity> entityType, Level world) {
        super(entityType, world);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OWNER_NAME, "");
        this.entityData.define(SCOUT_MODE_ID, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(2, new UseShield(this));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("scoutTaskState", this.scoutMode.getIndex());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("scoutTaskState")) {
            this.setTaskState(ScoutMode.fromIndex(nbt.getInt("scoutTaskState")));
        } else if (nbt.contains("taskState")) {
            this.setTaskState(ScoutMode.fromIndex(nbt.getInt("taskState")));
        }
    }

    public static AttributeSupplier.Builder setAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(ForgeMod.SWIM_SPEED.get(), 0.3D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.1D)
                .add(Attributes.ATTACK_DAMAGE, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 200.0D)
                .add(ForgeMod.ENTITY_REACH.get(), 0D)
                .add(Attributes.ATTACK_SPEED);
    }

    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficultyInstance, MobSpawnType reason, @Nullable SpawnGroupData data, @Nullable CompoundTag nbt) {
        SpawnGroupData ilivingentitydata = super.finalizeSpawn(world, difficultyInstance, reason, data, nbt);
        ((GroundPathNavigation)this.getNavigation()).setCanOpenDoors(true);
        this.populateDefaultEquipmentEnchantments(random, difficultyInstance);
        this.initSpawn();
        return ilivingentitydata;
    }

    @Override
    public void initSpawn() {
        this.setDropEquipment();
        this.setPersistenceRequired();
        if(this.getOwner() != null) this.setOwnerName(this.getOwner().getName().getString());
        AbstractRecruitEntity.applySpawnValues(this);
    }

    public Predicate<ItemEntity> getAllowedItems(){
        return ALLOWED_ITEMS;
    }

    @Override
    public boolean canHoldItem(ItemStack itemStack){
        return !(itemStack.getItem() instanceof CrossbowItem || itemStack.getItem() instanceof BowItem);
    }

    @Override
    public AbstractRecruitEntity get() {
        return this;
    }

    @Override
    public void openSpecialGUI(Player player) {
    }

    public byte getTaskState() {
        return entityData.get(SCOUT_MODE_ID);
    }
    
    public String getOwnerName() {
        return entityData.get(OWNER_NAME);
    }

    public void setOwnerName(String name) {
        entityData.set(OWNER_NAME, name);
    }
    
    public void setTaskState(ScoutMode mode) {
        this.entityData.set(SCOUT_MODE_ID, (byte) mode.getIndex());
        this.scoutMode = mode;
    }
    
    public boolean isAtMission() {
        return this.scoutMode != ScoutMode.IDLE;
    }

    public static class State { 
        public static final ScoutMode IDLE = ScoutMode.IDLE;
        public static final ScoutMode SCOUTING = ScoutMode.SCOUTING;
        public static final ScoutMode ADVANCED_SCOUTING = ScoutMode.ADVANCED_SCOUTING;
        public static final ScoutMode COMMANDING = ScoutMode.COMMANDING;
        public static ScoutMode fromIndex(int i) { return ScoutMode.fromIndex(i); }
    }

    public void startTask(ScoutMode mode) {
        setTaskState(mode);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.getCommandSenderWorld().isClientSide()) {
            switch (this.scoutMode) {
                case SCOUTING -> tickScouting();
                case ADVANCED_SCOUTING -> tickAdvancedScouting();
                case COMMANDING -> tickCommanding();
                default -> {}
            }
        }
    }

    private void tickScouting() {
        if (this.tickCount % 400 == 0) {
            scanAndReportEnemies(0, 32, false);
        }
        if (this.tickCount % 400 == 0) {
            scanAndReportEnemies(33, 128, true);
        }
    }

	private void tickAdvancedScouting() {
		// [구간 1] 중장거리 (128 ~ 160블록)
		// 설명: 시뮬레이션 거리 경계선. 적이 움직일 가능성이 있음.
		// 주기: 30초 (600틱) - 기존 유지
		if (this.tickCount % 600 == 0) {
			scanAndReportEnemies(128, 160, true);
		}

		// [구간 2] 초장거리 (160 ~ 200블록)
		// 설명: 시뮬레이션 거리 밖. 적이 100% 멈춰있음(Frozen). 급하지 않음.
		// 주기: 60초 (1200틱) - 더 느리게 검사해서 서버 부담 줄임
		// 포인트: '+ 300'을 넣어 위쪽 스캔과 겹치지 않게 엇갈려서 실행 (렉 분산)
		if ((this.tickCount + 300) % 1200 == 0) {
			scanAndReportEnemies(160, 200, true);
		}
	}

/**
     * [수정됨] 지휘 모드 (Commanding)
     * - 주기: 5초 (100틱)
     * - 기능 1: 0틱에 적(90~150칸) 탐지 후 사격 개시
     * - 기능 2: 90틱(4.5초)에 무조건 사격 중지 명령 (리셋)
     */
	private void tickCommanding() {
        // [Step 1] 사이클 시작 (0초): 적 탐지 및 명령 하달
        if (this.tickCount % 100 == 0) {
            
            // 1-1. [Danger Close] 내 주변 90칸 내에 '보이는' 적이 있는지 먼저 확인
            // 등잔 밑이 어두우면 안 되므로 최우선 체크
            LivingEntity closeThreat = findNearestEnemyInCommandRange(0.0, 90.0);

            if (closeThreat != null) {
                // 적이 너무 가까움! 전략 사격 즉시 취소 -> 병사들이 알아서 대응하게 함
                this.broadcastStrategicFireCommand(null, false);
                return; // [중요] 원거리 탐색 하지 않고 즉시 종료
            }

            // 1-2. [Long Range] 주변이 안전할 때만 90~150칸 밖의 적 탐색
            LivingEntity distantTarget = findNearestEnemyInCommandRange(90.0, 150.0);

            if (distantTarget != null) {
                // [정밀 조준] 발 밑이 아니라, 키의 75% 지점(가슴~머리) 좌표 계산
                double aimHeight = distantTarget.getBbHeight() * 0.75D; 
				BlockPos targetPos = new BlockPos(
					(int) (distantTarget.getX() + aimHeight), 
					(int) (distantTarget.getY() + aimHeight), 
					(int) (distantTarget.getZ() + aimHeight)
				);

                // 사격 명령 전송
                this.broadcastStrategicFireCommand(targetPos, true);

                // 플레이어에게 보고 (30초 쿨타임)
                if (this.tickCount - this.lastCombatMsgTick >= 600) {
                    sendCombatStatusToOwner();
                    this.lastCombatMsgTick = this.tickCount;
                }
            } else {
                // 범위 내 적이 아예 없음 -> 사격 중지
                this.broadcastStrategicFireCommand(null, false);
            }
        }

        // [Step 2] 사이클 종료 직전 (4.5초): 강제 사격 취소 (Pulse Reset)
        // 적이 있건 없건 무조건 실행되어 무한 난사를 방지함
        if (this.tickCount % 100 == 90) {
            this.broadcastStrategicFireCommand(null, false);
        }
    }

	private void broadcastStrategicFireCommand(BlockPos targetPos, boolean shouldFire) {
			double commandRange = 32.0D; // 필요하다면 이 범위를 늘려서 멀리 있는 같은 분대원에게도 명령할 수 있습니다.
			AABB searchBox = this.getBoundingBox().inflate(commandRange);

			List<AbstractRecruitEntity> artilleryUnits = this.getCommandSenderWorld()
					.getEntitiesOfClass(AbstractRecruitEntity.class, searchBox, (ally) -> 
						ally.isAlive() && 
						ally instanceof IStrategicFire && 
						ally.getTeam() == this.getTeam() && 
						ally.getOwnerUUID() != null &&
						ally.getOwnerUUID().equals(this.getOwnerUUID()) &&
						// [추가된 부분] 스카웃과 같은 그룹(조직)인지 확인
						ally.getGroup() == this.getGroup()
					);

			for (AbstractRecruitEntity unit : artilleryUnits) {
				IStrategicFire artillery = (IStrategicFire) unit;
				if (shouldFire && targetPos != null) {
					artillery.setStrategicFirePos(targetPos);
					artillery.setShouldStrategicFire(true);
				} else {
					artillery.setShouldStrategicFire(false);
					artillery.setStrategicFirePos(null);
				}
		}
    }
/**
     * [신규 추가] 지휘 범위 내에서 가장 가까운 적을 찾는 최적화된 메서드
     * - 클러스터 계산 X, 채팅 보고 X
     * - 오직 거리 비교만 수행 (가벼움)
     */
/**
     * [수정됨] 최소/최대 사거리를 모두 고려하여 가장 가까운 적을 찾음
     */
/**
     * [필수 헬퍼] 거리 + 시야(벽)까지 체크하여 가장 가까운 적을 찾음
     * ※ 벽 투시 방지(canSeeTargetLongRange) 로직이 여기에 포함되어 있습니다.
     */
    private LivingEntity findNearestEnemyInCommandRange(double minRange, double maxRange) {
        // 1. 범위 내 엔티티 가져오기 (탐색 박스는 최대 사거리 기준)
        AABB searchBox = this.getBoundingBox().inflate(maxRange);
        
        List<LivingEntity> enemies = this.getCommandSenderWorld().getEntitiesOfClass(
            LivingEntity.class, 
            searchBox, 
            this::shouldTargetEntity
        );

        LivingEntity closest = null;
        double minDistanceSqr = Double.MAX_VALUE;
        
        // 성능 최적화를 위해 제곱값 미리 계산
        double minRangeLimitSqr = minRange * minRange;
        double maxRangeLimitSqr = maxRange * maxRange;

        for (LivingEntity enemy : enemies) {
            double distSqr = this.distanceToSqr(enemy);

            // 2. [1차 필터] 거리 체크 (빠름)
            if (distSqr >= minRangeLimitSqr && distSqr <= maxRangeLimitSqr) {
                
                // 현재 찾은 최단 거리보다 더 가까운 경우에만 정밀 검사
                if (distSqr < minDistanceSqr) {
                    
                    // 3. [2차 필터] 시야(Line of Sight) 체크 (무거움)
                    // "벽을 뚫고 보지 않도록" 레이트레이싱 수행
                    if (canSeeTargetLongRange(enemy)) { 
                        minDistanceSqr = distSqr;
                        closest = enemy;
                    }
                }
            }
        }
        return closest;
    }

    /**
     * [신규 추가] 간소화된 교전 보고 메세지 전송
     */
    private void sendCombatStatusToOwner() {
        if (getOwner() == null) return;

        BlockPos myPos = this.blockPosition();
        
        // 메세지: "[스카웃이름]: 교전 중! 위치: (X, Y, Z)"
        MutableComponent message = Component.literal("Engaging Enemy at ")
            .withStyle(ChatFormatting.RED)
            .append(Component.literal("(" + myPos.getX() + ", " + myPos.getY() + ", " + myPos.getZ() + ")")
            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));

        sendMessageToOwner(message);
    }
/**
     * [중요] 엔티티가 월드에서 제거될 때 (사망, 디스폰 등) 실행되는 메서드
     * 안전장치: 내가 사라지면 아군에게 즉시 사격 중지 명령을 내림
     */
    @Override
    public void remove(RemovalReason reason) {
        // 서버 사이드에서만 실행
        if (!this.getCommandSenderWorld().isClientSide()) {
            // "나 죽는다! 사격 중지!"
            this.broadcastStrategicFireCommand(null, false);
        }
        
        // 원래 삭제 로직 실행 (이게 없으면 안 지워짐)
        super.remove(reason);
    }

    private boolean shouldTargetEntity(LivingEntity target) {
        if (target == null || target == this || !target.isAlive() || target.isSpectator()) return false;
        
        if (this.getOwnerUUID() != null && target.getUUID().equals(this.getOwnerUUID())) return false;

        if (target instanceof AbstractRecruitEntity recruitTarget) {
            if (this.getOwnerUUID() != null && this.getOwnerUUID().equals(recruitTarget.getOwnerUUID())) {
                return false; 
            }
        }

        if (this.getTeam() != null && target.getTeam() != null) {
            if (this.getTeam().isAlliedTo(target.getTeam())) return false;
            if (this.getTeam().getName().equals(target.getTeam().getName())) return false;
        }

        if (!(target instanceof Player) && !(target instanceof AbstractRecruitEntity)) {
            return false;
        }

        int aggressionState = this.getState(); 

        if (aggressionState == 3) return false; 

        if (aggressionState == 2) { 
            return true;
        }

        Team myTeam = this.getTeam(); 
        String targetTeamID = null;
        
        if (target.getTeam() != null) {
            targetTeamID = target.getTeam().getName();
        }

        RecruitsDiplomacyManager.DiplomacyStatus relation = RecruitsDiplomacyManager.DiplomacyStatus.NEUTRAL;
        
        if (myTeam != null && targetTeamID != null) {
            // [수정됨] recruitsDiplomacyManager 사용
            relation = TeamEvents.recruitsDiplomacyManager.getRelation(myTeam.getName(), targetTeamID);
        }

        if (aggressionState == 0) {
            return relation == RecruitsDiplomacyManager.DiplomacyStatus.ENEMY;
        }

        if (aggressionState == 1) {
            return relation != RecruitsDiplomacyManager.DiplomacyStatus.ALLY;
        }

        return false;
    }

    private List<LivingEntity> scanAndReportEnemies(double minRange, double maxRange, boolean requireLineOfSight) {
        if (getOwner() == null || this.getCommandSenderWorld().isClientSide()) return new ArrayList<>();

        List<LivingEntity> allDetected = new ArrayList<>();
        double minRangeSqr = minRange * minRange;
        double maxRangeSqr = maxRange * maxRange;

        List<ServerPlayer> players = this.getCommandSenderWorld().getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(maxRange), target -> {
            double d = this.distanceToSqr(target);
            if (d < minRangeSqr || d > maxRangeSqr) return false;
            if (requireLineOfSight && !canSeeTargetLongRange(target)) return false;
            return shouldTargetEntity(target);
        });

        for (ServerPlayer player : players) {
            RecruitsPlayerInfo playerInfo = new RecruitsPlayerInfo(player.getUUID(), player.getName().getString(), TeamEvents.recruitsTeamManager.getTeamByStringID(player.getTeam() != null ? player.getTeam().getName() : ""));
            ScoutingResult result = new ScoutingResult(playerInfo, 0, (int)Math.sqrt(this.distanceToSqr(player)), player.blockPosition());
            result.sendInfo(this);
            allDetected.add(player);
        }

        List<AbstractRecruitEntity> recruits = this.getCommandSenderWorld().getEntitiesOfClass(AbstractRecruitEntity.class, this.getBoundingBox().inflate(maxRange), target -> {
            double d = this.distanceToSqr(target);
            if (d < minRangeSqr || d > maxRangeSqr) return false;
            if (requireLineOfSight && !canSeeTargetLongRange(target)) return false;
            return shouldTargetEntity(target);
        });

        List<AbstractRecruitEntity> processList = new ArrayList<>(recruits);
        final double SQUAD_RADIUS_SQUARED = 32 * 32;

        while (!processList.isEmpty()) {
            AbstractRecruitEntity seed = processList.get(0);
            List<AbstractRecruitEntity> squad = processList.stream()
                    .filter(other -> seed.getTeam() == other.getTeam() && seed.distanceToSqr(other) < SQUAD_RADIUS_SQUARED)
                    .collect(Collectors.toList());

            if (!squad.isEmpty()) {
                Vec3 vec = FormationUtils.getCenterOfPositions(new ArrayList<>(squad), (ServerLevel) this.getCommandSenderWorld());
                BlockPos centerPos = new BlockPos((int)vec.x, (int)vec.y, (int)vec.z);
                ScoutingResult result = new ScoutingResult(squad.get(0).getTeam() != null ? squad.get(0).getTeam().getName() : "No Team", squad.size(), (int)Math.sqrt(this.distanceToSqr(vec)), centerPos);
                result.sendInfo(this);
            }
            processList.removeAll(squad);
        }
        
        allDetected.addAll(recruits);
        return allDetected;
    }

	private boolean canSeeTargetLongRange(Entity target) {
			Vec3 start = this.getEyePosition();
			Vec3 end = target.getEyePosition();
			
			// 내 눈(start)에서 적의 눈(end)까지 레이저를 쏴서 막히는 블록이 있는지 검사
			return this.getCommandSenderWorld().clip(new net.minecraft.world.level.ClipContext(
					start, end, 
					net.minecraft.world.level.ClipContext.Block.COLLIDER, 
					net.minecraft.world.level.ClipContext.Fluid.NONE, 
					this
			)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
	}

    private void sendMessageToOwner(Component message) {
        if (getOwner() != null) {
            MutableComponent prefix = Component.literal(this.getName().getString() + ": ").withStyle(ChatFormatting.GOLD);
            this.getOwner().sendSystemMessage(prefix.append(message));
        }
    }

    private int countRecruits(java.util.UUID uuid) { return 0; }

    private static class ScoutingResult {
        RecruitsPlayerInfo playerInfo;
        int recruitsCount;
        int distance;
        BlockPos coordinates;
        String team;

        ScoutingResult(RecruitsPlayerInfo playerInfo, int recruits, int distance, BlockPos coordinates){
            this.playerInfo = playerInfo; this.recruitsCount = recruits; this.distance = distance; this.coordinates = coordinates;
        }
        ScoutingResult(String team, int recruits, int distance, BlockPos coordinates){
            this((RecruitsPlayerInfo) null, recruits, distance, coordinates); this.team = team;
        }
        public void sendInfo(ScoutEntity scout) {
            Component coords = Component.literal("at (" + coordinates.getX() + ", " + coordinates.getY() + ", " + coordinates.getZ() + ")").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            Component dist = Component.literal(distance + "m").withStyle(ChatFormatting.RED);
            
            if (playerInfo != null) {
                 scout.sendMessageToOwner(Component.literal("Found Player: ").append(playerInfo.getName()).append(" ").append(dist).append(" ").append(coords));
            } else if (team != null) {
                 scout.sendMessageToOwner(Component.literal("Found Squad: " + recruitsCount + " units of " + team + " ").append(dist).append(" ").append(coords));
            }
        }
    }
}