package com.talhanation.recruits.entities;

import com.talhanation.recruits.entities.ai.UseShield; // 이 줄을 파일 상단에 추가해야 합니다.

import com.talhanation.recruits.compat.IWeapon;
import com.talhanation.recruits.config.RecruitsServerConfig;
import com.talhanation.recruits.entities.ai.RecruitMoveTowardsTargetGoal;
import com.talhanation.recruits.entities.ai.RecruitRangedCrossbowAttackGoal;
import com.talhanation.recruits.entities.ai.compat.RecruitRangedMusketAttackGoal;
import com.talhanation.recruits.world.RecruitsPatrolSpawn;
import com.talhanation.recruits.pathfinding.AsyncGroundPathNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static com.talhanation.recruits.Main.isMusketModLoaded;

// [추가] 대포 관련 AI import
import com.talhanation.recruits.entities.ai.RecruitLandCannonAttackGoal; 
import com.talhanation.recruits.entities.ai.RecruitLandCannonStrategicFire;


public class CrossBowmanEntity extends AbstractRecruitEntity implements CrossbowAttackMob, IRangedRecruit, IStrategicFire {

    private static final EntityDataAccessor<Boolean> DATA_IS_CHARGING_CROSSBOW = SynchedEntityData.defineId(CrossBowmanEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<BlockPos>> STRATEGIC_FIRE_POS = SynchedEntityData.defineId(CrossBowmanEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Boolean> SHOULD_STRATEGIC_FIRE = SynchedEntityData.defineId(CrossBowmanEntity.class, EntityDataSerializers.BOOLEAN);


    public CrossBowmanEntity(EntityType<? extends AbstractRecruitEntity> entityType, Level world) {
        super(entityType, world);

    }

    private final Predicate<ItemEntity> ALLOWED_ITEMS = (item) ->
            (!item.hasPickUpDelay() && item.isAlive() && getInventory().canAddItem(item.getItem()) && this.wantsToPickUp(item.getItem()));

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_IS_CHARGING_CROSSBOW, false);
        this.entityData.define(STRATEGIC_FIRE_POS, Optional.empty());
        this.entityData.define(SHOULD_STRATEGIC_FIRE, false);
    }

