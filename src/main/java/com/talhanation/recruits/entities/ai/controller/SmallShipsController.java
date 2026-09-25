package com.talhanation.recruits.entities.ai.controller;

import com.talhanation.recruits.compat.SmallShips;
import com.talhanation.recruits.entities.CaptainEntity;
import com.talhanation.recruits.entities.ai.navigation.SailorPathNavigation;
import com.talhanation.recruits.util.Kalkuel;
import com.talhanation.recruits.util.WaterObstacleScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import com.talhanation.recruits.entities.AbstractLeaderEntity;
import com.github.x3r.mekanism_turrets.common.entity.MissileEntity;
import com.talhanation.smallships.world.entity.ship.CruiserEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;

import com.talhanation.smallships.world.entity.ship.abilities.Cannonable;

import java.util.Comparator;

public class SmallShipsController {
    public static final boolean DEBUG = false;
    public static final int REACH = 150;
    public static final int RECALCULATION_TIME = 300;
    private Path path;
    private final Level world;
    private final SailorPathNavigation pathNavigation;
    private final CaptainEntity captain;
    public SmallShips ship;
    private int recalcPath;
    private Node currentNode;
    public double distanceToSailPos;
    public WaterObstacleScanner waterObstacleScanner;
// [★신규 추가] 미사일 전용 독립 쿨타임
    public int cruiserMissileCooldown = 0;

    public boolean lastShouldShootLeft = false;

    public SmallShipsController(CaptainEntity captain, Level world) {
        pathNavigation = new SailorPathNavigation(captain, world);
        this.world = world;
        this.captain = captain;
    }

    public void tryMountShip(Entity entity) {
        if (entity instanceof Boat boat && SmallShips.isSmallShip(boat)) {
            ship = new SmallShips(boat, this.captain);
        }
    }

    public void tryDisMountShip() {
        Entity entity = this.captain.getVehicle();
        if (entity instanceof Boat boat && SmallShips.isSmallShip(boat)) {
            ship = null;
        }
        waterObstacleScanner = null;
    }
    public void calculatePath() {
        this.recalcPath = 0;
    }
    private int sailState;
    public boolean right;
    public boolean left;
    public double reach;

