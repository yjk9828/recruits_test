package com.talhanation.recruits.entities.ai;

import com.talhanation.recruits.entities.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


public class RecruitUpkeepPosGoal extends Goal {
    public AbstractRecruitEntity recruit;
    public BlockPos chestPos;
    public Container container;
    public boolean message;
    public boolean messageNotChest;
    public boolean messageNeedNewChest;
    public boolean messageNotInRange;
    public int timeToRecalcPath = 0;
    public int timer = 0;
    public boolean setTimer = false;
    public boolean canResetPaymentTimer = false;

    // 최적화 변수
    private long lastCanUseCheck;
    private int checkInterval = 60; // 초기값 3초
    private int scanCooldown = 0;   // 실패 시 재탐색 쿨타임

    public RecruitUpkeepPosGoal(AbstractRecruitEntity recruit) {
        this.recruit = recruit;
    }

    @Override
    public boolean canUse() {
        // 1. 배고프지 않으면 즉시 리턴
        if (!recruit.needsToGetFood()) {
            return false;
        }

        long i = this.recruit.getCommandSenderWorld().getGameTime();
        
        // 2. 랜덤 쿨타임 적용
        if (i - this.lastCanUseCheck < this.checkInterval) {
            return false;
        }
        
        this.lastCanUseCheck = i;
        this.checkInterval = 60 + this.recruit.getRandom().nextInt(40); // 3~5초 랜덤

        return recruit.getUpkeepPos() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        super.start();
        this.timeToRecalcPath = 0;
        message = true;
        messageNotChest = true;
        messageNeedNewChest = true;
        messageNotInRange = true;
        this.chestPos = recruit.getUpkeepPos();
        this.scanCooldown = 0; // 시작할 때는 쿨타임 초기화

        if(chestPos != null) {
            BlockEntity entity = recruit.getCommandSenderWorld().getBlockEntity(chestPos);
            BlockState blockState = recruit.getCommandSenderWorld().getBlockState(chestPos);
            if (blockState.getBlock() instanceof ChestBlock chestBlock) {
                this.container = ChestBlock.getContainer(chestBlock, blockState, recruit.getCommandSenderWorld(), chestPos, false);
            } else if (entity instanceof Container containerEntity) {
                this.container = containerEntity;
            } else {
                if (recruit.getOwner() != null && messageNotChest) {
                    recruit.getOwner().sendSystemMessage(TEXT_CANT_INTERACT(recruit.getName().getString()));
                    messageNotChest = false;
                }
                this.chestPos = null;
                recruit.clearUpkeepPos();
            }
            if(chestPos != null){
                double distance = this.recruit.position().distanceToSqr(Vec3.atCenterOf(chestPos));
                if(distance > 10000){
                    if(recruit.getOwner() != null && messageNotInRange){
                        recruit.getOwner().sendSystemMessage(TEXT_NOT_IN_RANGE(recruit.getName().getString()));
                        messageNotInRange = false;
                    }
                    recruit.clearUpkeepPos();
                    stop();
                }
            }
        }
    }

// 3. canAddFood() 메서드: 식량 4개 제한
    private boolean canAddFood() {
        int foodCount = 0;
        boolean hasEmptySlot = false;
        for (int i = 6; i < 15; i++) {
            ItemStack stack = recruit.getInventory().getItem(i);
            if (recruit.canEatItemStack(stack)) foodCount++;
            if (stack.isEmpty()) hasEmptySlot = true;
        }
        return foodCount < 4 && hasEmptySlot;
    }