	@Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);

        nbt.putBoolean("isChargingCrossbow", this.getChargingCrossbow());

        // [수정] getStrategicFirePos() 사용 (get 추가)
        if(this.getStrategicFirePos() != null){
            nbt.putInt("StrategicFirePosX", this.getStrategicFirePos().getX());
            nbt.putInt("StrategicFirePosY", this.getStrategicFirePos().getY());
            nbt.putInt("StrategicFirePosZ", this.getStrategicFirePos().getZ());
            nbt.putBoolean("ShouldStrategicFire", this.getShouldStrategicFire());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);

        this.setChargingCrossbow(nbt.getBoolean("isChargingCrossbow"));

        if (nbt.contains("StrategicFirePosX") && nbt.contains("StrategicFirePosY") && nbt.contains("StrategicFirePosZ")) {
            this.setStrategicFirePos(new BlockPos (
                    nbt.getInt("StrategicFirePosX"),
                    nbt.getInt("StrategicFirePosY"),
                    nbt.getInt("StrategicFirePosZ")));
            this.setShouldStrategicFire(nbt.getBoolean("ShouldStrategicFire"));
        }
    }
	
    @Override
    public int getBlockCoolDown(){
        return 100;
    }
	
    @Override
    protected void registerGoals() {
        super.registerGoals();
				// [신규] 대포(LandCannon) 운용 AI 등록 (우선순위 0)
		this.goalSelector.addGoal(0, new RecruitLandCannonStrategicFire(this));
        this.goalSelector.addGoal(1, new RecruitLandCannonAttackGoal(this));
		this.goalSelector.addGoal(2, new RecruitRangedCrossbowAttackGoal(this, this.getMeleeStartRange()));
		// UseShield AI를 우선순위 3으로 추가합니다.
		this.goalSelector.addGoal(3, new UseShield(this)); 
        if(isMusketModLoaded){
            this.goalSelector.addGoal(4, new RecruitRangedMusketAttackGoal(this, this.getMeleeStartRange()));
        }
        this.goalSelector.addGoal(8, new RecruitMoveTowardsTargetGoal(this, 1.15D, (float) this.getMeleeStartRange()));
    }


    //ATTRIBUTES
    public static AttributeSupplier.Builder setAttributes() {
        return Mob.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(ForgeMod.SWIM_SPEED.get(), 0.3D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.05D)
                .add(Attributes.ATTACK_DAMAGE, 1.5D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(ForgeMod.ENTITY_REACH.get(), 0D)
                .add(Attributes.ATTACK_SPEED);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficultyInstance, MobSpawnType reason, @Nullable SpawnGroupData data, @Nullable CompoundTag nbt) {
        RandomSource randomsource = world.getRandom();
        SpawnGroupData ilivingentitydata = super.finalizeSpawn(world, difficultyInstance, reason, data, nbt);
        ((AsyncGroundPathNavigation)this.getNavigation()).setCanOpenDoors(true);
        this.populateDefaultEquipmentEnchantments(randomsource, difficultyInstance);
        this.initSpawn();
        return ilivingentitydata;
    }

    @Override
    public void initSpawn() {
        this.setCustomName(Component.literal("Crossbowman"));
        this.setCost(RecruitsServerConfig.CrossbowmanCost.get());
        this.setEquipment();
        this.setDropEquipment();
        this.setRandomSpawnBonus();
        this.setPersistenceRequired();

        this.setGroup(2);

        if(RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.get()){
            if(isMusketModLoaded && IWeapon.isMusketModWeapon(this.getMainHandItem())){
                int i = this.getRandom().nextInt(32);
                ItemStack arrows = ForgeRegistries.ITEMS.getDelegateOrThrow(ResourceLocation.tryParse("musketmod:cartridge")).get().getDefaultInstance();
                arrows.setCount(14 + i);
                this.inventory.setItem(6, arrows);
            }
            else RecruitsPatrolSpawn.setRangedArrows(this);
        }

        AbstractRecruitEntity.applySpawnValues(this);
    }

	@Override
	public boolean canHoldItem(ItemStack itemStack){
		// ShieldItem에 대한 제약을 제거하여 방패를 장착할 수 있도록 허용합니다.
		return !(itemStack.getItem() instanceof SwordItem) || itemStack.getItem() instanceof CrossbowItem;
	}
    public void performRangedAttack(@NotNull LivingEntity target, float v) {

    }
    @Override
    public boolean wantsToPickUp(@NotNull ItemStack itemStack) {
        if(isMusketModLoaded && IWeapon.isMusketModWeapon(itemStack)) return true;
        else if ((itemStack.getItem() instanceof BowItem || itemStack.getItem() instanceof ProjectileWeaponItem || itemStack.getItem() instanceof SwordItem) && this.getMainHandItem().isEmpty()){
            return !hasSameTypeOfItem(itemStack);
        }
        else if(itemStack.is(ItemTags.ARROWS) && RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.get())
            return true;
        else
            return super.wantsToPickUp(itemStack);

    }

    @Override
    public Predicate<ItemEntity> getAllowedItems() {
        return ALLOWED_ITEMS;
    }
    @Override
    public double getMeleeStartRange() {
        return 3D;
    }

    //Pillager
    @Override
    public void shootCrossbowProjectile(@NotNull LivingEntity target, @NotNull ItemStack stack, @NotNull Projectile projectile, float f) {
        this.shootCrossbowProjectile(this, target, projectile, f, 1.6F);
    }

    public boolean getChargingCrossbow() {
        return this.entityData.get(DATA_IS_CHARGING_CROSSBOW);
    }

    public void setChargingCrossbow(boolean is) {
        this.entityData.set(DATA_IS_CHARGING_CROSSBOW, is);
    }

    public boolean canFireProjectileWeapon(ProjectileWeaponItem weaponItem) {
        return weaponItem.equals(Items.CROSSBOW);
    }
	public void onCrossbowAttackPerformed() {
        this.noActionTime = 0;
    }

    // [중요 수정] IStrategicFire 인터페이스 구현 메서드 (get 붙임)
    public void setStrategicFirePos(BlockPos pos) {
        if (pos != null) {
            this.entityData.set(STRATEGIC_FIRE_POS, Optional.of(pos));
        } else {
            this.entityData.set(STRATEGIC_FIRE_POS, Optional.empty());
        }
    }
	@Override // [수정] getStrategicFirePos로 이름 변경 및 Override
    public BlockPos getStrategicFirePos(){
        return this.entityData.get(STRATEGIC_FIRE_POS).orElse(null);
    }

    public void clearArrowsPos(){
        this.entityData.set(STRATEGIC_FIRE_POS, Optional.empty());
    }

// [중요 수정] IStrategicFire 인터페이스 구현 메서드
    @Override 
    public void setShouldStrategicFire(boolean bool) {
        this.entityData.set(SHOULD_STRATEGIC_FIRE, bool);
    }
	@Override // [수정] getShouldStrategicFire로 이름 변경 및 Override
    public boolean getShouldStrategicFire(){
        return this.entityData.get(SHOULD_STRATEGIC_FIRE);
    }

    public List<List<String>> getEquipment(){
        return RecruitsServerConfig.CrossbowmanStartEquipments.get();
    }

}
