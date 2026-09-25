package com.talhanation.recruits.entities.ai;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.entities.HorsemanEntity;
import com.talhanation.recruits.pathfinding.AsyncPathfinderMob;
import com.talhanation.recruits.util.AttackUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.common.ToolActions;

import java.util.UUID;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance; // AttributeInstance도 필요합니다!

import com.talhanation.recruits.entities.CrossBowmanEntity;

public class UseShield extends Goal {
	public final AsyncPathfinderMob entity;
    
	// 모디파이어를 식별하기 위한 고유 UUID (기존 유지)
    private static final UUID SHIELD_SPEED_MODIFIER_UUID = UUID.fromString("8f69b8a-c6a1-4a1d-a021-870d47781b01");
    
    // [수정됨] 속도 역보정 모디파이어
    private static final AttributeModifier SHIELD_SPEED_MODIFIER = new AttributeModifier(
            SHIELD_SPEED_MODIFIER_UUID, 
            "Shield speed modifier", 
            - 0.15D, // [중요]
            AttributeModifier.Operation.MULTIPLY_TOTAL
    );

    public UseShield(AsyncPathfinderMob recruit){
        this.entity = recruit;
    }

	public boolean canUse() {
			boolean hasShield = this.entity.getOffhandItem().getItem().canPerformAction(entity.getOffhandItem(), ToolActions.SHIELD_BLOCK);
			if (!hasShield || this.entity.swinging) {
				return false; // 방패가 없거나 공격 중이면 중단
			}

			if (entity instanceof AbstractRecruitEntity recruit) {

				// [수정 2] 석궁병이 석궁을 "재장전" 중일 때는 방패를 들지 않도록 합니다. (AI 충돌 방지)
				if (recruit instanceof CrossBowmanEntity crossbowman) {
					if (crossbowman.getChargingCrossbow()) {
						return false;
					}
				}

				boolean forced = recruit.getShouldBlock(); // 강제 방어 명령
				
				// [수정 1] "!recruit.getShouldMovePos()" 조건을 제거합니다.
				// 이제 이동 명령(getShouldMovePos) 중에도 자율적으로 방패를 듭니다.
				boolean normal = canRaiseShield() && !recruit.isFollowing() && recruit.canBlock();

				return (forced || normal);
			}
			
			// AbstractRecruitEntity가 아닌 경우 (바닐라 몹 등)
			return canRaiseShield() && !this.entity.swinging;
	}

    public boolean canContinueToUse() {
        return canUse();
    }
	
	@Override
    public void start() {
        this.entity.startUsingItem(InteractionHand.OFF_HAND);
        
        // 모디파이어 추가
        AttributeInstance speedAttribute = this.entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttribute != null && !speedAttribute.hasModifier(SHIELD_SPEED_MODIFIER)) {
            speedAttribute.addPermanentModifier(SHIELD_SPEED_MODIFIER); // addPermanentModifier로 추가
        }
    }

    @Override
    public void stop(){
        // 모디파이어 제거
        AttributeInstance speedAttribute = this.entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttribute != null && speedAttribute.hasModifier(SHIELD_SPEED_MODIFIER)) {
            speedAttribute.removeModifier(SHIELD_SPEED_MODIFIER); // UUID로 제거
        }
        entity.stopUsingItem();
    }
/*
    public void start() {
        if (this.entity.getOffhandItem().getItem().canPerformAction(entity.getOffhandItem(), ToolActions.SHIELD_BLOCK)){
            this.entity.startUsingItem(InteractionHand.OFF_HAND);
            this.entity.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.12D);
        }
    }

    public  void stop(){
        this.entity.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3D);
        entity.stopUsingItem();
    }
*/
/*
    public void tick() {
        if (this.entity.getUsedItemHand() == InteractionHand.OFF_HAND) {
            this.entity.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.25D);
        } else {
            this.entity.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3D);
        }
    }
*/
	public boolean canRaiseShield() {
			boolean isSelfTargeted = false;
			LivingEntity target = this.entity.getTarget();

			if (target != null && target.isAlive()) {

				if (target instanceof Mob mobTarget) {
					//isSelfTargeted = mobTarget.getTarget() != null && mobTarget.getTarget().is(entity);
					isSelfTargeted = true;
				}
				else if (target instanceof Player player){
					// [수정 1]
					// 플레이어가 석궁병을 때렸는지(getLastHurtMob) 여부와 상관없이,
					// 석궁병이 플레이어를 타겟으로 잡았다면 isSelfTargeted를 true로 설정합니다.
					isSelfTargeted = true;
				}

				ItemStack itemStackInHand = target.getItemInHand(InteractionHand.MAIN_HAND);
				double ownReach = AttackUtil.getAttackReachSqr(entity);
				Item itemInHand = itemStackInHand.getItem();
				double distanceToTarget = this.entity.distanceToSqr(target); //120
				boolean isTargetInReachToBlock = this.entity instanceof HorsemanEntity horseman && horseman.getVehicle() instanceof AbstractHorse ?  70 > distanceToTarget :  120 > distanceToTarget ;

				boolean isDanger = itemInHand instanceof AxeItem || itemInHand instanceof PickaxeItem || itemInHand instanceof SwordItem;

				if(isSelfTargeted){
					//For Ranged
					// [수정 2] (사용자님이 요청하신 수정 사항)
					if(target instanceof RangedAttackMob || target instanceof Player || (itemInHand instanceof CrossbowItem && CrossbowItem.isCharged(itemStackInHand)) || (itemInHand instanceof BowItem && target.getTicksUsingItem() > 0)){
						return distanceToTarget > ownReach * 1.5;
					}
					//For Melee
					else return (isDanger || target instanceof Monster) && isTargetInReachToBlock;
				}
			}
			return false;
	}
}