package com.talhanation.recruits.entities;
//ezgi&talha kantar

import com.talhanation.recruits.*;
import com.talhanation.recruits.compat.IWeapon;
import com.talhanation.recruits.config.RecruitsClientConfig;
import com.talhanation.recruits.config.RecruitsServerConfig;
import com.talhanation.recruits.entities.ai.*;
import com.talhanation.recruits.entities.ai.async.AsyncManager;
import com.talhanation.recruits.entities.ai.async.AsyncTaskWithCallback;
import com.talhanation.recruits.entities.ai.compat.BlockWithWeapon;
import com.talhanation.recruits.entities.ai.navigation.RecruitPathNavigation;
import com.talhanation.recruits.init.ModItems;
import com.talhanation.recruits.inventory.DebugInvMenu;
import com.talhanation.recruits.inventory.RecruitHireMenu;
import com.talhanation.recruits.inventory.RecruitInventoryMenu;
import com.talhanation.recruits.network.*;
import com.talhanation.recruits.world.RecruitsDiplomacyManager;
import com.talhanation.recruits.world.RecruitsTeam;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.minecraftforge.common.Tags;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import com.talhanation.recruits.entities.ai.navigation.RecruitsOpenDoorGoal;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.world.item.ItemStack;

import mekanism.common.item.ItemNutritionalPasteBucket;

import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.FluidStack;
import java.util.Optional;

import net.minecraft.tags.DamageTypeTags;

//jeg
import ttv.migami.jeg.common.Gun;
import ttv.migami.jeg.item.GunItem;

import com.talhanation.recruits.entities.ai.controller.LandVehicleController;

public abstract class AbstractRecruitEntity extends AbstractInventoryEntity{
    private static final EntityDataAccessor<Integer> DATA_REMAINING_ANGER_TIME = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FOLLOW_STATE = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SHOULD_FOLLOW = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SHOULD_BLOCK = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SHOULD_MOUNT = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SHOULD_PROTECT = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SHOULD_HOLD_POS = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SHOULD_MOVE_POS = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<BlockPos>> HOLD_POS = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> MOVE_POS = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> UPKEEP_POS = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Boolean> LISTEN = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_FOLLOWING = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> MOUNT_ID = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> PROTECT_ID = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> GROUP = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> XP = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> KILLS = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FLEEING = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> HUNGER = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MORAL = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER_ID = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> OWNED = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> COST = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> UPKEEP_ID = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> COLOR = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> BIOME = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> SHOULD_REST = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SHOULD_RANGED = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.BOOLEAN);
		// 1. 필드 선언부에 추가 (다른 EntityDataAccessor 아래에)
	private static final EntityDataAccessor<Boolean> ONLY_PVP = SynchedEntityData.defineId(AbstractRecruitEntity.class, EntityDataSerializers.BOOLEAN);
    public int blockCoolDown;
    public boolean needsTeamUpdate = true;
    public boolean forcedUpkeep;
    public int dismount = 0;
    public int upkeepTimer = 0;
    public int mountTimer = 0;
    public int despawnTimer = -1;
    public boolean reachedMovePos;
    public int attackCooldown = 0;
    public int paymentTimer;
    public boolean rotate;
    public float ownerRot;
    public int formationPos = -1;
    private int maxFallDistance;
    public Vec3 holdPosVec;
    public boolean isInFormation;
    public boolean needsColorUpdate = true;
    public float moveSpeed = 1;
    public TargetingConditions targetingConditions;
		// 데미지 타입 
	private DamageSource handlingDamageSource;
	protected LandVehicleController landVehicleController;
	// [수정됨] 외부에서 접근 및 수정이 가능하도록 public 필드로 승격
    public double baseSearchRange = 90.0D;       // 기본 병사 탐지 거리
    public double leaderSearchRange = 150.0D;    // 지휘관 탐지 거리
    public double giveUpFactor = 1.1D;           // 추격 포기 거리 배율 (감지 거리의 1.1배)
	private LivingEntity cachedProtectingMob;
	private int protectingMobCacheTick = -1;

    public AbstractRecruitEntity(EntityType<? extends AbstractInventoryEntity> entityType, Level world) {
        super(entityType, world);
        this.xpReward = 6;
        this.navigation = this.createNavigation(world);
        this.targetingConditions = TargetingConditions.forCombat().ignoreInvisibilityTesting().selector(this::shouldAttack);
        this.setMaxUpStep(1.25F);
        this.setMaxFallDistance(1);
		this.landVehicleController = new LandVehicleController(this);
    }

    ///////////////////////////////////NAVIGATION/////////////////////////////////////////
    @NotNull
    protected PathNavigation createNavigation(@NotNull Level level) {
        return new RecruitPathNavigation(this, level);
    }

    public @NotNull PathNavigation getNavigation() {
        return super.getNavigation();
    }

    public void rideTick() {
        super.rideTick();
    }

    public double getMyRidingOffset() {
        return -0.35D;
    }

    public int getMaxFallDistance() {
        return maxFallDistance;
    }

    public void setMaxFallDistance(int x){
        this.maxFallDistance = x;
    }

    ///////////////////////////////////TICK/////////////////////////////////////////
    // @Override
    public void aiStep(){
        super.aiStep();
        updateSwingTime();
        updateShield();
        if(this instanceof IRangedRecruit  && this.tickCount % 20 == 0) pickUpArrows();
        if(needsTeamUpdate) updateTeam();
        if(needsColorUpdate && this.getTeam() != null) updateColor(this.getTeam().getName());
    }
	@Override
    public void tick() {
        super.tick();
        if(this.level().isClientSide()) return;

        if(despawnTimer > 0) despawnTimer--;
        if(despawnTimer == 0) recruitCheckDespawn();

		if(RecruitsServerConfig.RecruitsPayment.get()){
			if(paymentTimer > 0) paymentTimer--;
			if(paymentTimer == 0) {
				int wage = RecruitsServerConfig.RecruitsPaymentAmount.get();
				boolean paidFromBank = false;

				// 1. 병사가 파벌에 속해 있다면 파벌 금고(가상 계좌)에서 우선 결제
				if (this.getTeam() != null && !this.level().isClientSide()) {
					RecruitsTeam team = TeamEvents.recruitsTeamManager.getTeamByStringID(this.getTeam().getName());
					if (team != null && team.withdraw(wage)) {
						paidFromBank = true;
						TeamEvents.recruitsTeamManager.save((ServerLevel) this.level());
						this.resetPaymentTimer(); // 지불 성공 즉시 타이머 리셋
					}
				}

				// 2. 금고에서 못 냈을 때만 기존 방식대로 보급 상자를 찾아가게 함
				if (!paidFromBank) {
					if(getUpkeepPos() != null || getUpkeepUUID() != null) {
						forcedUpkeep = true;
					} else {
						checkPayment(this.getInventory());
					}
				}
			}
		}

        if(getMountTimer() > 0) setMountTimer(getMountTimer() - 1);
        if(getUpkeepTimer() > 0) setUpkeepTimer(getUpkeepTimer() - 1);
// ★ 여기 추가: 자연스러운 허기 감소 로직 실행
        if(this.tickCount % 20 == 0) { // 1초마다 실행
            this.updateHunger(); 
        }

        if(getHunger() >= 70F && getHealth() < getMaxHealth()){
            this.heal(1.0F/50F);
        }

        if(this.reachedMovePos){
            this.setFollowState(2);
            this.reachedMovePos = false;
        }

        if(this.attackCooldown > 0) this.attackCooldown--;


		if(this.isAlive() && this.getState() != 3 && (this.tickCount + getTickPhase()) % getTargetSearchInterval() == 0){
			searchForTargetsAsync();
		}
			// [신규 기능] 나침반 텔레포트 로직 호출 (10틱마다 검사)
		if (this.tickCount % 10 == 0) {
			this.checkCompassTeleport();
		}

        LivingEntity currentTarget = this.getTarget();
        
        if (currentTarget != null) {
            // 1. 타겟이 죽거나 월드에서 제거되었으면 타겟 해제
            if (currentTarget.isDeadOrDying() || currentTarget.isRemoved()) {
                this.setTarget(null);
            } 
            // 2. 타겟이 인식 범위를 벗어났으면 추격 포기 (Leashing 로직)
            else {
                // [변경됨] 하드코딩 제거 -> 멤버 변수 사용
                double limitRange = this.baseSearchRange; 

                if (this instanceof ScoutEntity) {
                    limitRange = this.baseSearchRange;
                }
                else if (this instanceof AbstractLeaderEntity) {
                    limitRange = this.leaderSearchRange; // 지휘관 변수 사용
                }
                else if (this instanceof IRangedRecruit) { 
                    limitRange = this.baseSearchRange;
                }
                else if (this instanceof HorsemanEntity) {
                    limitRange = this.baseSearchRange;
                }
                else if (this instanceof MessengerEntity) {
                    limitRange = this.baseSearchRange;
                }

                // [변경됨] 하드코딩 1.1D 제거 -> 멤버 변수 giveUpFactor 사용
                // 인식 거리보다 설정된 배율만큼 더 멀어지면 추격 포기
                double giveUpDistance = limitRange * this.giveUpFactor;

                // 거리 제곱 비교 (최적화)
                if (this.distanceToSqr(currentTarget) > (giveUpDistance * giveUpDistance)) {
                    this.setTarget(null);
                }
            }
        }
		
// [문제의 원인 해결]
        // 병사가 LandVehicle에 타고 있다면 컨트롤러를 작동시킵니다.
        if (this.getVehicle() instanceof com.talhanation.smallships.world.entity.ship.LandVehicle) {
            
            // [★수정] 대포(LandCannon)에 타고 있고 + 타겟이 있어서 조준 중이라면?
            // 운전 컨트롤러(landVehicleController)를 끄고, 공격 Goal(RecruitLandCannonAttackGoal)이 회전을 전담하게 합니다.
            boolean isManningCannon = this.getVehicle() instanceof com.talhanation.smallships.world.entity.ship.LandCannonEntity;
            boolean isAiming = this.getTarget() != null && this.getTarget().isAlive();

            // 대포를 잡고 조준 중일 때는 컨트롤러 틱을 실행하지 않음 (회전 간섭 방지)
            if (!(isManningCannon && isAiming)) {
                this.landVehicleController.tick();
            }
        }
		// ★ [추가됨] 구덩이 탈출을 위한 제한적 벽 타기 로직 호출
		//this.checkAndPerformWallClimb();
    }
	// 엔티티 ID를 기준으로 0~59틱의 오프셋을 부여하여 연산 분산
	private int getTickPhase() {
		return Math.floorMod(this.getId(), 60);
	}

	// 교전 중인지 여부에 따라 타깃 탐색 주기를 동적으로 조절
	private int getTargetSearchInterval() {
		LivingEntity target = this.getTarget();
		if (target != null && target.isAlive() && !target.isRemoved()) {
			return 60; // 이미 교전 중이면 3초(60틱)마다 스캔
		}
		return 20; // 타깃이 없으면 1초(20틱)마다 스캔
	}
