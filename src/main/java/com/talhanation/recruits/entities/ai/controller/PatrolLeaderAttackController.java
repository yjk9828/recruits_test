package com.talhanation.recruits.entities.ai.controller;

import com.talhanation.recruits.entities.*;
import com.talhanation.recruits.util.FormationUtils;
import com.talhanation.recruits.util.RecruitCommanderUtil;
import com.talhanation.recruits.util.NPCArmy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class PatrolLeaderAttackController implements IAttackController {

    public final AbstractLeaderEntity leader;

    public int timeOut = 1000;
    public Vec3 initPos;

    public PatrolLeaderAttackController(AbstractLeaderEntity recruit) {
        this.leader = recruit;
    }

    public void start(){
        if(!this.leader.getCommandSenderWorld().isClientSide() && leader.enemyArmy != null && leader.army != null){
            double distanceToTarget = this.leader.army.getPosition().distanceToSqr(leader.enemyArmy.getPosition());

            this.leader.army.updateArmy();
            this.leader.enemyArmy.updateArmy();

            if(leader.enemyArmy.size() == 0){
                // enemy army defeated
                this.leader.enemyArmy = null;
                return;
            }
            //To far from init pos -> enemy army is retreating
            if(initPos != null && initPos.distanceToSqr(this.leader.army.getPosition()) > 5000){
                this.leader.enemyArmy = null;
                return;
            }

            RecruitCommanderUtil.setRecruitsAggroState(this.leader.army.getAllRecruitUnits(), leader.getState());
            RecruitCommanderUtil.setRecruitsMoveSpeed( this.leader.army.getAllRecruitUnits(),1.0F);

            this.setRecruitsTargets();

            // [복구됨] 스마트 보고 로직 실행 (InfoMode 체크 포함)
            tryReportEncounter();

            if(distanceToTarget < 2500) {
                if(isArmyScattered()){
                    regroupArmy();
                    return;
                }
                leader.commandCooldown = 400;
                commandArmy(this.leader.army, this.leader.enemyArmy);
            }
            else{
                // [유지] Hold Position 상태가 아닐 때만 접근
                if (!leader.getShouldHoldPos()) {
                    forwarding();
                }
                leader.commandCooldown = 250;
            }
        }
    }

    // [신규] InfoMode에 따라 보고할지 말지 결정하는 로직
    private void tryReportEncounter() {
        // 이미 보고했거나 주인이 없으면 패스
        if(leader.getOwner() == null || leader.hasReportedEncounter) return;
        
        // 적 부대가 비어있으면 패스
        if (leader.enemyArmy == null || leader.enemyArmy.getAllUnits().isEmpty()) return;

        byte infoMode = leader.getInfoMode();
        // 1 = NONE 모드면 절대 보고하지 않음
        if (infoMode == 1) return;

        // 타겟 샘플 확인 (첫 번째 적 기준)
        LivingEntity targetSample = leader.enemyArmy.getAllUnits().get(0);
        
        boolean isMonster = targetSample.getType().getCategory() == MobCategory.MONSTER || targetSample instanceof Pillager;
        boolean isPlayerOrRecruit = targetSample instanceof Player || targetSample instanceof AbstractRecruitEntity;

        boolean isValidTarget = false;

        // 2 = ENEMIES 모드: 플레이어/Recruit일 때만 보고
        if (infoMode == 2) { 
            if (isPlayerOrRecruit) isValidTarget = true;
        }
        // 3 = HOSTILE 모드: 몬스터일 때만 보고
        else if (infoMode == 3) {
            if (isMonster) isValidTarget = true;
        }
        // 0 = ALL 모드: 둘 다 보고
        else if (infoMode == 0) {
            isValidTarget = true;
        }

        // 설정된 공격 대상(Valid Target)일 때만 메시지 전송
        if (isValidTarget) {
            sendToOwner("Enemy contact! Im advancing, their size is " + leader.enemyArmy.size());
            leader.hasReportedEncounter = true; // 보고 완료 처리
        }
    }

    public void tick() {
        if(leader.commandCooldown == 0){
            leader.commandCooldown = 400;
            start();
        }
    }

    @Override
    public void setInitPos(Vec3 pos) {
        initPos = pos;
    }

    @Override
    public boolean isTargetInRange() {
        return false;
    }

    private boolean isArmyScattered() {
        List<AbstractRecruitEntity> recruits = this.leader.army.getAllRecruitUnits();
        if (recruits.isEmpty()) return false;

        Vec3 commanderPos = this.leader.position();
        double maxDistance = 500.0;

        int scatteredCount = 0;
        for (AbstractRecruitEntity recruit : recruits) {
            double distance = recruit.position().distanceToSqr(commanderPos);
            if (distance > maxDistance) {
                scatteredCount++;
            }
        }


        return scatteredCount >= (recruits.size() / 2);
    }
    
    public void commandArmy(NPCArmy playerArmy, NPCArmy enemyArmy) {
        double distance = playerArmy.getPosition().distanceTo(enemyArmy.getPosition());
        int ownArmySize = playerArmy.getTotalUnits();
        int enemyArmySize = enemyArmy.getTotalUnits();
        double ownMorale = playerArmy.getAverageMorale();
        double enemyMorale = enemyArmy.getAverageMorale();
        int ownRangedUnits = playerArmy.getRanged().size();
        int enemyRangedUnits = enemyArmy.getRanged().size();
        int ownCavalry = playerArmy.getCavalry().size();
        int enemyCavalry = enemyArmy.getCavalry().size();
        int ownShieldmen = playerArmy.getShieldmen().size();
        int enemyShieldmen = enemyArmy.getShieldmen().size();

        double ownAverageHealth = playerArmy.getAverageHealth();
        double enemyAverageHealth = enemyArmy.getAverageHealth();

        if (ownArmySize >= 2 * enemyArmySize || ownAverageHealth > 50) {
            if(distance < 1000) charge();
            else forwarding();
        }
        else if (ownMorale > 70 && enemyMorale < 30) {
            forwarding();
        }
        else if (enemyArmySize >= 2 * ownArmySize || ownMorale < 20 || enemyAverageHealth > 50) {
            back();
        }
        else if (enemyRangedUnits > ownCavalry + ownShieldmen) {
            back();
        }
        else {
            if(distance < 1000) defaultAttack();
            else forwarding();
        }

        if(distance < 200){
            RecruitCommanderUtil.setRecruitsShields(this.leader.army.getRecruitShieldmen(), false);
        }
        else if(enemyRangedUnits >= ownShieldmen){
            RecruitCommanderUtil.setRecruitsShields(this.leader.army.getRecruitShieldmen(), true);
        }

    }

    public void shieldWall(){
        Vec3 target = leader.enemyArmy.getPosition();
        Vec3 toTarget = leader.position().vectorTo(target).normalize();
        Vec3 movePosInfantry = getPosTowardsTarget(target, 0.05);
        Vec3 movePosRanged = getPosTowardsTarget(target, -0.1);

        FormationUtils.lineFormation(toTarget, leader.army.getRecruitShieldmen(), movePosInfantry, 30, 1.0);
        FormationUtils.lineFormation(toTarget, leader.army.getRecruitInfantry(), movePosInfantry, 30, 1.0);

        FormationUtils.lineFormation(toTarget, leader.army.getRecruitRanged(), movePosRanged, 20, 1.8);

        RecruitCommanderUtil.setRecruitsWanderFreely(this.leader.army.getRecruitCavalry());
    }

    public void charge(){
        BlockPos movePosLeader = getBlockPosTowardsTarget(this.leader.enemyArmy.getPosition(), 0.2);
        this.leader.setHoldPos(Vec3.atCenterOf(movePosLeader));
        
        // [유지] 주인이 Follow/Hold 명령을 내렸다면 상태를 강제로 바꾸지 않음
        setTacticalState();

        RecruitCommanderUtil.setRecruitsWanderFreely(this.leader.army.getAllRecruitUnits());
        this.setRecruitsTargets();
    }

    public void defaultAttack(){
        Vec3 target = leader.enemyArmy.getPosition();
        Vec3 toTarget = leader.position().vectorTo(target).normalize();
        Vec3 movePosRanged = getPosTowardsTarget(target, 0.4);
        BlockPos movePosLeader = getBlockPosTowardsTarget(target, 0.2);
        Vec3 movePosInfantry = getPosTowardsTarget(target, 0.6);

        FormationUtils.lineFormation(toTarget, this.leader.army.getRecruitInfantry(), movePosInfantry, 20, 3.25);
        RecruitCommanderUtil.setRecruitsWanderFreely(this.leader.army.getRecruitShieldmen());
        FormationUtils.lineFormation(toTarget, leader.army.getRecruitRanged(), movePosRanged, 20, 3.25);
        RecruitCommanderUtil.setRecruitsWanderFreely(this.leader.army.getRecruitCavalry());

        this.setRecruitsTargets();

        this.leader.setHoldPos(Vec3.atCenterOf(movePosLeader));
        
        setTacticalState();
    }

    public void regroupArmy(){
        Vec3 target = leader.enemyArmy.getPosition();
        Vec3 toTarget = leader.position().vectorTo(target).normalize();
        Vec3 movePosInfantry = getPosTowardsTarget(target, 0.1);
        Vec3 movePosRanged = getPosTowardsTarget(target, -0.1);
        Vec3 movePosCav = getPosTowardsTarget(target, 0.0);

        FormationUtils.lineFormation(toTarget, leader.army.getRecruitInfantry(), movePosInfantry, 20, 1.75);
        FormationUtils.lineFormation(toTarget, leader.army.getRecruitShieldmen(), movePosInfantry, 10, 2.25);
        FormationUtils.lineFormation(toTarget, leader.army.getRecruitRanged(), movePosRanged, 20, 3.0);
        FormationUtils.squareFormation(toTarget, leader.army.getRecruitCavalry(), movePosCav, 2.0);
    }

    public void forwarding(){
        Vec3 target = leader.enemyArmy.getPosition();
        Vec3 toTarget = leader.position().vectorTo(target).normalize();
        Vec3 movePosInfantry = getPosTowardsTarget(target, 0.6);
        Vec3 movePosRanged = getPosTowardsTarget(target, 0.4);
        Vec3 movePosCav = getPosTowardsTarget(target, 0.2);
        BlockPos movePosLeader = getBlockPosTowardsTarget(target, 0.3);

        FormationUtils.lineFormation(toTarget, leader.army.getRecruitInfantry(), movePosInfantry, 20, 1.75);
        FormationUtils.lineFormation(toTarget, leader.army.getRecruitShieldmen(), movePosInfantry, 10, 2.25);
        FormationUtils.lineFormation(toTarget, leader.army.getRecruitRanged(), movePosRanged, 20, 3.0);
        FormationUtils.squareFormation(toTarget, leader.army.getRecruitCavalry(), movePosCav, 2.0);

        this.leader.setHoldPos(Vec3.atCenterOf(movePosLeader));
        
        setTacticalState();
    }

    public void back(){
        Vec3 target = leader.enemyArmy.getPosition();
        Vec3 toTarget = leader.position().vectorTo(target).normalize();
        Vec3 movePosInfantry = getPosTowardsTarget(target, -0.4);
        Vec3 movePosRanged = getPosTowardsTarget(target, -0.6);
        BlockPos movePosLeader = getBlockPosTowardsTarget(target, -0.7);

        FormationUtils.lineFormation(toTarget, leader.army.getRecruitInfantry(), movePosInfantry, 20, 1.25);
        FormationUtils.lineFormation(toTarget, leader.army.getRecruitShieldmen(), movePosInfantry, 20, 1.25);
        FormationUtils.lineFormation(toTarget, leader.army.getRecruitRanged(), movePosRanged, 20, 2.25);
        FormationUtils.lineFormation(toTarget, leader.army.getRecruitCavalry(), movePosRanged, 20, 2.25);

        this.leader.setHoldPos(Vec3.atCenterOf(movePosLeader));
        
        setTacticalState();
    }

    // [유지] 유저가 내린 명령(Follow, Hold)을 AI가 덮어쓰지 못하게 막는 헬퍼 메서드
    private void setTacticalState() {
        int currentState = this.leader.getFollowState();
        // 1: Follow, 2: Hold Position, 4: Hold My Position
        // 이 상태들일 때는 AI가 '3(Back to Pos/Tactical Hold)'으로 바꾸지 않음
        if (currentState != 1 && currentState != 2 && currentState != 4) {
            this.leader.setFollowState(3);
        }
    }

    public BlockPos getBlockPosTowardsTarget(Vec3 target, double x){
        Vec3 pos = leader.position().lerp(target, x);
        return FormationUtils.getPositionOrSurface(leader.getCommandSenderWorld(), new BlockPos((int) pos.x, (int) pos.y, (int) pos.z));
    }
    public Vec3 getPosTowardsTarget(Vec3 target, double x){
        return leader.position().lerp(target, x);
    }

    public void setRecruitsTargets() {
        for(int i = 0; i < this.leader.army.getAllRecruitUnits().size(); i++){
            AbstractRecruitEntity recruit = this.leader.army.getAllRecruitUnits().get(i);
            if(this.leader.enemyArmy.size() > i) recruit.setTarget(this.leader.enemyArmy.getAllUnits().get(i));
        }
    }

    public boolean canAttack(LivingEntity living) {
        int aggroState = this.leader.getState();
        switch(aggroState){
            case 0 -> { //Neutral
                if(living instanceof Monster){
                    return this.leader.canAttack(living);
                }
            }
            case 1 -> { //AGGRO
                if(living instanceof Player || living instanceof AbstractRecruitEntity || living instanceof Monster){
                    return this.leader.canAttack(living);
                }
            }
            case 2 -> { //RAID
                return this.leader.canAttack(living);
            }

            default -> {
                return false;
            }
        }
        return false;
    }

    public void sendToOwner(String string){
        if(leader.getOwner() != null)
            this.leader.getOwner().sendSystemMessage(Component.literal(leader.getName().getString() + ": " + string));

    }

}