	public void tick() {
        // [★ 핵심 수정 1] 미사일 쿨타임 감소 로직 최상단 배치
        // 어떤 얼리 리턴(return) 조건보다도 먼저 실행되어야 타겟이 죽더라도 쿨타임이 정상적으로 깎입니다.
        if (this.cruiserMissileCooldown > 0) {
            this.cruiserMissileCooldown--;
        }

        if(this.world.isClientSide() || this.captain.level().isClientSide()) return;
        
        // [★ 핵심 수정 2] 순양함 무장(ShouldRanged) 강제 활성화
        // 대포(Cannon)가 없더라도 원거리 공격을 하도록 플래그를 강제로 켭니다.
        if (ship != null && ship.getBoat() instanceof com.talhanation.smallships.world.entity.ship.CruiserEntity) {
            captain.setShouldRanged(true);
        }

        if(captain.getVehicle() == null || captain.getSailPos() == null || ship == null) return;
        if(!ship.isCaptainDriver()) return;

        if(updateAttacking()) return;

        BlockPos sailPos = this.captain.getSailPos();
        distanceToSailPos = captain.distanceToSqr(sailPos.getX(), captain.getY(), sailPos.getZ());
        reach = captain.getFollowState() == 1 || captain.getFollowState() == 5 ? REACH * 2 : REACH;
        if(distanceToSailPos < reach || captain.attackController.isTargetInRange()){
            currentNode = null;
            path = null;
            ship.setSailState(0);
            if(DEBUG && captain.getOwner() != null) this.captain.getOwner().sendSystemMessage(Component.literal(captain.getName().getString() + ": REACHED SAILPOS"));
            return;
        }

        //CHECK IF CORRECTLY ALIGNED
        if (currentNode != null) {
            Vec3 forward = ship.getBoat().getForward().yRot(-90).normalize();
            Vec3 target = new Vec3(currentNode.x, 0, currentNode.z);
            Vec3 toTarget = target.subtract(ship.getBoat().position()).normalize();

            double phi = Kalkuel.horizontalAngleBetweenVectors(forward, toTarget);
            double ref = 63.334F;
            double stopThreshold = ref * 0.80F;

            if (Math.abs(phi) < stopThreshold) {
                left = phi < ref;
                right = phi > ref;

                ship.updateSmallShipsControl(right, left, 0);
                if(DEBUG && captain.getOwner() != null) this.captain.getOwner().sendSystemMessage(Component.literal(captain.getName().getString() + ": ROTATING"));

                return;
            }
        }

        if (path != null && currentNode != null) {
            double distanceToNode = this.captain.getVehicle().distanceToSqr(currentNode.x, this.captain.getVehicle().getY(), currentNode.z);

            if (distanceToNode < reach) {
                calculatePath();
            }
        }

        //CHECK IF OBSTRACLES ARE NEAR
        if(waterObstacleScanner != null){
            int obstaclesLeft = waterObstacleScanner.getObstaclesLeft();
            int obstaclesRight = waterObstacleScanner.getObstaclesRight();
            int obstaclesFront = waterObstacleScanner.getObstaclesFront(10);

            if(obstaclesRight != 0 || obstaclesLeft != 0){
                if(obstaclesLeft < obstaclesRight){
                    right = true;
                    left = false;
                    sailState = 2;
                }
                else if(obstaclesLeft > obstaclesRight){
                    right = false;
                    left = true;
                    sailState = 2;
                }
                else if(obstaclesFront > 0){
                    sailState = 0;
                    right = true;
                    left = false;
                }
                else{
                    right = false;
                    left = false;
                }

                ship.updateSmallShipsControl(right, left, sailState);
                if(DEBUG && captain.getOwner() != null) this.captain.getOwner().sendSystemMessage(Component.literal(captain.getName().getString() + ": OBSTRACLES ARE NEAR"));

                return;
            }

            //CHECK IF FOLLOW
            if(this.captain.getFollowState() == 1 || this.captain.getFollowState() == 5) {
                if(obstaclesFront <= 0){
                    if(distanceToSailPos < 500){
                        ship.setSailState(2);
                    }
                    else{
                        ship.setSailState(4);
                    }

                    ship.updateSmallShipsControl(this.captain.getSailPos().getX(), this.captain.getSailPos().getZ(), sailState);
                    if(DEBUG && captain.getOwner() != null) this.captain.getOwner().sendSystemMessage(Component.literal(captain.getName().getString() + ": FOLLOWING"));
                    return;
                }
            }

            //DEFAULT PATHFINDING
            if (--recalcPath <= 0) {
                recalcPath = RECALCULATION_TIME;
                this.path = pathNavigation.createPath(this.captain.getSailPos(), 32, false, 0);
                if(DEBUG && captain.getOwner() != null) this.captain.getOwner().sendSystemMessage(Component.literal(captain.getName().getString() + ": CREATING PATH"));
                if(path != null){
                    try {
                        this.currentNode = path.getEndNode();// FIX for "IndexOutOfBoundsException: Index 23 out of bounds for length 23" or "Index 1 out of bounds for length 1"

                    } catch (IndexOutOfBoundsException e) {
                        this.currentNode = path.nodes.get(path.nodes.size() - 1);
                    }
                    sailState = 4;
                    reach = REACH;
                }
            }

            if(path != null && DEBUG){
                for(Node node : this.path.nodes) {
                    captain.getCommandSenderWorld().setBlock(new BlockPos(node.x, (int) (captain.getY() + 4), node.z), Blocks.ICE.defaultBlockState(), 3);
                }
            }

            if (path != null && currentNode != null) {
                double distanceToNode = this.captain.getVehicle().distanceToSqr(currentNode.x, this.captain.getVehicle().getY(), currentNode.z);

                if (distanceToNode < reach) {
                    calculatePath();
                }
                if(DEBUG && captain.getOwner() != null) this.captain.getOwner().sendSystemMessage(Component.literal(captain.getName().getString() + ": FOLLOWING PATH"));
                ship.updateSmallShipsControl(currentNode.x, currentNode.z, sailState);

            }
        }
        else{
            this.waterObstacleScanner = new WaterObstacleScanner(captain.getCommandSenderWorld(), ship.getBoat());
        }
    }
    public Entity target;
    private final int attackRange = 40000;
    private final int followRange = 60000;
    public double distanceToTarget;
	