/**
 * 병사가 이동 중 벽(구덩이)에 막혔을 때,
 * 앞의 벽이 단단한 블록이고 그 위가 뚫려있다면 벽을 타고 오릅니다.
 */
	private void checkAndPerformWallClimb() {
		// 1. [추가됨] Hold Position(대기, State 2) 모드일 때는 작동 금지
		// (참호나 벙커 등에서 위치 사수 중일 때 벽을 타고 튀어 나가는 것을 방지)
		if (this.getFollowState() == 2) return;

		// 2. 트리거 체크: 벽에 부딪혔거나(horizontalCollision), 이동 중인데 제자리걸음인 경우
		boolean isStuck = this.horizontalCollision || 
						  (!this.getNavigation().isDone() && this.getDeltaMovement().horizontalDistanceSqr() < 1.0E-6);

		if (!isStuck) return;
		
		// 이미 웅크리고 있거나, 타고 있는 중이면 실행 안함
		if (this.isCrouching() || this.isPassenger()) return;

		// 3. 바라보는 방향의 바로 앞 블록 확인
		net.minecraft.core.Direction facing = this.getDirection();
		BlockPos currentPos = this.blockPosition();
		BlockPos frontPos = currentPos.relative(facing);

		// 4. 벽 상태 분석
		Level level = this.level();
		
		// 높이 1 (다리~몸통): 여기가 막혀 있어야 "벽"이라고 인지함
		BlockPos wallBase = frontPos.above(0); 
		BlockPos wallMid = frontPos.above(1); // 높이 2 (머리)
		
		// 적어도 1칸 또는 2칸 높이에 '단단한 블록'이 있어야 함 (허공에 점프 방지)
		boolean hasSolidWall = level.getBlockState(wallBase).blocksMotion() || 
							   level.getBlockState(wallMid).blocksMotion();

		if (!hasSolidWall) return;

		// 5. 탈출구(착지 지점) 스캔 (높이 2칸~3칸)
		boolean canClimb = false;
		
		// 머리 높이(2칸) 혹은 점프 높이(3칸) 중 하나라도 뚫려있으면 등반 가능
		for (int i = 1; i <= 3; i++) {
			BlockPos checkPos = frontPos.above(i);
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(checkPos);
			
			// 이동을 방해하지 않는 블록(공기 등)을 발견하면 거기로 올라감
			if (!state.blocksMotion() || state.getCollisionShape(level, checkPos).isEmpty()) {
				canClimb = true;
				break;
			}
		}

		// 6. 등반 실행
		if (canClimb) {
			Vec3 motion = this.getDeltaMovement();
			
			// 점프와 유사한 강한 상승 속도 부여 (0.2 -> 0.42)
			double climbSpeed = 0.42D; 
			
			// 벽 쪽으로 밀어붙이는 힘
			double pushForce = 0.1D;
			double newX = facing.getStepX() * pushForce;
			double newZ = facing.getStepZ() * pushForce;

			// 기존 수평 속도가 있다면 유지하되, 벽 쪽으로 최소한의 힘은 가함
			if (Math.abs(motion.x) > 0.05) newX = motion.x;
			if (Math.abs(motion.z) > 0.05) newZ = motion.z;

			this.setDeltaMovement(newX, climbSpeed, newZ);
			
			// 클라이언트 동기화
			this.hasImpulse = true; 
			
			// 낙하 데미지 초기화
			this.fallDistance = 0.0F;
		}
	}

	private void searchForTargetsAsync() {
        if (!(this.getCommandSenderWorld() instanceof ServerLevel serverLevel)) return;

        // --------------------------------------------------------
        // [변경됨] 엔티티 타입에 따라 동적으로 탐지 범위(Search Range) 설정
        // --------------------------------------------------------
        // 하드코딩 제거 -> 멤버 변수 사용
        double searchRange = this.baseSearchRange;

        // 1. 정찰병
        if (this instanceof ScoutEntity) {
            searchRange = this.baseSearchRange; 
        }
        // 2. 지휘관 계열 (CaptainEntity, PatrolLeaderEntity 포함)
        else if (this instanceof AbstractLeaderEntity) {
            searchRange = this.leaderSearchRange; // 지휘관 변수 사용
        }
        // 3. 원거리 유닛 (Bowman, CrossBowman, Nomad 포함) - 인터페이스 체크
        else if (this instanceof IRangedRecruit) {
            searchRange = this.baseSearchRange;
        }
        // 4. 기병 (Horseman)
        else if (this instanceof HorsemanEntity) {
            searchRange = this.baseSearchRange;
        }
        // 5. 전령 (전투원 아님, 최소 방어)
        else if (this instanceof MessengerEntity) {
            searchRange = this.baseSearchRange;
        }

        // 설정된 범위를 적용
        AABB searchBox = this.getBoundingBox().inflate(searchRange);
        // --------------------------------------------------------

        List<LivingEntity> nearby = serverLevel.getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                entity -> entity != this
        );

        //MULTI THREADED
        Supplier<List<LivingEntity>> findTargetsTask = () -> {
            List<LivingEntity> copy = new ArrayList<>(nearby);
            copy.removeIf(potTarget -> !targetingConditions.test(this, potTarget));
            copy.sort(Comparator.comparingDouble(e -> e.distanceToSqr(this)));
            return copy.stream().limit(12).toList();
        };

        Consumer<List<LivingEntity>> handleTargets = targets -> {
            if (!targets.isEmpty()) {
                this.setTarget(targets.get(this.getRandom().nextInt(targets.size())));
            }
        };

        AsyncManager.executor.execute(new AsyncTaskWithCallback<>(findTargetsTask, handleTargets, serverLevel));
    }
		// [AbstractRecruitEntity.java] 내부에 추가
	public void setBaseSearchRange(double range) {
		this.baseSearchRange = range;
	}

	public double getBaseSearchRange() {
		return this.baseSearchRange;
	}

    private void recruitCheckDespawn() {
        if(this.isOwned()) return;
        Entity entity = this.getCommandSenderWorld().getNearestPlayer(this, -1.0D);

        if (entity != null) {
            double d0 = entity.distanceToSqr(this);
            int k = this.getType().getCategory().getNoDespawnDistance();
            int l = k * k;

            if (this.random.nextInt(800) == 0 && d0 > (double) l) {
                if(this.getVehicle() instanceof LivingEntity livingMount) livingMount.discard();
                this.discard();
            }
        }
    }

    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance diff, MobSpawnType reason, @Nullable SpawnGroupData spawnData, @Nullable CompoundTag nbt) {
        this.setRandomSpawnBonus();
        this.createNavigation(world.getLevel());
        return spawnData;
    }
    public void setRandomSpawnBonus(){
        getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier("heath_bonus", this.random.nextDouble() * 0.5D, AttributeModifier.Operation.MULTIPLY_BASE));
        getAttribute(Attributes.ATTACK_DAMAGE).addPermanentModifier(new AttributeModifier("attack_bonus", this.random.nextDouble() * 0.5D, AttributeModifier.Operation.MULTIPLY_BASE));
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).addPermanentModifier(new AttributeModifier("knockback_bonus", this.random.nextDouble() * 0.1D, AttributeModifier.Operation.MULTIPLY_BASE));
        getAttribute(Attributes.MOVEMENT_SPEED).addPermanentModifier(new AttributeModifier("speed_bonus", this.random.nextDouble() * 0.1D, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    public void setDropEquipment(){
        this.dropEquipment();
    }

    ////////////////////////////////////REGISTER////////////////////////////////////

    protected void registerGoals() {
        this.goalSelector.addGoal(4, new BlockWithWeapon(this));
        this.goalSelector.addGoal(0, new RecruitFloatGoal(this));
        this.goalSelector.addGoal(1, new RecruitQuaffGoal(this));
        this.goalSelector.addGoal(1, new FleeTNT(this));
        this.goalSelector.addGoal(1, new FleeFire(this));
        this.goalSelector.addGoal(6, new RecruitsOpenDoorGoal(this, true));
        this.goalSelector.addGoal(1, new RecruitProtectEntityGoal(this));
        this.goalSelector.addGoal(0, new RecruitEatGoal(this));
        this.goalSelector.addGoal(5, new RecruitUpkeepPosGoal(this));
        this.goalSelector.addGoal(6, new RecruitUpkeepEntityGoal(this));
        this.goalSelector.addGoal(3, new RecruitMountEntity(this));
        this.goalSelector.addGoal(3, new RecruitDismountEntity(this));
        this.goalSelector.addGoal(3, new RecruitMoveToPosGoal(this, 1.05D));
        this.goalSelector.addGoal(2, new RecruitFollowOwnerGoal(this, 1.05D, 300, 100));
        this.goalSelector.addGoal(2, new RecruitMeleeAttackGoal(this, 1.05D, this.getMeleeStartRange()));
        this.goalSelector.addGoal(3, new RecruitHoldPosGoal(this, 32.0F));
        //this.goalSelector.addGoal(7, new RecruitDodgeGoal(this));
        this.goalSelector.addGoal(4, new RestGoal(this));
        this.goalSelector.addGoal(10, new RecruitWanderGoal(this));
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 2.0F));
        this.goalSelector.addGoal(12, new RandomLookAroundGoal(this));
        //this.goalSelector.addGoal(13, new RecruitPickupWantedItemGoal(this));

        this.targetSelector.addGoal(1, new RecruitProtectHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new RecruitOwnerHurtByTargetGoal(this));

        this.targetSelector.addGoal(3, (new RecruitHurtByTargetGoal(this)).setAlertOthers());
        this.targetSelector.addGoal(4, new RecruitOwnerHurtTargetGoal(this));

        //this.targetSelector.addGoal(7, new RecruitDefendVillageFromPlayerGoal(this));
    }

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_REMAINING_ANGER_TIME, 0);
        this.entityData.define(GROUP, Optional.empty());
        this.entityData.define(SHOULD_FOLLOW, false);
        this.entityData.define(SHOULD_BLOCK, false);
        this.entityData.define(SHOULD_MOUNT, false);
        this.entityData.define(SHOULD_PROTECT, false);
        this.entityData.define(SHOULD_HOLD_POS, false);
        this.entityData.define(SHOULD_MOVE_POS, false);
        this.entityData.define(FLEEING, false);
        this.entityData.define(STATE, 0);
        this.entityData.define(VARIANT, 0);
        this.entityData.define(XP, 0);
        this.entityData.define(KILLS, 0);
        this.entityData.define(LEVEL, 1);
        this.entityData.define(FOLLOW_STATE, 0);
        this.entityData.define(HOLD_POS, Optional.empty());
        this.entityData.define(UPKEEP_POS, Optional.empty());
        this.entityData.define(MOVE_POS, Optional.empty());
        this.entityData.define(LISTEN, true);
        this.entityData.define(MOUNT_ID, Optional.empty());
        this.entityData.define(PROTECT_ID, Optional.empty());
        this.entityData.define(IS_FOLLOWING, false);
        this.entityData.define(HUNGER, 50F);
        this.entityData.define(MORAL, 50F);
        this.entityData.define(OWNER_ID, Optional.empty());
        this.entityData.define(UPKEEP_ID, Optional.empty());
        this.entityData.define(OWNED, false);
        this.entityData.define(COST, 1);
        this.entityData.define(COLOR, (byte) 0);
        this.entityData.define(BIOME, (byte) 0);
        this.entityData.define(SHOULD_REST, false);
        this.entityData.define(SHOULD_RANGED, true);
		this.entityData.define(ONLY_PVP, false); // 기본값 false (꺼짐)
        //STATE
        // 0 = NEUTRAL
        // 1 = AGGRESSIVE
        // 2 = RAID
        // 3 = PASSIVE

        //FOLLOW
        //0 = wander
        //1 = follow
        //2 = hold position
        //3 = back to position
        //4 = hold my position
        //5 = Protect
        //6 = Work

    }
    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("despawnTimer", this.despawnTimer);
        nbt.putInt("AggroState", this.getState());
        nbt.putInt("FollowState", this.getFollowState());
        nbt.putBoolean("ShouldFollow", this.getShouldFollow());
        nbt.putBoolean("ShouldMount", this.getShouldMount());
        nbt.putBoolean("ShouldProtect", this.getShouldProtect());
        nbt.putBoolean("ShouldBlock", this.getShouldBlock());
        if (this.getGroupUUID() != null) {
			nbt.putUUID("GroupUUID", this.getGroupUUID());
		}
        nbt.putInt("Variant", this.getVariant());
        nbt.putBoolean("Listen", this.getListen());
        nbt.putBoolean("Fleeing", this.getFleeing());
        nbt.putBoolean("isFollowing", this.isFollowing());
        nbt.putInt("Xp", this.getXp());
        nbt.putInt("Level", this.getXpLevel());
        nbt.putInt("Kills", this.getKills());
        nbt.putFloat("Hunger", this.getHunger());
        nbt.putFloat("Moral", this.getMorale());
        nbt.putBoolean("isOwned", this.getIsOwned());
        nbt.putInt("Cost", this.getCost());
        nbt.putInt("mountTimer", this.getMountTimer());
        nbt.putInt("upkeepTimer", this.getUpkeepTimer());
        nbt.putInt("Color", this.getColor());
        nbt.putInt("Biome", this.getBiome());
        nbt.putInt("MaxFallDistance", this.getMaxFallDistance());
        nbt.putInt("formationPos", formationPos);
        nbt.putBoolean("ShouldRest", this.getShouldRest());
        nbt.putBoolean("ShouldRanged", this.getShouldRanged());
        nbt.putBoolean("isInFormation", this.isInFormation);
        nbt.putInt("paymentTimer", this.paymentTimer);
		nbt.putBoolean("OnlyPvP", this.isOnlyPvP());

        if(this.getHoldPos() != null){
            nbt.putDouble("HoldPosX", this.getHoldPos().x());
            nbt.putDouble("HoldPosY", this.getHoldPos().y());
            nbt.putDouble("HoldPosZ", this.getHoldPos().z());
            nbt.putBoolean("ShouldHoldPos", this.getShouldHoldPos());
        }

        if(this.getMovePos() != null){
            nbt.putDouble("MovePosX", this.getMovePos().getX());
            nbt.putDouble("MovePosY", this.getMovePos().getY());
            nbt.putDouble("MovePosZ", this.getMovePos().getZ());
            nbt.putBoolean("ShouldMovePos", this.getShouldMovePos());
        }

        if(this.getOwnerUUID() != null){
            nbt.putUUID("OwnerUUID", this.getOwnerUUID());
        }

        if(this.getMountUUID() != null){
            nbt.putUUID("MountUUID", this.getMountUUID());
        }

        if(this.getProtectUUID() != null){
            nbt.putUUID("ProtectUUID", this.getProtectUUID());
        }

        if(this.getUpkeepUUID() != null){
            nbt.putUUID("UpkeepUUID", this.getUpkeepUUID());
        }

        if(this.getUpkeepPos() != null){
            nbt.putInt("UpkeepPosX", this.getUpkeepPos().getX());
            nbt.putInt("UpkeepPosY", this.getUpkeepPos().getY());
            nbt.putInt("UpkeepPosZ", this.getUpkeepPos().getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);

        if(nbt.contains("despawnTimer")) this.despawnTimer = nbt.getInt("despawnTimer");
        else this.despawnTimer = -1;//fixes random recruits disappearing

        this.setXpLevel(nbt.getInt("Level"));
        this.setState(nbt.getInt("AggroState"));
        this.setFollowState(nbt.getInt("FollowState"));
        this.setShouldFollow(nbt.getBoolean("ShouldFollow"));
        this.setShouldMount(nbt.getBoolean("ShouldMount"));
        this.setShouldBlock(nbt.getBoolean("ShouldBlock"));
        this.setShouldProtect(nbt.getBoolean("ShouldProtect"));
        this.setFleeing(nbt.getBoolean("Fleeing"));
		if (nbt.contains("GroupUUID")) {
			this.setGroup(nbt.getUUID("GroupUUID"));
		} else {
			this.setGroup((UUID) null);
		}
        this.setListen(nbt.getBoolean("Listen"));
        this.setIsFollowing(nbt.getBoolean("isFollowing"));
        this.setXp(nbt.getInt("Xp"));
        this.setKills(nbt.getInt("Kills"));
        this.setVariant(nbt.getInt("Variant"));
        this.setHunger(nbt.getFloat("Hunger"));
        this.setMoral(nbt.getFloat("Moral"));
        this.setIsOwned(nbt.getBoolean("isOwned"));
        this.setCost(nbt.getInt("Cost"));
        this.setMountTimer(nbt.getInt("mountTimer"));
        this.setUpkeepTimer(nbt.getInt("UpkeepTimer"));
        this.setColor(nbt.getByte("Color"));

        this.setMaxFallDistance(nbt.getInt("MaxFallDistance"));
        this.formationPos = (nbt.getInt("formationPos"));
        this.setShouldRest(nbt.getBoolean("ShouldRest"));
        this.isInFormation = nbt.getBoolean("isInFormation");
		this.setOnlyPvP(nbt.getBoolean("OnlyPvP"));

        if(nbt.contains("paymentTimer")){
            this.paymentTimer = (nbt.getInt("paymentTimer"));
        }
        else{
            resetPaymentTimer();
        }

        if (nbt.contains("HoldPosX") && nbt.contains("HoldPosY") && nbt.contains("HoldPosZ")) {
            this.setShouldHoldPos(nbt.getBoolean("ShouldHoldPos"));
            this.setHoldPos(new Vec3 (
                    nbt.getDouble("HoldPosX"),
                    nbt.getDouble("HoldPosY"),
                    nbt.getDouble("HoldPosZ")));
        }

        if (nbt.contains("MovePosX") && nbt.contains("MovePosY") && nbt.contains("MovePosZ")) {
            this.setShouldMovePos(nbt.getBoolean("ShouldMovePos"));
            this.setMovePos(new BlockPos (
                    nbt.getInt("MovePosX"),
                    nbt.getInt("MovePosY"),
                    nbt.getInt("MovePosZ")));
        }

        if (nbt.contains("OwnerUUID")){
            Optional<UUID> uuid = Optional.of(nbt.getUUID("OwnerUUID"));
            this.setOwnerUUID(uuid);
        }

        if (nbt.contains("ProtectUUID")){
            Optional<UUID> uuid = Optional.of(nbt.getUUID("ProtectUUID"));
            this.setProtectUUID(uuid);
        }

        if (nbt.contains("MountUUID")){
            Optional<UUID> uuid = Optional.of(nbt.getUUID("MountUUID"));
            this.setMountUUID(uuid);
        }

        if (nbt.contains("UpkeepUUID")){
            Optional<UUID> uuid = Optional.of(nbt.getUUID("UpkeepUUID"));
            this.setUpkeepUUID(uuid);
        }

        if (nbt.contains("UpkeepPosX") && nbt.contains("UpkeepPosY") && nbt.contains("UpkeepPosZ")) {
            this.setUpkeepPos(new BlockPos (
                    nbt.getInt("UpkeepPosX"),
                    nbt.getInt("UpkeepPosY"),
                    nbt.getInt("UpkeepPosZ")));
        }

        if(nbt.contains("Biome"))this.setBiome(nbt.getByte("Biome"));
        else applyBiomeAndVariant(this);;
    }

    ////////////////////////////////////GET////////////////////////////////////

    public int getUpkeepTimer(){
        return this.upkeepTimer;
    }

    public int getVariant() {
        return entityData.get(VARIANT);
    }
    public int getBlockCoolDown(){
        return 200;
    }
    public UUID getUpkeepUUID(){
        return  this.entityData.get(UPKEEP_ID).orElse(null);
    }
    public BlockPos getUpkeepPos(){
        return entityData.get(UPKEEP_POS).orElse(null);
    }

    @Nullable
    public Player getOwner(){
        if (this.isOwned() && this.getOwnerUUID() != null){
            UUID ownerID = this.getOwnerUUID();
            return this.getCommandSenderWorld().getPlayerByUUID(ownerID);
        }
        else
            return null;
    }

    public UUID getOwnerUUID(){
        return  this.entityData.get(OWNER_ID).orElse(null);
    }

    public UUID getProtectUUID(){
        return  this.entityData.get(PROTECT_ID).orElse(null);
    }

    public UUID getMountUUID(){
        return  this.entityData.get(MOUNT_ID).orElse(null);
    }

    public boolean getIsOwned() {
        return entityData.get(OWNED);
    }

    public float getMorale() {
        return this.entityData.get(MORAL);
    }

    public float getHunger() {
        return this.entityData.get(HUNGER);
    }
    public float getAttackDamage(){
        return (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    public float getMovementSpeed(){
        return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    public boolean getFleeing() {
        return entityData.get(FLEEING);
    }

    public int getKills() {
        return entityData.get(KILLS);
    }

    public int getXpLevel() {
        return entityData.get(LEVEL);
    }

    public int getXp() {
        return entityData.get(XP);
    }


    public boolean getShouldMovePos() {
        return entityData.get(SHOULD_MOVE_POS);
    }
    public boolean getShouldHoldPos() {
        return entityData.get(SHOULD_HOLD_POS);
    }

    public boolean getShouldMount() {
        return entityData.get(SHOULD_MOUNT);
    }

    public boolean getShouldProtect() {
        return entityData.get(SHOULD_PROTECT);
    }

    public boolean getShouldFollow() {
        return entityData.get(SHOULD_FOLLOW);
    }

    public boolean getShouldBlock() {
        return entityData.get(SHOULD_BLOCK);
    }

    public boolean isFollowing(){
        return entityData.get(IS_FOLLOWING);
    }
    public boolean getShouldRest() {
        return entityData.get(SHOULD_REST);
    }

    public boolean getShouldRanged() {
        return entityData.get(SHOULD_RANGED);
    }
    public int getState() {
        return entityData.get(STATE);
    }
    //STATE
    // 0 = NEUTRAL
    // 1 = AGGRESSIVE
    // 2 = RAID
    // 3 = PASSIVE

	@Nullable
	public UUID getGroupUUID() {
		return this.entityData.get(GROUP).orElse(null);
	}


    //FOLLOW
    //0 = wander
    //1 = follow
    //2 = hold your position
    //3 = back to position
    //4 = hold my position
    //5 = Protect
    //6 = Work
    public int getFollowState(){
        return entityData.get(FOLLOW_STATE);
    }

    public SoundEvent getHurtSound(@NotNull DamageSource ds) {
        if (this.isBlocking())
            return SoundEvents.SHIELD_BLOCK;
        return RecruitsClientConfig.RecruitsLookLikeVillagers.get() ? SoundEvents.VILLAGER_HURT : SoundEvents.GENERIC_HURT;
    }

    protected SoundEvent getDeathSound() {
        return RecruitsClientConfig.RecruitsLookLikeVillagers.get() ? SoundEvents.VILLAGER_DEATH : SoundEvents.GENERIC_DEATH;
    }

    protected float getSoundVolume() {
        return 0.4F;
    }

    protected float getStandingEyeHeight(@NotNull Pose pos, EntityDimensions size) {
        return size.height * 0.98F;
    }

    public int getMaxHeadXRot() {
        return super.getMaxHeadXRot();
    }

    public int getMaxSpawnClusterSize() {
        return 8;
    }

    public Vec3 getHoldPos(){
        return this.holdPosVec;
        //return entityData.get(HOLD_POS).orElse(null);
    }

    @Nullable
    public BlockPos getMovePos(){
        return entityData.get(MOVE_POS).orElse(null);
    }

    public boolean getListen() {
        return entityData.get(LISTEN);
    }
	// 5. Getter / Setter 추가 (클래스 내부 아무데나)
	public boolean isOnlyPvP() {
		return this.entityData.get(ONLY_PVP);
	}

	public void setOnlyPvP(boolean value) {
		this.entityData.set(ONLY_PVP, value);
	}

	@Nullable
	public LivingEntity getProtectingMob() {
		// 보호 대상 UUID가 없으면 즉시 null 반환
		if (this.getProtectUUID() == null) {
			this.cachedProtectingMob = null;
			return null;
		}

		// 1. 캐시된 몹이 유효한지 확인 (살아있고, 20틱(1초)이 지나지 않았고, UUID가 일치하는지)
		if (this.cachedProtectingMob != null && this.cachedProtectingMob.isAlive() &&
			(this.tickCount - this.protectingMobCacheTick) < 20 &&
			this.cachedProtectingMob.getUUID().equals(this.getProtectUUID())) {
			return this.cachedProtectingMob;
		}

		// 2. 캐시가 만료되었거나 유효하지 않으면 새로 검색 (기존 로직)
		List<LivingEntity> list = this.getCommandSenderWorld().getEntitiesOfClass(
				LivingEntity.class,
				this.getBoundingBox().inflate(32D),
				(living) -> living.getUUID().equals(this.getProtectUUID()) && living.isAlive()
		);

		// 3. 검색 결과 캐싱 및 타임스탬프 갱신
		if (!list.isEmpty()) {
			this.cachedProtectingMob = list.get(0);
			this.protectingMobCacheTick = this.tickCount;
			return this.cachedProtectingMob;
		} else {
			this.cachedProtectingMob = null;
			return null;
		}
	}

    public int getColor() {
        return entityData.get(COLOR);
    }

    public int getBiome() {
        return entityData.get(BIOME);
    }
    public DyeColor getDyeColor() {
        return DyeColor.byId(getColor());
    }

    ////////////////////////////////////SET////////////////////////////////////

    public void setUpkeepTimer(int x){
        this.upkeepTimer =  x;
    }
    public void setVariant(int variant){
        entityData.set(VARIANT, variant);
    }
    public void setColor(byte color){
        entityData.set(COLOR, color);
    }
    public void setBiome(byte biome){
        entityData.set(BIOME, biome);
    }
    public void setUpkeepUUID(Optional<UUID> id) {
        this.entityData.set(UPKEEP_ID, id);
    }
    public void setCost(int cost){
        entityData.set(COST, cost);
    }
    public void setUpkeepPos(BlockPos pos){
        this.entityData.set(UPKEEP_POS, Optional.of(pos));
    }

    public void setIsOwned(boolean bool){
        entityData.set(OWNED, bool);
    }

    public void setOwnerUUID(Optional<UUID> id) {
        this.entityData.set(OWNER_ID,id);
    }

    public void setProtectUUID(Optional<UUID> id) {
        this.entityData.set(PROTECT_ID, id);
    }

    public void setMountUUID(Optional<UUID> id) {
        this.entityData.set(MOUNT_ID, id);
    }

    public void setMoral(float value) {
        this.entityData.set(MORAL, value);
        this.applyMoralEffects();
    }

    public void setHunger(float value) {
        float currentHunger = getHunger();
        if(value < 0 && currentHunger - value <= 0)
            this.entityData.set(HUNGER, 0F);
        else
            this.entityData.set(HUNGER, value);
    }

    public void setFleeing(boolean bool){
        entityData.set(FLEEING, bool);
    }
    public void setMountTimer(int x){
        this.mountTimer = x;
    }

    public void disband(@Nullable Player player, boolean keepTeam, boolean increaseCost){
        String name = this.getName().getString();
        RecruitEvents.recruitsPlayerUnitManager.removeRecruits(this.getOwnerUUID(), 1);
        if(player != null){
            player.sendSystemMessage(TEXT_DISBAND(name));
        }

        this.setTarget(null);
        this.setIsOwned(false);
        this.setOwnerUUID(Optional.empty());

        if(increaseCost) this.recalculateCost();
        if (this.getTeam() != null){

            if(!this.getCommandSenderWorld().isClientSide() && !keepTeam)
                TeamEvents.removeRecruitFromTeam(this, this.getTeam(), (ServerLevel) this.getCommandSenderWorld());
        }
    }

    public void addXpLevel(int level){
        int currentLevel = this.getXpLevel();
        int newLevel = currentLevel + level;

        if(newLevel > RecruitsServerConfig.RecruitsMaxXpLevel.get()){
            newLevel = RecruitsServerConfig.RecruitsMaxXpLevel.get();
        }
        else{
            this.makeLevelUpSound();
            this.addLevelBuffs();
        }

        this.entityData.set(LEVEL, newLevel);
    }

    public void setKills(int kills){
        this.entityData.set(KILLS, kills);
    }

    public void setXpLevel(int XpLevel){
        this.entityData.set(LEVEL, XpLevel);
    }

    public void setXp(int xp){
        this.entityData.set(XP, xp);
    }

    public void addXp(int xp){
        int currentXp = this.getXp();
        int newXp = currentXp + xp;

        this.entityData.set(XP, newXp);
    }

    public void setShouldHoldPos(boolean bool){
        entityData.set(SHOULD_HOLD_POS, bool);
    }

    public void setShouldMovePos(boolean bool){
        entityData.set(SHOULD_MOVE_POS, bool);
    }
    public void setShouldProtect(boolean bool){
        entityData.set(SHOULD_PROTECT, bool);
    }

    public void setShouldMount(boolean bool){
        entityData.set(SHOULD_MOUNT, bool);
    }

    public void setShouldFollow(boolean bool){
        entityData.set(SHOULD_FOLLOW, bool);
    }

    public void setShouldBlock(boolean bool){
        entityData.set(SHOULD_BLOCK, bool);
    }

    public void setIsFollowing(boolean bool){
        entityData.set(IS_FOLLOWING, bool);
    }

	public void setGroup(@Nullable UUID groupUUID) {
		this.entityData.set(GROUP, Optional.ofNullable(groupUUID));
	}
    public void setShouldRest(boolean bool){
        if(bool) setFollowState(0);
        entityData.set(SHOULD_REST, bool);
    }

    public void setShouldRanged(boolean should) {
        entityData.set(SHOULD_RANGED, should);
    }

    public void setState(int state) {
        switch (state){
            case 0:
            case 3:
                setTarget(null);//wird nur 1x aufgerufen
                break;
            case 1:
                break;
            case 2:
                setFollowState(0);
                break;
        }
        entityData.set(STATE, state);
    }

    //STATE
    // 0 = NEUTRAL
    // 1 = AGGRESSIVE
    // 2 = RAID
    // 3 = PASSIVE

    //FOLLOW
    //0 = wander
    //1 = follow
    //2 = hold position
    //3 = back to position
    //4 = hold my position
    //5 = Protect
    //6 = Work
    public void setFollowState(int state){
        switch (state) {
            case 0,6 -> {
                setShouldFollow(false);
                setShouldHoldPos(false);
                setShouldProtect(false);
                setShouldMovePos(false);
            }
            case 1 -> {
                setShouldFollow(true);
                setShouldHoldPos(false);
                setShouldProtect(false);
                setShouldMovePos(false);
            }
            case 2 -> {
                setShouldFollow(false);
                setShouldHoldPos(true);
                clearHoldPos();
                setHoldPos(position());
                setShouldProtect(false);
                setShouldMovePos(false);
            }
            case 3 -> {
                setShouldFollow(false);
                setShouldHoldPos(true);
                setShouldProtect(false);
                setShouldMovePos(false);
            }
            case 4 -> {
                setShouldFollow(false);
                setShouldHoldPos(true);
                clearHoldPos();
                setHoldPos(this.getOwner().position());
                setShouldProtect(false);
                setShouldMovePos(false);
                state = 3;
            }
            case 5 -> {
                setShouldFollow(false);
                setShouldHoldPos(false);
                setShouldProtect(true);
                setShouldMovePos(false);
            }
        }

        this.entityData.set(FOLLOW_STATE, state);
    }

    public void setHoldPos(Vec3 holdPos){
        //this.entityData.set(HOLD_POS, Optional.of(holdPos));
        this.holdPosVec = holdPos;
    }
    public void setMovePos(BlockPos holdPos){
        this.entityData.set(MOVE_POS, Optional.of(holdPos));
        reachedMovePos = false;
    }

    public void clearHoldPos(){
        this.entityData.set(HOLD_POS, Optional.empty());
    }

    public void clearMovePos(){
        this.entityData.set(MOVE_POS, Optional.empty());
    }

    public void setListen(boolean bool) {
        entityData.set(LISTEN, bool);
    }

    public void setEquipment(){
        //Equipment
        List<List<String>> equipmentSets = getEquipment();
        if(!equipmentSets.isEmpty()){

            int size = equipmentSets.size();
            int i = this.random.nextInt(0, size);

            if(i >= 0){
                List<String> equipmentSet = equipmentSets.get(i);
                while(equipmentSet.size() < 6) equipmentSet.add("");

                String mainHandStr = equipmentSet.get(0);
                String offHandStr = equipmentSet.get(1);
                String feetStr = equipmentSet.get(2);
                String legsStr = equipmentSet.get(3);
                String chestStr = equipmentSet.get(4);
                String headStr = equipmentSet.get(5);

                Optional<Holder<Item>> holderHead = ForgeRegistries.ITEMS.getHolder(ResourceLocation.tryParse(headStr));
                holderHead.ifPresent(itemHolder -> this.setItemSlot(EquipmentSlot.HEAD, itemHolder.value().getDefaultInstance()));

                Optional<Holder<Item>> holderChest = ForgeRegistries.ITEMS.getHolder(ResourceLocation.tryParse(chestStr));
                holderChest.ifPresent(itemHolder -> this.setItemSlot(EquipmentSlot.CHEST, itemHolder.value().getDefaultInstance()));

                Optional<Holder<Item>> holderLegs = ForgeRegistries.ITEMS.getHolder(ResourceLocation.tryParse(legsStr));
                holderLegs.ifPresent(itemHolder -> this.setItemSlot(EquipmentSlot.LEGS, itemHolder.value().getDefaultInstance()));

                Optional<Holder<Item>> holderFeet = ForgeRegistries.ITEMS.getHolder(ResourceLocation.tryParse(feetStr));
                holderFeet.ifPresent(itemHolder -> this.setItemSlot(EquipmentSlot.FEET, itemHolder.value().getDefaultInstance()));

                Optional<Holder<Item>> holderMainHand = ForgeRegistries.ITEMS.getHolder(ResourceLocation.tryParse(mainHandStr));
                holderMainHand.ifPresent(itemHolder -> this.setItemSlot(EquipmentSlot.MAINHAND, itemHolder.value().getDefaultInstance()));

                Optional<Holder<Item>> holderOffHand = ForgeRegistries.ITEMS.getHolder(ResourceLocation.tryParse(offHandStr));
                holderOffHand.ifPresent(itemHolder -> this.setItemSlot(EquipmentSlot.OFFHAND, itemHolder.value().getDefaultInstance()));
            }
        }
    }

    @Override
    public void setTarget(@Nullable LivingEntity p_21544_) {
        super.setTarget(p_21544_);

        this.setUpkeepTimer(500);
    }

    public List<List<String>> getEquipment() {
        return null;
    }

    public double getMeleeStartRange() {
        return 32D;
    }

    public abstract void initSpawn();

    public static void applySpawnValues(AbstractRecruitEntity recruit){
        recruit.setHunger(50);
        recruit.setMoral(50);
        recruit.setListen(true);
        recruit.setXpLevel(1);

        applyBiomeAndVariant(recruit);
    }

    public static void applyBiomeAndVariant(AbstractRecruitEntity recruit){
        //ForgeBiomeTagsProvider
        Holder<Biome> biome = recruit.getCommandSenderWorld().getBiome(recruit.getOnPos());
        byte biomeByte = 2; //PLAINS
        int variant = recruit.random.nextInt(0, 14);
        //DESERT
        if(biome.is(Biomes.ERODED_BADLANDS) || biome.containsTag(Tags.Biomes.IS_DESERT) || biome.containsTag(Tags.Biomes.IS_SANDY) && !biome.containsTag(Tags.Biomes.IS_WET_OVERWORLD)){
            biomeByte = 0;
            variant = recruit.random.nextInt(15, 19);
        }
        //TAIGA
        else if(biome.is(Tags.Biomes.IS_CONIFEROUS) && biome.is(Tags.Biomes.IS_COLD_OVERWORLD) && !(biome.is(Tags.Biomes.IS_SNOWY))){
            biomeByte = 6;
            variant = recruit.random.nextInt(5, 14);
        }
        //JUNGLE
        else if(biome.is(Tags.Biomes.IS_WET_OVERWORLD) && !biome.is(Tags.Biomes.IS_SANDY) && !biome.is(Tags.Biomes.IS_SWAMP)){
            biomeByte = 1;
            variant = recruit.random.nextInt(15, 19);
        }
        //SVANNA
        else if(biome.is(Tags.Biomes.IS_HOT_OVERWORLD) && biome.is(Tags.Biomes.IS_SPARSE_OVERWORLD)){
            biomeByte = 3;
            variant = recruit.random.nextInt(15, 19);
        }
        //SNOWY
        else if(biome.is(Tags.Biomes.IS_SNOWY)){
            biomeByte = 4;
            variant = recruit.random.nextInt(5, 10);
        }
        //SWAMP
        else if(biome.is(Tags.Biomes.IS_SWAMP)){
            biomeByte = 5;
            variant = recruit.random.nextInt(5, 14);
        }

        recruit.setBiome(biomeByte);
        recruit.setVariant(variant);
    }

    ////////////////////////////////////is FUNCTIONS////////////////////////////////////

	public boolean isEffectedByCommand(UUID player_uuid, @Nullable UUID targetGroupUUID) {
		if (!this.isOwned() || !this.isAlive() || !this.getListen() || !Objects.equals(this.getOwnerUUID(), player_uuid)) {
			return false;
		}
		// targetGroupUUID가 null이면 전체(All/No Group) 대상 명령
		if (targetGroupUUID == null) {
			return true;
		}
		// 병사가 속한 그룹 UUID와 명령받은 그룹 UUID 대조
		return Objects.equals(this.getGroupUUID(), targetGroupUUID);
	}
    public boolean isOwned(){
        return getIsOwned();
    }

    public boolean isOwnedBy(Player player){
       return player.getUUID() == this.getOwnerUUID() || player == this.getOwner();
    }

    ////////////////////////////////////ON FUNCTIONS////////////////////////////////////

    public InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        String name = this.getName().getString();
        Team ownerTeam = this.getTeam();
        boolean isPlayerTarget = this.getTarget() != null && getTarget().equals(player);

        if(isPlayerTarget) return InteractionResult.PASS;

        if (this.getCommandSenderWorld().isClientSide) {
            boolean flag = this.isOwnedBy(player) || this.isOwned() || !this.isOwned();
            return flag ? InteractionResult.CONSUME : InteractionResult.PASS;
        } else {
            if (player.isCreative() && player.getItemInHand(hand).getItem().equals(ModItems.RECRUIT_SPAWN_EGG.get())){
                openDebugScreen(player);
                Main.LOGGER.warn("" + this.getName().getString() + " Target: " + getTarget());

                return InteractionResult.SUCCESS;
            }
            if ((this.isOwned() && player.getUUID().equals(this.getOwnerUUID()))) {
                if (player.isCrouching()) {
                    this.openGUI(player);
                    this.navigation.stop();
                    return InteractionResult.SUCCESS;
                }
                if(!player.isCrouching()) {

                    this.setUpkeepTimer(this.getUpkeepCooldown());
                    if(this.getShouldMount()) this.setShouldMount(false);

                    int state = this.getFollowState();
                    switch (state) {
                        default -> {
                            setFollowState(1);
                            player.sendSystemMessage(TEXT_FOLLOW(name));
                        }
                        case 1 -> {
                            setFollowState(4);
                            player.sendSystemMessage(TEXT_HOLD_YOUR_POS(name));
                        }
                        case 3 -> {
                            setFollowState(0);
                            player.sendSystemMessage(TEXT_WANDER(name));
                        }
                    }
                    if(this instanceof AbstractLeaderEntity) CommandEvents.checkPatrolLeaderState(this);
                    return InteractionResult.SUCCESS;
                }
            }
            else if(this.isOwned() && this.getTeam() != null && !player.getUUID().equals(this.getOwnerUUID()) &&
                    TeamEvents.recruitsTeamManager.getTeamByStringID(this.getTeam().getName()).getTeamLeaderUUID().equals(player.getUUID())){
                    //this will not work:
                    Main.SIMPLE_CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player), new MessageToClientOpenTakeOverScreen(this.getUUID()));
            }
            else if (!this.isOwned() && !isPlayerTarget) {
                this.openHireGUI(player);
                this.dialogue(name, player);
                this.navigation.stop();
                return InteractionResult.SUCCESS;
            }
            return super.mobInteract(player, hand);
        }
    }

    public boolean hire(Player player) {
        String name = this.getName().getString() + ": ";
        Team ownerTeam = player.getTeam();// player is the new owner
        String stringId = ownerTeam != null ? ownerTeam.getName() : "";
        if (!RecruitEvents.recruitsPlayerUnitManager.canPlayerRecruit(stringId, player.getUUID())) {

            player.sendSystemMessage(INFO_RECRUITING_MAX(name));
            return false;
        }
        else {
            this.makeHireSound();

            this.resetPaymentTimer();
            this.setOwnerUUID(Optional.of(player.getUUID()));
            this.setIsOwned(true);
            this.navigation.stop();
            this.setTarget(null);
            this.setFollowState(2);
            this.setState(0);
            this.despawnTimer = -1;

            if(!this.getCommandSenderWorld().isClientSide() && ownerTeam != null) TeamEvents.addRecruitToTeam(this, ownerTeam, (ServerLevel) this.getCommandSenderWorld());

            int i = this.random.nextInt(4);
            switch (i) {
                default -> {
                    player.sendSystemMessage(TEXT_RECRUITED1(name));
                }
                case 2 -> {
                    player.sendSystemMessage(TEXT_RECRUITED2(name));
                }
                case 3 -> {
                    player.sendSystemMessage(TEXT_RECRUITED3(name));
                }
            }
        }

        RecruitEvents.recruitsPlayerUnitManager.addRecruits(player.getUUID(), 1);
        return true;
    }

    public void dialogue(String name, Player player) {
        int i = this.random.nextInt(4);
        switch (i) {
            case 1 -> {
                player.sendSystemMessage(TEXT_HELLO_1(name));
            }
            case 2 -> {
                player.sendSystemMessage(TEXT_HELLO_2(name));
            }
            case 3 -> {
                player.sendSystemMessage(TEXT_HELLO_3(name));
            }
        }
    }

    ////////////////////////////////////ATTACK FUNCTIONS////////////////////////////////////

	@Override
    public boolean hurt(@NotNull DamageSource dmg, float amt) {
        // [수정됨] 현재 처리 중인 데미지 소스 저장
        this.handlingDamageSource = dmg;

        try {
            if (this.isInvulnerableTo(dmg)) {
                return false;
            } else {
                Entity entity = dmg.getEntity();
                if (entity != null && !(entity instanceof Player) && !(entity instanceof AbstractArrow)) {
                    amt = (amt + 1.0F) / 2.0F;
                }
                if (this.getMorale() > 0) this.setMoral(this.getMorale() - 0.25F);
                //if(isBlocking()) hurtCurrentlyUsedShield(amt); // 주석 처리된 기존 코드는 유지

                if (entity instanceof LivingEntity living && RecruitEvents.canAttack(this, living)) {
                    if (this.getFollowState() == 5) { //Protecting
                        List<AbstractRecruitEntity> list = this.getCommandSenderWorld().getEntitiesOfClass(AbstractRecruitEntity.class, this.getBoundingBox().inflate(32D));
                        for (AbstractRecruitEntity recruit : list) {
                            if (recruit.getUUID().equals(recruit.getProtectUUID()) && recruit.isAlive() && !recruit.equals(living)) {
                                //Patrolleader
                                recruit.setTarget(living);
                            }
                        }
                    }

                    if (this.getTarget() != null) {
                        double d1 = this.distanceToSqr(this.getTarget());
                        double d2 = this.distanceToSqr(living);

                        if (d2 < d1) this.setTarget(living);
                    } else
                        this.setTarget(living);

                    if (this.getShouldProtect() && this.getProtectingMob() instanceof AbstractRecruitEntity patrolLeader) {
                        patrolLeader.setTarget(living);
                    }
                }
                return super.hurt(dmg, amt);
            }
        } finally {
            // [수정됨] 처리가 끝나면 반드시 null로 초기화하여 다른 로직에 영향이 없도록 함
            this.handlingDamageSource = null;
        }
    }

    public boolean doHurtTarget(@NotNull Entity entity) {
        float f = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (entity instanceof LivingEntity) {
            f += EnchantmentHelper.getDamageBonus(this.getMainHandItem(), ((LivingEntity)entity).getMobType());
        }

        int i = EnchantmentHelper.getFireAspect(this);
        if (i > 0) {
            entity.setSecondsOnFire(i * 4);
        }

        boolean flag = entity.hurt(this.damageSources().mobAttack(this), f);
        if (flag) {

            this.doEnchantDamageEffects(this, entity);
            this.setLastHurtMob(entity);
        }
        this.addXp(1);
        if(this.getHunger() > 0) this.setHunger(this.getHunger() - 0.1F);
        this.checkLevel();
        if(this.getMorale() < 100) this.setMoral(this.getMorale() + 0.25F);
        this.damageMainHandItem();
        return true;
    }

    public void addLevelBuffs(){
        int level = getXpLevel();
        if(level <= 10){
            getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier("heath_bonus_level", 2D, AttributeModifier.Operation.ADDITION));
            getAttribute(Attributes.ATTACK_DAMAGE).addPermanentModifier(new AttributeModifier("attack_bonus_level", 0.03D, AttributeModifier.Operation.ADDITION));
            getAttribute(Attributes.KNOCKBACK_RESISTANCE).addPermanentModifier(new AttributeModifier("knockback_bonus_level", 0.0012D, AttributeModifier.Operation.ADDITION));
            getAttribute(Attributes.MOVEMENT_SPEED).addPermanentModifier(new AttributeModifier("speed_bonus_level", 0.0025D, AttributeModifier.Operation.ADDITION));
        }
        if(level > 10){
            getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier("heath_bonus_level", 2D, AttributeModifier.Operation.ADDITION));
        }
    }

    public void addLevelBuffsForLevel(int level){

        for(int i = 0; i < level; i++) {
            if (level <= 10) {
                getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier("heath_bonus_level", 2D, AttributeModifier.Operation.ADDITION));
                getAttribute(Attributes.ATTACK_DAMAGE).addPermanentModifier(new AttributeModifier("attack_bonus_level", 0.03D, AttributeModifier.Operation.ADDITION));
                getAttribute(Attributes.KNOCKBACK_RESISTANCE).addPermanentModifier(new AttributeModifier("knockback_bonus_level", 0.0012D, AttributeModifier.Operation.ADDITION));
                getAttribute(Attributes.MOVEMENT_SPEED).addPermanentModifier(new AttributeModifier("speed_bonus_level", 0.0025D, AttributeModifier.Operation.ADDITION));}
            if (level > 10) {
                getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier("heath_bonus_level", 2D, AttributeModifier.Operation.ADDITION));
            }
        }
    }

    /*
           .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.1D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    */
    /**
        Important for mod compat: See smallships or siege weapons mod
    **/
    public boolean isAlliedTo(@NotNull Team team) {
        if(!this.getCommandSenderWorld().isClientSide() && this.getTeam() != null){
            RecruitsDiplomacyManager.DiplomacyStatus status = TeamEvents.recruitsDiplomacyManager.getRelation(this.getTeam().getName(), team.getName());
            return status == RecruitsDiplomacyManager.DiplomacyStatus.ALLY;
        }
        return super.isAlliedTo(team);
    }

    public void die(DamageSource dmg) {
        net.minecraft.network.chat.Component deathMessage = this.getCombatTracker().getDeathMessage();
        super.die(dmg);
        if (this.dead) {
            if (!this.getCommandSenderWorld().isClientSide()){
                if (this.getCommandSenderWorld().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES) && this.getOwner() instanceof ServerPlayer) {
                    this.getOwner().sendSystemMessage(deathMessage);
                }
                if(this.isOwned()){
                    RecruitEvents.recruitsPlayerUnitManager.removeRecruits(this.getOwnerUUID(), 1);
                    TeamEvents.removeRecruitFromTeam(this, this.getTeam(), (ServerLevel) this.getCommandSenderWorld());
                }
                if(this.getTeam() != null){
                    TeamEvents.recruitsTeamManager.getTeamByStringID(this.getTeam().getName()).addNPCs(-1);
                }
            }
        }
    }

    ////////////////////////////////////OTHER FUNCTIONS////////////////////////////////////

    public void updateMorale(){
        //fast recovery
        float currentMorale = getMorale();
        float newMorale = currentMorale;

        if (isStarving() && this.isOwned()){
            if(currentMorale > 0) newMorale -= 2F;
        }

        if (this.isOwned() && !isSaturated()){
            if(currentMorale > 35) newMorale -= 1F;
        }

        if(this.isSaturated() || getHealth() >= getMaxHealth() * 0.85){
            if(currentMorale < 65) newMorale += 2F;
        }

        if(newMorale < 0) newMorale = 0;

        this.setMoral(newMorale);
    }

    public void applyMoralEffects(){
        boolean confused =  0 <= getMorale() && getMorale() < 20;
        boolean lowMoral =  20 <= getMorale() && getMorale() < 40;
        boolean highMoral =  90 <= getMorale() && getMorale() <= 100;

        if (confused) {
            if (!this.hasEffect(MobEffects.WEAKNESS))
                this.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 3, false, false, true));
            if (!this.hasEffect(MobEffects.MOVEMENT_SLOWDOWN))
                this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 2, false, false, true));
            if (!this.hasEffect(MobEffects.CONFUSION))
                this.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 1, false, false, true));
        }

        if (lowMoral) {
            if (!this.hasEffect(MobEffects.WEAKNESS))
                this.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1, false, false, true));
            if (!this.hasEffect(MobEffects.MOVEMENT_SLOWDOWN))
                this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1, false, false, true));
        }

        if (highMoral) {
            if (!this.hasEffect(MobEffects.DAMAGE_BOOST))
                this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0, false, false, true));
            if (!this.hasEffect(MobEffects.DAMAGE_RESISTANCE))
                this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0, false, false, true));
        }
    }

	public void updateHunger(){
		// 1. 대기 상태(2)이거나, 움직임이 거의 없는 경우(멈춤) 허기 소모 없음
		// (이 부분이 없으면 가만히 세워놔도 40분 뒤에 배고파서 비현실적임)
		if (this.getFollowState() == 2 || this.getDeltaMovement().lengthSqr() < 0.0001) {
			return; 
		}

		float hunger = getHunger();

		// 2. 움직일 때 허기 감소 (0.04F = 1초당 0.04, 1분당 2.4 감소)
		// -> 만복 기준 약 40분 활동 가능
		hunger -= 0.005F;

		// 3. 하한선 제한
		if (hunger < 0) hunger = 0;

		this.setHunger(hunger);
	}

	// 1. 화살/탄약이 필요한지 체크하는 메서드 추가
	public boolean needsAmmo() {
		// 원거리 유닛이 아니면 false
		if (!(this instanceof IRangedRecruit)) return false;
		
		// 머스킷 모드일 경우 탄약 체크
		if (this instanceof CrossBowmanEntity && Main.isMusketModLoaded) {
			return this.canTakeCartridge(); // 이미 구현된 메서드 활용 (32개 미만이면 true)
		}
		
		// 일반 활/석궁일 경우 화살 체크
		return this.canTakeArrows(); // 이미 구현된 메서드 활용 (32개 미만이면 true)
	}

	public boolean needsToGetFood() {
			int timer = this.getUpkeepTimer();
			
			boolean hasFood = this.hasFoodInInv();
			boolean needsToEat = this.needsToEat();
			
			// 밥 없고 배고픔
			boolean conditionFood = !hasFood && needsToEat;
			// 탄약/수류탄 부족함
			boolean conditionSupply = this.needsAmmoOrGrenades();

			boolean isChest = this.getUpkeepPos() != null;
			boolean isEntity = this.getUpkeepUUID() != null;

			// 타이머가 0일 때만 작동하도록 강제 (무한 루프 방지)
			return (timer == 0 && (forcedUpkeep || conditionFood || conditionSupply) 
				   && (isChest || isEntity)) 
				   && !getShouldProtect();
	}
