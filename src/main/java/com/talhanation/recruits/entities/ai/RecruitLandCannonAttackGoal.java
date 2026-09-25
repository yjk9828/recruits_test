package com.talhanation.recruits.entities.ai;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.smallships.world.entity.ship.LandCannonEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class RecruitLandCannonAttackGoal extends Goal {
    private final AbstractRecruitEntity recruit;
    private LandCannonEntity cannon;
    private int seeTime;
    private int stableTime = 0;
	// 최소 사거리 제곱 (예: 5블록 이내 사격 금지 -> 5 * 5 = 25)
	private static final double MIN_ATTACK_RANGE_SQR = 5.0 * 5.0;

    // [설정] 속도 4.5 / 중력 0.03 (AbstractCannonBall의 실제 물리값과 일치시킴)
    private static final double CANNON_SPEED = 4.5D;
    private static final double PROJECTILE_GRAVITY = 0.028D; // 기존 0.025 -> 0.03 (정확도 향상)
    private static final double DRAG = 0.99D; // 공기 저항 계수

    // 사거리 200 (40000)
    private static final double MAX_ATTACK_RANGE_SQR = 200.0 * 200.0;

    public RecruitLandCannonAttackGoal(AbstractRecruitEntity recruit) {
        this.recruit = recruit;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(recruit.getVehicle() instanceof LandCannonEntity landCannon)) return false;
        this.cannon = landCannon;
        LivingEntity target = recruit.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        this.seeTime = 0;
        this.stableTime = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = recruit.getTarget();
        if (target == null) return;

        double distSqr = recruit.distanceToSqr(target);
        boolean canSee = recruit.getSensing().hasLineOfSight(target);
		// [추가] 목표가 너무 가까우면(최소 사거리 이내) 조준/발사를 중단하고 리턴
		if (distSqr < MIN_ATTACK_RANGE_SQR) {
            return; 
        }

        if (canSee) this.seeTime++;
        else this.seeTime = 0;

        // =============================================================
        // [탄도학 계산 수정] 공기 저항(0.99)을 고려한 정밀 계산
        // =============================================================
        Vec3 startPos = cannon.position().add(0, 1.5, 0); // 대포 발사구 위치 근사치
        // 목표: 적의 눈높이 50% 지점 (너무 머리를 노리면 빗나감)
        Vec3 targetBasePos = target.position().add(0, target.getEyeHeight() * 0.5, 0);

        double dx = targetBasePos.x - startPos.x;
        double dz = targetBasePos.z - startPos.z;
        double distH = Math.sqrt(dx * dx + dz * dz);

        // 1. 단순 비행 시간 계산 (진공 상태 가정)
        double rawFlightTime = distH / CANNON_SPEED;

        // 2. 공기 저항 보정 (속도가 매 틱 99%로 줄어듦)
        // 비행 중간 지점에서의 평균 속도를 추정하여 시간을 재계산합니다.
        // t/2 시점의 속도 = 초기속도 * 0.99^(t/2)
        double midFlightSpeed = CANNON_SPEED * Math.pow(DRAG, rawFlightTime * 0.5);
        
        // 보정된 비행 시간 (속도가 줄어드니 시간은 늘어남)
        double flightTime = distH / midFlightSpeed;

        // 3. 낙차 계산 (h = 0.5 * g * t^2)
        double dropHeight = 0.5 * PROJECTILE_GRAVITY * flightTime * flightTime;

        // 최종 조준 높이
        double aimY = targetBasePos.y + dropHeight;

        // =============================================================
        // [1] 차체 회전 (Yaw)
        // =============================================================
        double cdx = targetBasePos.x - cannon.getX();
        double cdz = targetBasePos.z - cannon.getZ();
        float targetYRot = (float)(Math.atan2(cdz, cdx) * (180D / Math.PI)) - 90.0F;

        float currentYRot = cannon.getYRot();
        float rotDiff = targetYRot - currentYRot;
        while (rotDiff >= 180.0F) rotDiff -= 360.0F;
        while (rotDiff < -180.0F) rotDiff += 360.0F;

        boolean isAligned = Math.abs(rotDiff) < 5.0F;

        if (isAligned) {
            cannon.setYRot(targetYRot);
            this.stableTime = Math.min(20, this.stableTime + 2);
        } else {
            float turnSpeed = 5.0F;
            if (rotDiff > turnSpeed) rotDiff = turnSpeed;
            if (rotDiff < -turnSpeed) rotDiff = -turnSpeed;
            
            cannon.setYRot(currentYRot + rotDiff);
            this.stableTime = Math.max(0, this.stableTime - 1);
        }

        // =============================================================
        // [2] 포신 각도 (Pitch) - 인위적 보정 제거
        // =============================================================
        double cannonY = cannon.getY() + 0.5; // 포신 회전축 높이
        double heightDiff = aimY - cannonY;
        
        // 각도 계산
        float basePitch = (float) -(Math.atan2(heightDiff, distH) * (180D / Math.PI));
        
        // [수정] -2.0F 보정 제거. 물리 계산이 정확하면 보정 없이 맞아야 함.
        float finalPitch = basePitch;

        cannon.setXRot(finalPitch);

        // =============================================================
        // [3] AI 시선 처리
        // =============================================================
        Vec3 aimPoint = new Vec3(targetBasePos.x, aimY, targetBasePos.z);
        Vec3 lookDir = aimPoint.subtract(startPos).normalize();
        Vec3 dummyLookPos = startPos.add(lookDir.scale(10.0D));

        recruit.getLookControl().setLookAt(dummyLookPos.x, dummyLookPos.y, dummyLookPos.z, 30.0F, 30.0F);

        // =============================================================
        // [4] 발사 조건
        // =============================================================
        if (this.seeTime >= 10 && this.stableTime > 10 && distSqr < MAX_ATTACK_RANGE_SQR) {
             cannon.tryAiShoot(recruit);
        }
    }
}