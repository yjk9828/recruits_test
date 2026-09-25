package com.talhanation.recruits.entities.ai;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class RecruitUpkeepEntityGoal extends Goal {
    public AbstractRecruitEntity recruit;
    public Optional<Entity> entity;
    public Container container;
    public boolean message;
    public boolean messageNotInRange;
    public BlockPos pos;
    public int timeToRecalcPath;
    
    // 최적화 변수
    private long lastCanUseCheck;
    private int checkInterval = 60; // 초기값 3초
    
    public boolean canResetPaymentTimer = false;

    public RecruitUpkeepEntityGoal(AbstractRecruitEntity recruit) {
        this.recruit = recruit;
    }

    @Override
    public boolean canUse() {
        // 1. 배고프지 않으면 가장 먼저 리턴 (가벼운 연산 우선)
        if (!recruit.needsToGetFood()) {
            return false;
        }

        long i = this.recruit.getCommandSenderWorld().getGameTime();
        
        // 2. 랜덤 쿨타임 적용 (20틱 고정 -> 60~100틱 랜덤)
        if (i - this.lastCanUseCheck < this.checkInterval) {
            return false;
        }
        
        this.lastCanUseCheck = i;
        // 다음 체크는 3초~5초 사이 랜덤한 시점에 수행 (서버 부하 분산)
        this.checkInterval = 60 + this.recruit.getRandom().nextInt(40);

        return recruit.getUpkeepUUID() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    private boolean isFoodInEntity(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack foodItem = container.getItem(i);
            if (recruit.canEatItemStack(foodItem)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void start() {
        super.start();
        timeToRecalcPath = 0;
        message = true;
        messageNotInRange = true;

        this.entity = findEntity();
        if (entity.isPresent()) {
            this.pos = this.entity.get().getOnPos();

            if (entity.get() instanceof AbstractHorse horse) {
                this.container = horse.inventory;
            } else if (entity.get() instanceof InventoryCarrier carrier) {
                this.container = carrier.getInventory();
            } else if (entity.get() instanceof Container containerEntity) {
                this.container = containerEntity;
            }
        }
        else {
            if (recruit.getOwner() != null && messageNotInRange) {
                recruit.getOwner().sendSystemMessage(TEXT_NOT_IN_RANGE(recruit.getName().getString()));
                messageNotInRange = false;
            }
            recruit.clearUpkeepEntity();
            this.stop();
        }
    }

    @Override
    public void tick() {
        super.tick();
        
        if (entity.isPresent()) {
            if (--this.timeToRecalcPath <= 0) {
                this.timeToRecalcPath = this.adjustedTickDelay(10);
                this.recruit.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), 1.15D);
            }

            if (recruit.horizontalCollision || recruit.minorHorizontalCollision) {
                this.recruit.getJumpControl().jump();
            }
            
            // 거리 체크
            double distance = this.recruit.position().distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < 50 && container != null) {

                this.checkIfMounted(entity.get());

                this.recruit.getNavigation().stop();
                this.recruit.getLookControl().setLookAt(entity.get().getX(), entity.get().getY() + 1, entity.get().getZ(), 10.0F, (float) this.recruit.getMaxHeadXRot());

                if(recruit.paymentTimer == 0){
                    recruit.checkPayment(container);
                    canResetPaymentTimer = true;
                }

                this.recruit.upkeepReequip(container);

                if (isFoodInEntity(container)) {
                    for (int i = 0; i < 3; i++) {
                        ItemStack foodItem = this.getFoodFromInv(container);
                        ItemStack food;
                        if (foodItem != null && canAddFood()) {
                            food = foodItem.copy();
                            food.setCount(1);
                            recruit.getInventory().addItem(food);
                            foodItem.shrink(1);
                        } else {
                            if (recruit.getOwner() != null && message) {
                                recruit.getOwner().sendSystemMessage(TEXT_NO_PLACE(recruit.getName().getString()));
                                message = false;
                            }
                        }
                    }
                    this.stop();
                    return;
                } else {
                    if (recruit.getOwner() != null && message) {
                        recruit.getOwner().sendSystemMessage(TEXT_FOOD(recruit.getName().getString()));
                        message = false;
                    }
                    this.stop();
                    return;
                }
            }
        } else {
            if (recruit.getOwner() != null && messageNotInRange) {
                recruit.getOwner().sendSystemMessage(TEXT_NOT_IN_RANGE(recruit.getName().getString()));
                messageNotInRange = false;

                recruit.clearUpkeepEntity();
                this.stop();
            }
        }
    }

    private void checkIfMounted(Entity entity) {
        Entity vehicle = this.recruit.getVehicle();
        if (vehicle != null) {
            if (vehicle.getUUID().equals(entity.getUUID())) {
                this.recruit.stopRiding();
            } else if (vehicle.getVehicle() != null && vehicle.getVehicle().getUUID().equals(entity.getUUID())) {
                vehicle.stopRiding();
            }
        }
    }

    @Override
    public void stop() {
        super.stop();
        recruit.setUpkeepTimer(recruit.getUpkeepCooldown());
        recruit.forcedUpkeep = false;
        if(recruit.paymentTimer == 0 && canResetPaymentTimer){
            canResetPaymentTimer = false;
            recruit.resetPaymentTimer();
        }
    }

    private Optional<Entity> findEntity() {
        if (this.recruit.getUpkeepUUID() == null) return Optional.empty();

        // 최적화: 검색 범위를 100 -> 40으로 축소 (서버 부하 감소)
        List<Entity> entities = recruit.getCommandSenderWorld().getEntitiesOfClass(
                Entity.class,
                recruit.getBoundingBox().inflate(40.0D), 
                (entity) -> entity.getUUID().equals(recruit.getUpkeepUUID())
        );

        return entities.isEmpty() ? Optional.empty() : Optional.of(entities.get(0));
    }


    @Nullable
    private ItemStack getFoodFromInv(Container inv) {
        ItemStack itemStack = null;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (recruit.canEatItemStack(inv.getItem(i))) {
                itemStack = inv.getItem(i);
                break;
            }
        }
        return itemStack;
    }


    private boolean canAddFood() {
        for (int i = 6; i < 14; i++) {
            if (recruit.getInventory().getItem(i).isEmpty())
                return true;
        }
        return false;
    }

    private MutableComponent TEXT_NO_PLACE(String name) {
        return Component.translatable("chat.recruits.text.noPlaceInInv", name);
    }

    private MutableComponent TEXT_NOT_IN_RANGE(String name) {
        return Component.translatable("chat.recruits.text.cantFindEntity", name);
    }

    private MutableComponent TEXT_FOOD(String name) {
        return Component.translatable("chat.recruits.text.noFoodInUpkeep", name);
    }
}
