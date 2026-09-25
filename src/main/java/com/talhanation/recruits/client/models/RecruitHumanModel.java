package com.talhanation.recruits.client.models;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

public class RecruitHumanModel extends HumanoidModel<AbstractRecruitEntity> {

    public RecruitHumanModel(ModelPart part) {
        super(part);
    }

	@Override
	public void setupAnim(AbstractRecruitEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

		// [초기화] 매 프레임 리셋 (필수)
		this.rightArm.x = -5.0F; this.rightArm.y = 2.0F; this.rightArm.z = 0.0F;
		this.leftArm.x = 5.0F;   this.leftArm.y = 2.0F;  this.leftArm.z = 0.0F;
		
		// 몸통 회전도 반드시 초기화해야 걸어다닐 때 게걸음 안 걷습니다.
		this.body.yRot = 0.0F; 

		float radianFactor = (float)Math.PI / 180F;
		float pitchRad = headPitch * radianFactor;
		float yawRad = netHeadYaw * radianFactor;

		// =========================================================
		// [조건 1] 방패 사용 중
		// =========================================================
		if (entity.isUsingItem() && entity.getUseItem().getItem() instanceof ShieldItem) {
			// (방패 로직은 기존 유지 - 필요하면 복사해서 넣으세요)
			boolean isMainHandShield = entity.getUsedItemHand() == InteractionHand.MAIN_HAND;
			boolean isRightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
			ModelPart weaponArm = isRightHanded ? (isMainHandShield ? this.leftArm : this.rightArm) : (isMainHandShield ? this.rightArm : this.leftArm);
			ModelPart shieldArm = isRightHanded ? (isMainHandShield ? this.rightArm : this.leftArm) : (isMainHandShield ? this.leftArm : this.rightArm);

			weaponArm.xRot = -1.57F + pitchRad; 
			weaponArm.yRot = yawRad + (weaponArm == this.rightArm ? -0.1F : 0.1F);
			shieldArm.xRot = -1.1F + pitchRad; 
			shieldArm.yRot = yawRad + (shieldArm == this.rightArm ? -0.75F : 0.75F);
		} 
		// =========================================================
		// [조건 2] 총기 파지 (몸통 비틀기 적용)
		// =========================================================
		else {
			ItemStack mainItem = entity.getMainHandItem();
			
			if (!mainItem.isEmpty()) {
				ResourceLocation regName = ForgeRegistries.ITEMS.getKey(mainItem.getItem());
				
				if (regName != null) {
					String itemId = regName.toString();

					if (itemId.startsWith("jeg:") || itemId.startsWith("mteg:")) {
						
						// --- [Type A] 권총류 (기존 유지) ---
						if (itemId.equals("mteg:m1911") || itemId.equals("jeg:combat_pistol")) {
							this.rightArm.xRot = -1.57F + pitchRad;
							this.rightArm.yRot = yawRad - 0.1F; 
							this.leftArm.xRot = -1.57F + pitchRad;
							this.leftArm.yRot = yawRad + 0.8F; 
						} 
						// --- [Type B] 소총/라이플류 (★ 몸통 회전 로직 ★) ---
						else {
							// 1. [몸통 & 머리] 자세 제어
							// 몸통을 오른쪽으로 0.5라디안(약 30도) 틉니다.
							// 이렇게 하면 오른쪽 어깨는 뒤로, 왼쪽 어깨는 앞으로 나갑니다.
							float bodyTurnAngle = 0.5F; 
							this.body.yRot = bodyTurnAngle;

							// 머리는 몸이 돌아간 만큼 반대로 돌려야 정면(Target)을 봅니다.
							// yawRad(시선) - bodyTurnAngle(몸통각도)
							this.head.yRot = yawRad - bodyTurnAngle;


							// 2. [오른팔] 방아쇠 (몸이 돌아갔으므로 팔은 상대적으로 덜 꺾어도 됨)
							// 위치: 몸 안쪽으로 살짝 이동 (-4.0F)
							this.rightArm.x = -4.0F; 
							this.rightArm.z = 1.0F; // 견착을 위해 살짝 뒤로
							
							this.rightArm.xRot = -1.57F + pitchRad;
							// 몸이 이미 오른쪽으로 돌았으므로, 팔은 왼쪽(안쪽)으로 살짝만 꺾으면 정렬됨
							this.rightArm.yRot = yawRad - 0.4F; 


							// 3. [왼팔] 총열 덮개
							// 위치: 몸 안쪽으로 이동 (4.0F)
							// 몸이 왼쪽 어깨를 앞으로 보냈으므로, 억지로 z를 뺄 필요가 줄어듦
							this.leftArm.x = 4.0F;
							this.leftArm.z = -1.0F; // 살짝만 앞으로

							this.leftArm.xRot = -1.57F + pitchRad;
							// 몸이 오른쪽으로 돌았으니, 왼팔은 오른쪽(안쪽)으로 꺾어야 총을 잡음
							this.leftArm.yRot = yawRad + 0.0F; 
						}
					}
				}
			}
		}
	}
}