// [신규] 유탄발사기(jeg:grenade_launcher)를 주무기에 들고 있는지 확인
    public boolean isHoldingGrenadeLauncher() {
        if (this.getMainHandItem().isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(this.getMainHandItem().getItem());
        return id != null && id.toString().equals("jeg:grenade_launcher");
    }

    // [신규] 인벤토리에 수류탄(jeg:grenade)이 64개 미만인지 확인
	public boolean canTakeGrenades() {
			int count = 0;
			for(ItemStack stack : this.inventory.items) {
				// 빈 아이템은 건너뛰기
				if (stack.isEmpty()) continue;
				
				ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
				// ID가 null이 아니고 "jeg:grenade"와 정확히 일치하면 카운트
				if(id != null && id.toString().equals("jeg:grenade")) {
					count += stack.getCount();
				}
			}
			// 192개 있으면 당연히 false 반환
			return count < 64;
	}

// [수정됨] 탄약/화살/수류탄 등 보급이 필요한지 통합 체크
    public boolean needsAmmoOrGrenades() {
        // 1. 기존 원거리 유닛 화살/탄약 체크 (활, 석궁, 머스킷 등)
        if (this instanceof IRangedRecruit) {
            if (this instanceof CrossBowmanEntity && Main.isMusketModLoaded) {
                if (this.canTakeCartridge()) return true;
            } else {
                if (this.canTakeArrows()) return true;
            }
        }
        
        // 2. 유탄발사기 들고 있으면 수류탄 체크
        if (this.isHoldingGrenadeLauncher()) {
            if (this.canTakeGrenades()) return true;
        }

        // 3. [핵심 추가] JEG 총기를 들고 있을 때 탄약 체크
        // 이 부분이 없어서 총알이 없어도 보급하러 가지 않았던 것입니다.
        ItemStack mainHand = this.getMainHandItem();
        if (mainHand.getItem() instanceof ttv.migami.jeg.item.GunItem) {
             ttv.migami.jeg.common.Gun gun = ((ttv.migami.jeg.item.GunItem) mainHand.getItem()).getGun();
             if (gun != null && gun.getProjectile() != null) {
                 net.minecraft.resources.ResourceLocation ammoId = gun.getProjectile().getItem();
                 // 탄약(박스 포함)이 128발 미만이면 true 반환 -> 보급 출발
                 if (this.canTakeGunAmmo(ammoId)) return true;
             }
        }
        
        return false;
    }

	public boolean hasFoodInInv(){
        return this.getInventory().items
                .stream()
                // [수정] 내 인벤토리 검사 시에도 canEatItemStack 로직을 사용하여 수통을 식량으로 인식하게 함
                .anyMatch(this::canEatItemStack); 
    }

    public boolean needsToEat(){
        if (getHunger() <= 50F){
            return true;
        }
        if (getHunger() <= 70F && getHealth() != getMaxHealth() && this.getTarget() == null && this.getIsOwned()){
            return true;
        }
        else return getHealth() <= (getMaxHealth() * 0.30) && this.getTarget() == null;
    }

    public boolean needsToPotion(){
        LivingEntity target = this.getTarget();
        if(target != null){
            return getHealth() <= (getMaxHealth() * 0.60);
        }
        return false;
    }

    public boolean isStarving(){
        return (getHunger() <= 1F );
    }

    public boolean isSaturated(){
        return (getHunger() >= 90F);
    }

    public void checkLevel(){
        int currentXp = this.getXp();
        if (currentXp >= RecruitsServerConfig.RecruitsMaxXpForLevelUp.get()){
            this.addXpLevel(1);
            this.setXp(0);
            this.heal(10F);
            this.recalculateCost();

            if(this.getMorale() < 100)
                this.setMoral(getMorale() + 5F);
        }
    }

    private void recalculateCost() {
        int currCost = getCost();
        int armorBonus = this.getArmorValue() * 2;
        //Main.LOGGER.debug("armorBonus: " + armorBonus);

        int weaponBonus = 4;
        //Main.LOGGER.debug("weaponBonus: " + weaponBonus);

        int speedBonus = (int) (this.getSpeed() * 2);
        //Main.LOGGER.debug("speedBonus: " + speedBonus);

        int shieldBonus = this.getOffhandItem().getItem() instanceof ShieldItem ? 10 : 0;
        //Main.LOGGER.debug("shieldBonus: " + shieldBonus);

        int newCost = Math.abs((shieldBonus + speedBonus + weaponBonus + armorBonus + currCost + getXpLevel() * 2));
        this.setCost(newCost);
    }

    public void makeLevelUpSound() {
        this.getCommandSenderWorld().playSound(null, this.getX(), this.getY() + 1 , this.getZ(), SoundEvents.PLAYER_LEVELUP, this.getSoundSource(), 1.0F, 0.8F + 0.4F * this.random.nextFloat());

        if(RecruitsClientConfig.RecruitsLookLikeVillagers.get())
            this.getCommandSenderWorld().playSound(null, this.getX(), this.getY() + 1 , this.getZ(), SoundEvents.VILLAGER_CELEBRATE, this.getSoundSource(), 1.0F, 0.8F + 0.4F * this.random.nextFloat());
    }

    public void makeHireSound() {
        if(RecruitsClientConfig.RecruitsLookLikeVillagers.get())
            this.playSound(SoundEvents.VILLAGER_AMBIENT, 1.0F, 0.8F + 0.4F * this.random.nextFloat());
    }

    @Override
    public boolean canBeLeashed(@NotNull Player player) {
        return false;
    }

    public int getCost(){
        return entityData.get(COST);
    }

    protected void hurtArmor(@NotNull DamageSource damageSource, float damage) {
        ItemStack headArmor = this.getItemBySlot(EquipmentSlot.HEAD);
        boolean hasHeadArmor = !headArmor.isEmpty();
        //Main.LOGGER.debug("headArmor :" + headArmor);
        //Main.LOGGER.debug("hasHeadArmor: " + hasHeadArmor);

        if (((!(damageSource.is(DamageTypes.IN_FIRE) && (damageSource.is(DamageTypes.ON_FIRE))) || !headArmor.getItem().isFireResistant()) && headArmor.getItem() instanceof ArmorItem)){
        //damage
            headArmor.hurtAndBreak(1, this, (p_43296_) -> {
                //p_43296_.broadcastBreakEvent(EquipmentSlot.HEAD);
            });
        }

        if (this.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && hasHeadArmor) {
            this.inventory.setItem(0, ItemStack.EMPTY);
            this.getInventory().setChanged();
            this.playSound(SoundEvents.ITEM_BREAK, 0.8F, 0.8F + this.getCommandSenderWorld().random.nextFloat() * 0.4F);
            this.tryToReequip(EquipmentSlot.HEAD);
        }

        ItemStack chestArmor = this.getItemBySlot(EquipmentSlot.CHEST);
        boolean hasChestArmor = !chestArmor.isEmpty();
        if (((!(damageSource.is(DamageTypes.IN_FIRE) && (damageSource.is(DamageTypes.ON_FIRE))) || !chestArmor.getItem().isFireResistant()) && chestArmor.getItem() instanceof ArmorItem)){
            //damage
            chestArmor.hurtAndBreak(1, this, (p_43296_) -> {

            });
        }
        if (this.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && hasChestArmor) {
            this.inventory.setItem(1, ItemStack.EMPTY);
            this.getInventory().setChanged();
            this.playSound(SoundEvents.ITEM_BREAK, 0.8F, 0.8F + this.getCommandSenderWorld().random.nextFloat() * 0.4F);
            this.tryToReequip(EquipmentSlot.CHEST);
        }

        ItemStack legsArmor = this.getItemBySlot(EquipmentSlot.LEGS);
        boolean hasLegsArmor = !legsArmor.isEmpty();

        if (((!(damageSource.is(DamageTypes.IN_FIRE) && (damageSource.is(DamageTypes.ON_FIRE))) || !legsArmor.getItem().isFireResistant()) && legsArmor.getItem() instanceof ArmorItem)){
            //damage
            legsArmor.hurtAndBreak(1, this, (p_43296_) -> {

            });
        }
        if (this.getItemBySlot(EquipmentSlot.LEGS).isEmpty() && hasLegsArmor) {
            this.inventory.setItem(2, ItemStack.EMPTY);
            this.getInventory().setChanged();
            this.playSound(SoundEvents.ITEM_BREAK, 0.8F, 0.8F + this.getCommandSenderWorld().random.nextFloat() * 0.4F);
            this.tryToReequip(EquipmentSlot.LEGS);
        }


        ItemStack feetArmor = this.getItemBySlot(EquipmentSlot.FEET);
        boolean hasFeetArmor = !feetArmor.isEmpty();

        if (((!(damageSource.is(DamageTypes.IN_FIRE) && (damageSource.is(DamageTypes.ON_FIRE))) || !feetArmor.getItem().isFireResistant()) && feetArmor.getItem() instanceof ArmorItem)){
            //damage
            feetArmor.hurtAndBreak(1, this, (p_43296_) -> {

            });

        }
        if (this.getItemBySlot(EquipmentSlot.FEET).isEmpty() && hasFeetArmor) {
            this.inventory.setItem(3, ItemStack.EMPTY);
            this.getInventory().setChanged();
            this.playSound(SoundEvents.ITEM_BREAK, 0.8F, 0.8F + this.getCommandSenderWorld().random.nextFloat() * 0.4F);
            this.tryToReequip(EquipmentSlot.FEET);
        }

    }

    public void damageMainHandItem() {
        //dont know why the fuck i cant assign this mainhand slot to inventory slot 4
        //therefor i need to make this twice
        ItemStack handItem = this.getItemBySlot(EquipmentSlot.MAINHAND);
        boolean hasHandItem = !handItem.isEmpty();
        /*//Fixes damage duplication
        this.getMainHandItem().hurtAndBreak(1, this, (p_43296_) -> {
            p_43296_.broadcastBreakEvent(EquipmentSlot.MAINHAND);
        });
         */
        this.inventory.getItem(5).hurtAndBreak(1, this, (p_43296_) -> {
            p_43296_.broadcastBreakEvent(EquipmentSlot.MAINHAND);
        });

        if (this.getMainHandItem().isEmpty() && hasHandItem) {
            this.inventory.setItem(5, ItemStack.EMPTY);
            this.getInventory().setChanged();
            this.playSound(SoundEvents.ITEM_BREAK, 0.8F, 0.8F + this.getCommandSenderWorld().random.nextFloat() * 0.4F);
            this.tryToReequip(EquipmentSlot.MAINHAND);
        }
    }

    public void tryToReequip(EquipmentSlot equipmentSlot){
        for(int i = 6; i < 15; i++){
            ItemStack itemStack = this.getInventory().getItem(i);
            if(canEquipItemToSlot(itemStack, equipmentSlot)) {
                this.setItemSlot(equipmentSlot, itemStack);
                this.inventory.setItem(getInventorySlotIndex(equipmentSlot), itemStack);
                this.inventory.removeItemNoUpdate(i);
                Equipable equipable = Equipable.get(itemStack);
                if(equipable != null)
                    this.getCommandSenderWorld().playSound(null, this.getX(), this.getY(), this.getZ(), equipable.getEquipSound(), this.getSoundSource(), 1.0F, 1.0F);
            }
        }
    }

    public void tryToReequipShield(){
        for(ItemStack itemStack : this.getInventory().items){
            if(itemStack.getItem() instanceof ShieldItem){
                this.setItemSlot(EquipmentSlot.OFFHAND, itemStack);
                this.inventory.setItem(getInventorySlotIndex(EquipmentSlot.OFFHAND), itemStack);
                Equipable equipable = Equipable.get(itemStack);
                if(equipable != null)
                    this.getCommandSenderWorld().playSound(null, this.getX(), this.getY(), this.getZ(), equipable.getEquipSound(), this.getSoundSource(), 1.0F, 1.0F);

                itemStack.shrink(1);
            }
        }
    }

    @Override
    public boolean killedEntity(@NotNull ServerLevel level, @NotNull LivingEntity living) {
        super.killedEntity(level, living);

        this.addXp(5);
        this.setKills(this.getKills() + 1);
        if(this.getMorale() < 100) this.setMoral(this.getMorale() + 1);

        if(living instanceof Player){
            this.addXp(45);
            if(this.getMorale() < 100) this.setMoral(this.getMorale() + 9);
        }

        if(living instanceof Raider){
            this.addXp(5);
            if(this.getMorale() < 100) this.setMoral(this.getMorale() + 2);
        }

        if(living instanceof Villager villager){
            if (villager.isBaby()) if(this.getMorale() > 0) this.setMoral(this.getMorale() - 10);
            else {
                if (this.getMorale() > 0) this.setMoral(this.getMorale() - 2);
            }
        }

        if(living instanceof WitherBoss){
            this.addXp(99);
            if(this.getMorale() < 100) this.setMoral(this.getMorale() + 9);
        }

        if(living instanceof IronGolem){
            this.addXp(49);
            if(this.getMorale() > 0) this.setMoral(this.getMorale() - 1);
        }

        if(living instanceof EnderDragon){
            this.addXp(999);
            if(this.getMorale() < 100) this.setMoral(this.getMorale() + 49);
        }

        this.checkLevel();
        return true;
    }

    @Override
    protected void blockUsingShield(@NotNull LivingEntity living) {
        super.blockUsingShield(living);
        if (living.getMainHandItem().canDisableShield(this.useItem, this, living))
            this.disableShield();
    }

    public void disableShield() {
            this.blockCoolDown = this.getBlockCoolDown();
            this.stopUsingItem();
            this.getCommandSenderWorld().broadcastEntityEvent(this, (byte) 30);
    }

    public boolean canBlock(){
        return this.blockCoolDown == 0;
    }

    public void updateShield(){
        if(this.blockCoolDown > 0){
            this.blockCoolDown--;
        }
    }

    public int getMountTimer() {
        return this.mountTimer;
    }

	@Override
	protected void hurtCurrentlyUsedShield(float damage) {
		ItemStack shieldStack = this.getOffhandItem();
		if (shieldStack.isEmpty()) return;

		boolean handledBySpartan = false;

		// 1. Spartan Shields (에너지 방패) 처리 - 하드 디펜던시(컴파일 에러) 제거 버전
		// 클래스 직접 참조 대신 NBT("Energy")의 존재 여부로 확인합니다.
		if (shieldStack.hasTag() && shieldStack.getTag().contains("Energy")) {
			
			// 스파르탄 쉴드의 기본 에너지 소모 배율
			int multiplier = 100;
			
			// 리플렉션을 통해 Config 값 가져오기 시도 (실패 시 기본값 100으로 안전하게 작동)
			if (net.minecraftforge.fml.ModList.get().isLoaded("spartanshields")) {
				try {
					Class<?> configClass = Class.forName("com.oblivioussp.spartanshields.config.Config");
					Object instanceObj = configClass.getField("INSTANCE").get(null);
					Object configValueObj = instanceObj.getClass().getField("damageToFEMultiplier").get(instanceObj);
					multiplier = (Integer) configValueObj.getClass().getMethod("get").invoke(configValueObj);
				} catch (Exception ignored) { }
			}

			// [폭발 시 에너지 소모 10배] (기존에 주석은 10배였으나 코드는 5배로 되어있던 오류 수정)
			if (this.handlingDamageSource != null && this.handlingDamageSource.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
				multiplier *= 10;
			}

			int energyToUse = Math.round(damage * multiplier);
			int currentEnergy = shieldStack.getTag().getInt("Energy");
			int newEnergy = Math.max(0, currentEnergy - energyToUse);
			
			// NBT 데이터 직접 갱신
			shieldStack.getTag().putInt("Energy", newEnergy);

			// 에너지가 0이 되었을 때 방어 해제 및 사운드 출력
			if (newEnergy <= 0 && currentEnergy > 0) {
				this.level().playSound(null, this.getX(), this.getY(), this.getZ(), 
					net.minecraft.sounds.SoundEvents.SHIELD_BREAK, this.getSoundSource(), 0.8F, 0.8F + this.level().random.nextFloat() * 0.4F);
				this.stopUsingItem(); 
			}
			handledBySpartan = true;
		} 
		
		// 2. 일반 방패 처리 (내구도 감소)
		if (!handledBySpartan) {
			float damageRatio = 0.75F;
			// [폭발 시 내구도 소모 5배]
			if (this.handlingDamageSource != null && this.handlingDamageSource.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
				damageRatio = 5.0F; 
			}
			int damageToApply = Math.max(1, Math.round((1.0F + damage) * damageRatio));
			
			// 내구도 깎기 (내구도 0 되면 아이템 파괴 이벤트 발생)
			shieldStack.hurtAndBreak(damageToApply, this, (p) -> p.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.OFFHAND));
			
			// [★핵심 수정] 동기화 오류 해결: 방패가 실제로 파괴되었는지 확실하게 동기화
			if (shieldStack.isEmpty()) {
				// 1. 서버 슬롯 명시적 비우기
				this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, ItemStack.EMPTY);
				// 2. 방어 모션 강제 해제
				this.stopUsingItem();
				// 3. 파괴 사운드 보장
				this.level().playSound(null, this.getX(), this.getY(), this.getZ(), 
					net.minecraft.sounds.SoundEvents.SHIELD_BREAK, this.getSoundSource(), 0.8F, 0.8F + this.level().random.nextFloat() * 0.4F);
			}
		}
	}

    @Override
    public void openGUI(Player player) {
        if (player instanceof ServerPlayer) {
            CommandEvents.updateRecruitInventoryScreen((ServerPlayer) player);
            NetworkHooks.openScreen((ServerPlayer) player, new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() {
                    return getName();
                }

                @Override
                public @NotNull AbstractContainerMenu createMenu(int i, @NotNull Inventory playerInventory, @NotNull Player playerEntity) {
                    return new RecruitInventoryMenu(i, AbstractRecruitEntity.this, playerInventory);
                }
            }, packetBuffer -> {packetBuffer.writeUUID(getUUID());});
        } else {
            Main.SIMPLE_CHANNEL.sendToServer(new MessageRecruitGui(player, this.getUUID()));
        }
    }

    public void openDebugScreen(Player player) {
        if (player instanceof ServerPlayer) {
            NetworkHooks.openScreen((ServerPlayer) player, new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() {
                    return getName();
                }

                @Override
                public AbstractContainerMenu createMenu(int i, @NotNull Inventory playerInventory, @NotNull Player playerEntity) {
                    return new DebugInvMenu(i, AbstractRecruitEntity.this, playerInventory);
                }
            }, packetBuffer -> {packetBuffer.writeUUID(getUUID());});
        } else {
            Main.SIMPLE_CHANNEL.sendToServer(new MessageDebugScreen(player, this.getUUID()));
        }
    }

    public static void openTakeOverGUI(Player player) {

    }

    @Override
    public boolean canAttack(@Nonnull LivingEntity target) {
        return RecruitEvents.canAttack(this, target);
    }
    // 0 = NEUTRAL
    // 1 = AGGRESSIVE
    // 2 = RAID
    // 3 = PASSIVE
	public boolean shouldAttack(LivingEntity target) {
		// 1. 기존 블랙리스트 체크
		if(RecruitsServerConfig.TargetBlackList.get().contains(target.getEncodeId())) return false;
		if(RecruitsServerConfig.TargetWhiteList.get().contains(target.getEncodeId())) return true;
		if(target instanceof MessengerEntity messenger && messenger.isAtMission()) return false;

		// 2. 안전 장치 (자기 자신, 주인, 같은 팀 보호)
		if (target == this) return false;
		if (this.isOwned() && target.getUUID().equals(this.getOwnerUUID())) return false;
		
		if (target instanceof AbstractRecruitEntity recruitTarget) {
			 if (this.isOwned() && recruitTarget.isOwned() && this.getOwnerUUID().equals(recruitTarget.getOwnerUUID())) return false;
		}

		if (this.getTeam() != null && target.getTeam() != null) {
			if (this.getTeam().isAlliedTo(target.getTeam())) return false;
			if (this.getTeam().getName().equals(target.getTeam().getName())) return false;
		}

		// =========================================================
		// [수정된 위치] ONLY P 로직은 반드시 switch문(최종 리턴)보다 위에 있어야 합니다.
		// =========================================================
		if (this.isOnlyPvP()) {
			boolean isPlayer = target instanceof Player;
			boolean isRecruit = target instanceof AbstractRecruitEntity;
			
			// 대상이 플레이어도 아니고 리크루트도 아니라면(몬스터라면) 공격 금지
			if (!isPlayer && !isRecruit) {
				return false;
			}
		}
		// =========================================================

		// 3. 최종 상태 판단 (기존 로직)
		return switch (this.getState()) {
			case 3 -> false; // PASSIVE
			case 0 -> shouldAttackOnNeutral(target) && canAttack(target);
			case 1 -> (shouldAttackOnNeutral(target) || shouldAttackOnAggressive(target)) && canAttack(target); // AGGRESSIVE
			case 2 -> !RecruitEvents.isAlly(this.getTeam(), target.getTeam()) && canAttack(target); // RAID
			default -> canAttack(target);
		};
	}

    private boolean shouldAttackOnNeutral(LivingEntity target){
        return isMonster(target) || isAttackingOwnerOrSelf(this, target) || RecruitEvents.isEnemy(this.getTeam(), target.getTeam());
    }

    private boolean shouldAttackOnAggressive(LivingEntity target){
        return (target instanceof AbstractRecruitEntity || target instanceof Player) && (RecruitEvents.isNeutral(this.getTeam(), target.getTeam()) || RecruitEvents.isEnemy(this.getTeam(), target.getTeam()));
    }

    private boolean isMonster(LivingEntity target) {
        return target instanceof Enemy;
    }

    private boolean isAttackingOwnerOrSelf(AbstractRecruitEntity recruit, LivingEntity target) {
        return target.getLastHurtByMob() != null &&
                (target.getLastHurtByMob().equals(recruit) || target.getLastHurtByMob().equals(recruit.getOwner()));
    }

    public boolean isAlliedTo(Entity target) {
        if (target instanceof LivingEntity livingTarget) {
            return !RecruitEvents.canHarmTeam(this, livingTarget);
        } else {
            return super.isAlliedTo(target);
        }
    }

    //
    /*********************************************************
     * Update the current team of the recruit in following conditions:
     * - If recruit team is not the same team as the owner
     * - If recruit team is null but owner team != null
     * - If recruit team is != null but owner team is null
     *********************************************************/
    public void updateTeam(){
// [추가된 부분] 그룹 99번(Black Ops)이면 팀 업데이트 차단
		/*if (this.getGroup() == 999999) {
			// 만약 어떤 이유로 팀 정보가 남아있다면 제거
			if (this.getTeam() != null && !this.getCommandSenderWorld().isClientSide()) {
				TeamEvents.removeRecruitFromTeam(this, this.getTeam(), (ServerLevel) this.getCommandSenderWorld());
			}
			this.needsTeamUpdate = false;
			return; 
		}*/
		// [추가된 부분 끝]
        if(this.isOwned() && !this.getCommandSenderWorld().isClientSide()){
            Player owner = getOwner();
            if(owner != null) {
                Team recruitTeam = this.getTeam();
                Team ownerTeam = owner.getTeam();

                if (ownerTeam == null) {
                    if(recruitTeam != null){
                        //Remove from current team because ownerTeam is null
                        TeamEvents.removeRecruitFromTeam(this, recruitTeam, (ServerLevel) this.getCommandSenderWorld());
                        TeamEvents.addNPCToData((ServerLevel) this.getCommandSenderWorld(), recruitTeam.getName(), -1 );
                    }
                    //recruit team is also null, so no do nothing
                    needsTeamUpdate = false;
                }
                else if(recruitTeam == null){
                    TeamEvents.addRecruitToTeam(this, ownerTeam, (ServerLevel) this.getCommandSenderWorld());
                    TeamEvents.addNPCToData((ServerLevel) this.getCommandSenderWorld(), ownerTeam.getName(), +1 );
                    needsTeamUpdate = false;
                }
                else if(recruitTeam == ownerTeam){
                    updateColor(ownerTeam.getName());
                    needsTeamUpdate = false;
                }
                else{
                    TeamEvents.removeRecruitFromTeam(this, recruitTeam, (ServerLevel) this.getCommandSenderWorld());
                    TeamEvents.addNPCToData((ServerLevel) this.getCommandSenderWorld(), recruitTeam.getName(), -1 );

                    TeamEvents.addRecruitToTeam(this, ownerTeam, (ServerLevel) this.getCommandSenderWorld());
                    TeamEvents.addNPCToData((ServerLevel) this.getCommandSenderWorld(), ownerTeam.getName(), +1 );
                    needsTeamUpdate = false;
                }
            }
        }
    }

    private void updateColor(String name) {
        if(!this.getCommandSenderWorld().isClientSide()){
            RecruitsTeam recruitsTeam = TeamEvents.recruitsTeamManager.getTeamByStringID(name);
            if(recruitsTeam != null && recruitsTeam.getUnitColor() != this.getColor()){
                this.setColor(recruitsTeam.getUnitColor());
                this.needsColorUpdate = false;
            }
        }
    }

    public void openHireGUI(Player player) {
        if (player instanceof ServerPlayer) {
            this.navigation.stop();
            Team ownerTeam = player.getTeam();
            String stringId = ownerTeam != null ? ownerTeam.getName() : "";
            boolean canHire = RecruitEvents.recruitsPlayerUnitManager.canPlayerRecruit(stringId, player.getUUID());
            Main.SIMPLE_CHANNEL.send(PacketDistributor.PLAYER.with(()-> (ServerPlayer) player), new MessageToClientUpdateHireScreen(TeamEvents.getCurrency(), canHire));
            NetworkHooks.openScreen((ServerPlayer) player, new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() {
                    return getName();
                }

                @Override
                public AbstractContainerMenu createMenu(int i, @NotNull Inventory playerInventory, @NotNull Player playerEntity) {
                    return new RecruitHireMenu(i, playerInventory.player, AbstractRecruitEntity.this, playerInventory);
                }
            }, packetBuffer -> {packetBuffer.writeUUID(getUUID());});
        } else {
            Main.SIMPLE_CHANNEL.sendToServer(new MessageHireGui(player, this.getUUID()));
        }
    }

    public void shouldMount(boolean should, UUID mount_uuid) {
        if (!this.isPassenger()){
            this.setShouldMount(should);
            if(mount_uuid != null) {
                this.setMountUUID(Optional.of(mount_uuid));
            }
            else
                this.setMountUUID(Optional.empty());
        }
        if(should) this.dismount = 0;
    }

    public void shouldProtect(boolean should, UUID protect_uuid) {
        this.setShouldProtect(should);
        if(protect_uuid != null) this.setProtectUUID(Optional.of(protect_uuid));
        else this.setProtectUUID(Optional.empty());
    }

    public void clearUpkeepPos() {
        this.entityData.set(UPKEEP_POS, Optional.empty());
    }

    public void clearUpkeepEntity() {
        this.entityData.set(UPKEEP_ID, Optional.empty());
    }

    public boolean hasUpkeep(){
        return this.getUpkeepPos() != null || this.getUpkeepUUID() != null;
    }

