package com.talhanation.recruits.entities.ai;

import com.talhanation.recruits.entities.AbstractRecruitEntity;

import com.talhanation.recruits.entities.CaptainEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.vehicle.Boat;


public class RecruitFollowOwnerGoal extends Goal {
    private final AbstractRecruitEntity recruit;
    private LivingEntity owner;
    private final double speedModifier;
    private int timeToRecalcPath;
    private final float stopDistance;
    private final float startDistance;
    private long lastCanUseCheck;

    public RecruitFollowOwnerGoal(AbstractRecruitEntity recruit, double speedModifier, float startDistance, float stopDistance) {
        this.recruit = recruit;
        this.speedModifier = speedModifier;
        this.startDistance = 9;
        this.stopDistance = 7;
    }

    // [Helper] 동적 정지 거리 계산
    private double getAdjustedStopDistance() {
        double baseStop = this.stopDistance;
        // 차량에 타고 있다면?
        if (this.recruit.getVehicle() != null) {
            // 차량 너비 가져오기 (LandBrigg = 3.5)
            float vehicleWidth = this.recruit.getVehicle().getBbWidth();
            // (차량 너비 * 1.2)^2 만큼 여유를 둠. 
            // 예: 3.5 * 1.2 = 4.2 -> 제곱하면 약 17.6
            // 즉 4~5블록 떨어진 곳에서 멈춤
            double widthBuffer = (vehicleWidth * 4.2D);
            return baseStop + (widthBuffer * widthBuffer);
        }
        return baseStop;
    }

    public boolean canUse() {
        long i = this.recruit.getCommandSenderWorld().getGameTime();
        if (i - this.lastCanUseCheck >= 10L) {
            this.lastCanUseCheck = i;
            LivingEntity livingentity = this.recruit.getOwner();
            LivingEntity target = this.recruit.getTarget();
            double start = target == null ? startDistance : startDistance + 100;
            
            // [수정] 시작 거리도 차량 탑승 시 늘려줌
            if (this.recruit.getVehicle() != null) {
                start += (this.recruit.getVehicle().getBbWidth() * this.recruit.getVehicle().getBbWidth());
            }

            if (livingentity == null) {
                return false;
            }
            else if (livingentity.isSpectator()) {
                return false;
            }
            else if (this.recruit.distanceToSqr(livingentity) < start) {
                return false;
            }
            else {
                this.owner = livingentity;
                return this.recruit.getShouldFollow() && !recruit.getFleeing() && recruit.getFollowState() == 1 && !recruit.needsToGetFood() && !recruit.getShouldMount() && !recruit.getShouldMovePos();
            }
        }
        return false;

    }

    public boolean canContinueToUse() {
        if (this.recruit.getNavigation().isDone()) {
            return false;
        }
        // [수정] getAdjustedStopDistance() 사용
        return !(this.recruit.distanceToSqr(this.owner) <= this.getAdjustedStopDistance()) && this.recruit.getShouldFollow() && !recruit.getFleeing() && recruit.getFollowState() == 1 && !recruit.needsToGetFood() && !recruit.getShouldMount() && !recruit.getShouldMovePos();
    }

    public void start() {
        this.timeToRecalcPath = 0;
        this.recruit.setIsFollowing(true);
    }

    public void stop() {
        super.stop();
        this.owner = null;
        this.recruit.setIsFollowing(false);
        this.recruit.getNavigation().stop();
    }

    public void tick() {
        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = this.recruit.getVehicle() != null ? this.adjustedTickDelay(5) : this.adjustedTickDelay(10);

            this.recruit.getLookControl().setLookAt(this.owner, 10.0F, (float)this.recruit.getMaxHeadXRot());
            this.recruit.getNavigation().moveTo(this.owner, this.speedModifier);

            if(this.recruit instanceof CaptainEntity captain && captain.getVehicle() instanceof Boat){
                captain.setSailPos(this.owner.getOnPos());
            }
        }
    }
}