	@Override
    public void tick() {
        super.tick();
        
        // 1. 목표 상자 위치가 유효한지 검증 (목표가 바뀌었거나 사라졌으면 중단)
        if(this.chestPos != recruit.getUpkeepPos()){
            this.chestPos = recruit.getUpkeepPos();
            this.stop();
            return;
        }

        if (container != null && chestPos != null){
            // 2. [수정됨] 이동 로직: 상자가 존재하면 계속 이동 (매번 재검색하지 않음)
            if (--this.timeToRecalcPath <= 0) {
                this.timeToRecalcPath = this.adjustedTickDelay(10);
                this.recruit.getNavigation().moveTo(chestPos.getX(), chestPos.getY(), chestPos.getZ(), 1.15D);
            }

            // 점프 로직
            if (recruit.horizontalCollision || recruit.minorHorizontalCollision) {
                this.recruit.getJumpControl().jump();
            }

            // 3. 거리 체크 (6.0D로 여유 있게)
            if (chestPos.closerThan(recruit.getOnPos(), 6.0D)) {

                this.recruit.getNavigation().stop();
                this.recruit.getLookControl().setLookAt(chestPos.getX(), chestPos.getY() + 1, chestPos.getZ(), 10.0F, (float) this.recruit.getMaxHeadXRot());

                // 아직 보급 프로세스를 시작하지 않았다면 진입
                if(!setTimer){
                    
                    // [핵심 수정] 상자를 열기 전에 "진짜로 필요한가?"를 최종 확인 (낭비 방지)
                    // 배도 부르고, 탄약도 충분하고, 강제 명령도 아니라면 -> 그냥 집에 가라.
                    if (!recruit.forcedUpkeep && !recruit.needsAmmoOrGrenades() && !recruit.needsToEat()) {
                        this.stop();
                        return;
                    }

                    if(recruit.paymentTimer == 0){
                        recruit.checkPayment(container);
                        canResetPaymentTimer = true;
                    }
                    
                    // 1. 장비/탄약 보급 실행
                    this.recruit.upkeepReequip(container);
                    
                    // 2. 타이머 시작 (상호작용 성공)
                    timer = 30;
                    setTimer = true; 

                    // 3. 식량 보급 로직 (수통 점수 반영)
                    if (isFoodInContainer(container)) {
                        
                        int myFoodScore = 0;
                        Container inv = recruit.getInventory();
                        
                        // 인벤토리 스캔하여 식량 점수 계산
                        for (int i = 0; i < inv.getContainerSize(); i++) {
                            ItemStack item = inv.getItem(i);
                            if (item.isEmpty()) continue;
                            net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem());
                            
                            if (id != null && id.toString().equals("mekanism:canteen")) myFoodScore += 4; 
                            else if (recruit.canEatItemStack(item)) myFoodScore += item.getCount();
                        }

                        // 점수가 4점 미만일 때만 상자 상호작용 및 식량 꺼내기
                        if (myFoodScore < 4) {
                            interactChest(container, true);
                            
                            for (int i = 0; i < 3; i++) {
                                if (myFoodScore >= 4) break;
                                ItemStack foodItem = this.getFoodFromInv(container);
                                if (foodItem != null && canAddFood()) { 
                                    ItemStack food = foodItem.copy();
                                    food.setCount(1);
                                    recruit.getInventory().addItem(food);
                                    foodItem.shrink(1);
                                    myFoodScore++; 
                                } else {
                                    break;
                                }
                            }
                        }
                    }
                    
                    // 탄약을 챙겼거나 식량을 챙겼다면 타이머 대기 후 종료
                }
            }
            // [중요] 거리가 멀 때(else 블록) 아무것도 하지 않음! 
            // 기존 코드에 있던 findInvPos() 재검색 로직을 삭제하여,
            // 병사가 상자를 향해 묵묵히 걸어가게 만듦.
        }
        else {
            // 상자 변수(container) 자체가 null일 때만 새 상자 찾기 시도
            if (scanCooldown > 0) {
                scanCooldown--;
                return;
            }
            this.chestPos = findInvPos();
            if(chestPos == null){
                this.scanCooldown = 200; 
                recruit.clearUpkeepPos();
                stop();
            }
            else recruit.setUpkeepPos(chestPos);
        }

        // 타이머 로직 (보급 후 1.5초 대기)
        if(setTimer){
            if(timer > 0) timer--;
            if(timer == 0) stop();
        }
    }
