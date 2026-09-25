package com.talhanation.recruits.entities.ai;

import com.talhanation.recruits.entities.CaptainEntity;
import com.talhanation.smallships.world.entity.ship.Ship;
import com.talhanation.smallships.world.entity.ship.abilities.Cannonable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class CaptainStrategicFire extends Goal {
    private final CaptainEntity captain;

    private static final int RAPID_MIN = 2;
    private static final int RAPID_MAX = 6;
    // 마인크래프트 투사체(화살/포탄)의 일반적인 중력 가속도 근사값 (0.05)
    // 포탄이 너무 무겁게 떨어지면 0.03 정도로 낮추고, 너무 뜨면 0.06으로 높이세요.
    private static final double PROJECTILE_GRAVITY = 0.025; 
    private int attackTime = 0;

    public CaptainStrategicFire(CaptainEntity captain, int ignoredMin, int ignoredMax) {
        this.captain = captain;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (captain.level().isClientSide()) return false;
        if (!(captain.getVehicle() instanceof Boat boat)) return false;
        if (!(boat instanceof Cannonable)) return false;
        if (!captain.getShouldStrategicFire()) return false;
        return captain.getStrategicFirePos() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        attackTime = 0;
        captain.setTarget(null);
        if (captain.smallShipsController != null) {
            captain.smallShipsController.target = null;
        }
    }

    @Override
    public void stop() {
        attackTime = 0;
    }

    @Override
    public void tick() {
        final BlockPos bp = captain.getStrategicFirePos();
        if (bp == null) return;

        if (!(captain.getVehicle() instanceof Boat boat)) return;
        if (!(boat instanceof Cannonable cannonShip)) return;

        if (!cannonShip.canShoot()) { attackTime = 10; return; }

        if (attackTime <= 0) {
            // 1. 기본 정보 계산
            Vec3 from = boat.position(); // 발사 원점 (배의 중심)
            // 목표 지점 (블록의 중앙)
            Vec3 targetPos = new Vec3(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);

            double diffX = targetPos.x - from.x;
            double diffZ = targetPos.z - from.z;
            double diffY = targetPos.y - from.y;

            // 수평 거리 (Horizontal Distance)
            double distH = Math.sqrt(diffX * diffX + diffZ * diffZ);
            
            // 너무 가까우면(1블록 이내) 발사 안 함 (자폭 방지)
            if (distH < 1.0) return;

            // 2. 포탄 속도 가져오기
            float basePower = boat.getEntityData().get(Ship.CANNON_POWER);
            double speed = basePower + cannonShip.getCannonModifier();
            
            // 3. 탄도학 계산 (Ballistics)
            // 공식: 비행 시간(t) ≈ 거리 / 속도
            // 낙차(drop) ≈ 0.5 * g * t^2
            // 목표 높이 보정 = 원래 높이 차이 + 낙차
            
            double flightTime = distH / speed; 
            double dropHeight = 0.5 * PROJECTILE_GRAVITY * flightTime * flightTime;
            
            // 목표 지점보다 'dropHeight'만큼 위를 보고 쏴야 거기 떨어짐
            double targetY = diffY + dropHeight;

            // 4. 최종 발사 벡터 생성
            // 수평 방향은 그대로 유지하되, 수직(Y) 방향만 보정된 값을 사용
            Vec3 aimVec = new Vec3(diffX, targetY, diffZ).normalize();
            
            double yShoot = aimVec.y;
            double accuracy = 0.0; // 0.0 = 완전 정확 (필요시 오차 추가)

            // 5. 발사 명령
            // 이제 aiming 벡터가 약간 위를 향하므로 배의 난간이나 물을 넘어서 날아감
            cannonShip.triggerCannons(aimVec, yShoot, captain, speed, accuracy);

            int span = RAPID_MAX - RAPID_MIN + 1;
            attackTime = RAPID_MIN + captain.getRandom().nextInt(span);
        } else {
            attackTime--;
        }
    }
}