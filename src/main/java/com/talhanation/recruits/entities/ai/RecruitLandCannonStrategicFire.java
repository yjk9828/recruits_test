package com.talhanation.recruits.entities.ai;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.entities.IStrategicFire;
import com.talhanation.smallships.world.entity.ship.LandCannonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class RecruitLandCannonStrategicFire extends Goal {
    private final AbstractRecruitEntity recruit;
    private LandCannonEntity cannon;
	private int stableTime = 0;
    
    // [설정] 물리 상수 통일
    private static final double CANNON_SPEED = 4.5D; 
    private static final double PROJECTILE_GRAVITY = 0.028D; 
    private static final double DRAG = 0.99D;

    public RecruitLandCannonStrategicFire(AbstractRecruitEntity recruit) {
        this.recruit = recruit;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public void start() {
        this.stableTime = 0;
    }

    @Override
    public boolean canUse() {
        if (!(recruit.getVehicle() instanceof LandCannonEntity landCannon)) return false;
        this.cannon = landCannon;
        if (!(recruit instanceof IStrategicFire strategicRecruit)) return false;
        if (!strategicRecruit.getShouldStrategicFire()) return false;
        return strategicRecruit.getStrategicFirePos() != null;
    }

	@Override
    public void tick() {
        if (!(recruit instanceof IStrategicFire strategicRecruit)) return;
        BlockPos targetPos = strategicRecruit.getStrategicFirePos();
        if (targetPos == null) return;

        Vec3 startPos = cannon.position().add(0, 1.5, 0);
        Vec3 endPos = new Vec3(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);

        double diffX = endPos.x - startPos.x;
        // 높이 차이 계산 시 착탄 지점을 아주 약간 위로 잡음 (바닥에 꽂히는 것 방지)
        double diffY = endPos.y - startPos.y; 
        double diffZ = endPos.z - startPos.z;

        double distH = Math.sqrt(diffX * diffX + diffZ * diffZ); 

        // =============================================================
        // [탄도학 수정] 물리 기반 공기 저항 계산
        // =============================================================
        double rawFlightTime = distH / CANNON_SPEED;
        
        // 공기 저항(0.99) 적용: 비행 중간 시점의 속도를 기준으로 시간 재계산
        double midFlightSpeed = CANNON_SPEED * Math.pow(DRAG, rawFlightTime * 0.5);
        double flightTime = distH / midFlightSpeed;

		double dropHeight = 0.5 * PROJECTILE_GRAVITY * flightTime * flightTime;
		double aimY = endPos.y + dropHeight;
        
        // --- 회전 로직 (Yaw) ---
        double dx = endPos.x - cannon.getX();
        double dz = endPos.z - cannon.getZ();
        float targetYRot = (float)(Math.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;

        float currentYRot = cannon.getYRot();
        float rotDiff = targetYRot - currentYRot;
        
        while (rotDiff >= 180.0F) rotDiff -= 360.0F;
        while (rotDiff < -180.0F) rotDiff += 360.0F;

		boolean isAligned = Math.abs(rotDiff) < 4.0F;

        if (isAligned) {
            cannon.setYRot(targetYRot);
            cannon.yRotO = targetYRot; 
            this.stableTime = Math.min(100, this.stableTime + 2); 
        } else {
            float turnSpeed = 3.0F;
            if (rotDiff > turnSpeed) rotDiff = turnSpeed;
            if (rotDiff < -turnSpeed) rotDiff = -turnSpeed;
            
            cannon.setYRot(currentYRot + rotDiff);
            
            if (Math.abs(rotDiff) > 10.0F) {
                this.stableTime = Math.max(0, this.stableTime - 2); 
            }
        }

        // --- 상하 포각(Pitch) 계산 ---
        double cannonY = cannon.getY() + 0.5;
        double heightDiff = aimY - cannonY; 

        float basePitch = (float) -(Math.atan2(heightDiff, distH) * (180D / Math.PI));
        
        // [수정] -2.0F 보정 제거
        float finalPitch = basePitch;
        
        cannon.setXRot(finalPitch);

        // [시선 처리]
        Vec3 eyePos = recruit.getEyePosition();
        Vec3 targetPoint = new Vec3(endPos.x, aimY, endPos.z);
        Vec3 lookDir = targetPoint.subtract(eyePos).normalize();
        Vec3 dummyLookPos = eyePos.add(lookDir.scale(10.0D));

        recruit.getLookControl().setLookAt(dummyLookPos.x, dummyLookPos.y, dummyLookPos.z, 30.0F, 30.0F);

        // --- 발사 ---
        if (this.stableTime > 15) { 
            cannon.tryAiShoot(recruit);
        }
    }
}