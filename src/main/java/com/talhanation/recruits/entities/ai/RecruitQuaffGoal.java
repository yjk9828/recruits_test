package com.talhanation.recruits.entities.ai;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class RecruitQuaffGoal extends Goal {
    public final AbstractRecruitEntity recruit;
    private int cooldownTicks = 0;

    private int medkitSlot = -1;
    private int potionSlot = -1;

    public RecruitQuaffGoal(AbstractRecruitEntity recruit) {
        this.recruit = recruit;
    }

    @Override
    public boolean canUse() {
        if (this.cooldownTicks > 0 || !this.recruit.needsToPotion()) {
            return false;
        }
        this.findUsableItemSlot();
        return this.medkitSlot != -1 || this.potionSlot != -1;
    }

    @Override
    public void start() {
        // 메드킷 사용 로직
        if (this.medkitSlot != -1) {
            ItemStack medkitStack = this.recruit.inventory.getItem(this.medkitSlot);
            medkitStack.shrink(1); // 1개 소모
            this.recruit.heal(10.0F);
            this.cooldownTicks = 100;
        }
        // 포션 사용 로직
        else if (this.potionSlot != -1) {
            // 1. 인벤토리에서 포션 '스택'을 가져옴 (제거하지 않음)
            ItemStack potionStack = this.recruit.inventory.getItem(this.potionSlot);
            
            // 2. 포션 효과를 읽어옴
            List<MobEffectInstance> effects = PotionUtils.getMobEffects(potionStack);
            
            // 3. 효과를 엔티티에게 적용
            for (MobEffectInstance instance : effects) {
                if (!instance.getEffect().isBeneficial()) continue;
                this.recruit.addEffect(new MobEffectInstance(instance));
            }
            
            // 4. ★★★★★ 핵심 수정: 스택에서 아이템 갯수만 1개 '감소'시킴
            potionStack.shrink(1);
            
            // 5. 빈 병 추가
            //this.recruit.inventory.addItem(Items.GLASS_BOTTLE.getDefaultInstance());

            // 6. 쿨다운 설정
            this.cooldownTicks = 60;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.cooldownTicks > 0;
    }

    @Override
    public void stop() {
        this.medkitSlot = -1;
        this.potionSlot = -1;
    }

    @Override
    public void tick() {
        if (this.cooldownTicks > 0) {
            this.cooldownTicks--;
        }
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private void findUsableItemSlot() {
        this.medkitSlot = -1;
        this.potionSlot = -1;

        for (int i = 0; i < this.recruit.inventory.getContainerSize(); ++i) {
            ItemStack stack = this.recruit.inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().equals("test_a1:medkit")) {
                this.medkitSlot = i;
                return;
            }
            if (this.potionSlot == -1 && PotionUtils.getMobEffects(stack).stream().anyMatch(e -> e.getEffect().getCategory().equals(MobEffectCategory.BENEFICIAL))) {
                this.potionSlot = i;
            }
        }
    }
}