	private boolean updateAttacking() {
        if (captain.getShouldStrategicFire() && captain.getStrategicFirePos() != null) {
            return false;
        }

        if (this.target != null && !this.target.isAlive()) {
            this.target = null;
            this.captain.setTarget(null);
        }

        if (this.target == null || this.target.isUnderWater() || ship.getBoat().getPassengers().contains(this.target)) {
            if (this.captain.getTarget() != null && this.captain.getTarget().isAlive() && !this.captain.getTarget().isUnderWater()) {
                this.target = this.captain.getTarget();
            } else {
                if (this.captain.tickCount % 20 == 0) checkForNextTarget();
                return false;
            }
        }

        boolean isCruiser = ship.getBoat() instanceof CruiserEntity;

        if (target instanceof LivingEntity livingTarget) {
            // [★핵심 수정] 순양함 여부와 상관없이 무조건 선장의 타겟 설정(외교, 공격 성향 등)에 부합하는지 검사합니다.
            // 맞지 않는 타겟이라면 즉시 타겟팅을 해제하고 공격을 멈춥니다.
            if (!this.captain.canFireAtTarget(livingTarget)) {
                this.target = null; 
                this.captain.setTarget(null);
                return false;
            }
        } else if (target instanceof Boat) {
            if (this.captain.getInfoMode() == AbstractLeaderEntity.InfoMode.NONE.getIndex()) {
                this.target = null;
                return false;
            }
        }

        this.distanceToTarget = enemyDistanceToLeader(target);
        if(distanceToTarget > attackRange) return false;

        if(isTooFarFromMovementRange()){
            target = null;
            this.captain.commandCooldown = 100;
            return false;
        }

        if (ship.canShootCannons() || isCruiser) {
            int followState = captain.getFollowState();
            boolean isHolding = (followState == 2 || followState == 3 || followState == 4);

            if (isHolding) {
                // 정지 상태일 때는 사격만
                if (!isCruiser) this.shootCannons();
                else this.shootCruiserMissile();
            } else {
                // 이동 중일 때는 조준을 위해 배를 회전시킴
                if (this.rotateAndCheckAngle(isCruiser)) {
                    if (!isCruiser) this.shootCannons();
                    else this.shootCruiserMissile();
                }
            }
        }

        return true;
    }
	private void shootCruiserMissile() {
        if (captain.getState() == 3) return; // PASSIVE 상태면 안 쏨
        
        if (target == null || !target.isAlive() || !(target instanceof net.minecraft.world.entity.LivingEntity livingTarget)) return;

        // [★방어 코드 추가] 미사일 발사 버튼을 누르기 직전, 다시 한번 이 타겟이 설정상 쏴도 되는 적군인지 확인합니다.
        if (!this.captain.canFireAtTarget(livingTarget)) {
            return;
        }
        
        // 전용 독립 쿨타임 체크
        if (this.cruiserMissileCooldown > 0) return;

        boolean hasAmmo = false;
        ItemStack ammoStack = ItemStack.EMPTY;

        // 탄약(modern_cannon_ball) 탐색 (캡틴 인벤토리)
        for (int i = 0; i < this.captain.getInventory().getContainerSize(); i++) {
            ItemStack stack = this.captain.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getDescriptionId().contains("modern_cannon_ball")) {
                ammoStack = stack;
                hasAmmo = true;
                break;
            }
        }
        
