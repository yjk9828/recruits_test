package com.talhanation.recruits.entities.ai;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.nbt.CompoundTag;
import java.util.EnumSet;
import java.util.Optional;

public class RecruitEatGoal extends Goal {

    private final AbstractRecruitEntity recruit;
    private int cooldownTimer;
    private int checkTimer; // tickCount 의존성 제거를 위한 내부 타이머

    public RecruitEatGoal(AbstractRecruitEntity recruit) {
        this.recruit = recruit;
        this.cooldownTimer = 0;
        this.checkTimer = 0;
        // 병렬 실행 허용 (다른 행동 중에도 먹을 수 있음)
        this.setFlags(EnumSet.noneOf(Goal.Flag.class)); 
    }

    @Override
    public boolean canUse() {
        if (!this.recruit.isAlive()) return false;

        // 쿨타임이 있으면 감소시키고 리턴
        if (this.cooldownTimer > 0) {
            this.cooldownTimer--;
            return false;
        }

        // 내부 타이머로 10틱(0.5초)마다 체크 (반응 속도 향상)
        // tickCount를 쓰면 서버 렉이나 연산 순서에 따라 씹힐 수 있음
        if (++this.checkTimer >= 10) {
            this.checkTimer = 0;
            
            // 배고픔 조건 만족 시 인벤토리 검사
            if (this.recruit.needsToEat()) {
                return hasValidFood();
            }
        }
        
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return false; // start()에서 즉시 처리하므로 지속 실행 안 함
    }

    @Override
    public void start() {
        boolean success = attemptToEatOrDrink();
        
        if (success) {
            // 성공 시: 먹는 모션/소리 후 적당한 쿨타임 (약 2~3초)
            this.cooldownTimer = 40 + this.recruit.getRandom().nextInt(20);
        } else {
            // 실패 시: (아이템이 있었는데 못 먹음 등) 짧은 쿨타임 후 다시 체크 (0.5초)
            // 이렇게 해야 "먹으려다 실패해서 멍때리는" 현상을 방지함
            this.cooldownTimer = 10; 
        }
    }

    private boolean attemptToEatOrDrink() {
        for (int i = 0; i < this.recruit.getInventory().getContainerSize(); i++) {
            ItemStack stack = this.recruit.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            // Entity 로직 활용 (canEatItemStack)
            if (!this.recruit.canEatItemStack(stack)) continue;

            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id == null) continue;

            // Mekanism 수통 처리
            if (id.toString().equals("mekanism:canteen")) {
                if (tryDrinkCanteen(stack)) return true; 
            }
            // 일반 음식 처리
            else {
                if (tryEatFood(stack)) return true;
            }
        }
        return false;
    }

    private boolean tryDrinkCanteen(ItemStack stack) {
        // 1. Fluid Capability 시도
        Optional<IFluidHandlerItem> handlerOpt = FluidUtil.getFluidHandler(stack).resolve();
        if (handlerOpt.isPresent()) {
            IFluidHandlerItem handler = handlerOpt.get();
            FluidStack fluidInTank = handler.getFluidInTank(0);

            if (!fluidInTank.isEmpty() && fluidInTank.getAmount() >= 10) {
                handler.drain(10, IFluidHandler.FluidAction.EXECUTE);
                applyEffects(true, 40.0F, 2.0F);
                return true;
            }
        }
        
        // 2. NBT 직접 조작 (Carry On 등 버그 대응)
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("FluidTanks")) {
            net.minecraft.nbt.ListTag tanks = tag.getList("FluidTanks", 10);
            for(int i = 0; i < tanks.size(); i++) {
                CompoundTag tank = tanks.getCompound(i);
                if (tank.contains("Stored")) {
                    CompoundTag stored = tank.getCompound("Stored");
                    if (stored.contains("Amount")) {
                        int currentAmount = stored.getInt("Amount");
                        if (currentAmount >= 10) {
                            stored.putInt("Amount", currentAmount - 10);
                            applyEffects(true, 40.0F, 2.0F);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean tryEatFood(ItemStack stack) {
        FoodProperties food = stack.getItem().getFoodProperties(stack, this.recruit);
        if (food != null) {
            stack.shrink(1);
            // 영양 * 5 + 포만감 (기존 공식 유지)
            float healAmount = (food.getNutrition() * 5.0F) + food.getSaturationModifier();
            applyEffects(false, healAmount, 1.0F);
            return true;
        }
        return false;
    }

    private void applyEffects(boolean isDrink, float hungerAmount, float moraleAmount) {
        this.recruit.playSound(
            isDrink ? SoundEvents.GENERIC_DRINK : SoundEvents.GENERIC_EAT, 
            0.5F, 
            this.recruit.getRandom().nextFloat() * 0.1F + 0.9F
        );

        float currentHunger = this.recruit.getHunger();
        this.recruit.setHunger(Math.min(100F, currentHunger + hungerAmount));

        if (this.recruit.getHealth() < this.recruit.getMaxHealth()) {
            this.recruit.heal(1.0F);
        }

        if (this.recruit.getMorale() < 100) {
            this.recruit.setMoral(this.recruit.getMorale() + moraleAmount);
        }
        
        // 가끔 트림 소리
        if (this.recruit.getRandom().nextInt(5) == 0) {
             this.recruit.playSound(SoundEvents.PLAYER_BURP, 0.5F, this.recruit.getRandom().nextFloat() * 0.1F + 0.9F);
        }
    }
    
    private boolean hasValidFood() {
        // 인벤토리 전체 스캔
        return this.recruit.getInventory().items.stream().anyMatch(stack -> 
            !stack.isEmpty() && this.recruit.canEatItemStack(stack)
        );
    }
}