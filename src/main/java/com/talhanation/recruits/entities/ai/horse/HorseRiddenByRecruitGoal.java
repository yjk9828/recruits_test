package com.talhanation.recruits.entities.ai.horse;

import com.talhanation.recruits.entities.AbstractLeaderEntity;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class HorseRiddenByRecruitGoal extends Goal {
    // 고유 UUID
    private static final UUID RECRUIT_HORSE_SPEED_UUID = UUID.fromString("7107DE5E-7CE8-4030-940E-514C1F160890");

    public final AbstractHorse horse;
    public boolean leaderFastSpeed;

    // 치료 쿨타임
    private int healCooldown = 0;

    public HorseRiddenByRecruitGoal(AbstractHorse horse){
        this.horse = horse;
    }

    @Override
    public boolean canUse() {
        return horse.getControllingPassenger() instanceof AbstractRecruitEntity;
    }

    @Override
    public void start() {
        super.start();
        this.updateSpeedModifier();
        this.healCooldown = 0;
    }

    @Override
    public void tick() {
        // 리더 상태 확인 및 속도 갱신
        if(this.horse.getControllingPassenger() instanceof AbstractLeaderEntity leader) {
            if (this.leaderFastSpeed != leader.getFastPatrolling()) {
                this.leaderFastSpeed = leader.getFastPatrolling();
                this.updateSpeedModifier();
            }
        }

        // 물에서 뜨기
        if(this.horse.isInWater()){
            this.horse.setDeltaMovement(this.horse.getDeltaMovement().add(0, 0.05, 0));
        }

        // 건초 치료 로직
        this.tickHorseHealing();
    }

    private void tickHorseHealing() {
        if (this.healCooldown > 0) {
            this.healCooldown--;
            return;
        }

        if (this.horse.getHealth() < this.horse.getMaxHealth() && this.horse.isAlive()) {
            if (this.horse.getControllingPassenger() instanceof AbstractRecruitEntity recruit) {
                this.attemptToHealWithHay(recruit);
            }
        }
    }

    private void attemptToHealWithHay(AbstractRecruitEntity recruit) {
        for (int i = 0; i < recruit.inventory.getContainerSize(); i++) {
            ItemStack stack = recruit.inventory.getItem(i);
            
            if (stack.is(Items.HAY_BLOCK)) {
                stack.shrink(1);
                this.horse.heal(20.0F);

                // [수정됨] level -> getCommandSenderWorld() 사용 (빌드 오류 해결)
                this.horse.getCommandSenderWorld().playSound(null, this.horse.getX(), this.horse.getY(), this.horse.getZ(), 
                        SoundEvents.HORSE_EAT, this.horse.getSoundSource(), 1.0F, 1.0F + (this.horse.getRandom().nextFloat() - this.horse.getRandom().nextFloat()) * 0.2F);

                this.horse.getCommandSenderWorld().broadcastEntityEvent(this.horse, (byte) 7);
                
                recruit.swing(InteractionHand.MAIN_HAND);
                this.healCooldown = 100;
                return;
            }
        }
    }

    @Override
    public void stop() {
        super.stop();
        this.removeSpeedModifier();
    }

    private void updateSpeedModifier() {
        AttributeInstance attribute = this.horse.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;

        this.removeSpeedModifier();

        // [수정됨] 속도 값 대폭 하향 및 방식 변경
        // 0.4D = 기본 속도의 40% 증가 (기존 값인 0.255 더하기는 약 150% 증가 효과였음)
        double speedBoost = 1.05D; 

        // 리더가 빠른 순찰 모드가 아니면 보너스 없음 (0%)
        if (this.horse.getControllingPassenger() instanceof AbstractLeaderEntity leader && !leader.getFastPatrolling()) {
             speedBoost = 0.0D; 
        }

        // [수정됨] ADDITION -> MULTIPLY_BASE (퍼센트 증가 방식)
        // 이렇게 하면 원래 느린 말은 적당히 빨라지고, 빠른 말은 더 빨라져서 제어가 쉽습니다.
        AttributeModifier modifier = new AttributeModifier(
                RECRUIT_HORSE_SPEED_UUID,
                "Recruit horse speed boost",
                speedBoost,
                AttributeModifier.Operation.MULTIPLY_BASE 
        );

        attribute.addTransientModifier(modifier);
    }

    private void removeSpeedModifier() {
        AttributeInstance attribute = this.horse.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) {
            attribute.removeModifier(RECRUIT_HORSE_SPEED_UUID);
        }
    }
}