// [수정됨] 인벤토리 + 탄약 박스 내부까지 모두 뒤져서 총알 갯수를 파악하는 메서드
    public boolean canTakeGunAmmo(ResourceLocation ammoId) {
        if (ammoId == null) return false;

        int totalCount = 0;

        // 용병 인벤토리 전체 순회
        for(int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack stack = this.inventory.getItem(i);
            if (stack.isEmpty()) continue;

            ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (itemId == null) continue;

            // 1. 낱개 탄약 카운트
            if (itemId.equals(ammoId)) {
                totalCount += stack.getCount();
            }
            
            // 2. 탄약 박스(jeg:ammo_box) 내부 카운트
            else if (itemId.toString().equals("jeg:ammo_box")) {
                CompoundTag rootTag = stack.getTag();
                // NBT 데이터가 있는 경우만 검사
                if (rootTag != null && rootTag.contains("BlockEntityTag", 10)) {
                    CompoundTag blockEntityTag = rootTag.getCompound("BlockEntityTag");
                    if (blockEntityTag.contains("Items", 9)) {
                        // 탄약 박스 내부 아이템 목록 로드 (크기 15)
                        net.minecraft.core.NonNullList<ItemStack> boxContents = 
                            net.minecraft.core.NonNullList.withSize(15, ItemStack.EMPTY);
                        
                        net.minecraft.world.ContainerHelper.loadAllItems(blockEntityTag, boxContents);

                        // 박스 내부 아이템 순회
                        for (ItemStack innerStack : boxContents) {
                            if (!innerStack.isEmpty()) {
                                ResourceLocation innerId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(innerStack.getItem());
                                if (innerId != null && innerId.equals(ammoId)) {
                                    totalCount += innerStack.getCount();
                                }
                            }
                        }
                    }
                }
            }
        }

        // 합산된 총알이 128발(2세트) 미만일 때만 true 반환
        return totalCount < 128;
    }

	public void upkeepReequip(@NotNull Container container) {
        // 0. [신규] 빈 탄약 박스 반납 시도 (인벤토리 공간 확보)
        this.returnEmptyAmmoBoxes(container);

        // 1. 현재 주무기가 JEG 총기인지 확인하고, 필요한 탄약 ID 파악
        ResourceLocation gunAmmoId = null;
        ItemStack mainHand = this.getMainHandItem();

        // JEG 총기인지 확인
        if (mainHand.getItem() instanceof ttv.migami.jeg.item.GunItem) {
            ttv.migami.jeg.common.Gun gun = ((ttv.migami.jeg.item.GunItem) mainHand.getItem()).getGun();
            if (gun != null && gun.getProjectile() != null) {
                gunAmmoId = gun.getProjectile().getItem();
            }
        }

        boolean tookAmmoBox = false; // 이번 틱에 탄약 박스를 가져갔는지 체크

        // 2. 보급 상자 스캔
        for(int i = 0; i < container.getContainerSize(); i++) {
            ItemStack itemstack = container.getItem(i);
            
            if (itemstack.isEmpty()) continue;

            ResourceLocation regId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(itemstack.getItem());
            if (regId == null) continue;
            String itemId = regId.toString();
            
            ItemStack equipment;

            // --- [JEG 총기 탄약 보급 로직] ---
            if (gunAmmoId != null) {
                
                // Case A: [우선순위 1] '탄약 박스(jeg:ammo_box)'인 경우
                // 이미 박스를 하나 챙겼다면(tookAmmoBox) 더 가져가지 않음 (1개만)
                if (!tookAmmoBox && itemId.equals("jeg:ammo_box")) {
                    // 탄약이 부족하고 + 박스 안에 내 총알이 128발 이상 들어있으면
                    if (this.canTakeGunAmmo(gunAmmoId) && getAmmoCountInBox(itemstack, gunAmmoId) >= 128) {
                        
                        // 박스를 통째로 복사해서 내 인벤토리에 넣음
                        equipment = itemstack.copy();
                        equipment.setCount(1); // 박스는 1개만
                        
                        // [수정됨] addItem()은 남은 아이템(ItemStack)을 반환하므로, isEmpty()로 성공 여부 확인
                        if (this.inventory.addItem(equipment).isEmpty()) {
                            itemstack.shrink(1); // 보급 상자에서 박스 1개 제거 (실제 이동)
                            tookAmmoBox = true;  // 박스 챙김 표시
                            continue; // 다음 아이템으로
                        }
                    }
                }
                
                // Case B: [우선순위 2] '낱개 탄약'인 경우
                // 박스를 못 찾았거나 못 가져갔을 때만 낱개를 챙김
                else if (regId.equals(gunAmmoId)) {
                    if (this.canTakeGunAmmo(gunAmmoId)) {
                        equipment = itemstack.copy();
                        int countBefore = equipment.getCount();
                        
                        this.inventory.addItem(equipment); 
                        
                        // 실제로 들어간 갯수만큼 상자에서 차감
                        int amountTaken = countBefore - equipment.getCount();
                        itemstack.shrink(amountTaken); 
                        
                        if (itemstack.isEmpty()) continue;
                    }
                }
            }

            // --- [기존 장비 및 기타 모드 아이템 보급 로직] ---
            if(!this.canEatItemStack(itemstack) && this.wantsToPickUp(itemstack)){
                if (this.canEquipItem(itemstack)) {
                    equipment = itemstack.copy();
                    equipment.setCount(1);
                    this.equipItem(equipment);
                    itemstack.shrink(1);
                }
                
                if(this instanceof CrossBowmanEntity crossBowmanEntity && Main.isMusketModLoaded && IWeapon.isMusketModWeapon(crossBowmanEntity.getMainHandItem()) && itemstack.getDescriptionId().contains("cartridge")){
                    if(this.canTakeCartridge()){
                        equipment = itemstack.copy();
                        this.inventory.addItem(equipment);
                        itemstack.shrink(equipment.getCount());
                    }
                }
                else if (this instanceof IRangedRecruit && itemstack.is(ItemTags.ARROWS)){ 
                    if(this.canTakeArrows()){
                        equipment = itemstack.copy();
                        this.inventory.addItem(equipment);
                        itemstack.shrink(equipment.getCount());
                    }
                }
            }
            
            // 수류탄 및 선장 유닛 로직
            if (this.isHoldingGrenadeLauncher() && itemId.equals("jeg:grenade")) {
                if (this.canTakeGrenades()) {
                    equipment = itemstack.copy();
                    this.inventory.addItem(equipment); 
                    itemstack.shrink(equipment.getCount());
                }
            }
            if (this instanceof CaptainEntity && Main.isSmallShipsLoaded){
                if(itemstack.getDescriptionId().contains("cannon_ball")){
                    if(this.canTakeCannonBalls()){
                        equipment = itemstack.copy();
                        this.inventory.addItem(equipment);
                        itemstack.shrink(equipment.getCount());
                    }
                }
                else if (itemstack.is(ItemTags.PLANKS)){
                    if(this.canTakePlanks()){
                        equipment = itemstack.copy();
                        this.inventory.addItem(equipment);
                        itemstack.shrink(equipment.getCount());
                    }
                }
                else if (itemstack.is(Items.IRON_NUGGET)){
                    if(this.canTakeIronNuggets()){
                        equipment = itemstack.copy();
                        this.inventory.addItem(equipment);
                        itemstack.shrink(equipment.getCount());
                    }
                }
            }
        }
    }