        // 탄약 탐색 (배 화물칸)
        if (!hasAmmo && ship.getBoat() instanceof net.minecraft.world.Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && stack.getDescriptionId().contains("modern_cannon_ball")) {
                    ammoStack = stack;
                    hasAmmo = true;
                    break;
                }
            }
        }

        if (!hasAmmo) {
            this.cruiserMissileCooldown = 40; // 탄약 없으면 2초간 재검사 방지
            return;
        }

        // 미사일 발사 실행
        var boat = captain.getVehicle();
        var level = captain.getCommandSenderWorld();
        net.minecraft.world.phys.Vec3 spawnPos = boat.position().add(0, 3.0D, 0);

        com.github.x3r.mekanism_turrets.common.entity.MissileEntity missile = 
            new com.github.x3r.mekanism_turrets.common.entity.MissileEntity(
                level, spawnPos, 100.0F, 12.0F, captain.getUUID(), livingTarget
        );

        missile.setDeltaMovement(new net.minecraft.world.phys.Vec3(0, 1.5D, 0));
        level.addFreshEntity(missile);
        
        // 탄약 소모
        ammoStack.shrink(1);
        
        level.playSound(null, boat.blockPosition(), net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH, net.minecraft.sounds.SoundSource.NEUTRAL, 5.0F, 0.8F);

        // 연사 속도 설정 (20 = 1초)
        this.cruiserMissileCooldown = 100; 
    }

	public boolean checkForNextTarget() {
		// 1. 기본 유효성 검사
		if (captain.enemyArmy == null || captain.enemyArmy.ships == null || captain.enemyArmy.getAllUnits() == null) {
			return false;
		}

		boolean hasShips = !captain.enemyArmy.ships.isEmpty();
		boolean hasUnits = !captain.enemyArmy.getAllUnits().isEmpty();

		// 2. 보고 로직 (필터링 적용: InfoMode에 따라 보고할지 말지 결정)
		if ((hasShips || hasUnits) && !captain.hasReportedEncounter && captain.getInfoMode() != AbstractLeaderEntity.InfoMode.NONE.getIndex()) {
			
			boolean shouldReport = false;
			byte infoMode = captain.getInfoMode(); // 0:ALL, 1:NONE, 2:ENEMY, 3:HOSTILE

			if (infoMode == 0) {
				// ALL 모드면 무조건 보고 (적 플레이어든 몬스터든 상관없음)
				shouldReport = true;
			} 
			else {
				// 타겟 샘플을 가져와서 타입 확인
				if (hasUnits) {
					LivingEntity sample = captain.enemyArmy.getAllUnits().get(0);
					boolean isMonster = sample.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER || sample instanceof net.minecraft.world.entity.monster.Pillager;
					boolean isPlayerOrRecruit = sample instanceof net.minecraft.world.entity.player.Player || sample instanceof com.talhanation.recruits.entities.AbstractRecruitEntity;

					if (infoMode == 2 && isPlayerOrRecruit) shouldReport = true; // ENEMY 모드
					if (infoMode == 3 && isMonster) shouldReport = true;         // HOSTILE 모드
				} 
				else if (hasShips) {
					// 배만 있는 경우: ENEMY 모드에서만 보고 (일반적으로 적대적 배는 Player/Recruit 소속)
					if (infoMode == 2) shouldReport = true;
				}
			}

			// 3. 조건이 맞을 때만 메시지 전송
			if (shouldReport && captain.getOwner() != null) {
				if (hasShips) {
					this.captain.getOwner().sendSystemMessage(Component.literal(captain.getName().getString() + ": Enemy Ship in contact, im counting " + captain.enemyArmy.ships.size() + "!"));
				} else {
					this.captain.getOwner().sendSystemMessage(Component.literal(captain.getName().getString() + ": Enemies in contact, im counting " + captain.enemyArmy.getAllUnits().size() + "!"));
				}
			}
			
			// 보고를 했든, 필터링되어 무시했든 "이번 적 발견에 대한 보고 처리"는 끝난 것으로 간주
			captain.hasReportedEncounter = true;
		}

		// 4. 타겟 설정 (기존 로직)
		if (hasShips) {
			this.target = captain.enemyArmy.ships.stream()
					.min(Comparator.comparing(this::enemyDistanceToLeader))
					.orElse(null);
		} else if (hasUnits) {
			this.target = captain.enemyArmy.getAllUnits().stream()
					.filter(captain::canFireAtTarget)
					.min(Comparator.comparing(this::enemyDistanceToLeader))
					.orElse(null);
		} else {
			this.captain.enemyArmy = null;
			this.target = null;
			return false;
		}

		return this.target != null;
	}
	
    public boolean shouldGetInRange() {
        return ship.canShootCannons() && ship.getDamage() < 70 && captain.getFollowState() == 0 && !captain.getShouldMovePos();
    }

    private boolean isTooFarFromMovementRange(){
        Vec3 boatPos = ship.getBoat().position();
        int movementState = captain.getFollowState();

        switch (movementState) {
            case 0 -> { // Wander
                BlockPos movePos = captain.getMovePos();
                return captain.getShouldMovePos() && movePos != null && movePos.getCenter().distanceToSqr(boatPos) > (followRange * 0.5F);
            }
            case 1 -> { // Follow
                LivingEntity owner = captain.getOwner();
                return owner != null && owner.distanceToSqr(boatPos) > followRange;
            }
            case 2 -> { // Hold Position
                Vec3 holdPos = captain.getHoldPos();
                return holdPos != null && holdPos.distanceToSqr(boatPos) > (followRange * 0.5F);
            }
            case 5 -> { // Protect
                LivingEntity protect = captain.getProtectingMob();
                return protect != null && captain.getShouldProtect() && protect.distanceToSqr(boatPos) > followRange;
            }
        }
        return false;
    }

