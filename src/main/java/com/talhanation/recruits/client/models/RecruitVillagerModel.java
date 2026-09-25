package com.talhanation.recruits.client.models;

import com.google.common.collect.ImmutableList;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
// [중요] 아래 3개 import가 꼭 있어야 에러가 안 납니다!
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ShieldItem;

import java.util.List;
import java.util.Random;

public class RecruitVillagerModel extends HumanoidModel<AbstractRecruitEntity> {
    private final List<ModelPart> parts;

    public RecruitVillagerModel(ModelPart part) {
        super(part);
        this.parts = part.getAllParts().filter((parts) -> {
            return !parts.isEmpty();
        }).collect(ImmutableList.toImmutableList());
    }
    
    public static LayerDefinition createLayerDefinition() {
        MeshDefinition meshdefinition = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition partdefinition = meshdefinition.getRoot();
        PartDefinition head = partdefinition.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F), PartPose.ZERO);
        head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(24, 0).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(0.0F, -2.0F, 0.0F));
        partdefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 20).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
        partdefinition.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(44, 22).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, 2.0F, 0.0F));
        partdefinition.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(44, 22).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(5.0F, 2.0F, 0.0F));
        partdefinition.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.9F, 12.0F, 0.0F));
        partdefinition.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 22).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(1.9F, 12.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    public void setRotateAngle(ModelPart ModelRenderer, float x, float y, float z) {
        ModelRenderer.xRot = x;
        ModelRenderer.yRot = y;
        ModelRenderer.zRot = z;
    }

    @Override
    public void setupAnim(AbstractRecruitEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // 1. 기본 애니메이션 (걷기 등)
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        // 2. 방패 사용 시 전술적 자세 (Tactical Stance) 적용
        if (entity.isUsingItem() && entity.getUseItem().getItem() instanceof ShieldItem) {
            
            float radianFactor = (float)Math.PI / 180F;
            float pitchRad = headPitch * radianFactor; 
            float yawRad = netHeadYaw * radianFactor;  

            // 팔 구분 로직
            boolean isMainHandShield = entity.getUsedItemHand() == InteractionHand.MAIN_HAND;
            boolean isRightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
            
            ModelPart weaponArm; 
            ModelPart shieldArm; 
            
            if (isRightHanded) {
                weaponArm = isMainHandShield ? this.leftArm : this.rightArm;
                shieldArm = isMainHandShield ? this.rightArm : this.leftArm;
            } else {
                weaponArm = isMainHandShield ? this.rightArm : this.leftArm;
                shieldArm = isMainHandShield ? this.leftArm : this.rightArm;
            }

            // [주무기] 정면 조준 (-1.57F = 90도) + 시선 추적
            weaponArm.xRot = -1.57F + pitchRad; 
            
            // 조준선 정렬 (몸 안쪽으로 살짝 모음)
            if (weaponArm == this.rightArm) {
                weaponArm.yRot = yawRad - 0.1F; 
            } else {
                weaponArm.yRot = yawRad + 0.1F;
            }

            // [방패] 시선에 따라 방어 방향 조정
            shieldArm.xRot += pitchRad; 
            shieldArm.yRot += yawRad;   
        }
    }

    public ModelPart getRandomModelPart(Random p_103407_) {
        return this.parts.get(p_103407_.nextInt(this.parts.size()));
    }
}