// [신규] 탄약 박스 아이템(Stack) 안에 특정 탄약이 몇 발 들었는지 확인
    private int getAmmoCountInBox(ItemStack boxStack, ResourceLocation targetAmmoId) {
        int total = 0;
        CompoundTag rootTag = boxStack.getTag();
        if (rootTag != null && rootTag.contains("BlockEntityTag", 10)) {
            CompoundTag blockEntityTag = rootTag.getCompound("BlockEntityTag");
            if (blockEntityTag.contains("Items", 9)) {
                net.minecraft.core.NonNullList<ItemStack> contents = 
                    net.minecraft.core.NonNullList.withSize(15, ItemStack.EMPTY);
                net.minecraft.world.ContainerHelper.loadAllItems(blockEntityTag, contents);

                for (ItemStack s : contents) {
                    if (!s.isEmpty()) {
                        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem());
                        if (id != null && id.equals(targetAmmoId)) {
                            total += s.getCount();
                        }
                    }
                }
            }
        }
        return total;
    }

    // [신규] 탄약 박스가 비어있는지 확인
    private boolean isAmmoBoxEmpty(ItemStack boxStack) {
        CompoundTag rootTag = boxStack.getTag();
        if (rootTag == null || !rootTag.contains("BlockEntityTag", 10)) return true; // 태그 없으면 빈 것 취급
        
        CompoundTag blockEntityTag = rootTag.getCompound("BlockEntityTag");
        if (!blockEntityTag.contains("Items", 9)) return true; // 아이템 리스트 없으면 빈 것

        net.minecraft.core.NonNullList<ItemStack> contents = 
            net.minecraft.core.NonNullList.withSize(15, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(blockEntityTag, contents);

        for (ItemStack s : contents) {
            if (!s.isEmpty()) return false; // 하나라도 들어있으면 안 빈 것
        }
        return true;
    }

    // [신규] 용병 인벤토리의 빈 탄약 박스를 보급 상자로 반납
    private void returnEmptyAmmoBoxes(Container chest) {
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack myStack = this.inventory.getItem(i);
            ResourceLocation regId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(myStack.getItem());
            
            // 내 인벤토리에 탄약 박스가 있고, 그게 비어있다면
            if (regId != null && regId.toString().equals("jeg:ammo_box") && isAmmoBoxEmpty(myStack)) {
                
                // 보급 상자(chest)의 빈 슬롯을 찾음
                for (int j = 0; j < chest.getContainerSize(); j++) {
                    if (chest.getItem(j).isEmpty()) {
                        // 상자에 넣고 내 인벤에서 삭제
                        chest.setItem(j, myStack.copy());
                        this.inventory.removeItemNoUpdate(i); // 혹은 setItem(i, ItemStack.EMPTY)
                        break; // 하나 반납했으면 루프 종료 (한 틱에 하나씩)
                    }
                }
            }
        }
    }

    public static enum ArmPose {
        ATTACKING,
        BLOCKING,
        BOW_AND_ARROW,
        CROSSBOW_HOLD,
        CROSSBOW_CHARGE,
        CELEBRATING,
        NEUTRAL;
    }

    public int getUpkeepCooldown() {
        return 3000;
    }

    public AbstractRecruitEntity.ArmPose getArmPose() {
        return AbstractRecruitEntity.ArmPose.NEUTRAL;
    }

    private MutableComponent TEXT_RECRUITED1(String name) {
        return Component.translatable("chat.recruits.text.recruited1", name);
    }

    private MutableComponent TEXT_RECRUITED2(String name) {
        return Component.translatable("chat.recruits.text.recruited2", name);
    }

    private MutableComponent TEXT_RECRUITED3(String name) {
        return Component.translatable("chat.recruits.text.recruited3", name);
    }

    private Component INFO_RECRUITING_MAX(String name) {
        return Component.translatable("chat.recruits.info.reached_max", name);
    }

    private MutableComponent TEXT_DISBAND(String name) {
        return Component.translatable("chat.recruits.text.disband", name);
    }

    private MutableComponent TEXT_WANDER(String name) {
        return Component.translatable("chat.recruits.text.wander", name);
    }

    private MutableComponent TEXT_HOLD_YOUR_POS(String name) {
        return Component.translatable("chat.recruits.text.holdPos", name);
    }

    private MutableComponent TEXT_FOLLOW(String name) {
        return Component.translatable("chat.recruits.text.follow", name);
    }

    private MutableComponent TEXT_HELLO_1(String name) {
        return Component.translatable("chat.recruits.text.hello_1", name);
    }

    private MutableComponent TEXT_HELLO_2(String name) {
        return Component.translatable("chat.recruits.text.hello_2", name);
    }

    private MutableComponent TEXT_HELLO_3(String name) {
        return Component.translatable("chat.recruits.text.hello_3", name);
    }

    private MutableComponent TEXT_NO_PAYMENT(String name) {
        return Component.translatable("chat.recruits.text.noPaymentInUpkeep", name);
    }

    private void pickUpArrows() {
        this.getCommandSenderWorld().getEntitiesOfClass(
                AbstractArrow.class,
                this.getBoundingBox().inflate(7D),
                (arrow) -> arrow.inGround &&
                        arrow.pickup == AbstractArrow.Pickup.ALLOWED &&
                        this.getInventory().canAddItem(Items.ARROW.getDefaultInstance())
        ).forEach((arrow) -> {
            this.getInventory().addItem(Items.ARROW.getDefaultInstance());
            arrow.moveTo(this.position());
            arrow.discard();
        });
    }

    @Override
    public boolean startRiding(Entity entity) {
        this.setMountUUID(Optional.of(entity.getUUID()));
        return super.startRiding(entity);
    }

    @Override
    public boolean removeWhenFarAway(double p_21542_) {
        return false;
    }

	