// [수정] 파라미터에 boolean isCruiser 추가
    private boolean rotateAndCheckAngle(boolean isCruiser) {
        if (target == null || !target.isAlive() || target.isUnderWater() || captain.smallShipsController.ship.getBoat().getPassengers().contains(target)) {
            target = null;
            this.captain.commandCooldown = 0;
            return false;
        }

        Vec3 forward = captain.smallShipsController.ship.getBoat().getForward().normalize();
        Vec3 toTarget = target.position().subtract(captain.smallShipsController.ship.getBoat().position()).normalize();

        double angleDiff = Math.toDegrees(Math.atan2(toTarget.z, toTarget.x) - Math.atan2(forward.z, forward.x));
        angleDiff = net.minecraft.util.Mth.wrapDegrees(angleDiff);

        boolean inputLeft = angleDiff < -2.0;
        boolean inputRight = angleDiff > 2.0;

        double distSqr = captain.distanceToSqr(target);
        
        if (distSqr < 22500) { 
             captain.smallShipsController.ship.setSailState(0); 
        } else {
             captain.smallShipsController.ship.setSailState(1); 
        }
        
        captain.smallShipsController.ship.rotateShip(inputLeft, inputRight);

        // [핵심 수정] 타겟 정렬 대기 시간 제거
        if (isCruiser) {
            // 순양함의 유도 미사일(VLS)은 뱃머리를 정확히 맞출 필요가 없습니다.
            // 전방 180도(좌우 90도) 이내에 적이 들어오면 회전 중이더라도 즉시 발사합니다!
            return Math.abs(angleDiff) <= 90.0;
        }

        // 일반 포탑의 경우 15도 이내로 조준 시 발사 (너무 빡빡했던 10도에서 완화)
        return Math.abs(angleDiff) <= 90.0; 
    }
	
	private void shootCannons() {
		if (ship.canShootCannons() && captain.getShouldRanged() && captain.getState() != 3) {
			if (target == null || !target.isAlive()) {
				target = null;
				return;
			}

			Vec3 toTarget = target.position().subtract(this.captain.position()).normalize();
			Vec3 forward = this.captain.getVehicle().getForward();
			Vec3 vecRight = forward.yRot((float) (-Math.PI / 2));

			boolean shootLeftSide = toTarget.dot(vecRight) < 0;

			this.shootCannonsToPos(target.position(), shootLeftSide);
		}
	}

    public double enemyDistanceToLeader(Entity entity){
        return entity.distanceToSqr(this.captain.position());
    }

    public boolean rotateAndCheckAngleToPos(Vec3 targetPos) {
        if (ship == null || captain.getVehicle() == null) return false;

        Vec3 forward = ship.getBoat().getForward().normalize();
        double offsetDistance = ship.isGalley() ? 2.0 : 0;
        Vec3 cannonPosition = ship.getBoat().position().add(forward.scale(offsetDistance));
        Vec3 toTarget = cannonPosition.vectorTo(targetPos);

        Vec3 vecRight = forward.yRot(-3.14F / 2).normalize();
        Vec3 vecLeft  = forward.yRot( 3.14F / 2).normalize();

        double distanceToLeft  = toTarget.distanceTo(vecLeft);
        double distanceToRight = toTarget.distanceTo(vecRight);
        boolean shootLeftSide = distanceToLeft < distanceToRight;
        this.lastShouldShootLeft = shootLeftSide;

        double alpha = Kalkuel.horizontalAngleBetweenVectors(forward, toTarget);
        double phi = shootLeftSide ? -alpha : alpha;
        double ref = shootLeftSide ? -90 : 90;

        boolean inputLeft  = (phi < ref);
        boolean inputRight = (phi > ref);
        ship.rotateShip(inputLeft, inputRight);

        double beta = shootLeftSide
                ? Kalkuel.horizontalAngleBetweenVectors(forward.yRot(3.14F / 2), toTarget)
                : Kalkuel.horizontalAngleBetweenVectors(forward.yRot(-3.14F / 2), toTarget);

        return beta < 25;
    }

	// [유지] 중력 상수는 0.03 유지 (조준 정확도 위해 필수)
	private static final double PROJECTILE_GRAVITY = 0.023; 

	public void shootCannonsToPos(Vec3 aimPos, boolean shootLeftSide){
		if (!ship.canShootCannons() || !captain.getShouldRanged() || captain.getState() == 3) return;

		var boat = (Boat) captain.getVehicle();
		var level = captain.getCommandSenderWorld();

		// [수정] 여기서 원하는 속도를 4.5로 딱 정합니다.
		double finalSpeed = 7.0D; 

		// [삭제] NBT 값 가져오기, 더하기, 다시 저장하기 등등 전부 삭제!
		// boat.getEntityData().set(...) <--- 필요 없음

		// 거리 계산 (탄도 예측용)
		Vec3 from = boat.position();
		double diffX = aimPos.x - from.x;
		double diffZ = aimPos.z - from.z;
		double distH = Math.sqrt(diffX * diffX + diffZ * diffZ);

		double flightTime = distH / finalSpeed; 
		double dropHeight = 0.5 * PROJECTILE_GRAVITY * flightTime * flightTime;

		double correctedY = aimPos.y + 0.5 + dropHeight;
		net.minecraft.world.entity.decoration.ArmorStand marker =
				new net.minecraft.world.entity.decoration.ArmorStand(level, aimPos.x, correctedY, aimPos.z);
		marker.setInvisible(true);
		marker.setNoGravity(true);
		marker.setInvulnerable(true);

		level.addFreshEntity(marker);
		try {
			// [수정] 마지막 인자에 우리가 정한 'finalSpeed'(4.5)를 같이 보냅니다.
			SmallShips.shootCannonsSmallShip(this.captain, boat, marker, shootLeftSide, finalSpeed);
		} finally {
			marker.discard();
			// [삭제] NBT 복구 코드도 필요 없음
		}
	}
}
