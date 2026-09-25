package com.talhanation.recruits.entities.ai;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class RecruitHoldPosGoal extends Goal {
    private final AbstractRecruitEntity recruit;

    private int timeToRecalcPath;

    public RecruitHoldPosGoal(AbstractRecruitEntity recruit, double within) {
      this.recruit = recruit;
      this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public void start() {
        super.start();
        timeToRecalcPath = 0;
    }

    public boolean canUse() {
        if (this.recruit.getHoldPos() == null) {
            return false;
        }
        else
            return this.recruit.getShouldHoldPos() && !recruit.getFleeing() && !recruit.needsToGetFood() && !recruit.getShouldMount();
    }

    public boolean canContinueToUse() {
        return canUse();
    }

	public void tick() {
        Vec3 pos = this.recruit.getHoldPos();
        if (pos != null) {
            double distanceSq = recruit.distanceToSqr(pos);
            
            // 목표 지점 도달 판정 거리 (Stop Tolerance)
            double stopTolerance = 0.3;
            
            if (this.recruit.getVehicle() != null) {
                float width = this.recruit.getVehicle().getBbWidth();
                stopTolerance = (width * 0.5) * (width * 0.5);
            }

            if(distanceSq >= stopTolerance) {
                if (--this.timeToRecalcPath <= 0) {
                    
                    // [최적화 1] 쿨다운 증가 및 틱 분산 (Tick Spreading)
                    // 고정된 짧은 틱 대신 20~40틱 사이의 랜덤 딜레이를 주어 서버 부하가 한 번에 몰리는 것을 방지합니다.
                    int baseDelay = this.recruit.getVehicle() != null ? 10 : 20;
                    this.timeToRecalcPath = this.adjustedTickDelay(baseDelay + this.recruit.getRandom().nextInt(20));

                    // [최적화 2] 근거리(예: 2블록 이내, 거리 제곱 4.0)는 무거운 길찾기 대신 직선으로 강제 이동
                    if (distanceSq < 4.0) {
                        this.recruit.getMoveControl().setWantedPosition(pos.x(), pos.y(), pos.z(), this.recruit.moveSpeed);
                    } else {
                        // 멀리 밀려났을 때만 정상적으로 길찾기 수행
                        this.recruit.getNavigation().moveTo(pos.x(), pos.y(), pos.z(), this.recruit.moveSpeed);
                    }
                }

                if (recruit.horizontalCollision || recruit.minorHorizontalCollision) {
                    this.recruit.getJumpControl().jump();
                }
            } else {
                // 도착 후 회전 로직
                if(recruit.rotate){
                    this.recruit.setYRot(recruit.ownerRot);
                    this.recruit.yRotO = this.recruit.ownerRot;
                    this.recruit.yBodyRot = this.recruit.ownerRot;
                    this.recruit.yHeadRot = this.recruit.ownerRot;
                    recruit.rotate = false;
                }
                
                if (this.recruit.getVehicle() != null) {
                   this.recruit.getNavigation().stop();
                }
            }
        }
    }
}