// [수정됨] 크래시 해결 및 일반 음식 인식 로직 포함
    public boolean canEatItemStack(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // 1. 메카니즘 수통(Canteen) 체크 로직
        // 복잡한 캐스팅이나 Capability 검사 없이 ID만 확인하여 안전하게 처리합니다.
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null && id.toString().equals("mekanism:canteen")) {
             // 수통이면 무조건 식량으로 간주합니다. 
             // (내용물이 비어있는지 여부는 먹을 때(RecruitEatGoal) 판단하면 되므로, 
             //  보급 단계에서는 "일단 도시락통이 있다"고 판단하는 것이 더 안전합니다.)
             return true; 
        }

        // 2. [필수] 일반 마인크래프트 음식(감자, 빵 등) 체크
        // 이 줄이 있어야 감자를 식량으로 인식합니다.
        if (stack.isEdible()) {
            return true;
        }

        return false;
    }
	@Override
	public void completeUsingItem() {
		ItemStack stack = this.getUseItem();
		ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());

		// [수통 사용 로직]
		if (!stack.isEmpty() && id != null && id.toString().equals("mekanism:canteen")) {
			
			// 1. 액체 핸들러 가져오기
			Optional<IFluidHandlerItem> handlerOpt = FluidUtil.getFluidHandler(stack).resolve();
			
			if (handlerOpt.isPresent()) {
				IFluidHandlerItem handler = handlerOpt.get();
				
				// 2. 액체 소모 시도 (10mB를 빼냄)
				// Mekanism 기본 설정상 영양 페이스트는 소량으로도 허기가 많이 찹니다.
				int drainAmount = 10; 
				FluidStack drained = handler.drain(drainAmount, IFluidHandler.FluidAction.EXECUTE);

				// 3. 실제로 액체가 빠져나갔다면 회복 적용
				if (!drained.isEmpty() && drained.getAmount() > 0) {
					// 효과음
					this.playSound(SoundEvents.GENERIC_DRINK, 0.5F, this.level().random.nextFloat() * 0.1F + 0.9F);

					// 허기 회복 (Mekanism 기준 10mB면 꽤 많은 양입니다. 적절히 조절하세요)
					// Recruits의 Max Hunger는 100입니다.
					float healAmount = 40.0F; 
					this.setHunger(Math.min(100F, this.getHunger() + healAmount));

					// 사기 진작
					if(this.getMorale() < 100) {
						this.setMoral(this.getMorale() + 2.0F);
					}
				}
			}
			
			// 4. 아이템 상태 업데이트 (액체가 줄어든 수통을 다시 인벤토리에 반영할 필요가 있음)
			// AbstractInventoryEntity.resetItemInHand()가 호출되면서 
			// 현재 손에 들고 있는(액체가 줄어든) 스택을 인벤토리로 되돌립니다.
			
			this.stopUsingItem();
			return; 
		}

		// 일반 음식 로직
		super.completeUsingItem();
	}

	public void checkPayment(Container container) {
		if (RecruitsServerConfig.RecruitsPayment.get() && isOwned()) {
			int wage = RecruitsServerConfig.RecruitsPaymentAmount.get();
			boolean paid = false;

			// 1. [파벌 금고 자동 차감]: 병사가 팀에 속해 있다면 파벌 금고에서 우선 지출
			if (this.getTeam() != null && !this.level().isClientSide()) {
				RecruitsTeam team = TeamEvents.recruitsTeamManager.getTeamByStringID(this.getTeam().getName());
				if (team != null && team.withdraw(wage)) {
					paid = true;
					TeamEvents.recruitsTeamManager.save((ServerLevel) this.level());
				}
			}

			// 2. 파벌 금고에서 못 냈다면 기존 보급 상자 확인
			if (!paid && isPaymentInContainer(container)) {
				doPayment(container);
				paid = true;
			}

			// 3. 보급 상자에도 없다면 병사 인벤토리 확인
			if (!paid && isPaymentInContainer(this.getInventory())) {
				doPayment(this.getInventory());
				paid = true;
			}

			// 4. 전부 실패했을 때만 미지급 페널티 부여
			if (!paid) {
				this.doNoPaymentAction();
				if (this.getOwner() != null) {
					this.getOwner().sendSystemMessage(TEXT_NO_PAYMENT(this.getName().getString()));
				}
			}

			resetPaymentTimer();
		}
	}

    public void doNoPaymentAction(){
        NoPaymentAction action = RecruitsServerConfig.RecruitsNoPaymentAction.get();
        switch (action){
            case MORALE_LOSS -> {
                float current = this.getMorale();
                float newMorale = (float) Math.max(0, current * 0.7);//30% loss
                this.setMoral(newMorale);
            }

            case DISBAND_KEEP_TEAM -> {
                this.disband(this.getOwner(), true, true);
            }

            case DISBAND -> {
                this.disband(this.getOwner(), false, true);
            }

            case DESPAWN -> {
                this.discard();
            }
        }

    }

    public void resetPaymentTimer(){
        int interval = RecruitsServerConfig.RecruitsPaymentInterval.get();
        this.paymentTimer = 20*60*interval;
    }

    public enum NoPaymentAction{
        MORALE_LOSS,
        DISBAND,
        DISBAND_KEEP_TEAM,
        DESPAWN;

        public static NoPaymentAction fromString(String name) {
            try {
                return NoPaymentAction.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                return MORALE_LOSS;
            }
        }
    }
	// [신규] 나침반을 가지고 있는지 확인하는 헬퍼 메서드
    public boolean hasCompass() {
        // 인벤토리 전체(장비창 포함)를 순회하며 나침반 확인
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack stack = this.inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == net.minecraft.world.item.Items.COMPASS) {
                return true;
            }
        }
        return false;
    }

    // [신규] 텔레포트 조건 검사 및 실행
    private void checkCompassTeleport() {
        // 1. 따라가기(Follow) 상태가 아니면 작동 안 함
        if (this.getFollowState() != 1) return;

        // 2. 주인이 없거나 죽었으면 작동 안 함
        LivingEntity owner = this.getOwner();
        if (owner == null || !owner.isAlive()) return;

        // 3. 탑승 중이거나(말, 배 등), 앉아있거나, 비행 중이면 작동 안 함 (안전 장치)
        if (this.isPassenger() || this.isLeashed()) return;

        // 4. 거리 체크: 12블록(거리제곱 144) 이상 떨어졌을 때만
        double distanceSq = this.distanceToSqr(owner);
        if (distanceSq < 576.0D) return;

        // 5. [핵심] 인벤토리에 나침반이 있는지 확인
        if (!hasCompass()) return;

        // 6. 텔레포트 시도
        this.tryToTeleportNearEntity(owner);
    }

	private void tryToTeleportNearEntity(LivingEntity target) {
        BlockPos targetPos = target.blockPosition();

        for (int i = 0; i < 10; ++i) {
            int dx = this.random.nextInt(7) - 3; 
            int dz = this.random.nextInt(7) - 3; 
            int dy = this.random.nextInt(3) - 1; 

            double finalX = target.getX() + dx;
            double finalY = target.getY() + dy;
            double finalZ = target.getZ() + dz;

            if (canTeleportTo(new BlockPos((int)finalX, (int)finalY, (int)finalZ))) {
                
                // 1. 텔레포트 실행
                this.teleportTo(finalX, finalY, finalZ);
                
                // 2. [추가됨] 나침반 1개 소모
                this.consumeOneCompass();

                // 3. 네비게이션 초기화 및 효과음
                this.getNavigation().stop();
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), 
                        SoundEvents.ENDERMAN_TELEPORT, this.getSoundSource(), 1.0F, 1.0F);
                
                // [선택 사항] 소모되었다는 효과음 추가 (아이템 부서지는 소리 등)
                // this.playSound(SoundEvents.ITEM_BREAK, 0.8F, 0.8F + this.level().random.nextFloat() * 0.4F);

                return; 
            }
        }
    }

    // [신규] 텔레포트 도착 지점이 안전한지 검사
    private boolean canTeleportTo(BlockPos pos) {
        // 1. 발 디딜 땅이 단단한 블록인가?
        boolean groundSolid = this.level().getBlockState(pos.below()).blocksMotion();
        
        // 2. 몸통 위치가 공기(또는 통과 가능)인가?
        boolean bodyEmpty = this.level().getBlockState(pos).getCollisionShape(this.level(), pos).isEmpty();
        
        // 3. 머리 위치가 공기인가?
        boolean headEmpty = this.level().getBlockState(pos.above()).getCollisionShape(this.level(), pos.above()).isEmpty();

        return groundSolid && bodyEmpty && headEmpty;
    }
	
	// [신규] 인벤토리에서 나침반 1개를 소모하는 메서드
    private void consumeOneCompass() {
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack stack = this.inventory.getItem(i);
            
            // 아이템이 나침반인지 확인
            if (!stack.isEmpty() && stack.getItem() == net.minecraft.world.item.Items.COMPASS) {
                
                // 1개 줄임 (만약 1개였다면 자동으로 사라짐)
                stack.shrink(1);
                
                // 소모했으니 루프 종료 (한 번에 하나만 소모)
                break;
            }
        }
    }
// 기존 int 그룹 번호 요청 시: UUID의 하위 64비트를 int로 변환 (0번은 0 반환)
    public int getGroup() {
        UUID u = getGroupUUID();
        if (u == null) return 0;
        if (u.getMostSignificantBits() == 0L) {
            return (int) u.getLeastSignificantBits(); // 0, 1, 2, 3 등 기본 번호 복원
        }
        return Math.abs(u.hashCode());
    }

    // 기존 int 그룹 지정 시: this.setGroup(1); 호출 지원
    public void setGroup(int legacyGroupId) {
        if (legacyGroupId == 0) {
            this.setGroup((UUID) null);
        } else {
            this.setGroup(new UUID(0L, (long) legacyGroupId));
        }
    }

    // int형 명령 호출 호환: recruit.isEffectedByCommand(playerUUID, intGroup)
    public boolean isEffectedByCommand(UUID player_uuid, int legacyGroupId) {
        if (legacyGroupId == 0) {
            return this.isEffectedByCommand(player_uuid, (UUID) null);
        }
        return this.isEffectedByCommand(player_uuid, new UUID(0L, (long) legacyGroupId));
    }
}