// 2. stop() 메서드: [중요] 조건 불문하고 강제 쿨타임 부여
    @Override
    public void stop() {
        super.stop();
        recruit.forcedUpkeep = false;
        timer = 0;
        setTimer = false;

        if(recruit.paymentTimer == 0 && canResetPaymentTimer){
            canResetPaymentTimer = false;
            recruit.resetPaymentTimer();
        }

        if(container != null) {
            interactChest(container, false);
            container.setChanged();
        }

        // [핵심 해결책]
        // 인벤토리에 수류탄이 있든 없든, 부족하다고 착각하든 말든
        // 상자를 한번 열었다 닫았으면 무조건 3000틱(2분 30초) 동안은 다시 오지 마라.
        recruit.setUpkeepTimer(recruit.getUpkeepCooldown());
    }

    @Nullable
    private BlockPos findInvPos() {
        List<BlockPos> list = new ArrayList<>();
        BlockPos chestPos;
        if(this.recruit.getUpkeepPos() != null) {
            // 최적화: 탐색 범위를 8 -> 5로 축소 (4096회 반복 -> 1000회 반복)
            int range = 5; 
            for (int x = -range; x < range; x++) {
                for (int y = -range; y < range; y++) {
                    for (int z = -range; z < range; z++) {
                        chestPos = recruit.getUpkeepPos().offset(x, y, z);
                        BlockEntity block = recruit.getCommandSenderWorld().getBlockEntity(chestPos);
                        if (block instanceof Container blockContainer){
                            if(isFoodInContainer(blockContainer)) return chestPos;
                            else list.add(chestPos);
                        }
                    }
                }
            }
        }

        if(list.isEmpty()) return null;
        else return list.get(recruit.getRandom().nextInt(list.size()));
    }

    private boolean isFoodInContainer(Container container){
        for(int i = 0; i < container.getContainerSize(); i++) {
            ItemStack foodItem = container.getItem(i);
            if(recruit.canEatItemStack(foodItem)){
                return true;
            }
        }
        return false;
    }
    @Nullable
    private ItemStack getFoodFromInv(Container inv){
        ItemStack itemStack = null;
        for(int i = 0; i < inv.getContainerSize(); i++){
            if(recruit.canEatItemStack(inv.getItem(i))){
                itemStack = inv.getItem(i);
                break;
            }
        }
        return itemStack;
    }

    public void interactChest(Container container, boolean open) {
        if(this.chestPos != null && (container instanceof CompoundContainer || container instanceof ChestBlockEntity)){
            BlockState state = this.recruit.getCommandSenderWorld().getBlockState(this.chestPos);
            Block block = state.getBlock();
            boolean isOpened = false;
            CompoundTag compoundTag = new CompoundTag();
            if(recruit.getCommandSenderWorld().getBlockEntity(chestPos) instanceof ChestBlockEntity chestBlockEntity){
                compoundTag = chestBlockEntity.getPersistentData();
                if(compoundTag.contains("isOpened"))
                    isOpened = compoundTag.getBoolean("isOpened");
                else
                    compoundTag.putBoolean("isOpened", false);
            }

            if (open) {
                if(!isOpened){
                    this.recruit.getCommandSenderWorld().blockEvent(this.chestPos, block, 1, 1);
                    this.recruit.getCommandSenderWorld().playSound(null, chestPos, SoundEvents.CHEST_OPEN, recruit.getSoundSource(), 0.7F, 0.8F + 0.4F * recruit.getRandom().nextFloat());
                    compoundTag.putBoolean("isOpened", true);
                }
            }
            else {
                if(isOpened){
                    this.recruit.getCommandSenderWorld().blockEvent(this.chestPos, block, 1, 0);
                    this.recruit.getCommandSenderWorld().playSound(null, chestPos, SoundEvents.CHEST_CLOSE, recruit.getSoundSource(), 0.7F, 0.8F + 0.4F * recruit.getRandom().nextFloat());
                    compoundTag.putBoolean("isOpened", false);
                }

            }
            this.recruit.getCommandSenderWorld().gameEvent(this.recruit, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, chestPos);
        }
    }

    private MutableComponent TEXT_NO_PLACE(String name) {
        return Component.translatable("chat.recruits.text.noPlaceInInv", name);
    }

    private MutableComponent TEXT_CANT_INTERACT(String name) {
        return Component.translatable("chat.recruits.text.cantInteract", name);
    }

    private MutableComponent NEED_NEW_UPKEEP(String name) {
        return Component.translatable("chat.recruits.text.findMeNewChest", name);
    }

    private MutableComponent TEXT_FOOD(String name) {
        return Component.translatable("chat.recruits.text.noFoodInUpkeep", name);
    }

    private MutableComponent TEXT_NOT_IN_RANGE(String name) {
        return Component.translatable("chat.recruits.text.upkeepNotInRange", name);
    }
}